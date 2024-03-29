package su.sres.securesms.groups.ui.addtogroup;

import android.content.Context;

import androidx.annotation.NonNull;

import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.groups.GroupChangeException;
import su.sres.securesms.groups.GroupId;
import su.sres.securesms.groups.GroupManager;
import su.sres.securesms.groups.MembershipNotSuitableForV2Exception;
import su.sres.securesms.groups.ui.GroupChangeErrorCallback;
import su.sres.securesms.groups.ui.GroupChangeFailureReason;
import su.sres.core.util.logging.Log;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.core.util.concurrent.SignalExecutors;

import java.io.IOException;
import java.util.Collections;

final class AddToGroupRepository {

    private static final String TAG = Log.tag(AddToGroupRepository.class);

    private final Context context;

    AddToGroupRepository() {
        this.context = ApplicationDependencies.getApplication();
    }

    public void add(@NonNull RecipientId recipientId,
                    @NonNull Recipient groupRecipient,
                    @NonNull GroupChangeErrorCallback error,
                    @NonNull Runnable success)
    {
        SignalExecutors.UNBOUNDED.execute(() -> {
            try {
                GroupId.Push pushGroupId = groupRecipient.requireGroupId().requirePush();

                GroupManager.addMembers(context, pushGroupId, Collections.singletonList(recipientId));

                success.run();
            } catch (GroupChangeException | MembershipNotSuitableForV2Exception | IOException e) {
                Log.w(TAG, e);
                error.onError(GroupChangeFailureReason.fromException(e));
            }
        });
    }
}