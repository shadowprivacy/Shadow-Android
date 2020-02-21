/**  This is the newer "CDS" version supposedly including "legacy" directory support
 *   Will see how it works
 *
 */

package su.sres.securesms.contacts.sync;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import su.sres.securesms.ApplicationContext;
import su.sres.securesms.R;
import su.sres.securesms.contacts.ContactAccessor;
import su.sres.securesms.crypto.SessionUtil;
import su.sres.securesms.crypto.UnidentifiedAccessUtil;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.MessagingDatabase.InsertResult;
import su.sres.securesms.database.RecipientDatabase;
import su.sres.securesms.database.RecipientDatabase.RegisteredState;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobs.MultiDeviceContactUpdateJob;
import su.sres.securesms.logging.Log;
import su.sres.securesms.notifications.MessageNotifier;
import su.sres.securesms.notifications.NotificationChannels;
import su.sres.securesms.permissions.Permissions;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.recipients.RecipientUtil;
import su.sres.securesms.service.IncomingMessageObserver;
import su.sres.securesms.sms.IncomingJoinedMessage;
import su.sres.securesms.util.TextSecurePreferences;
import su.sres.securesms.util.Util;
import su.sres.securesms.util.concurrent.SignalExecutors;
import org.whispersystems.libsignal.util.guava.Optional;
import su.sres.signalservice.api.SignalServiceAccountManager;
import su.sres.signalservice.api.SignalServiceMessagePipe;
import su.sres.signalservice.api.crypto.UnidentifiedAccessPair;
import su.sres.signalservice.api.push.ContactTokenDetails;
import su.sres.signalservice.api.util.UuidUtil;
import su.sres.signalservice.api.push.SignalServiceAddress;
import su.sres.signalservice.api.push.exceptions.NotFoundException;

import java.io.IOException;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

class DirectoryHelperV1 {

    private static final String TAG = DirectoryHelperV1.class.getSimpleName();

    @WorkerThread
    static void refreshDirectory(@NonNull Context context, boolean notifyOfNewUsers) throws IOException {
        if (TextUtils.isEmpty(TextSecurePreferences.getLocalNumber(context))) return;
        if (!Permissions.hasAll(context, Manifest.permission.WRITE_CONTACTS)) return;

        List<RecipientId> newlyActiveUsers = refreshDirectory(context, ApplicationDependencies.getSignalServiceAccountManager());

        if (TextSecurePreferences.isMultiDevice(context)) {
            ApplicationDependencies.getJobManager().add(new MultiDeviceContactUpdateJob());
        }

        if (notifyOfNewUsers) notifyNewUsers(context, newlyActiveUsers);
    }

    @SuppressLint("CheckResult")
    private static @NonNull List<RecipientId> refreshDirectory(@NonNull Context context, @NonNull SignalServiceAccountManager accountManager) throws IOException {
        if (TextUtils.isEmpty(TextSecurePreferences.getLocalNumber(context))) {
            // return new LinkedList<>();
            return Collections.emptyList();
        }

        if (!Permissions.hasAll(context, Manifest.permission.WRITE_CONTACTS)) {
            // return new LinkedList<>();
            return Collections.emptyList();
        }

        RecipientDatabase recipientDatabase                       = DatabaseFactory.getRecipientDatabase(context);
        Stream<String>    eligibleRecipientDatabaseContactNumbers = Stream.of(recipientDatabase.getAllPhoneNumbers());
        Stream<String>    eligibleSystemDatabaseContactNumbers    = Stream.of(ContactAccessor.getInstance().getAllContactsWithNumbers(context));
        Set<String>       eligibleContactNumbers                  = Stream.concat(eligibleRecipientDatabaseContactNumbers, eligibleSystemDatabaseContactNumbers).collect(Collectors.toSet());

        try {
            Future<DirectoryResult> legacyRequest = getLegacyDirectoryResult(context, accountManager, recipientDatabase, eligibleContactNumbers);
            DirectoryResult         legacyResult  = legacyRequest.get();

            return legacyResult.getNewlyActiveRecipients();
        } catch (InterruptedException e) {
            throw new IOException("[Batch] Operation was interrupted.", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            } else {
                Log.e(TAG, "[Batch] Experienced an unexpected exception.", e);
                throw new AssertionError(e);
            }
        }
    }

    @WorkerThread
    static RegisteredState refreshDirectoryFor(@NonNull Context context, @NonNull Recipient recipient, boolean notifyOfNewUsers) throws IOException {
        RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);

        if (recipient.getUuid().isPresent() && !recipient.getE164().isPresent()) {
            boolean isRegistered = isUuidRegistered(context, recipient);
            if (isRegistered) {
                recipientDatabase.markRegistered(recipient.getId(), recipient.getUuid().get());
            } else {
                recipientDatabase.markUnregistered(recipient.getId());
            }

            return isRegistered ? RegisteredState.REGISTERED : RegisteredState.NOT_REGISTERED;
        }
        SignalServiceAccountManager accountManager    = ApplicationDependencies.getSignalServiceAccountManager();
        Future<RegisteredState>     legacyRequest     = getLegacyRegisteredState(context, accountManager, recipientDatabase, recipient);

        try {
            return legacyRequest.get();
        } catch (InterruptedException e) {
            throw new IOException("[Singular] Operation was interrupted.", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            } else {
                Log.e(TAG, "[Singular] Experienced an unexpected exception.", e);
                throw new AssertionError(e);
            }
        }
    }

    private static void updateContactsDatabase(@NonNull Context context, @NonNull List<RecipientId> activeIds, boolean removeMissing) {
        Optional<AccountHolder> account = getOrCreateAccount(context);

        if (account.isPresent()) {
            try {
                List<String> activeAddresses = Stream.of(activeIds).map(Recipient::resolved).filter(Recipient::hasE164).map(Recipient::requireE164).toList();

                DatabaseFactory.getContactsDatabase(context).removeDeletedRawContacts(account.get().getAccount());
                DatabaseFactory.getContactsDatabase(context).setRegisteredUsers(account.get().getAccount(), activeAddresses, removeMissing);

                Cursor                                 cursor = ContactAccessor.getInstance().getAllSystemContacts(context);
                RecipientDatabase.BulkOperationsHandle handle = DatabaseFactory.getRecipientDatabase(context).resetAllSystemContactInfo();

                try {
                    while (cursor != null && cursor.moveToNext()) {
                        String number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));

                        if (isValidContactNumber(number)) {
                            RecipientId recipientId     = Recipient.externalContact(context, number).getId();
                            String      displayName     = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                            String      contactPhotoUri = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_URI));
                            String      contactLabel    = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL));
                            int         phoneType       = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE));
                            Uri         contactUri      = ContactsContract.Contacts.getLookupUri(cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone._ID)),
                                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)));


                            handle.setSystemContactInfo(recipientId, displayName, contactPhotoUri, contactLabel, phoneType, contactUri.toString());
                        }
                    }
                } finally {
                    handle.finish();
                }

                if (NotificationChannels.supported()) {
                    try (RecipientDatabase.RecipientReader recipients = DatabaseFactory.getRecipientDatabase(context).getRecipientsWithNotificationChannels()) {
                        Recipient recipient;
                        while ((recipient = recipients.getNext()) != null) {
                            NotificationChannels.updateContactChannelName(context, recipient);
                        }
                    }
                }
            } catch (RemoteException | OperationApplicationException e) {
                Log.w(TAG, "Failed to update contacts.", e);
            }
        }
    }

    private static void notifyNewUsers(@NonNull  Context context,
                                       @NonNull  List<RecipientId> newUsers)
    {
        if (!TextSecurePreferences.isNewContactsNotificationEnabled(context)) return;

        for (RecipientId newUser: newUsers) {
            Recipient recipient = Recipient.resolved(newUser);
            if (!SessionUtil.hasSession(context, recipient.getId()) && !recipient.isLocalNumber()) {
                IncomingJoinedMessage  message      = new IncomingJoinedMessage(newUser);
                Optional<InsertResult> insertResult = DatabaseFactory.getSmsDatabase(context).insertMessageInbox(message);

                if (insertResult.isPresent()) {
                    int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                    if (hour >= 9 && hour < 23) {
                        MessageNotifier.updateNotification(context, insertResult.get().getThreadId(), true);
                    } else {
                        MessageNotifier.updateNotification(context, insertResult.get().getThreadId(), false);
                    }
                }
            }
        }
    }

    private static Optional<AccountHolder> getOrCreateAccount(Context context) {
        AccountManager accountManager = AccountManager.get(context);
        Account[]      accounts       = accountManager.getAccountsByType("su.sres.securesms");

        Optional<AccountHolder> account;

        if (accounts.length == 0) account = createAccount(context);
        else                      account = Optional.of(new AccountHolder(accounts[0], false));

        if (account.isPresent() && !ContentResolver.getSyncAutomatically(account.get().getAccount(), ContactsContract.AUTHORITY)) {
            ContentResolver.setSyncAutomatically(account.get().getAccount(), ContactsContract.AUTHORITY, true);
        }

        return account;
    }

    private static Optional<AccountHolder> createAccount(Context context) {
        AccountManager accountManager = AccountManager.get(context);
        Account        account        = new Account(context.getString(R.string.app_name), "su.sres.securesms");

        if (accountManager.addAccountExplicitly(account, null, null)) {
            Log.i(TAG, "Created new account...");
            ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1);
            return Optional.of(new AccountHolder(account, true));
        } else {
            Log.w(TAG, "Failed to create account!");
            return Optional.absent();
        }
    }

    private static Future<DirectoryResult> getLegacyDirectoryResult(@NonNull Context context,
                                                                    @NonNull SignalServiceAccountManager accountManager,
                                                                    @NonNull RecipientDatabase recipientDatabase,
                                                                    @NonNull Set<String> eligibleContactNumbers)
    {
        return SignalExecutors.UNBOUNDED.submit(() -> {
            List<ContactTokenDetails> activeTokens = accountManager.getContacts(eligibleContactNumbers);

            if (activeTokens != null) {
                List<RecipientId> activeIds   = new LinkedList<>();
                List<RecipientId> inactiveIds = new LinkedList<>();

                Set<String> inactiveContactNumbers = new HashSet<>(eligibleContactNumbers);

                for (ContactTokenDetails activeToken : activeTokens) {
                    activeIds.add(recipientDatabase.getOrInsertFromE164(activeToken.getNumber()));
                    inactiveContactNumbers.remove(activeToken.getNumber());
                }

                for (String inactiveContactNumber : inactiveContactNumbers) {
                    inactiveIds.add(recipientDatabase.getOrInsertFromE164(inactiveContactNumber));
                }

                Set<RecipientId>  currentActiveIds = new HashSet<>(recipientDatabase.getRegistered());
                Set<RecipientId>  contactIds       = new HashSet<>(recipientDatabase.getSystemContacts());
                List<RecipientId> newlyActiveIds   = Stream.of(activeIds)
                        .filter(id -> !currentActiveIds.contains(id))
                        .filter(contactIds::contains)
                        .toList();

                recipientDatabase.setRegistered(activeIds, inactiveIds);
                updateContactsDatabase(context, activeIds, true);

                Set<String> activeContactNumbers = Stream.of(activeIds).map(Recipient::resolved).filter(Recipient::hasSmsAddress).map(Recipient::requireSmsAddress).collect(Collectors.toSet());

                if (TextSecurePreferences.hasSuccessfullyRetrievedDirectory(context)) {
                    return new DirectoryResult(activeContactNumbers, newlyActiveIds);
                } else {
                    TextSecurePreferences.setHasSuccessfullyRetrievedDirectory(context, true);
                    return new DirectoryResult(activeContactNumbers);
                }
            }
            return new DirectoryResult(Collections.emptySet(), Collections.emptyList());
        });
    }

    private static Future<RegisteredState> getLegacyRegisteredState(@NonNull Context                     context,
                                                                    @NonNull SignalServiceAccountManager accountManager,
                                                                    @NonNull RecipientDatabase           recipientDatabase,
                                                                    @NonNull Recipient                   recipient)
    {
        return SignalExecutors.UNBOUNDED.submit(() -> {
            boolean                       activeUser    = recipient.resolve().getRegistered() == RegisteredState.REGISTERED;
            boolean                       systemContact = recipient.isSystemContact();
            Optional<ContactTokenDetails> details       = recipient.hasE164() ? accountManager.getContact(recipient.requireE164()) : Optional.absent();

            if (details.isPresent()) {
                recipientDatabase.setRegistered(recipient.getId(), RegisteredState.REGISTERED);

                if (Permissions.hasAll(context, Manifest.permission.WRITE_CONTACTS)) {
                    updateContactsDatabase(context, Util.asList(recipient.getId()), false);
                }

                if (!activeUser && TextSecurePreferences.isMultiDevice(context)) {
                    ApplicationDependencies.getJobManager().add(new MultiDeviceContactUpdateJob());
                }

                if (!activeUser && systemContact && !TextSecurePreferences.getNeedsSqlCipherMigration(context)) {
                    notifyNewUsers(context, Collections.singletonList(recipient.getId()));
                }

                return RegisteredState.REGISTERED;
            } else {
                recipientDatabase.setRegistered(recipient.getId(), RegisteredState.NOT_REGISTERED);
                return RegisteredState.NOT_REGISTERED;
            }
        });
    }

    private static boolean isValidContactNumber(@Nullable String number) {
        return !TextUtils.isEmpty(number) && !UuidUtil.isUuid(number);
    }

    private static boolean isUuidRegistered(@NonNull Context context, @NonNull Recipient recipient) throws IOException {
        Optional<UnidentifiedAccessPair> unidentifiedAccess = UnidentifiedAccessUtil.getAccessFor(context, recipient);
        SignalServiceMessagePipe         authPipe           = IncomingMessageObserver.getPipe();
        SignalServiceMessagePipe         unidentifiedPipe   = IncomingMessageObserver.getUnidentifiedPipe();
        SignalServiceMessagePipe         pipe               = unidentifiedPipe != null && unidentifiedAccess.isPresent() ? unidentifiedPipe : authPipe;
        SignalServiceAddress             address            = RecipientUtil.toSignalServiceAddress(context, recipient);

        if (pipe != null) {
            try {
                pipe.getProfile(address, unidentifiedAccess.get().getTargetUnidentifiedAccess());
                return true;
            } catch (NotFoundException e) {
                return false;
            } catch (IOException e) {
                Log.w(TAG, "Websocket request failed. Falling back to REST.");
            }
        }

        try {
            ApplicationDependencies.getSignalServiceMessageReceiver().retrieveProfile(address, unidentifiedAccess.get().getTargetUnidentifiedAccess());
            return true;
        } catch (NotFoundException e) {
            return false;
        }
    }

    private static class DirectoryResult {

        private final Set<String>       numbers;
        private final List<RecipientId> newlyActiveRecipients;

        DirectoryResult(@NonNull Set<String> numbers) {
            this(numbers, Collections.emptyList());
        }

        DirectoryResult(@NonNull Set<String> numbers, @NonNull List<RecipientId> newlyActiveRecipients) {
            this.numbers               = numbers;
            this.newlyActiveRecipients = newlyActiveRecipients;
        }

        Set<String> getNumbers() {
            return numbers;
        }

        List<RecipientId> getNewlyActiveRecipients() {
            return newlyActiveRecipients;
        }
    }

    private static class AccountHolder {

        private final boolean fresh;
        private final Account account;

        private AccountHolder(Account account, boolean fresh) {
            this.fresh   = fresh;
            this.account = account;
        }

        @SuppressWarnings("unused")
        public boolean isFresh() {
            return fresh;
        }

        public Account getAccount() {
            return account;
        }

    }

}