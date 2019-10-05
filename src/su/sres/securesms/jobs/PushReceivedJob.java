package su.sres.securesms.jobs;

import android.annotation.SuppressLint;
import androidx.annotation.NonNull;

import su.sres.securesms.ApplicationContext;
import su.sres.securesms.database.Address;
import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.MessagingDatabase.SyncMessageId;
import su.sres.securesms.database.RecipientDatabase;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.logging.Log;
import su.sres.securesms.recipients.Recipient;
import su.sres.signalservice.api.messages.SignalServiceEnvelope;

public abstract class PushReceivedJob extends BaseJob {

  private static final String TAG = PushReceivedJob.class.getSimpleName();

  protected PushReceivedJob(Job.Parameters parameters) {
    super(parameters);
  }
}