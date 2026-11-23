package com.mirror.recorder.handler;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/** Shared GL state for the mod's world overlays (path line, start marker). */
final class RenderGl {
  private RenderGl() {}

  /** No textures or lighting, with transparency; throughWalls means draw over blocks. */
  static void begin(boolean throughWalls) {
    GlStateManager.pushMatrix();
    GlStateManager.disableTexture2D();
    GlStateManager.enableBlend();
    GlStateManager.disableLighting();
    GlStateManager.disableAlpha();
    GlStateManager.disableCull();
    GlStateManager.shadeModel(GL11.GL_SMOOTH);
    GlStateManager.enableDepth();
    if (throughWalls) GlStateManager.disableDepth();
    GlStateManager.depthMask(false);
  }

  /**
   * Applies the vanilla baseline expected after world overlays, not the previous GL state:
   * otherwise world and UI rendering after us breaks.
   */
  static void restore() {
    GL11.glLineWidth(1f);
    GlStateManager.shadeModel(GL11.GL_FLAT);
    GlStateManager.tryBlendFuncSeparate(
        GlStateManager.SourceFactor.SRC_ALPHA,
        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
        GlStateManager.SourceFactor.ONE,
        GlStateManager.DestFactor.ZERO);
    GlStateManager.depthMask(true);
    GlStateManager.enableDepth();
    GlStateManager.disableLighting();
    GlStateManager.enableAlpha();
    GlStateManager.enableCull();
    GlStateManager.disableBlend();
    GlStateManager.enableTexture2D();
    GlStateManager.color(1, 1, 1, 1);
    GlStateManager.popMatrix();
  }
}
