package su.sres.securesms.service;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import su.sres.securesms.database.MmsSmsDatabase;
import su.sres.securesms.database.ShadowDatabase;
import su.sres.securesms.database.ThreadDatabase;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.keyvalue.KeepMessagesDuration;
import su.sres.securesms.keyvalue.SignalStore;
import su.sres.core.util.logging.Log;

public class TrimThreadsByDateManager extends TimedEventManager<TrimThreadsByDateManager.TrimEvent> {

  private static final String TAG = Log.tag(TrimThreadsByDateManager.class);

  private final ThreadDatabase threadDatabase;
  private final MmsSmsDatabase mmsSmsDatabase;

  public TrimThreadsByDateManager(@NonNull Application application) {
    super(application, "TrimThreadsByDateManager");

    threadDatabase = ShadowDatabase.threads();
    mmsSmsDatabase = ShadowDatabase.mmsSms();

    scheduleIfNecessary();
  }

  @Override
  protected @Nullable TrimEvent getNextClosestEvent() {
    KeepMessagesDuration keepMessagesDuration = SignalStore.settings().getKeepMessagesDuration();
    if (keepMessagesDuration == KeepMessagesDuration.FOREVER) {
      return null;
    }

    long trimBeforeDate = System.currentTimeMillis() - keepMessagesDuration.getDuration();

    if (mmsSmsDatabase.getMessageCountBeforeDate(trimBeforeDate) > 0) {
      Log.i(TAG, "Messages exist before date, trim immediately");
      return new TrimEvent(0);
    }

    long timestamp = mmsSmsDatabase.getTimestampForFirstMessageAfterDate(trimBeforeDate);

    if (timestamp == 0) {
      return null;
    }

    return new TrimEvent(Math.max(0, keepMessagesDuration.getDuration() - (System.currentTimeMillis() - timestamp)));
  }

  @Override
  protected void executeEvent(@NonNull TrimEvent event) {
    KeepMessagesDuration keepMessagesDuration = SignalStore.settings().getKeepMessagesDuration();

    int trimLength = SignalStore.settings().isTrimByLengthEnabled() ? SignalStore.settings().getThreadTrimLength()
                                                                    : ThreadDatabase.NO_TRIM_MESSAGE_COUNT_SET;

    long trimBeforeDate = keepMessagesDuration != KeepMessagesDuration.FOREVER ? System.currentTimeMillis() - keepMessagesDuration.getDuration()
                                                                               : ThreadDatabase.NO_TRIM_BEFORE_DATE_SET;

    Log.i(TAG, "Trimming all threads with length: " + trimLength + " before: " + trimBeforeDate);
    threadDatabase.trimAllThreads(trimLength, trimBeforeDate);
  }

  @Override
  protected long getDelayForEvent(@NonNull TrimEvent event) {
    return event.delay;
  }

  @Override
  protected void scheduleAlarm(@NonNull Application application, long delay) {
    setAlarm(application, delay, TrimThreadsByDateAlarm.class);
  }

  public static class TrimThreadsByDateAlarm extends BroadcastReceiver {

    private static final String TAG = Log.tag(TrimThreadsByDateAlarm.class);

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
      Log.d(TAG, "onReceive()");
      ApplicationDependencies.getTrimThreadsByDateManager().scheduleIfNecessary();
    }
  }

  public static class TrimEvent {
    final long delay;

    public TrimEvent(long delay) {
      this.delay = delay;
    }
  }
}
