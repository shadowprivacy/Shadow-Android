package su.sres.securesms.revealable;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import su.sres.securesms.ApplicationContext;
import su.sres.securesms.database.AttachmentDatabase;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.MmsDatabase;
import su.sres.securesms.logging.Log;
import su.sres.securesms.service.TimedEventManager;

/**
 * Manages clearing removable message content after they're opened.
 */
public class RevealableMessageManager extends TimedEventManager<RevealExpirationInfo> {

    private static final String TAG = Log.tag(RevealableMessageManager.class);

    private final MmsDatabase        mmsDatabase;
    private final AttachmentDatabase attachmentDatabase;

    public RevealableMessageManager(@NonNull Application application) {
        super(application, "RevealableMessageManager");

        this.mmsDatabase        = DatabaseFactory.getMmsDatabase(application);
        this.attachmentDatabase = DatabaseFactory.getAttachmentDatabase(application);
    }

    @WorkerThread
    @Override
    protected @Nullable RevealExpirationInfo getNextClosestEvent() {
        RevealExpirationInfo expirationInfo = mmsDatabase.getNearestExpiringRevealableMessage();

        if (expirationInfo != null) {
            Log.i(TAG, "Next closest expiration is in " + getDelayForEvent(expirationInfo) + " ms for messsage " + expirationInfo.getMessageId() + ".");
        } else {
            Log.i(TAG, "No messages to schedule.");
        }

        return expirationInfo;
    }

    @WorkerThread
    @Override
    protected void executeEvent(@NonNull RevealExpirationInfo event) {
        Log.i(TAG, "Deleting attachments for message " + event.getMessageId());
        attachmentDatabase.deleteAttachmentFilesForMessage(event.getMessageId());
    }

    @WorkerThread
    @Override
    protected long getDelayForEvent(@NonNull RevealExpirationInfo event) {
        if (event.getRevealStartTime() == 0) {
            return event.getReceiveTime() + RevealableUtil.MAX_LIFESPAN;
        } else {
            long timeSinceStart = System.currentTimeMillis() - event.getRevealStartTime();
            long timeLeft       = event.getRevealDuration() - timeSinceStart;

            return Math.max(0, timeLeft);
        }
    }

    @AnyThread
    @Override
    protected void scheduleAlarm(@NonNull Application application, long delay) {
        setAlarm(application, delay, RevealAlarm.class);
    }

    public static class RevealAlarm extends BroadcastReceiver {

        private static final String TAG = Log.tag(RevealAlarm.class);

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive()");
            ApplicationContext.getInstance(context).getRevealableMessageManager().scheduleIfNecessary();
        }
    }
}