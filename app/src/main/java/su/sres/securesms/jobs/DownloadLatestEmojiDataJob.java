package su.sres.securesms.jobs;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.IntPair;
import com.annimon.stream.Stream;
import com.mobilecoin.lib.util.Hex;

import okio.Sink;
import su.sres.core.util.logging.Log;
import su.sres.securesms.components.emoji.EmojiPageModel;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.emoji.EmojiData;
import su.sres.securesms.emoji.EmojiFiles;
import su.sres.securesms.emoji.EmojiImageRequest;
import su.sres.securesms.emoji.EmojiJsonRequest;
import su.sres.securesms.emoji.EmojiPageCache;
import su.sres.securesms.emoji.EmojiRemote;
import su.sres.securesms.emoji.EmojiSource;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.AutoDownloadEmojiConstraint;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.util.FileUtils;
import su.sres.securesms.util.ScreenDensity;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Okio;
import okio.Source;

/**
 * Downloads Emoji JSON and Images to local persistent storage.
 * <p>
 */
public class DownloadLatestEmojiDataJob extends BaseJob {

  private static final long INTERVAL_WITHOUT_REMOTE_DOWNLOAD = TimeUnit.DAYS.toMillis(1);
  private static final long INTERVAL_WITH_REMOTE_DOWNLOAD    = TimeUnit.DAYS.toMillis(7);

  private static final String TAG = Log.tag(DownloadLatestEmojiDataJob.class);

  public static final  String KEY             = "DownloadLatestEmojiDataJob";
  private static final String QUEUE_KEY       = "EmojiDownloadJobs";
  private static final String VERSION_INT     = "version_int";
  private static final String VERSION_UUID    = "version_uuid";
  private static final String VERSION_DENSITY = "version_density";

  private EmojiFiles.Version targetVersion;

  public static void scheduleIfNecessary(@NonNull Context context) {
    long nextScheduledCheck = SignalStore.emojiValues().getNextScheduledImageCheck();

    if (nextScheduledCheck <= System.currentTimeMillis()) {
      Log.i(TAG, "Scheduling DownloadLatestEmojiDataJob.");
      ApplicationDependencies.getJobManager().add(new DownloadLatestEmojiDataJob(false));

      EmojiFiles.Version version = EmojiFiles.Version.readVersion(context);

      long interval;
      if (EmojiFiles.Version.isVersionValid(context, version)) {
        interval = INTERVAL_WITH_REMOTE_DOWNLOAD;
      } else {
        interval = INTERVAL_WITHOUT_REMOTE_DOWNLOAD;
      }

      SignalStore.emojiValues().setNextScheduledImageCheck(System.currentTimeMillis() + interval);
    }
  }

  public DownloadLatestEmojiDataJob(boolean force) {
    this(new Job.Parameters.Builder()
             .setQueue(QUEUE_KEY)
             .addConstraint(force ? NetworkConstraint.KEY : AutoDownloadEmojiConstraint.KEY)
             .setMaxInstancesForQueue(1)
             .setMaxAttempts(5)
             .setLifespan(TimeUnit.DAYS.toMillis(1))
             .build(), null);
  }

  public DownloadLatestEmojiDataJob(@NonNull Parameters parameters, @Nullable EmojiFiles.Version targetVersion) {
    super(parameters);
    this.targetVersion = targetVersion;
  }

  @Override
  protected void onRun() throws Exception {
    EmojiFiles.Version version       = EmojiFiles.Version.readVersion(context);
    int                localVersion  = (version != null) ? version.getVersion() : 0;
    int                serverVersion = EmojiRemote.getVersion();
    String             bucket;

    if (targetVersion == null) {
      ScreenDensity density = ScreenDensity.get(context);

      bucket = getDesiredRemoteBucketForDensity(density);
    } else {
      bucket = targetVersion.getDensity();
    }

    Log.d(TAG, "LocalVersion: " + localVersion + ", ServerVersion: " + serverVersion + ", Bucket: " + bucket);

    if (bucket == null) {
      Log.d(TAG, "This device has too low a display density to download remote emoji.");
    } else if (localVersion == serverVersion) {
      Log.d(TAG, "Already have latest emoji data. Skipping.");
    } else if (serverVersion > localVersion) {
      Log.d(TAG, "New server data detected. Starting download...");

      if (targetVersion == null || targetVersion.getVersion() != serverVersion) {
        targetVersion = new EmojiFiles.Version(serverVersion, UUID.randomUUID(), bucket);
      }

      if (isCanceled()) {
        Log.w(TAG, "Job was cancelled prior to downloading json.");
        return;
      }

      EmojiData    emojiData          = downloadJson(context, targetVersion);
      List<String> supportedDensities = emojiData.getDensities();
      String       format             = emojiData.getFormat();
      List<String> imagePaths = Stream.of(emojiData.getDataPages())
                                      .map(EmojiPageModel::getSpriteUri)
                                      .map(Uri::getLastPathSegment)
                                      .toList();

      String density = resolveDensity(supportedDensities, targetVersion.getDensity());
      targetVersion = new EmojiFiles.Version(targetVersion.getVersion(), targetVersion.getUuid(), density);

      if (isCanceled()) {
        Log.w(TAG, "Job was cancelled after downloading json.");
        return;
      }

      downloadImages(context, targetVersion, imagePaths, format, this::isCanceled);

      if (isCanceled()) {
        Log.w(TAG, "Job was cancelled during or after downloading images.");
        return;
      }

      clearOldEmojiData(context, targetVersion);
      markComplete(targetVersion);
      EmojiSource.refresh();
    } else {
      Log.d(TAG, "Server has an older version than we do. Skipping.");
    }
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof IOException;
  }

  @Override
  public @NonNull Data serialize() {
    if (targetVersion == null) {
      return Data.EMPTY;
    } else {
      return new Data.Builder()
          .putInt(VERSION_INT, targetVersion.getVersion())
          .putString(VERSION_UUID, targetVersion.getUuid().toString())
          .putString(VERSION_DENSITY, targetVersion.getDensity())
          .build();
    }
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onFailure() {
  }

  private static @Nullable String getDesiredRemoteBucketForDensity(@NonNull ScreenDensity screenDensity) {
    if (screenDensity.isKnownDensity()) {
      return screenDensity.getBucket();
    } else {
      return "xhdpi";
    }
  }

  private static @Nullable String resolveDensity(@NonNull List<String> supportedDensities, @NonNull String desiredDensity) {
    if (supportedDensities.isEmpty()) {
      throw new IllegalStateException("Version does not have any supported densities.");
    }

    if (supportedDensities.contains(desiredDensity)) {
      Log.d(TAG, "Version supports our density.");
      return desiredDensity;
    } else {
      Log.d(TAG, "Version does not support our density.");
    }

    List<String> allDensities = Arrays.asList("ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi");

    int desiredIndex = allDensities.indexOf(desiredDensity);

    if (desiredIndex == -1) {
      Log.d(TAG, "Unknown density. Falling back...");
      if (supportedDensities.contains("xhdpi")) {
        return "xhdpi";
      } else {
        return supportedDensities.get(0);
      }
    }

    return Stream.of(allDensities)
                 .indexed()
                 .sorted((lhs, rhs) -> {
                   int lhsDistance = Math.abs(desiredIndex - lhs.getFirst());
                   int rhsDistance = Math.abs(desiredIndex - rhs.getFirst());

                   int comp = Integer.compare(lhsDistance, rhsDistance);
                   if (comp == 0) {
                     return Integer.compare(lhs.getFirst(), rhs.getFirst());
                   } else {
                     return comp;
                   }
                 })
                 .map(IntPair::getSecond)
                 .filter(supportedDensities::contains)
                 .findFirst()
                 .orElseThrow(() -> new IllegalStateException("No density available."));
  }

  private static @Nullable byte[] getRemoteImageHash(@NonNull EmojiFiles.Version version, @NonNull String imagePath, @NonNull String format) {
    return EmojiRemote.getMd5(new EmojiImageRequest(version.getVersion(), version.getDensity(), imagePath, format));
  }

  private static @NonNull EmojiData downloadJson(@NonNull Context context, @NonNull EmojiFiles.Version version) throws IOException, InvalidEmojiDataJsonException {
    EmojiFiles.NameCollection names      = EmojiFiles.NameCollection.read(context, version);
    UUID                      emojiData  = names.getUUIDForEmojiData();
    byte[]                    remoteHash = EmojiRemote.getMd5(new EmojiJsonRequest(version.getVersion()));
    byte[]                    localHash;

    if (emojiData != null) {
      localHash = EmojiFiles.getMd5(context, version, emojiData);
    } else {
      localHash = null;
    }

    if (!Arrays.equals(localHash, remoteHash)) {
      Log.d(TAG, "Downloading JSON from Remote");
      assertRemoteDownloadConstraints(context);
      EmojiFiles.Name name = downloadAndVerifyJsonFromRemote(context, version);
      EmojiFiles.NameCollection.append(context, names, name);
    } else {
      Log.d(TAG, "Already have JSON from remote, skipping download");
    }

    EmojiData latestData = EmojiFiles.getLatestEmojiData(context, version);

    if (latestData == null) {
      throw new InvalidEmojiDataJsonException();
    }

    return latestData;
  }

  private static void downloadImages(@NonNull Context context,
                                     @NonNull EmojiFiles.Version version,
                                     @NonNull List<String> imagePaths,
                                     @NonNull String format,
                                     @NonNull Producer<Boolean> cancelled) throws IOException
  {
    EmojiFiles.NameCollection names = EmojiFiles.NameCollection.read(context, version);
    for (final String imagePath : imagePaths) {
      if (cancelled.produce()) {
        Log.w(TAG, "Job was cancelled while downloading images.");
        return;
      }

      UUID   uuid = names.getUUIDForName(imagePath);
      byte[] hash;

      if (uuid != null) {
        hash = EmojiFiles.getMd5(context, version, uuid);
      } else {
        hash = null;
      }

      byte[] ImageHash = getRemoteImageHash(version, imagePath, format);
      if (hash == null || !Arrays.equals(hash, ImageHash)) {
        if (hash != null) {
          Log.d(TAG, "Hash mismatch. Deleting data and re-downloading file.");
          EmojiFiles.delete(context, version, uuid);
        }

        assertRemoteDownloadConstraints(context);
        EmojiFiles.Name name = downloadAndVerifyImageFromRemote(context, version, version.getDensity(), imagePath, format);
        names = EmojiFiles.NameCollection.append(context, names, name);
      } else {
        Log.d(TAG, "Already have Image from remote, skipping download");
      }
    }
  }

  private void markComplete(@NonNull EmojiFiles.Version version) {
    EmojiFiles.Version.writeVersion(context, version);
  }

  private static void assertRemoteDownloadConstraints(@NonNull Context context) throws IOException {
    if (!AutoDownloadEmojiConstraint.canAutoDownloadEmoji(context)) {
      throw new IOException("Network conditions no longer permit download.");
    }
  }

  private static @NonNull EmojiFiles.Name downloadAndVerifyJsonFromRemote(@NonNull Context context, @NonNull EmojiFiles.Version version) throws IOException {
    return downloadAndVerifyFromRemote(context,
                                       version,
                                       () -> EmojiRemote.getObject(new EmojiJsonRequest(version.getVersion())),
                                       EmojiFiles.Name::forEmojiDataJson);
  }

  private static @NonNull EmojiFiles.Name downloadAndVerifyImageFromRemote(@NonNull Context context,
                                                                           @NonNull EmojiFiles.Version version,
                                                                           @NonNull String bucket,
                                                                           @NonNull String imagePath,
                                                                           @NonNull String format) throws IOException
  {
    return downloadAndVerifyFromRemote(context,
                                       version,
                                       () -> EmojiRemote.getObject(new EmojiImageRequest(version.getVersion(), bucket, imagePath, format)),
                                       () -> new EmojiFiles.Name(imagePath, UUID.randomUUID()));
  }

  private static @NonNull EmojiFiles.Name downloadAndVerifyFromRemote(@NonNull Context context,
                                                                      @NonNull EmojiFiles.Version version,
                                                                      @NonNull Producer<Response> responseProducer,
                                                                      @NonNull Producer<EmojiFiles.Name> nameProducer) throws IOException
  {
    try (Response response = responseProducer.produce()) {
      if (!response.isSuccessful()) {
        throw new IOException("Unsuccessful response " + response.code());
      }

      ResponseBody responseBody = response.body();
      if (responseBody == null) {
        throw new IOException("No response body");
      }

      String responseMD5 = getMD5FromResponse(response);
      if (responseMD5 == null) {
        throw new IOException("Invalid ETag on response");
      }

      EmojiFiles.Name name = nameProducer.produce();

      byte[] savedMd5;

      try (OutputStream outputStream = EmojiFiles.openForWriting(context, version, name.getUuid())) {
        Source source = response.body().source();
        Sink   sink   = Okio.sink(outputStream);

        Okio.buffer(source).readAll(sink);
        outputStream.flush();

        source.close();
        sink.close();

        savedMd5 = EmojiFiles.getMd5(context, version, name.getUuid());
      }

      if (!Arrays.equals(savedMd5, Hex.toByteArray(responseMD5))) {
        EmojiFiles.delete(context, version, name.getUuid());
        throw new IOException("MD5 Mismatch.");
      }

      return name;
    }
  }

  private static @Nullable String getMD5FromResponse(@NonNull Response response) {
    Pattern pattern = Pattern.compile(".*([a-f0-9]{32}).*");
    String  header  = response.header("etag");
    Matcher matcher = pattern.matcher(header);

    if (matcher.find()) {
      return matcher.group(1);
    } else {
      return null;
    }
  }

  private static void clearOldEmojiData(@NonNull Context context, @Nullable EmojiFiles.Version newVersion) {
    EmojiFiles.Version version = EmojiFiles.Version.readVersion(context);

    final String currentDirectoryName;
    final String newVersionDirectoryName;

    if (version != null) {
      currentDirectoryName = version.getUuid().toString();
    } else {
      currentDirectoryName = "";
    }

    if (newVersion != null) {
      newVersionDirectoryName = newVersion.getUuid().toString();
    } else {
      newVersionDirectoryName = "";
    }

    File   emojiDirectory = EmojiFiles.getBaseDirectory(context);
    File[] files          = emojiDirectory.listFiles();

    if (files == null) {
      Log.d(TAG, "No emoji data to delete.");
      return;
    }

    Log.d(TAG, "Deleting old folders of emoji data");

    Stream.of(files)
          .filter(File::isDirectory)
          .filterNot(file -> file.getName().equals(currentDirectoryName))
          .filterNot(file -> file.getName().equals(newVersionDirectoryName))
          .forEach(FileUtils::deleteDirectory);

    EmojiPageCache.INSTANCE.clear();
  }

  public static final class Factory implements Job.Factory<DownloadLatestEmojiDataJob> {
    @Override
    public @NonNull DownloadLatestEmojiDataJob create(@NonNull Parameters parameters, @NonNull Data data) {
      final EmojiFiles.Version version;

      if (data.hasInt(VERSION_INT) &&
          data.hasString(VERSION_UUID) &&
          data.hasString(VERSION_DENSITY))
      {
        int    versionInt = data.getInt(VERSION_INT);
        UUID   uuid       = UUID.fromString(data.getString(VERSION_UUID));
        String density    = data.getString(VERSION_DENSITY);

        version = new EmojiFiles.Version(versionInt, uuid, density);
      } else {
        version = null;
      }

      return new DownloadLatestEmojiDataJob(parameters, version);
    }
  }

  private interface Producer<T> {
    @NonNull T produce();
  }

  /**
   * Thrown when the JSON on the server is invalid. In this case, we should NOT
   * try to download this version again.
   */
  private static class InvalidEmojiDataJsonException extends Exception {
  }
}
