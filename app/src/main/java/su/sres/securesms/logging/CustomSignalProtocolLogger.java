package su.sres.securesms.logging;

import org.whispersystems.libsignal.logging.SignalProtocolLogger;

import su.sres.core.util.logging.Log;

public class CustomSignalProtocolLogger implements SignalProtocolLogger {
  @Override
  public void log(int priority, String tag, String message) {
    switch (priority) {
      case VERBOSE:
        Log.v(tag, message);
        break;
      case DEBUG:
        Log.d(tag, message);
        break;
      case INFO:
        Log.i(tag, message);
        break;
      case WARN:
        Log.w(tag, message);
        break;
      case ERROR:
        Log.e(tag, message);
        break;
      case ASSERT:
        Log.wtf(tag, message);
        break;
    }
  }
}
