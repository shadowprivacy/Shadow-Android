package su.sres.securesms.gcm;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import su.sres.securesms.IncomingMessageProcessor;
import su.sres.securesms.dependencies.ApplicationDependencies;
import su.sres.securesms.logging.Log;
import su.sres.signalservice.api.SignalServiceMessageReceiver;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Retrieves messages over the REST endpoint.
 */
public class RestStrategy implements MessageRetriever.Strategy {

    private static final String TAG = Log.tag(RestStrategy.class);

    private static final long SOCKET_TIMEOUT = TimeUnit.SECONDS.toMillis(10);

    @WorkerThread
    @Override
    public boolean run() {
        long startTime = System.currentTimeMillis();

        try (IncomingMessageProcessor.Processor processor = ApplicationDependencies.getIncomingMessageProcessor().acquire()) {
            SignalServiceMessageReceiver receiver = ApplicationDependencies.getSignalServiceMessageReceiver();
            receiver.setSoTimeoutMillis(SOCKET_TIMEOUT);

            receiver.retrieveMessages(envelope -> {
                Log.i(TAG, "Retrieved an envelope." + timeSuffix(startTime));
                processor.processEnvelope(envelope);
                Log.i(TAG, "Successfully processed an envelope." + timeSuffix(startTime));
            });

            return true;
        } catch (IOException e) {
            Log.w(TAG, "Failed to retrieve messages. Resetting the SignalServiceMessageReceiver.", e);
            ApplicationDependencies.resetSignalServiceMessageReceiver();
            return false;
        }
    }

    private static String timeSuffix(long startTime) {
        return " (" + (System.currentTimeMillis() - startTime) + " ms elapsed)";
    }

    @Override
    public @NonNull String toString() {
        return RestStrategy.class.getSimpleName();
    }
}