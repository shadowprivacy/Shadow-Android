/**  This is the newer "CDS" version supposedly including "legacy" directory support
 *   Will see how it works
 *
 */

package su.sres.securesms.contacts.sync;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.RecipientDatabase;
import su.sres.securesms.database.RecipientDatabase.RegisteredState;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobs.MultiDeviceContactUpdateJob;
import su.sres.securesms.jobs.RotateProfileKeyJob;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.securesms.logging.Log;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;

import su.sres.signalservice.api.storage.protos.DirectoryResponse;
import su.sres.signalservice.api.storage.protos.DirectoryUpdate;
import su.sres.signalservice.api.SignalServiceAccountManager;
import su.sres.signalservice.api.push.ContactTokenDetails;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

class DirectoryHelperV1 {

    private static final String TAG = DirectoryHelperV1.class.getSimpleName();

    @WorkerThread
    static void refreshDirectory(@NonNull Context context, boolean notifyOfNewUsers) throws IOException {
        if (TextUtils.isEmpty(TextSecurePreferences.getLocalNumber(context))) return;

        refreshDirectory(context, ApplicationDependencies.getSignalServiceAccountManager());

        // TODO: deal with this later
        if (TextSecurePreferences.isMultiDevice(context)) {
            ApplicationDependencies.getJobManager().add(new MultiDeviceContactUpdateJob());
        }

        // if (notifyOfNewUsers) notifyNewUsers(context, newlyActiveUsers);
    }

    private static void refreshDirectory(@NonNull Context context, @NonNull SignalServiceAccountManager accountManager) throws IOException {
        if (TextUtils.isEmpty(TextSecurePreferences.getLocalNumber(context))) {
            return;
        }

        RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
        PlainDirectoryResult directoryResult = getDirectoryResult(context, accountManager);

        long remoteVersion = directoryResult.version;

        if (directoryResult.isUpdate) {

            int inserted = 0,
                removed = 0;

            if (directoryResult.isFullUpdate) {
                // perform full update

                Set<String> currentUserLogins = recipientDatabase.getAllUserLogins();
                Set<String> userLoginsToInsert = directoryResult.getUserLogins();

                for(String userLogin : currentUserLogins) {

                    RecipientId id = recipientDatabase.getOrInsertFromUserLogin(userLogin);

                    if (!userLoginsToInsert.contains(userLogin)) {
                        recipientDatabase.markUnregistered(id);
                        recipientDatabase.setProfileSharing(id, false);
                        removed++;
                    }
                }

                for(String userLogin : userLoginsToInsert) {

                    RecipientId id = recipientDatabase.getOrInsertFromUserLogin(userLogin);
                    recipientDatabase.markRegistered(id);
                    recipientDatabase.setProfileSharing(id, true);
                    inserted++;
                }

                Log.i(TAG, String.format("Full update to version %s successful. Inserted %s entries, removed %s entries", remoteVersion, inserted, removed));

            } else {
                // perform incremental update

                Map<String, String> incrementalUpdate = directoryResult.updateContents.get();

                for (Map.Entry<String, String> entry : incrementalUpdate.entrySet()) {

                    String userLogin = entry.getKey();

                    // this will effectively skip an empty incremental update
                    if (userLogin.equals("")) continue;

                    RecipientId id = recipientDatabase.getOrInsertFromUserLogin(userLogin);

                    if (entry.getValue().equals("-1")) {
                        recipientDatabase.markUnregistered(id);
                        recipientDatabase.setProfileSharing(id, false);
                        removed++;
                    } else {

                        recipientDatabase.markRegistered(id);
                        recipientDatabase.setProfileSharing(id, true);
                        inserted++;
                    }
                }

                Log.i(TAG, String.format("Incremental update to version %s successful. Inserted %s entries, removed %s entries", remoteVersion, inserted, removed));
            }

            SignalStore.serviceConfigurationValues().setCurrentDirVer(remoteVersion);
            if(removed > 0) ApplicationDependencies.getJobManager().add(new RotateProfileKeyJob());
        }
    }

    // this returns whether the recipient is registered or not, should not be used as of now
/*    @WorkerThread
    static RegisteredState refreshDirectoryFor(@NonNull Context context, @NonNull Recipient recipient, boolean notifyOfNewUsers) throws IOException {
        RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);

        if (recipient.getUuid().isPresent() && !recipient.getE164().isPresent()) {  // if UUID is there, but the phone number is not
            boolean isRegistered = isUuidRegistered(context, recipient);            // if the profile can be retrieved, this is  true
            if (isRegistered) {
                recipientDatabase.markRegistered(recipient.getId(), recipient.getUuid().get());    // sets the UUID and sets the user as registered
            } else {
                recipientDatabase.markUnregistered(recipient.getId());                             // removes the UUID and sets the user as unregistered
            }

            return isRegistered ? RegisteredState.REGISTERED : RegisteredState.NOT_REGISTERED;
        } // the following is enacted if there's no UUID or the phone number is present (this is our case)
        return getRegisteredState(context, ApplicationDependencies.getSignalServiceAccountManager(), recipientDatabase, recipient);
    }

    private static void updateContactsDatabase(@NonNull Context context, @NonNull List<RecipientId> activeIds, boolean removeMissing, Map<String, String> rewrites) {
        Optional<AccountHolder> account = getOrCreateAccount(context);

        if (account.isPresent()) {
            try {
                List<String> activeAddresses = Stream.of(activeIds).map(Recipient::resolved).filter(Recipient::hasE164).map(Recipient::requireE164).toList();

                DatabaseFactory.getContactsDatabase(context).removeDeletedRawContacts(account.get().getAccount());
                DatabaseFactory.getContactsDatabase(context).setRegisteredUsers(account.get().getAccount(), activeAddresses, removeMissing);

                Cursor                                 cursor = ContactAccessor.getInstance().getAllSystemContacts(context);
                RecipientDatabase.BulkOperationsHandle handle = DatabaseFactory.getRecipientDatabase(context).beginBulkSystemContactUpdate();

                try {
                    while (cursor != null && cursor.moveToNext()) {
                        String number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));

                        if (isValidContactNumber(number)) {
                            String      formattedNumber = PhoneNumberFormatter.get(context).format(number);
                            String      realNumber      = Util.getFirstNonEmpty(rewrites.get(formattedNumber), formattedNumber);
                            RecipientId recipientId     = Recipient.externalContact(context, realNumber).getId();
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
//        if (!TextSecurePreferences.isNewContactsNotificationEnabled(context)) return;

        for (RecipientId newUser: newUsers) {
            Recipient recipient = Recipient.resolved(newUser);
            if (!SessionUtil.hasSession(context, recipient.getId()) && !recipient.isLocalNumber()) {
                IncomingJoinedMessage  message      = new IncomingJoinedMessage(newUser);
                Optional<InsertResult> insertResult = DatabaseFactory.getSmsDatabase(context).insertMessageInbox(message);

                if (insertResult.isPresent()) {
                    int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                    if (hour >= 9 && hour < 23) {
                        ApplicationDependencies.getMessageNotifier().updateNotification(context, insertResult.get().getThreadId(), true);
                    } else {
                         ApplicationDependencies.getMessageNotifier().updateNotification(context, insertResult.get().getThreadId(), false);
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
    } */

    private static PlainDirectoryResult getDirectoryResult(@NonNull Context context, @NonNull SignalServiceAccountManager accountManager)
            throws IOException
    {
        DirectoryResponse directoryResponse = accountManager.getDirectoryResponse(SignalStore.serviceConfigurationValues().getCurrentDirVer());

            TextSecurePreferences.setHasSuccessfullyRetrievedDirectory(context, true);
            return new PlainDirectoryResult(directoryResponse);
    }

    // in short, this sets the recipient registered if it has a e164 number and is registered on the server or resolved through fuzzy, and sets unregistered otherwise
    private static RegisteredState getRegisteredState(@NonNull Context                     context,
                                                      @NonNull SignalServiceAccountManager accountManager,
                                                      @NonNull RecipientDatabase           recipientDatabase,
                                                      @NonNull Recipient                   recipient)
            throws IOException
    {
        boolean                       activeUser    = recipient.resolve().getRegistered() == RegisteredState.REGISTERED;
  //      boolean                       systemContact = recipient.isSystemContact();
        Optional<ContactTokenDetails> details       = Optional.absent();
 //       Map<String, String>           rewrites      = new HashMap<>();

        if (recipient.hasE164()) {
  //          FuzzyPhoneNumberHelper.InputResult inputResult = FuzzyPhoneNumberHelper.generateInput(Collections.singletonList(recipient.requireE164()), recipientDatabase.getAllPhoneNumbers());

   /*         if (inputResult.getNumbers().size() > 1) {
                Log.i(TAG, "[getRegisteredState] Got a fuzzy number result.");

                List<ContactTokenDetails>           detailList   = accountManager.getContacts(inputResult.getNumbers());
                Collection<String>                  registered   = Stream.of(detailList).map(ContactTokenDetails::getNumber).collect(Collectors.toSet());
                FuzzyPhoneNumberHelper.OutputResult outputResult = FuzzyPhoneNumberHelper.generateOutput(registered, inputResult);
                String                              finalNumber  = recipient.requireE164();
                ContactTokenDetails                 detail       = new ContactTokenDetails();

                if (outputResult.getRewrites().size() > 0 && outputResult.getRewrites().containsKey(finalNumber)) {
                    Log.i(TAG, "[getRegisteredState] Need to rewrite a number.");
                    finalNumber = outputResult.getRewrites().get(finalNumber);
                    rewrites    = outputResult.getRewrites();
                }

                detail.setNumber(finalNumber);
                details = Optional.of(detail);

                recipientDatabase.updatePhoneNumbers(outputResult.getRewrites());
            } else { */
                details = accountManager.getContact(recipient.requireE164()); // checks if the recipient is registered on the server
        //    }
        }

        if (details.isPresent()) {
            recipientDatabase.setRegistered(recipient.getId(), RegisteredState.REGISTERED);

    //        if (Permissions.hasAll(context, Manifest.permission.WRITE_CONTACTS)) {
    //            updateContactsDatabase(context, Util.asList(recipient.getId()), false, rewrites);
    //        }

            if (!activeUser && TextSecurePreferences.isMultiDevice(context)) {
                ApplicationDependencies.getJobManager().add(new MultiDeviceContactUpdateJob());
            }

    //        if (!activeUser && systemContact && !TextSecurePreferences.getNeedsSqlCipherMigration(context)) {
    //            notifyNewUsers(context, Collections.singletonList(recipient.getId()));
    //        }

            return RegisteredState.REGISTERED;
        } else {
            recipientDatabase.setRegistered(recipient.getId(), RegisteredState.NOT_REGISTERED);
            return RegisteredState.NOT_REGISTERED;
        }
    }

/*    private static boolean isValidContactNumber(@Nullable String number) {
        return !TextUtils.isEmpty(number) && !UuidUtil.isUuid(number);
    }

    private static boolean isUuidRegistered(@NonNull Context context, @NonNull Recipient recipient) throws IOException {

        try {
            ProfileUtil.retrieveProfile(context, recipient, SignalServiceProfile.RequestType.PROFILE);
            return true;
        } catch (NotFoundException e) {
            return false;
        }
    } */

    private static class PlainDirectoryResult {

        private Optional<Map<String, String>> updateContents;
        private boolean isUpdate;
        private boolean isFullUpdate;
        private long version;

        PlainDirectoryResult(DirectoryResponse directoryResponse) {
           DirectoryResponse.StatusOrUpdateCase responseType = directoryResponse.getStatusOrUpdateCase();

           version = directoryResponse.getVersion();

           switch (responseType) {

               case DIRECTORY_UPDATE:
                   isUpdate = true;
                   DirectoryUpdate directoryUpdate = directoryResponse.getDirectoryUpdate();
                   DirectoryUpdate.Type updateType = directoryUpdate.getType();
                   updateContents = Optional.of(directoryUpdate.getDirectoryEntryMap());

                  switch (updateType) {
                      case FULL:
                          isFullUpdate = true;
                          break;
                      default:
                          isFullUpdate = false;
                   }

                   break;

               default:
                   isUpdate = false;
                   isFullUpdate = false;
                   updateContents = Optional.absent();
           }
        }

        Set<String> getUserLogins() {
            return updateContents.get().keySet();
        }
    }

/*    private static class AccountHolder {

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
    } */
}