package su.sres.securesms.groups.ui.addtogroup;

import android.content.Context;

import androidx.annotation.NonNull;

import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.groups.GroupChangeBusyException;
import su.sres.securesms.groups.GroupChangeFailedException;
import su.sres.securesms.groups.GroupId;
import su.sres.securesms.groups.GroupInsufficientRightsException;
import su.sres.securesms.groups.GroupManager;
import su.sres.securesms.groups.GroupNotAMemberException;
import su.sres.securesms.groups.MembershipNotSuitableForV2Exception;
import su.sres.securesms.groups.ui.GroupChangeErrorCallback;
import su.sres.securesms.groups.ui.GroupChangeFailureReason;
import su.sres.securesms.logging.Log;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.recipients.RecipientId;
import su.sres.securesms.util.concurrent.SignalExecutors;

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
            } catch (GroupInsufficientRightsException | GroupNotAMemberException e) {
                Log.w(TAG, e);
                error.onError(GroupChangeFailureReason.NO_RIGHTS);
            } catch (GroupChangeFailedException | GroupChangeBusyException | IOException e) {
                Log.w(TAG, e);
                error.onError(GroupChangeFailureReason.OTHER);
            } catch (MembershipNotSuitableForV2Exception e) {
                Log.w(TAG, e);
                error.onError(GroupChangeFailureReason.NOT_CAPABLE);
            }
        });
    }
}