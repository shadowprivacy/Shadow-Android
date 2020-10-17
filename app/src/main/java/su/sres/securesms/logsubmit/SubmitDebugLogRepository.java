package su.sres.securesms.logsubmit;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.logging.Log;
import su.sres.securesms.logsubmit.util.Scrubber;
import su.sres.securesms.net.UserAgentInterceptor;
import su.sres.securesms.push.SignalServiceNetworkAccess;
import su.sres.securesms.push.SignalServiceTrustStore;
import su.sres.securesms.util.concurrent.SignalExecutors;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import su.sres.signalservice.api.SignalServiceAccountManager;
import su.sres.signalservice.api.util.Tls12SocketFactory;
import su.sres.signalservice.internal.push.AttachmentV2UploadAttributes;
import su.sres.signalservice.internal.util.BlacklistingTrustManager;

/**
 * Handles retrieving, scrubbing, and uploading of all debug logs.
 *
 * Adding a new log section:
 * - Create a new {@link LogSection}.
 * - Add it to {@link #SECTIONS}. The order of the list is the order the sections are displayed.
 */
public class SubmitDebugLogRepository {

    private static final String TAG = Log.tag(SubmitDebugLogRepository.class);

    private static final char   TITLE_DECORATION = '=';
    private static final int    MIN_DECORATIONS  = 5;
    private static final int    SECTION_SPACING  = 3;
    private static final String DEBUG_LOGS_PATH = "/debuglogs/";

    private final SignalServiceAccountManager accountManager;

    /** Ordered list of log sections. */
    private static final List<LogSection> SECTIONS = new ArrayList<LogSection>() {{
        add(new LogSectionSystemInfo());
        add(new LogSectionJobs());
        if (Build.VERSION.SDK_INT >= 28) {
            add(new LogSectionPower());
        }
        add(new LogSectionThreads());
        add(new LogSectionCapabilities());
        add(new LogSectionFeatureFlags());
        add(new LogSectionPermissions());
        add(new LogSectionLogcat());
        add(new LogSectionLogger());
    }};

    private final Context         context;
    private final ExecutorService executor;

    public SubmitDebugLogRepository() {
        this.context  = ApplicationDependencies.getApplication();
        this.executor = SignalExecutors.SERIAL;
        this.accountManager = ApplicationDependencies.getSignalServiceAccountManager();
    }

    public void getLogLines(@NonNull Callback<List<LogLine>> callback) {
        executor.execute(() -> callback.onResult(getLogLinesInternal()));
    }

    public void submitLog(@NonNull List<LogLine> lines, Callback<Optional<String>> callback) {
        SignalExecutors.UNBOUNDED.execute(() -> callback.onResult(submitLogInternal(lines)));
    }

    @WorkerThread
    private @NonNull Optional<String> submitLogInternal(@NonNull List<LogLine> lines) {
        StringBuilder bodyBuilder = new StringBuilder();
        for (LogLine line : lines) {
            bodyBuilder.append(line.getText()).append('\n');
        }

        try {

            String cloudUrl = accountManager.getConfigurationInfo().getCloudUri() + DEBUG_LOGS_PATH;

            TrustManager[] trustManagers = BlacklistingTrustManager.createFor(new SignalServiceTrustStore(context));
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustManagers, null);

            OkHttpClient client   = new OkHttpClient.Builder()
                    .addInterceptor(new UserAgentInterceptor())
                    .dns(SignalServiceNetworkAccess.DNS)
                    .sslSocketFactory(new Tls12SocketFactory(context.getSocketFactory()), (X509TrustManager)trustManagers[0])
                    .build();

            AttachmentV2UploadAttributes debugLogUploadAttributes = accountManager.getDebugLogUploadAttributes();

            RequestBody requestBody   = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("acl", debugLogUploadAttributes.getAcl())
                    .addFormDataPart("key", debugLogUploadAttributes.getKey())
                    .addFormDataPart("policy", debugLogUploadAttributes.getPolicy())
                    .addFormDataPart("x-amz-algorithm", debugLogUploadAttributes.getAlgorithm())
                    .addFormDataPart("x-amz-credential", debugLogUploadAttributes.getCredential())
                    .addFormDataPart("x-amz-date", debugLogUploadAttributes.getDate())
                    .addFormDataPart("x-amz-signature", debugLogUploadAttributes.getSignature())
                    .addFormDataPart("Content-Type", "text/plain")
                    .addFormDataPart("file", "file", RequestBody.create(MediaType.parse("text/plain"), bodyBuilder.toString()))
                    .build();

            Response postResponse = client.newCall(new Request.Builder().url(cloudUrl).post(requestBody).build()).execute();

            if (!postResponse.isSuccessful()) {
                throw new IOException("Bad response: " + postResponse);
            }

            return Optional.of(cloudUrl + debugLogUploadAttributes.getKey());
        } catch (IOException | NoSuchAlgorithmException | KeyManagementException e) {
            Log.w(TAG, "Error during upload.", e);
            return Optional.absent();
        }
    }

    @WorkerThread
    private @NonNull List<LogLine> getLogLinesInternal() {
        long startTime = System.currentTimeMillis();

        int maxTitleLength = Stream.of(SECTIONS).reduce(0, (max, section) -> Math.max(max, section.getTitle().length()));

        List<Future<List<LogLine>>> futures = new ArrayList<>();

        for (LogSection section : SECTIONS) {
            futures.add(SignalExecutors.BOUNDED.submit(() -> {
                List<LogLine> lines = getLinesForSection(context, section, maxTitleLength);

                if (SECTIONS.indexOf(section) != SECTIONS.size() - 1) {
                    for (int i = 0; i < SECTION_SPACING; i++) {
                        lines.add(SimpleLogLine.EMPTY);
                    }
                }

                return lines;
            }));
        }

        List<LogLine> allLines = new ArrayList<>();

        for (Future<List<LogLine>> future : futures) {
            try {
                allLines.addAll(future.get());
            } catch (ExecutionException | InterruptedException e) {
                throw new AssertionError(e);
            }
        }

        List<LogLine> withIds = new ArrayList<>(allLines.size());

        for (int i = 0; i < allLines.size(); i++) {
            withIds.add(new CompleteLogLine(i, allLines.get(i)));
        }

        Log.d(TAG, "Total time: " + (System.currentTimeMillis() - startTime) + " ms");

        return withIds;
    }

    @WorkerThread
    private static @NonNull List<LogLine> getLinesForSection(@NonNull Context context, @NonNull LogSection section, int maxTitleLength) {
        long startTime = System.currentTimeMillis();

        List<LogLine> out = new ArrayList<>();
        out.add(new SimpleLogLine(formatTitle(section.getTitle(), maxTitleLength), LogLine.Style.NONE));

        CharSequence content = Scrubber.scrub(section.getContent(context));

        List<LogLine> lines = Stream.of(Pattern.compile("\\n").split(content))
                .map(s -> new SimpleLogLine(s, LogStyleParser.parseStyle(s)))
                .map(line -> (LogLine) line)
                .toList();

        out.addAll(lines);

        Log.d(TAG, "[" + section.getTitle() + "] Took " + (System.currentTimeMillis() - startTime) + " ms");

        return out;
    }

    private static @NonNull String formatTitle(@NonNull String title, int maxTitleLength) {
        int neededPadding = maxTitleLength - title.length();
        int leftPadding   = neededPadding  / 2;
        int rightPadding  = neededPadding  - leftPadding;

        StringBuilder out = new StringBuilder();

        for (int i = 0; i < leftPadding + MIN_DECORATIONS; i++) {
            out.append(TITLE_DECORATION);
        }

        out.append(' ').append(title).append(' ');

        for (int i = 0; i < rightPadding + MIN_DECORATIONS; i++) {
            out.append(TITLE_DECORATION);
        }

        return out.toString();
    }

    public interface Callback<E> {
        void onResult(E result);
    }
}