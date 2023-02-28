package su.sres.securesms.components.webrtc;

import android.content.Context;
import android.media.AudioManager;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import java.util.Collections;
import java.util.List;

import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.GroupDatabase;
import su.sres.securesms.database.IdentityDatabase;
import su.sres.securesms.database.identity.IdentityRecordList;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.util.ServiceUtil;
import su.sres.core.util.concurrent.SignalExecutors;

class WebRtcCallRepository {

    private final Context context;
    private final AudioManager audioManager;

    WebRtcCallRepository(@NonNull Context context) {
        this.context      = context;
        this.audioManager = ServiceUtil.getAudioManager(ApplicationDependencies.getApplication());
    }

    @NonNull WebRtcAudioOutput getAudioOutput() {
        if (audioManager.isBluetoothScoOn()) {
            return WebRtcAudioOutput.HEADSET;
        } else if (audioManager.isSpeakerphoneOn()) {
            return WebRtcAudioOutput.SPEAKER;
        } else {
            return WebRtcAudioOutput.HANDSET;
        }
    }

    @WorkerThread
    void getIdentityRecords(@NonNull Recipient recipient, @NonNull Consumer<IdentityRecordList> consumer) {
        SignalExecutors.BOUNDED.execute(() -> {
            IdentityDatabase identityDatabase = DatabaseFactory.getIdentityDatabase(context);
            List<Recipient> recipients;

            if (recipient.isGroup()) {
                recipients = DatabaseFactory.getGroupDatabase(context).getGroupMembers(recipient.requireGroupId(), GroupDatabase.MemberSet.FULL_MEMBERS_EXCLUDING_SELF);
            } else {
                recipients = Collections.singletonList(recipient);
            }

            consumer.accept(identityDatabase.getIdentities(recipients));
        });
    }
}