package su.sres.securesms.groups.ui.addmembers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import su.sres.securesms.contacts.SelectedContact;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.recipients.RecipientId;
import su.sres.core.util.concurrent.SignalExecutors;

class AddMembersRepository {

    private final Context context;

    AddMembersRepository() {
        this.context = ApplicationDependencies.getApplication();
    }

    void getOrCreateRecipientId(@NonNull SelectedContact selectedContact, @NonNull Consumer<RecipientId> consumer) {
        SignalExecutors.BOUNDED.execute(() -> consumer.accept(selectedContact.getOrCreateRecipientId(context)));
    }
}