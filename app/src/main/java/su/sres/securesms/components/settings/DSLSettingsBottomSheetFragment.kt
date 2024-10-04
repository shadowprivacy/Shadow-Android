package su.sres.securesms.components.settings

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EdgeEffect
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import su.sres.securesms.R
import su.sres.securesms.components.FixedRoundedCornerBottomSheetDialogFragment

abstract class DSLSettingsBottomSheetFragment(
  @LayoutRes private val layoutId: Int = R.layout.dsl_settings_bottom_sheet,
  val layoutManagerProducer: (Context) -> RecyclerView.LayoutManager = { context -> LinearLayoutManager(context) },
  override val peekHeightPercentage: Float = 1f
) : FixedRoundedCornerBottomSheetDialogFragment() {

  private lateinit var recyclerView: RecyclerView

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(layoutId, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    recyclerView = view.findViewById(R.id.recycler)
    recyclerView.edgeEffectFactory = EdgeEffectFactory()
    val adapter = DSLSettingsAdapter()

    recyclerView.layoutManager = layoutManagerProducer(requireContext())
    recyclerView.adapter = adapter

    bindAdapter(adapter)
  }

  abstract fun bindAdapter(adapter: DSLSettingsAdapter)

  private class EdgeEffectFactory : RecyclerView.EdgeEffectFactory() {
    override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
      return super.createEdgeEffect(view, direction).apply {
        color =
          requireNotNull(ContextCompat.getColor(view.context, R.color.settings_ripple_color))
      }
    }
  }
}