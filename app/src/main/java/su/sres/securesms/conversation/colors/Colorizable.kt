package su.sres.securesms.conversation.colors

import su.sres.securesms.util.Projection

/**
 * Denotes that a class can be colorized. The class is responsible for
 * generating its own projection.
 */
interface Colorizable {
  val colorizerProjections: List<Projection>
}