package su.sres.securesms.components.settings.conversation.preferences

import android.view.View
import android.widget.TextView
import su.sres.securesms.R
import su.sres.securesms.components.settings.PreferenceModel
import su.sres.securesms.recipients.Recipient
import su.sres.securesms.util.Base64
import su.sres.securesms.util.Hex
import su.sres.securesms.util.MappingAdapter
import su.sres.securesms.util.MappingViewHolder
import java.util.UUID

object InternalPreference {

  fun register(adapter: MappingAdapter) {
    adapter.registerFactory(Model::class.java, MappingAdapter.LayoutFactory(::ViewHolder, R.layout.conversation_settings_internal_preference))
  }

  class Model(
    private val recipient: Recipient,
    val onInternalDetailsClicked: () -> Unit,
  ) : PreferenceModel<Model>() {

    override fun areItemsTheSame(newItem: Model): Boolean {
      return recipient == newItem.recipient
    }
  }

  private class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val internalDetails: View = itemView.findViewById(R.id.internal_details)

    override fun bind(model: Model) {
      internalDetails.setOnClickListener { model.onInternalDetailsClicked() }
    }
  }
}