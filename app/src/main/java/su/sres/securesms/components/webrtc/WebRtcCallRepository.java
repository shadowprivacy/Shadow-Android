package su.sres.securesms.components.webrtc;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import java.util.Collections;
import java.util.List;

import su.sres.securesms.database.DatabaseFactory;
import su.sres.securesms.database.GroupDatabase;
import su.sres.securesms.database.identity.IdentityRecordList;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.recipients.Recipient;
import su.sres.core.util.concurrent.SignalExecutors;

class WebRtcCallRepository {

  private final Context      context;

  WebRtcCallRepository(@NonNull Context context) {
    this.context      = context;
  }

  @WorkerThread
  void getIdentityRecords(@NonNull Recipient recipient, @NonNull Consumer<IdentityRecordList> consumer) {
    SignalExecutors.BOUNDED.execute(() -> {
      List<Recipient> recipients;

      if (recipient.isGroup()) {
        recipients = DatabaseFactory.getGroupDatabase(context).getGroupMembers(recipient.requireGroupId(), GroupDatabase.MemberSet.FULL_MEMBERS_EXCLUDING_SELF);
      } else {
        recipients = Collections.singletonList(recipient);
      }

      consumer.accept(ApplicationDependencies.getIdentityStore().getIdentityRecords(recipients));
    });
  }
}