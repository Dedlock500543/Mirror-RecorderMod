package com.mirror.recorder.handler;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/** Camera-relative replay geometry, shared by the world renderers and the native visual fixture. */
public final class ReplayWorldVisuals {
  private static final int RING_SEGMENTS = 64, MAX_ARROWS = 48;
  private static final long RING_TURN_MS = 12000L, MARKER_PULSE_MS = 2400L;

  private ReplayWorldVisuals() {}

  public static float phase(long now, long period) {
    return Math.floorMod(now, period) / (float) period;
  }

  public static float routeAlpha(int index, int count, boolean fade, float travel) {
    float u = Math.max(0f, Math.min(1f, index / (float) Math.max(1, count - 1)));
    float distance = Math.abs(u - travel);
    float gleam = Math.max(0f, 1f - distance * 10f);
    return Math.min(1f, (fade ? 1f - u * 0.82f : 0.78f) + gleam * 0.22f);
  }

  public static void route(
      double[] x,
      double[] y,
      double[] z,
      int count,
      int style,
      int step,
      double size,
      float width,
      int color,
      boolean fade,
      float travel) {
    if (count < 2) return;
    normalBlend();
    if (style != 2) {
      ribbon(x, y, z, count, Math.max(0.055, width * 0.055), color, 0.18f, fade, travel);
      ribbon(x, y, z, count, Math.max(0.02, width * 0.021), color, 0.60f, fade, travel);
      GL11.glLineWidth(Math.max(1.2f, width));
      BufferBuilder q = begin(GL11.GL_LINE_STRIP);
      for (int i = 0; i < count; i++)
        vertex(q, x[i], y[i] + 0.006, z[i], color, routeAlpha(i, count, fade, travel), 0.28f);
      end();
    }
    if (style != 0)
      arrows(x, y, z, count, Math.max(1, step), Math.max(0.1, size), color, fade, travel);
  }

  private static void ribbon(
      double[] x,
      double[] y,
      double[] z,
      int count,
      double radius,
      int color,
      float opacity,
      boolean fade,
      float travel) {
    BufferBuilder q = begin(GL11.GL_QUADS);
    for (int i = 1; i < count; i++) {
      double dx = x[i] - x[i - 1], dz = z[i] - z[i - 1], len = Math.sqrt(dx * dx + dz * dz);
      if (len < 0.0001) continue;
      double nx = -dz / len * radius, nz = dx / len * radius;
      float a0 = opacity * routeAlpha(i - 1, count, fade, travel);
      float a1 = opacity * routeAlpha(i, count, fade, travel);
      vertex(q, x[i - 1] + nx, y[i - 1], z[i - 1] + nz, color, a0, 0);
      vertex(q, x[i - 1] - nx, y[i - 1], z[i - 1] - nz, color, a0, 0);
      vertex(q, x[i] - nx, y[i], z[i] - nz, color, a1, 0);
      vertex(q, x[i] + nx, y[i], z[i] + nz, color, a1, 0);
    }
    end();
  }

  private static void arrows(
      double[] x,
      double[] y,
      double[] z,
      int count,
      int step,
      double size,
      int color,
      boolean fade,
      float travel) {
    GL11.glLineWidth(1.7f);
    BufferBuilder q = begin(GL11.GL_LINES);
    int drawn = 0;
    double distance = 0d;
    for (int i = 1; i < count && drawn < MAX_ARROWS; i++) {
      double dx = x[i] - x[i - 1], dz = z[i] - z[i - 1];
      distance += Math.sqrt(dx * dx + dz * dz);
      if (i % step != 0 || distance < Math.max(0.85d, size * 5d)) continue;
      int next = Math.min(count - 1, i + 1);
      dx = x[next] - x[i - 1];
      dz = z[next] - z[i - 1];
      double length = Math.sqrt(dx * dx + dz * dz);
      if (length < 0.001d) continue;
      dx /= length;
      dz /= length;
      double tipX = x[i] + dx * size, tipZ = z[i] + dz * size;
      double backX = x[i] - dx * size, backZ = z[i] - dz * size;
      float a = routeAlpha(i, count, fade, travel);
      vertex(q, backX - dz * size, y[i] + 0.018, backZ + dx * size, color, a, 0.52f);
      vertex(q, tipX, y[i] + 0.018, tipZ, color, a, 0.52f);
      vertex(q, tipX, y[i] + 0.018, tipZ, color, a, 0.52f);
      vertex(q, backX + dz * size, y[i] + 0.018, backZ - dx * size, color, a, 0.52f);
      distance = 0;
      drawn++;
    }
    end();
  }

  public static void marker(
      double x,
      double y,
      double z,
      int mode,
      double radius,
      double beamHeight,
      int color,
      long now,
      boolean animate) {
    normalBlend();
    if (mode == 1) {
      halo(x, y, z, 0.28, color, 0.14f);
      diamond(x, y + 0.08, z, 0.12, color);
      return;
    }
    double r = Math.max(0.1, radius);
    float turn = animate ? phase(now, RING_TURN_MS) * 360f : 0;
    halo(x, y, z, r * 1.28, color, 0.13f);
    for (int quadrant = 0; quadrant < 4; quadrant++) {
      annulus(x, y + 0.045, z, r * 0.94, r * 1.02, quadrant * 90 + turn, 64, color, 0.88f);
      annulus(x, y + 0.049, z, r * 0.70, r * 0.73, quadrant * 90 - turn * 0.5, 72, color, 0.40f);
    }
    ticks(x, y + 0.055, z, r, color);
    diamond(x, y + 0.065, z, r * 0.18, color);
    if (animate) {
      float p = phase(now, MARKER_PULSE_MS);
      annulus(
          x,
          y + 0.025,
          z,
          r * (1.02 + 0.38 * p),
          r * (1.04 + 0.38 * p),
          0,
          360,
          color,
          (1 - p) * 0.20f);
    }
    if (mode == 3) beam(x, y + 0.07, z, beamHeight, color);
  }

  private static void halo(double x, double y, double z, double radius, int color, float opacity) {
    BufferBuilder q = begin(GL11.GL_TRIANGLE_FAN);
    vertex(q, x, y + 0.02, z, color, opacity, 0);
    for (int i = 0; i <= RING_SEGMENTS; i++) {
      double a = Math.PI * 2 * i / RING_SEGMENTS;
      vertex(q, x + Math.cos(a) * radius, y + 0.02, z + Math.sin(a) * radius, color, 0, 0);
    }
    end();
  }

  private static void annulus(
      double x,
      double y,
      double z,
      double inner,
      double outer,
      double start,
      double extent,
      int color,
      float opacity) {
    BufferBuilder q = begin(GL11.GL_QUAD_STRIP);
    int steps = Math.max(2, (int) Math.ceil(RING_SEGMENTS * extent / 360d));
    for (int i = 0; i <= steps; i++) {
      double a = Math.toRadians(start + extent * i / steps), c = Math.cos(a), s = Math.sin(a);
      vertex(q, x + c * inner, y, z + s * inner, color, opacity, 0.2f);
      vertex(q, x + c * outer, y, z + s * outer, color, opacity, 0.2f);
    }
    end();
  }

  private static void ticks(double x, double y, double z, double radius, int color) {
    GL11.glLineWidth(1.6f);
    BufferBuilder q = begin(GL11.GL_LINES);
    for (int i = 0; i < 4; i++) {
      double a = Math.PI * i / 2, dx = Math.cos(a), dz = Math.sin(a);
      vertex(q, x + dx * radius * 1.08, y, z + dz * radius * 1.08, color, 0.9f, 0.45f);
      vertex(q, x + dx * radius * 1.22, y, z + dz * radius * 1.22, color, 0.9f, 0.45f);
    }
    end();
  }

  private static void diamond(double x, double y, double z, double radius, int color) {
    BufferBuilder q = begin(GL11.GL_QUADS);
    vertex(q, x, y, z - radius, color, 0.75f, 0.45f);
    vertex(q, x + radius, y, z, color, 0.75f, 0.45f);
    vertex(q, x, y, z + radius, color, 0.75f, 0.45f);
    vertex(q, x - radius, y, z, color, 0.75f, 0.45f);
    end();
  }

  private static void beam(double x, double y, double z, double height, int color) {
    BufferBuilder q = begin(GL11.GL_QUADS);
    for (int i = 0; i < 12; i++) {
      double a = Math.PI * 2 * i / 12, b = Math.PI * 2 * (i + 1) / 12;
      double ax = Math.cos(a) * 0.045, az = Math.sin(a) * 0.045;
      double bx = Math.cos(b) * 0.045, bz = Math.sin(b) * 0.045;
      vertex(q, x + ax, y, z + az, color, 0.65f, 0.4f);
      vertex(q, x + bx, y, z + bz, color, 0.65f, 0.4f);
      vertex(q, x + bx, y + height, z + bz, color, 0, 0.4f);
      vertex(q, x + ax, y + height, z + az, color, 0, 0.4f);
    }
    end();
  }

  private static BufferBuilder begin(int type) {
    BufferBuilder q = Tessellator.getInstance().getBuffer();
    q.begin(type, DefaultVertexFormats.POSITION_COLOR);
    return q;
  }

  private static void end() {
    Tessellator.getInstance().draw();
  }

  private static void vertex(
      BufferBuilder q, double x, double y, double z, int color, float opacity, float white) {
    float r = ((color >> 16) & 255) / 255f,
        g = ((color >> 8) & 255) / 255f,
        b = (color & 255) / 255f;
    float alpha = ((color >>> 24) & 255) / 255f * Math.max(0f, Math.min(1f, opacity));
    q.pos(x, y, z)
        .color(r + (1 - r) * white, g + (1 - g) * white, b + (1 - b) * white, alpha)
        .endVertex();
  }

  private static void normalBlend() {
    GlStateManager.tryBlendFuncSeparate(
        GlStateManager.SourceFactor.SRC_ALPHA,
        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
        GlStateManager.SourceFactor.ONE,
        GlStateManager.DestFactor.ZERO);
  }
}
