package su.sres.securesms.recipients;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import su.sres.securesms.contacts.sync.DirectoryHelper;
import su.sres.securesms.database.GroupDatabase;
import su.sres.securesms.database.RecipientDatabase;
import su.sres.securesms.database.ShadowDatabase;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.database.ThreadDatabase;
import su.sres.securesms.groups.GroupChangeBusyException;
import su.sres.securesms.groups.GroupChangeException;
import su.sres.securesms.groups.GroupChangeFailedException;
import su.sres.securesms.groups.GroupManager;
import su.sres.securesms.jobs.MultiDeviceBlockedUpdateJob;
import su.sres.securesms.jobs.MultiDeviceMessageRequestResponseJob;
import su.sres.securesms.jobs.RotateProfileKeyJob;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.core.util.logging.Log;

import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import su.sres.securesms.mms.OutgoingExpirationUpdateMessage;
import su.sres.securesms.sms.MessageSender;
import su.sres.signalservice.api.push.SignalServiceAddress;
import su.sres.signalservice.api.push.exceptions.NotFoundException;

public class RecipientUtil {

  private static final String TAG = Log.tag(RecipientUtil.class);

  /**
   * This method will do it's best to craft a fully-populated {@link SignalServiceAddress} based on
   * the provided recipient. This includes performing a possible network request if no UUID is
   * available. If the request to get a UUID fails or the user is not registered, an IOException is thrown.
   */
  @WorkerThread
  public static @NonNull
  SignalServiceAddress toSignalServiceAddress(@NonNull Context context, @NonNull Recipient recipient)
      throws IOException
  {
    recipient = recipient.resolve();

    if (!recipient.getAci().isPresent() && !recipient.getE164().isPresent()) {
      throw new AssertionError(recipient.getId() + " - No UUID or phone number!");
    }

    if (!recipient.getAci().isPresent()) {

      Log.i(TAG, recipient.getId() + " is missing a UUID...");
      DirectoryHelper.refreshDirectory(context);

      recipient = Recipient.resolved(recipient.getId());

    }

    if (recipient.hasAci()) {
      return new SignalServiceAddress(recipient.requireAci(), Optional.fromNullable(recipient.resolve().getE164().orNull()));
    } else {
      throw new NotFoundException(recipient.getId() + " is not registered!");
    }
  }

  public static @NonNull
  List<SignalServiceAddress> toSignalServiceAddresses(@NonNull Context context, @NonNull List<RecipientId> recipients)
      throws IOException
  {
    return toSignalServiceAddressesFromResolved(context, Recipient.resolvedList(recipients));
  }

  public static @NonNull
  List<SignalServiceAddress> toSignalServiceAddressesFromResolved(@NonNull Context context, @NonNull List<Recipient> recipients)
      throws IOException
  {
    ensureUuidsAreAvailable(context, recipients);

    return Stream.of(recipients)
                 .map(Recipient::resolve)
                 .map(r -> new SignalServiceAddress(r.requireAci(), r.getE164().orNull()))
                 .toList();
  }

  /**
   * Ensures that UUIDs are available. If a UUID cannot be retrieved or a user is found to be unregistered, an exception is thrown.
   */
  public static boolean ensureUuidsAreAvailable(@NonNull Context context, @NonNull Collection<Recipient> recipients)
      throws IOException
  {

    List<Recipient> recipientsWithoutUuids = Stream.of(recipients)
                                                   .map(Recipient::resolve)
                                                   .filterNot(Recipient::hasAci)
                                                   .toList();

    if (recipientsWithoutUuids.size() > 0) {
      DirectoryHelper.refreshDirectory(context);

      if (recipients.stream().map(Recipient::resolve).anyMatch(Recipient::isUnregistered)) {
        throw new NotFoundException("1 or more recipients are not registered!");
      }

      return true;
    } else {
      return false;
    }
  }

  public static boolean isBlockable(@NonNull Recipient recipient) {
    Recipient resolved = recipient.resolve();
    return !resolved.isMmsGroup();
  }

  public static List<Recipient> getEligibleForSending(@NonNull List<Recipient> recipients) {
    return Stream.of(recipients)
                 .filter(r -> r.getRegistered() != RecipientDatabase.RegisteredState.NOT_REGISTERED)
                 .toList();
  }

  /**
   * You can call this for non-groups and not have to handle any network errors.
   */
  @WorkerThread
  public static void blockNonGroup(@NonNull Context context, @NonNull Recipient recipient) {
    if (recipient.isGroup()) {
      throw new AssertionError();
    }

    try {
      block(context, recipient);
    } catch (GroupChangeException | IOException e) {
      throw new AssertionError(e);
    }
  }

  /**
   * You can call this for any type of recipient but must handle network errors that can occur from
   * GV2.
   * <p>
   * GV2 operations can also take longer due to the network.
   */

  @WorkerThread
  public static void block(@NonNull Context context, @NonNull Recipient recipient)
      throws GroupChangeBusyException, IOException, GroupChangeFailedException
  {
    if (!isBlockable(recipient)) {
      throw new AssertionError("Recipient is not blockable!");
    }

    recipient = recipient.resolve();

    if (recipient.isGroup() && recipient.getGroupId().get().isPush()) {
      GroupManager.leaveGroupFromBlockOrMessageRequest(context, recipient.getGroupId().get().requirePush());
    }

    ShadowDatabase.recipients().setBlocked(recipient.getId(), true);

    if (recipient.isSystemContact() || recipient.isProfileSharing() || isProfileSharedViaGroup(context, recipient)) {
      ApplicationDependencies.getJobManager().add(new RotateProfileKeyJob());
      ShadowDatabase.recipients().setProfileSharing(recipient.getId(), false);
    }

    ApplicationDependencies.getJobManager().add(new MultiDeviceBlockedUpdateJob());
//        StorageSyncHelper.scheduleSyncForDataChange();
  }

  @WorkerThread
  public static void unblock(@NonNull Context context, @NonNull Recipient recipient) {
    if (!isBlockable(recipient)) {
      throw new AssertionError("Recipient is not blockable!");
    }

    ShadowDatabase.recipients().setBlocked(recipient.getId(), false);
    ShadowDatabase.recipients().setProfileSharing(recipient.getId(), true);
    ApplicationDependencies.getJobManager().add(new MultiDeviceBlockedUpdateJob());
//        StorageSyncHelper.scheduleSyncForDataChange();

    if (recipient.hasServiceIdentifier()) {
      ApplicationDependencies.getJobManager().add(MultiDeviceMessageRequestResponseJob.forAccept(recipient.getId()));
    }
  }

  /**
   * If true, the new message request UI does not need to be shown, and it's safe to send read
   * receipts.
   * <p>
   * Note that this does not imply that a user has explicitly accepted a message request -- it could
   * also be the case that the thread in question is for a system contact or something of the like.
   */
  @WorkerThread
  public static boolean isMessageRequestAccepted(@NonNull Context context, long threadId) {
    if (threadId < 0) {
      return true;
    }

    ThreadDatabase threadDatabase  = ShadowDatabase.threads();
    Recipient      threadRecipient = threadDatabase.getRecipientForThreadId(threadId);

    if (threadRecipient == null) {
      return true;
    }

    return isMessageRequestAccepted(context, threadId, threadRecipient);
  }

  /**
   * See {@link #isMessageRequestAccepted(Context, long)}.
   */
  @WorkerThread
  public static boolean isMessageRequestAccepted(@NonNull Context context, @Nullable Recipient threadRecipient) {
    if (threadRecipient == null) {
      return true;
    }

    Long threadId = ShadowDatabase.threads().getThreadIdFor(threadRecipient.getId());
    return isMessageRequestAccepted(context, threadId, threadRecipient);
  }

  /**
   * Like {@link #isMessageRequestAccepted(Context, long)} but with fewer checks around messages so it
   * is more likely to return false.
   */
  @WorkerThread
  public static boolean isCallRequestAccepted(@NonNull Context context, @Nullable Recipient threadRecipient) {
    if (threadRecipient == null) {
      return true;
    }

    Long threadId = ShadowDatabase.threads().getThreadIdFor(threadRecipient.getId());
    return isCallRequestAccepted(context, threadId, threadRecipient);
  }

  /**
   * @return True if a conversation existed before we enabled message requests, otherwise false.
   */
  @WorkerThread
  public static boolean isPreMessageRequestThread(@NonNull Context context, @Nullable Long threadId) {

    long beforeTime = SignalStore.misc().getMessageRequestEnableTime();
    return threadId != null && ShadowDatabase.mmsSms().getConversationCount(threadId, beforeTime) > 0;
  }

  @WorkerThread
  public static void shareProfileIfFirstSecureMessage(@NonNull Context context, @NonNull Recipient recipient) {
    if (recipient.isProfileSharing()) {
      return;
    }

    long threadId = ShadowDatabase.threads().getThreadIdIfExistsFor(recipient.getId());

    if (isPreMessageRequestThread(context, threadId)) {
      return;
    }

    boolean firstMessage = ShadowDatabase.mmsSms().getOutgoingSecureConversationCount(threadId) == 0;

    if (firstMessage) {
      ShadowDatabase.recipients().setProfileSharing(recipient.getId(), true);
    }
  }

  public static boolean isLegacyProfileSharingAccepted(@NonNull Recipient threadRecipient) {
    return threadRecipient.isSelf() ||
           threadRecipient.isProfileSharing() ||
           threadRecipient.isSystemContact() ||
           !threadRecipient.isRegistered() ||
           threadRecipient.isForceSmsSelection();
  }

  /**
   * @return True if this recipient should already have your profile key, otherwise false.
   */
  public static boolean shouldHaveProfileKey(@NonNull Context context, @NonNull Recipient recipient) {
    if (recipient.isBlocked()) {
      return false;
    }

    if (recipient.isProfileSharing()) {
      return true;
    } else {
      GroupDatabase groupDatabase = ShadowDatabase.groups();
      return groupDatabase.getPushGroupsContainingMember(recipient.getId())
                          .stream()
                          .anyMatch(GroupDatabase.GroupRecord::isV2Group);

    }
  }

  /**
   * Checks if a universal timer is set and if the thread should have it set on it. Attempts to abort quickly and perform
   * minimal database access.
   */
  @WorkerThread
  public static boolean setAndSendUniversalExpireTimerIfNecessary(@NonNull Context context, @NonNull Recipient recipient, long threadId) {
    int defaultTimer = SignalStore.settings().getUniversalExpireTimer();
    if (defaultTimer == 0 || recipient.isGroup() || recipient.getExpiresInSeconds() != 0 || !recipient.isRegistered()) {
      return false;
    }

    if (threadId == -1 || !ShadowDatabase.mmsSms().hasMeaningfulMessage(threadId)) {
      ShadowDatabase.recipients().setExpireMessages(recipient.getId(), defaultTimer);
      OutgoingExpirationUpdateMessage outgoingMessage = new OutgoingExpirationUpdateMessage(recipient, System.currentTimeMillis(), defaultTimer * 1000L);
      MessageSender.send(context, outgoingMessage, ShadowDatabase.threads().getOrCreateThreadIdFor(recipient), false, null, null);
      return true;
    }
    return false;
  }

  @WorkerThread
  public static boolean isMessageRequestAccepted(@NonNull Context context, @Nullable Long threadId, @Nullable Recipient threadRecipient) {
    return threadRecipient == null ||
           threadRecipient.isSelf() ||
           threadRecipient.isProfileSharing() ||
           threadRecipient.isSystemContact() ||
           threadRecipient.isForceSmsSelection() ||
           !threadRecipient.isRegistered() ||
           hasSentMessageInThread(context, threadId) ||
           noSecureMessagesAndNoCallsInThread(context, threadId) ||
           isPreMessageRequestThread(context, threadId);
  }

  @WorkerThread
  private static boolean isCallRequestAccepted(@NonNull Context context, @Nullable Long threadId, @NonNull Recipient threadRecipient) {
    return threadRecipient.isProfileSharing() ||
           threadRecipient.isSystemContact() ||
           hasSentMessageInThread(context, threadId) ||
           isPreMessageRequestThread(context, threadId);
  }

  @WorkerThread
  public static boolean hasSentMessageInThread(@NonNull Context context, @Nullable Long threadId) {
    return threadId != null && ShadowDatabase.mmsSms().getOutgoingSecureConversationCount(threadId) != 0;
  }

  @WorkerThread
  private static boolean noSecureMessagesAndNoCallsInThread(@NonNull Context context, @Nullable Long threadId) {
    if (threadId == null) {
      return true;
    }

    return ShadowDatabase.mmsSms().getSecureConversationCount(threadId) == 0 &&
           !ShadowDatabase.threads().hasReceivedAnyCallsSince(threadId, 0);
  }

  @WorkerThread
  private static boolean isProfileSharedViaGroup(@NonNull Context context, @NonNull Recipient recipient) {
    return Stream.of(ShadowDatabase.groups().getPushGroupsContainingMember(recipient.getId()))
                 .anyMatch(group -> Recipient.resolved(group.getRecipientId()).isProfileSharing());
  }
}