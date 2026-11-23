package com.mirror.recorder.handler;

import com.mirror.recorder.config.RecorderConfig;
import com.mirror.recorder.gui.UiTheme;
import com.mirror.recorder.manager.RecorderManager;
import com.mirror.recorder.model.Frame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** A translucent navigation ribbon anchored to the player's feet, never to the camera origin. */
@SideOnly(Side.CLIENT)
public class PathLineRenderer {
  private static final int MAX_POINTS = 300;
  private static final long FLOW_PERIOD_MS = 2800L;
  private final Minecraft mc = Minecraft.getMinecraft();
  private final RecorderManager manager;
  private final RecorderConfig config;
  private final PlaybackTrajectory trajectory;
  private final ReplayVisualClock clock = new ReplayVisualClock();
  private long cachedRun = -1L;
  private int cachedIndex = -1, cachedCount;
  private final Frame[] cachedFrames = new Frame[MAX_POINTS];
  private final double[] x = new double[MAX_POINTS + 1],
      y = new double[MAX_POINTS + 1],
      z = new double[MAX_POINTS + 1];

  public PathLineRenderer(RecorderManager m, RecorderConfig c, PlaybackTrajectory t) {
    manager = m;
    config = c;
    trajectory = t;
  }

  @SubscribeEvent
  public void onRenderWorldLast(RenderWorldLastEvent event) {
    if (!manager.isPlaying()) {
      cachedRun = -1L;
      cachedIndex = -1;
      cachedCount = 0;
      clock.reset();
      return;
    }
    if (!config.isPathLineEnabled() || !trajectory.isReady() || mc.player == null) return;
    refreshFrames();
    int count =
        ReplayVisualFrames.visibleCount(
            cachedFrames, Math.min(cachedCount, Math.max(2, config.getPathLineLength())));
    if (count == 0) return;
    fillCoordinates(count, event.getPartialTicks());
    int color =
        config.getPathLineAlpha() << 24
            | config.getPathLineR() << 16
            | config.getPathLineG() << 8
            | config.getPathLineB();
    long visualTime =
        clock.sample(UiTheme.nowMs(), config.isUiAnimationsEnabled() && !manager.isPaused());
    float travel = ReplayWorldVisuals.phase(visualTime, FLOW_PERIOD_MS);
    RenderGl.begin(config.isPathLineThroughWalls());
    try {
      ReplayWorldVisuals.route(
          x,
          y,
          z,
          count + 1,
          config.getPathLineStyle(),
          config.getPathPointStep(),
          config.getPathPointSize(),
          config.getPathLineWidth(),
          color,
          config.isPathLineFade(),
          travel);
    } finally {
      RenderGl.restore();
    }
  }

  private void fillCoordinates(int count, float partial) {
    RenderManager camera = mc.getRenderManager();
    EntityPlayerSP player = mc.player;
    double lift = config.getPathLineHeight();
    x[0] = player.lastTickPosX + (player.posX - player.lastTickPosX) * partial - camera.viewerPosX;
    y[0] =
        player.lastTickPosY
            + (player.posY - player.lastTickPosY) * partial
            - camera.viewerPosY
            + lift;
    z[0] = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partial - camera.viewerPosZ;
    for (int i = 0; i < count; i++) {
      Frame frame = cachedFrames[i];
      x[i + 1] = trajectory.expectedX(frame) - camera.viewerPosX;
      y[i + 1] = trajectory.expectedY(frame) - camera.viewerPosY + lift;
      z[i + 1] = trajectory.expectedZ(frame) - camera.viewerPosZ;
    }
  }

  private void refreshFrames() {
    long run = manager.getPlaybackRunId();
    int index = manager.getCurrentFrameIndex();
    if (cachedRun == run && cachedIndex == index) return;
    cachedCount = manager.copyPlaybackFramesAhead(cachedFrames);
    cachedRun = run;
    cachedIndex = index;
  }
}
