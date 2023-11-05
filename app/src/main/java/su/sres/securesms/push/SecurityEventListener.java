package su.sres.securesms.push;

import android.content.Context;

import su.sres.core.util.logging.Log;
import su.sres.securesms.crypto.SecurityEvent;

import su.sres.signalservice.api.SignalServiceMessageSender;
import su.sres.signalservice.api.push.SignalServiceAddress;

public class SecurityEventListener implements SignalServiceMessageSender.EventListener {

  private static final String TAG = Log.tag(SecurityEventListener.class);

  private final Context context;

  public SecurityEventListener(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public void onSecurityEvent(SignalServiceAddress textSecureAddress) {
    SecurityEvent.broadcastSecurityUpdateEvent(context);
  }

}
