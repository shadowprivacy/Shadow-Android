package su.sres.securesms.subscription

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import su.sres.securesms.R
import su.sres.securesms.components.settings.app.AppSettingsActivity
import su.sres.securesms.help.HelpFragment
import su.sres.securesms.notifications.NotificationChannels
import su.sres.securesms.notifications.NotificationIds

sealed class SubscriptionNotification {
  object VerificationFailed : SubscriptionNotification() {
    override fun show(context: Context) {
      val notification = NotificationCompat.Builder(context, NotificationChannels.FAILURES)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(context.getString(R.string.Subscription__verification_failed))
        .setContentText(context.getString(R.string.Subscription__please_contact_support_for_more_information))
        .addAction(
          NotificationCompat.Action.Builder(
            null,
            context.getString(R.string.Subscription__contact_support),
            PendingIntent.getActivity(
              context,
              0,
              AppSettingsActivity.help(context, HelpFragment.DONATION_INDEX),
              PendingIntent.FLAG_ONE_SHOT
            )
          ).build()
        )
        .build()

      NotificationManagerCompat
        .from(context)
        .notify(NotificationIds.SUBSCRIPTION_VERIFY_FAILED, notification)
    }
  }

  abstract fun show(context: Context)
}