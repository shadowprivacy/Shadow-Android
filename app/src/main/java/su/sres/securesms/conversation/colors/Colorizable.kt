package su.sres.securesms.conversation.colors

import android.view.ViewGroup
import su.sres.securesms.util.Projection

/**
 * Denotes that a class can be colorized. The class is responsible for
 * generating its own projection.
 */
interface Colorizable {
  fun getColorizerProjections(coordinateRoot: ViewGroup): List<Projection>
}