package su.sres.securesms;

import android.os.Handler;
import android.os.HandlerThread;

public class InitWorker extends HandlerThread {

    private Handler initHandler;

    private static final String TAG = "InitWorker";

     public InitWorker() {
     super(TAG);
     start();
     initHandler = new Handler(getLooper());

    }

    public InitWorker execute (Runnable initializeOnCreateTask) {
         initHandler.post(initializeOnCreateTask);
         return this;
    }
}
