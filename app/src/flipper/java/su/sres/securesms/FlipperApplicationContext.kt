package su.sres.securesms

import com.facebook.soloader.SoLoader
import com.facebook.flipper.android.AndroidFlipperClient
import com.facebook.flipper.plugins.inspector.InspectorFlipperPlugin
import com.facebook.flipper.plugins.inspector.DescriptorMapping
import com.facebook.flipper.plugins.databases.DatabasesFlipperPlugin
import org.thoughtcrime.securesms.database.FlipperSqlCipherAdapter
import com.facebook.flipper.plugins.sharedpreferences.SharedPreferencesFlipperPlugin
import leakcanary.LeakCanary
import shark.AndroidReferenceMatchers

class FlipperApplicationContext : ApplicationContext() {
  override fun onCreate() {
    super.onCreate()
    SoLoader.init(this, false)

    val client = AndroidFlipperClient.getInstance(this)
    client.addPlugin(InspectorFlipperPlugin(this, DescriptorMapping.withDefaults()))
    client.addPlugin(DatabasesFlipperPlugin(FlipperSqlCipherAdapter(this)))
    client.addPlugin(SharedPreferencesFlipperPlugin(this))
    client.start()

    LeakCanary.config = LeakCanary.config.copy(
      referenceMatchers = AndroidReferenceMatchers.appDefaults +
        AndroidReferenceMatchers.ignoredInstanceField(
          className = "android.service.media.MediaBrowserService\$ServiceBinder",
          ffieldName = "this\$0"
        ) +
        AndroidReferenceMatchers.ignoredInstanceField(
          className = "androidx.media.MediaBrowserServiceCompat\$MediaBrowserServiceImplApi26\$MediaBrowserServiceApi26",
          fieldName = "mBase"
        ) +
        AndroidReferenceMatchers.ignoredInstanceField(
          className = "android.support.v4.media.MediaBrowserCompat",
          fieldName = "mImpl"
        ) +
        AndroidReferenceMatchers.ignoredInstanceField(
          className = "android.support.v4.media.session.MediaControllerCompat",
          fieldName = "mToken"
        ) +
        AndroidReferenceMatchers.ignoredInstanceField(
          className = "android.support.v4.media.session.MediaControllerCompat",
          fieldName = "mImpl"
        ) +
        AndroidReferenceMatchers.ignoredInstanceField(
          className = "su.sres.securesms.components.voice.VoiceNotePlaybackService",
          fieldName = "mApplication"
        ) +
        AndroidReferenceMatchers.ignoredInstanceField(
          className = "org.thoughtcrime.securesms.service.GenericForegroundService\$LocalBinder",
          fieldName = "this\$0"
        ) +
        AndroidReferenceMatchers.ignoredInstanceField(
          className = "org.thoughtcrime.securesms.contacts.ContactsSyncAdapter",
          fieldName = "mContext"
        )
    )
  }
}
