package su.sres.securesms.reactions.edit

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import su.sres.securesms.PassphraseRequiredActivity
import su.sres.securesms.R
import su.sres.securesms.util.DynamicNoActionBarTheme
import su.sres.securesms.util.DynamicTheme
import su.sres.securesms.util.WindowUtil

class EditReactionsActivity : PassphraseRequiredActivity() {

  private val theme: DynamicTheme = DynamicNoActionBarTheme()

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)
    theme.onCreate(this)

    @Suppress("DEPRECATION")
    findViewById<View>(android.R.id.content).systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    WindowUtil.setStatusBarColor(window, ContextCompat.getColor(this, R.color.transparent))

    if (savedInstanceState == null) {
      supportFragmentManager.beginTransaction()
        .replace(android.R.id.content, EditReactionsFragment())
        .commit()
    }
  }

  override fun onResume() {
    super.onResume()
    theme.onResume(this)
  }
}