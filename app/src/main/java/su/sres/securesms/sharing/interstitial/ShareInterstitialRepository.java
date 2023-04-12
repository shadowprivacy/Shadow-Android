package su.sres.securesms.sharing.interstitial;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import com.annimon.stream.Stream;

import su.sres.core.util.concurrent.SignalExecutors;
import su.sres.securesms.recipients.Recipient;
import su.sres.securesms.sharing.ShareContactAndThread;

import java.util.List;
import java.util.Set;

class ShareInterstitialRepository {

    void loadRecipients(@NonNull Set<ShareContactAndThread> shareContactAndThreads, Consumer<List<Recipient>> consumer) {
        SignalExecutors.BOUNDED.execute(() -> consumer.accept(resolveRecipients(shareContactAndThreads)));
    }

    @WorkerThread
    private List<Recipient> resolveRecipients(@NonNull Set<ShareContactAndThread> shareContactAndThreads) {
        return Stream.of(shareContactAndThreads)
                .map(ShareContactAndThread::getRecipientId)
                .map(Recipient::resolved)
                .toList();
    }
}
