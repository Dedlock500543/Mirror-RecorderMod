package com.mirror.recorder.handler;

import com.mirror.recorder.config.RecorderConfig;
import com.mirror.recorder.gui.Lang;
import com.mirror.recorder.gui.UiFonts;
import com.mirror.recorder.gui.UiTheme;
import com.mirror.recorder.manager.RecorderManager;
import com.mirror.recorder.model.Frame;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/** A segmented ground reticle and compact world label for recording, return and active playback. */
@SideOnly(Side.CLIENT)
public class StartMarkerRenderer {
  private final Minecraft mc = Minecraft.getMinecraft();
  private final RecorderManager manager;
  private final RecorderConfig config;
  private final BaritoneReturnController returner;
  private final PlaybackTrajectory trajectory;
  private final ReplayVisualClock clock = new ReplayVisualClock();
  private double x, y, z;

  public StartMarkerRenderer(RecorderManager m, RecorderConfig c, BaritoneReturnController r) {
    this(m, c, r, null);
  }

  public StartMarkerRenderer(
      RecorderManager m, RecorderConfig c, BaritoneReturnController r, PlaybackTrajectory t) {
    manager = m;
    config = c;
    returner = r;
    trajectory = t;
  }

  @SubscribeEvent
  public void onRenderWorldLast(RenderWorldLastEvent event) {
    if (mc.player == null || mc.world == null) return;
    boolean recovery = returner != null && returner.isRecovering();
    int mode = config.getStartMarkerMode();
    if (mode == 0 && !recovery) return;
    if (!locate(recovery)) {
      clock.reset();
      return;
    }
    if (recovery && mode == 0) mode = 3;
    RenderManager camera = mc.getRenderManager();
    double rx = x - camera.viewerPosX, ry = y - camera.viewerPosY, rz = z - camera.viewerPosZ;
    int color =
        config.getMarkerAlpha() << 24
            | config.getMarkerR() << 16
            | config.getMarkerG() << 8
            | config.getMarkerB();
    boolean animate = config.isMarkerPulse() && config.isUiAnimationsEnabled();
    long visualTime = clock.sample(UiTheme.nowMs(), animate && !manager.isPaused());
    RenderGl.begin(config.isMarkerThroughWalls());
    try {
      ReplayWorldVisuals.marker(
          rx,
          ry,
          rz,
          mode,
          config.getMarkerRadius(),
          config.getMarkerBeaconHeight(),
          color,
          visualTime,
          animate);
      drawLabelIfVisible(rx, ry, rz, recovery, color);
    } finally {
      RenderGl.restore();
    }
  }

  /** The recorded world's start must never leak into a later dimension or world segment. */
  private boolean locate(boolean recovery) {
    if (recovery) return target();
    if (manager.isRecording() && manager.hasRecordingStart()) {
      x = manager.getRecordingStartX();
      y = manager.getRecordingStartY();
      z = manager.getRecordingStartZ();
      return true;
    }
    if (!manager.isPlaying())
      return returner != null && returner.isBusy() && !returner.isLeaving() && target();
    Frame first = manager.getPlaybackFirstFrame(), current = manager.getCurrentPlaybackFrame();
    if (!ReplayVisualFrames.sameWorld(first, current)) return false;
    if (trajectory == null || !trajectory.isReady()) return false;
    x = trajectory.expectedX(first);
    y = trajectory.expectedY(first);
    z = trajectory.expectedZ(first);
    return true;
  }

  private boolean target() {
    if (returner == null) return false;
    x = returner.getTargetX();
    y = returner.getTargetY();
    z = returner.getTargetZ();
    return true;
  }

  private void drawLabelIfVisible(double rx, double ry, double rz, boolean recovery, int color) {
    EntityPlayerSP p = mc.player;
    double dx = p.posX - x, dy = p.posY - y, dz = p.posZ - z;
    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (!config.isMarkerLabel()
        || !config.isMarkerLabelUnlimited() && distance > config.getMarkerLabelDistance()) return;
    String label =
        recovery
            ? Lang.t("msg.start_point")
            : manager.isRecording() ? Lang.t("msg.recording_start") : Lang.t("hud.playback_start");
    String metric = Lang.t("hud.marker_distance", String.format(Locale.ROOT, "%.1f", distance));
    drawLabel(rx, ry, rz, label, metric, color);
  }

  private void drawLabel(double rx, double ry, double rz, String label, String metric, int color) {
    FontRenderer font = UiFonts.getWorld(mc);
    if (font == null) return;
    GlStateManager.pushMatrix();
    try {
      GlStateManager.translate(rx, ry + 1.35d, rz);
      GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0, 1, 0);
      GlStateManager.rotate(mc.getRenderManager().playerViewX, 1, 0, 0);
      GlStateManager.scale(-0.023f, -0.023f, 0.023f);
      if (config.isMarkerThroughWalls()) GlStateManager.disableDepth();
      int width = Math.max(font.getStringWidth(label), font.getStringWidth(metric)) + 24;
      int left = -width / 2;
      drawPlate(left, width, color);
      GlStateManager.enableTexture2D();
      GlStateManager.enableAlpha();
      GlStateManager.color(1, 1, 1, 1);
      font.drawString(label, left + 15, 0, UiTheme.TEXT, false);
      font.drawString(metric, left + 15, 12, UiTheme.TEXT_MUTE, false);
    } finally {
      GlStateManager.popMatrix();
    }
  }

  /** Menu primitives disable depth; a world label must obey the through-walls setting instead. */
  private static void drawPlate(int left, int width, int color) {
    BufferBuilder q = Tessellator.getInstance().getBuffer();
    GlStateManager.disableTexture2D();
    GlStateManager.disableAlpha();
    GlStateManager.enableBlend();
    q.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
    plateVertex(q, left + width / 2, 11, 0xDA0B1422);
    plateOutline(q, left, left + width, 0xDA0B1422);
    plateVertex(q, left + 4, -7, 0xDA0B1422);
    Tessellator.getInstance().draw();
    q.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
    plateOutline(q, left, left + width, UiTheme.alpha(0xFF000000 | color, 0.40f));
    Tessellator.getInstance().draw();
    q.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
    plateVertex(q, left + 7, 1, 0xFF000000 | color);
    plateVertex(q, left + 9, 1, 0xFF000000 | color);
    plateVertex(q, left + 9, 21, 0xFF000000 | color);
    plateVertex(q, left + 7, 21, 0xFF000000 | color);
    Tessellator.getInstance().draw();
  }

  private static void plateOutline(BufferBuilder q, int left, int right, int color) {
    plateVertex(q, left + 4, -7, color);
    plateVertex(q, right - 4, -7, color);
    plateVertex(q, right, -3, color);
    plateVertex(q, right, 25, color);
    plateVertex(q, right - 4, 29, color);
    plateVertex(q, left + 4, 29, color);
    plateVertex(q, left, 25, color);
    plateVertex(q, left, -3, color);
  }

  private static void plateVertex(BufferBuilder q, int x, int y, int color) {
    q.pos(x, y, 0)
        .color((color >> 16) & 255, (color >> 8) & 255, color & 255, (color >>> 24) & 255)
        .endVertex();
  }
}
