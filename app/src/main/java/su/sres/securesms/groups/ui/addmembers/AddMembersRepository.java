package su.sres.securesms.groups.ui.addmembers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import su.sres.securesms.contacts.SelectedContact;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.groups.GroupId;
import su.sres.securesms.recipients.RecipientId;
import su.sres.core.util.concurrent.SignalExecutors;

final class AddMembersRepository {

    private final Context context;
    private final GroupId groupId;

    AddMembersRepository(@NonNull GroupId groupId) {
        this.groupId = groupId;
        this.context = ApplicationDependencies.getApplication();
    }

    @WorkerThread
    RecipientId getOrCreateRecipientId(@NonNull SelectedContact selectedContact) {
        return selectedContact.getOrCreateRecipientId(context);
    }

    @WorkerThread
    String getGroupTitle() {
        return DatabaseFactory.getGroupDatabase(context).requireGroup(groupId).getTitle();
    }
}