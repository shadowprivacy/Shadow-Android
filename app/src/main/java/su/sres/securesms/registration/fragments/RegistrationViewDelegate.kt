package su.sres.securesms.registration.fragments

import android.content.Intent
import android.view.View
import android.widget.Toast
import su.sres.securesms.R
import su.sres.securesms.logsubmit.SubmitDebugLogActivity

object RegistrationViewDelegate {

  @JvmStatic
  fun setDebugLogSubmitMultiTapView(view: View?) {
    view?.setOnClickListener(object : View.OnClickListener {
      private val DEBUG_TAP_TARGET = 8
      private val DEBUG_TAP_ANNOUNCE = 4
      private var debugTapCounter = 0

      override fun onClick(view: View) {
        debugTapCounter++

        if (debugTapCounter >= DEBUG_TAP_TARGET) {
          view.context.startActivity(Intent(view.context, SubmitDebugLogActivity::class.java))
        } else if (debugTapCounter >= DEBUG_TAP_ANNOUNCE) {
          val remaining = DEBUG_TAP_TARGET - debugTapCounter
          Toast.makeText(view.context, view.context.resources.getQuantityString(R.plurals.RegistrationActivity_debug_log_hint, remaining, remaining), Toast.LENGTH_SHORT).show()
        }
      }
    })
  }
}