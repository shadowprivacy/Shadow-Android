package su.sres.securesms.badges.models

import android.view.View
import android.widget.TextView
import su.sres.securesms.R
import su.sres.securesms.badges.BadgeImageView
import su.sres.securesms.util.MappingAdapter
import su.sres.securesms.util.MappingModel
import su.sres.securesms.util.MappingViewHolder

data class LargeBadge(
  val badge: Badge
) {

  class Model(val largeBadge: LargeBadge, val shortName: String, val maxLines: Int) : MappingModel<Model> {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return newItem.largeBadge.badge.id == largeBadge.badge.id
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return newItem.largeBadge == largeBadge && newItem.shortName == shortName && newItem.maxLines == maxLines
    }
  }

  class EmptyModel : MappingModel<EmptyModel> {
    override fun areItemsTheSame(newItem: EmptyModel): Boolean = true
    override fun areContentsTheSame(newItem: EmptyModel): Boolean = true
  }

  class EmptyViewHolder(itemView: View) : MappingViewHolder<EmptyModel>(itemView) {
    override fun bind(model: EmptyModel) {
    }
  }

  class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val badge: BadgeImageView = itemView.findViewById(R.id.badge)
    private val name: TextView = itemView.findViewById(R.id.name)
    private val description: TextView = itemView.findViewById(R.id.description)

    override fun bind(model: Model) {
      badge.setBadge(model.largeBadge.badge)

      name.text = model.largeBadge.badge.name
      description.text = model.largeBadge.badge.resolveDescription(model.shortName)
      description.setLines(model.maxLines)
      description.maxLines = model.maxLines
      description.minLines = model.maxLines
    }
  }

  companion object {
    fun register(mappingAdapter: MappingAdapter) {
      mappingAdapter.registerFactory(Model::class.java, MappingAdapter.LayoutFactory({ ViewHolder(it) }, R.layout.view_badge_bottom_sheet_dialog_fragment_page))

      mappingAdapter.registerFactory(EmptyModel::class.java, MappingAdapter.LayoutFactory({ EmptyViewHolder(it) }, R.layout.view_badge_bottom_sheet_dialog_fragment_page))
    }
  }
}