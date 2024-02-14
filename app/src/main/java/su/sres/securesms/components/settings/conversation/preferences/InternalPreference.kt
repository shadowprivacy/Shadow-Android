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
    val onDisableProfileSharingClick: () -> Unit
  ) : PreferenceModel<Model>() {

    val body: String get() {
      return String.format(
        """
        -- Profile Name --
        [${recipient.profileName.givenName}] [${recipient.profileName.familyName}]
        
        -- Profile Sharing --
        ${recipient.isProfileSharing}
        
        -- Profile Key (Base64) --
        ${recipient.profileKey?.let(Base64::encodeBytes) ?: "None"}
        
        -- Profile Key (Hex) --
        ${recipient.profileKey?.let(Hex::toStringCondensed) ?: "None"}
        
        -- Sealed Sender Mode --
        ${recipient.unidentifiedAccessMode}
        
        -- UUID --
        ${recipient.uuid.transform { obj: UUID -> obj.toString() }.or("None")}
        
        -- RecipientId --
        ${recipient.id.serialize()}
        """.trimIndent(),
      )
    }

    override fun areItemsTheSame(newItem: Model): Boolean {
      return recipient == newItem.recipient
    }
  }

  private class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val body: TextView = itemView.findViewById(R.id.internal_preference_body)
    private val disableProfileSharing: View = itemView.findViewById(R.id.internal_disable_profile_sharing)

    override fun bind(model: Model) {
      body.text = model.body
      disableProfileSharing.setOnClickListener { model.onDisableProfileSharingClick() }
    }
  }
}