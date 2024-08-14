package su.sres.imageeditor.app.renderers;

import su.sres.imageeditor.core.Bounds;
import su.sres.imageeditor.core.Renderer;

public abstract class StandardHitTestRenderer implements Renderer {

  @Override
  public boolean hitTest(float x, float y) {
    return Bounds.contains(x, y);
  }
}
