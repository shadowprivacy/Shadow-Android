package su.sres.securesms.jobs;

import su.sres.securesms.jobmanager.Job;

public abstract class PushReceivedJob extends BaseJob {

  private static final String TAG = PushReceivedJob.class.getSimpleName();

  protected PushReceivedJob(Job.Parameters parameters) {
    super(parameters);
  }
}