package su.sres.securesms.reactions.edit

import android.os.Bundle
import su.sres.securesms.PassphraseRequiredActivity
import su.sres.securesms.util.DynamicNoActionBarTheme
import su.sres.securesms.util.DynamicTheme

class EditReactionsActivity : PassphraseRequiredActivity() {

  private val theme: DynamicTheme = DynamicNoActionBarTheme()

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)
    theme.onCreate(this)

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