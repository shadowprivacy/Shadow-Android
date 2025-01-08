/*
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package su.sres.securesms.jobs;

import androidx.annotation.NonNull;

import su.sres.core.util.concurrent.SignalExecutors;
import su.sres.securesms.database.ShadowDatabase;
import su.sres.securesms.database.ThreadDatabase;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.jobmanager.Data;
import su.sres.securesms.jobmanager.Job;
import su.sres.securesms.keyvalue.KeepMessagesDuration;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.core.util.logging.Log;

public class TrimThreadJob extends BaseJob {

  public static final String KEY = "TrimThreadJob";

  private static final String TAG          = Log.tag(TrimThreadJob.class);
  private static final String QUEUE_PREFIX = "TrimThreadJob_";

  private static final String KEY_THREAD_ID = "thread_id";

  private final long threadId;

  public static void enqueueAsync(long threadId) {
    if (SignalStore.settings().getKeepMessagesDuration() != KeepMessagesDuration.FOREVER || SignalStore.settings().isTrimByLengthEnabled()) {
      SignalExecutors.BOUNDED.execute(() -> ApplicationDependencies.getJobManager().add(new TrimThreadJob(threadId)));
    }
  }

  private TrimThreadJob(long threadId) {
    this(new Job.Parameters.Builder().setQueue(QUEUE_PREFIX + threadId)
                                     .setMaxInstancesForQueue(2)
                                     .build(),
         threadId);
  }

  private TrimThreadJob(@NonNull Job.Parameters parameters, long threadId) {
    super(parameters);
    this.threadId = threadId;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putLong(KEY_THREAD_ID, threadId).build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() {
    KeepMessagesDuration keepMessagesDuration = SignalStore.settings().getKeepMessagesDuration();

    int trimLength = SignalStore.settings().isTrimByLengthEnabled() ? SignalStore.settings().getThreadTrimLength()
                                                                    : ThreadDatabase.NO_TRIM_MESSAGE_COUNT_SET;

    long trimBeforeDate = keepMessagesDuration != KeepMessagesDuration.FOREVER ? System.currentTimeMillis() - keepMessagesDuration.getDuration()
                                                                               : ThreadDatabase.NO_TRIM_BEFORE_DATE_SET;

    ShadowDatabase.threads().trimThread(threadId, trimLength, trimBeforeDate);
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    return false;
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "Canceling trim attempt: " + threadId);
  }

  public static final class Factory implements Job.Factory<TrimThreadJob> {
    @Override
    public @NonNull TrimThreadJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new TrimThreadJob(parameters, data.getLong(KEY_THREAD_ID));
    }
  }
}
