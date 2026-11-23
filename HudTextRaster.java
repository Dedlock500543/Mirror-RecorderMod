package com.mirror.recorder.gui;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Align scaled HUD text to physical pixels; reconstructed from the supplied release class. */
final class HudTextRaster {
  private static final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16);
  private static final FloatBuffer MODELVIEW = BufferUtils.createFloatBuffer(16);
  private static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);
  private static float pixelsX = 1f, pixelsY = 1f;
  private HudTextRaster() {}
  static void begin() {
    PROJECTION.clear();
    VIEWPORT.clear();
    GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION);
    GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);
    pixelsX = Math.abs(PROJECTION.get(0) * VIEWPORT.get(2) * 0.5f);
    pixelsY = Math.abs(PROJECTION.get(5) * VIEWPORT.get(3) * 0.5f);
  }
  static void align() {
    MODELVIEW.clear();
    GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW);
    GlStateManager.translate(snapOffset(MODELVIEW.get(12), pixelsX, MODELVIEW.get(0)),
        snapOffset(MODELVIEW.get(13), pixelsY, MODELVIEW.get(5)), 0f);
  }
  static float snapOffset(float origin, float pixelsPerUnit, float localScale) {
    float physicalOrigin = origin * pixelsPerUnit, physicalScale = pixelsPerUnit * localScale;
    if (!Float.isFinite(physicalOrigin) || !Float.isFinite(physicalScale)
        || Math.abs(physicalScale) < 1.0E-5f) return 0f;
    return (Math.round(physicalOrigin) - physicalOrigin) / physicalScale;
  }
}
