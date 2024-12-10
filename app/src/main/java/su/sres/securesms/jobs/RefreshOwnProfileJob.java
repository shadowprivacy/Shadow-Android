package su.sres.securesms.jobs;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;

import su.sres.securesms.badges.BadgeRepository;
import su.sres.securesms.badges.Badges;
import su.sres.securesms.badges.models.Badge;
import su.sres.securesms.crypto.ProfileKeyUtil;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.RecipientDatabase;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.jobmanager.impl.NetworkConstraint;
import su.sres.core.util.logging.Log;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.profiles.ProfileName;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.util.ProfileUtil;
import su.sres.securesms.util.TextSecurePreferences;

import org.whispersystems.libsignal.util.guava.Optional;

import su.sres.securesms.util.Util;
import su.sres.signalservice.api.crypto.InvalidCiphertextException;
import su.sres.signalservice.api.profiles.ProfileAndCredential;
import su.sres.signalservice.api.profiles.SignalServiceProfile;
import su.sres.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Refreshes the profile of the local user. Different from {@link RetrieveProfileJob} in that we
 * have to sometimes look at/set different data stores, and we will *always* do the fetch regardless
 * of caching.
 */
public class RefreshOwnProfileJob extends BaseJob {

  public static final String KEY = "RefreshOwnProfileJob";

  private static final String TAG = Log.tag(RefreshOwnProfileJob.class);

  public RefreshOwnProfileJob() {
    this(new Parameters.Builder()
             .addConstraint(NetworkConstraint.KEY)
             .setQueue(ProfileUploadJob.QUEUE)
             .setMaxInstancesForFactory(1)
             .setMaxAttempts(10)
             .build());
  }


  private RefreshOwnProfileJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull Data serialize() {
    return Data.EMPTY;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws Exception {
    if (!TextSecurePreferences.isPushRegistered(context) || TextUtils.isEmpty(TextSecurePreferences.getLocalNumber(context))) {
      Log.w(TAG, "Not yet registered!");
      return;
    }

        /* if (SignalStore.kbsValues().hasPin() && !SignalStore.kbsValues().hasOptedOut() && SignalStore.storageService().getLastSyncTime() == 0) {
            Log.i(TAG, "Registered with PIN but haven't completed storage sync yet.");
            return;
        } */

    if (!SignalStore.registrationValues().hasUploadedProfile()) {
      Log.i(TAG, "Registered but haven't uploaded profile yet.");
      return;
    }

    Recipient            self                 = Recipient.self();
    ProfileAndCredential profileAndCredential = ProfileUtil.retrieveProfileSync(context, self, getRequestType(self), false);
    SignalServiceProfile profile              = profileAndCredential.getProfile();

    setProfileName(profile.getName());
    setProfileAbout(profile.getAbout(), profile.getAboutEmoji());
    setProfileAvatar(profile.getAvatar());
    setProfileCapabilities(profile.getCapabilities());
    setProfileBadges(profile.getBadges());
    Optional<ProfileKeyCredential> profileKeyCredential = profileAndCredential.getProfileKeyCredential();
    if (profileKeyCredential.isPresent()) {
      setProfileKeyCredential(self, ProfileKeyUtil.getSelfProfileKey(), profileKeyCredential.get());
    }
  }

  private void setProfileKeyCredential(@NonNull Recipient recipient,
                                       @NonNull ProfileKey recipientProfileKey,
                                       @NonNull ProfileKeyCredential credential)
  {
    RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    recipientDatabase.setProfileKeyCredential(recipient.getId(), recipientProfileKey, credential);
  }

  private static SignalServiceProfile.RequestType getRequestType(@NonNull Recipient recipient) {
    return !recipient.hasProfileKeyCredential()
           ? SignalServiceProfile.RequestType.PROFILE_AND_CREDENTIAL
           : SignalServiceProfile.RequestType.PROFILE;
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof PushNetworkException;
  }

  @Override
  public void onFailure() {}

  private void setProfileName(@Nullable String encryptedName) {
    try {
      ProfileKey  profileKey    = ProfileKeyUtil.getSelfProfileKey();
      String      plaintextName = ProfileUtil.decryptString(profileKey, encryptedName);
      ProfileName profileName   = ProfileName.fromSerialized(plaintextName);

      Log.d(TAG, "Saving " + (!Util.isEmpty(plaintextName) ? "non-" : "") + "empty name.");
      DatabaseFactory.getRecipientDatabase(context).setProfileName(Recipient.self().getId(), profileName);
    } catch (InvalidCiphertextException | IOException e) {
      Log.w(TAG, e);
    }
  }

  private void setProfileAbout(@Nullable String encryptedAbout, @Nullable String encryptedEmoji) {
    try {
      ProfileKey profileKey     = ProfileKeyUtil.getSelfProfileKey();
      String     plaintextAbout = ProfileUtil.decryptString(profileKey, encryptedAbout);
      String     plaintextEmoji = ProfileUtil.decryptString(profileKey, encryptedEmoji);

      Log.d(TAG, "Saving " + (!Util.isEmpty(plaintextAbout) ? "non-" : "") + "empty about.");
      Log.d(TAG, "Saving " + (!Util.isEmpty(plaintextEmoji) ? "non-" : "") + "empty emoji.");

      DatabaseFactory.getRecipientDatabase(context).setAbout(Recipient.self().getId(), plaintextAbout, plaintextEmoji);
    } catch (InvalidCiphertextException | IOException e) {
      Log.w(TAG, e);
    }
  }

  private static void setProfileAvatar(@Nullable String avatar) {
    Log.d(TAG, "Saving " + (!Util.isEmpty(avatar) ? "non-" : "") + "empty avatar.");
    ApplicationDependencies.getJobManager().add(new RetrieveProfileAvatarJob(Recipient.self(), avatar));
  }

  private void setProfileCapabilities(@Nullable SignalServiceProfile.Capabilities capabilities) {
    if (capabilities == null) {
      return;
    }

    DatabaseFactory.getRecipientDatabase(context).setCapabilities(Recipient.self().getId(), capabilities);
  }

  private void setProfileBadges(@Nullable List<SignalServiceProfile.Badge> badges) {
    if (badges == null) {
      return;
    }

    Set<String> localDonorBadgeIds = Recipient.self()
                                              .getBadges()
                                              .stream()
                                              .filter(badge -> badge.getCategory() == Badge.Category.Donor)
                                              .map(Badge::getId)
                                              .collect(Collectors.toSet());

    Set<String> remoteDonorBadgeIds = badges.stream()
                                            .filter(badge -> Objects.equals(badge.getCategory(), Badge.Category.Donor.getCode()))
                                            .map(SignalServiceProfile.Badge::getId)
                                            .collect(Collectors.toSet());

    boolean remoteHasSubscriptionBadges = remoteDonorBadgeIds.stream().anyMatch(RefreshOwnProfileJob::isSubscription);
    boolean localHasSubscriptionBadges  = localDonorBadgeIds.stream().anyMatch(RefreshOwnProfileJob::isSubscription);
    boolean remoteHasBoostBadges        = remoteDonorBadgeIds.stream().anyMatch(RefreshOwnProfileJob::isBoost);
    boolean localHasBoostBadges         = localDonorBadgeIds.stream().anyMatch(RefreshOwnProfileJob::isBoost);

    if (!remoteHasSubscriptionBadges && localHasSubscriptionBadges) {
      Badge mostRecentExpiration = Recipient.self()
                                            .getBadges()
                                            .stream()
                                            .filter(badge -> badge.getCategory() == Badge.Category.Donor)
                                            .filter(badge -> isSubscription(badge.getId()))
                                            .max(Comparator.comparingLong(Badge::getExpirationTimestamp))
                                            .get();

      Log.d(TAG, "Marking subscription badge as expired, should notify next time the conversation list is open.");
      SignalStore.donationsValues().setExpiredBadge(mostRecentExpiration);
    } else if (!remoteHasBoostBadges && localHasBoostBadges) {
      Badge mostRecentExpiration = Recipient.self()
                                            .getBadges()
                                            .stream()
                                            .filter(badge -> badge.getCategory() == Badge.Category.Donor)
                                            .filter(badge -> isBoost(badge.getId()))
                                            .max(Comparator.comparingLong(Badge::getExpirationTimestamp))
                                            .get();

      Log.d(TAG, "Marking boost badge as expired, should notify next time the conversation list is open.");
      SignalStore.donationsValues().setExpiredBadge(mostRecentExpiration);
    }

    boolean userHasVisibleBadges   = badges.stream().anyMatch(SignalServiceProfile.Badge::isVisible);
    boolean userHasInvisibleBadges = badges.stream().anyMatch(b -> !b.isVisible());

    List<Badge> appBadges = badges.stream().map(Badges::fromServiceBadge).collect(Collectors.toList());

    if (userHasVisibleBadges && userHasInvisibleBadges) {
      boolean displayBadgesOnProfile = SignalStore.donationsValues().getDisplayBadgesOnProfile();
      Log.d(TAG, "Detected mixed visibility of badges. Telling the server to mark them all " +
                 (displayBadgesOnProfile ? "" : "not") +
                 " visible.", true);

      BadgeRepository badgeRepository = new BadgeRepository(context);
      badgeRepository.setVisibilityForAllBadges(displayBadgesOnProfile, appBadges).blockingSubscribe();

    } else {
      DatabaseFactory.getRecipientDatabase(context)
                     .setBadges(Recipient.self().getId(), appBadges);
    }
  }

  private static boolean isSubscription(String badgeId) {
    return !Objects.equals(badgeId, Badge.BOOST_BADGE_ID);
  }

  private static boolean isBoost(String badgeId) {
    return Objects.equals(badgeId, Badge.BOOST_BADGE_ID);
  }

  public static final class Factory implements Job.Factory<RefreshOwnProfileJob> {

    @Override
    public @NonNull RefreshOwnProfileJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new RefreshOwnProfileJob(parameters);
    }
  }
}