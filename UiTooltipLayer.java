package com.mirror.recorder.gui;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/** A popup is an overlay, never a child of the hovered row's clipping/depth viewport. */
final class UiTooltipLayer implements AutoCloseable {
  private final boolean clipped = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
  private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
  private final boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

  UiTooltipLayer() {
    // Do not alter the scissor box or the theme's nested clip stack. Disabling the test is
    // enough, and restoring it leaves the caller's exact rectangle intact.
    GL11.glDisable(GL11.GL_SCISSOR_TEST);
    // UiTheme.card restores GUI depth testing. Keep all popup drawing in the foreground,
    // including its background and glyphs, without writing into the underlying depth buffer.
    GlStateManager.depthMask(false);
  }

  void text() {
    GlStateManager.disableDepth();
  }

  @Override
  public void close() {
    GlStateManager.depthMask(depthWrite);
    if (depth) GlStateManager.enableDepth();
    else GlStateManager.disableDepth();
    if (clipped) GL11.glEnable(GL11.GL_SCISSOR_TEST);
    else GL11.glDisable(GL11.GL_SCISSOR_TEST);
  }
}
