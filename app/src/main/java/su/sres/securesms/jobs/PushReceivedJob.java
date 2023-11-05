package su.sres.securesms.jobs;

import su.sres.core.util.logging.Log;
import su.sres.securesms.jobmanager.Job;

public abstract class PushReceivedJob extends BaseJob {

  private static final String TAG = Log.tag(PushReceivedJob.class);

  protected PushReceivedJob(Job.Parameters parameters) {
    super(parameters);
  }
}