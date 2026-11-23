package com.mirror.recorder.handler;

import com.mirror.recorder.config.RecorderConfig;
import com.mirror.recorder.gui.Lang;
import com.mirror.recorder.gui.ReplayHudLayout;
import com.mirror.recorder.gui.ReplayHudRenderer;
import com.mirror.recorder.gui.UiFonts;
import com.mirror.recorder.gui.UiTheme;
import com.mirror.recorder.manager.RecorderManager;
import com.mirror.recorder.storage.StorageManager;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;

/**
 * On-screen status bar: recording, playback, loop, pause, return and countdown; it can be dragged.
 */
@SideOnly(Side.CLIENT)
public class StatusOverlayHandler {
  private final Minecraft mc = Minecraft.getMinecraft();
  private final RecorderManager manager;
  private final RecorderConfig config;
  private final BaritoneReturnController returner;
  private String title = "", detail = "";
  private final ReplayHudRenderer.Data card = new ReplayHudRenderer.Data();
  private float progress = -1f;
  private int color = UiTheme.ACCENT,
      drawX = 8,
      drawY = 8,
      drawW = 0,
      drawH = 42,
      cardWidth = 0,
      dragDX = 0,
      dragDY = 0,
      dragW = 0;
  private static final int HUD_MARGIN = ReplayHudLayout.MARGIN;
  private static final float HUD_SCALE = 0.80f;
  private boolean dragging = false, hudVisible = false;

  public StatusOverlayHandler(RecorderManager m, RecorderConfig c, BaritoneReturnController r) {
    manager = m;
    config = c;
    returner = r;
  }

  @SubscribeEvent
  public void onTick(TickEvent.ClientTickEvent e) {
    if (e.phase != TickEvent.Phase.END) return;
    if (dragging && !Mouse.isButtonDown(0)) {
      dragging = false;
      dragW = 0;
      config.saveHudPosition();
    }
    title = "";
    detail = "";
    progress = -1f;
    card.meta = card.left = card.right = card.metric = "";
    card.paused = false;
    card.icon = "play";
    if (mc.player == null || mc.world == null) return;
    int slot = manager.getActiveOperationSlot();
    if (manager.isRecording()) {
      showRecording(slot);
    } else if (manager.isPlaying()) {
      showPlayback(slot);
    } else if (returner != null && returner.isRecovering()) {
      showRecovery();
    } else if (returner != null && returner.isBusy() && !returner.isLeaving()) {
      showReturn();
    } else if (manager.hasPendingAction()) {
      showPending();
    }
  }

  /** Recording: elapsed and remaining time until the frame limit. */
  private void showRecording(int slot) {
    long ticks = manager.getRecordingTickCounter(),
        left = Math.max(0L, (long) StorageManager.MAX_FRAMES - ticks);
    title = Lang.t("hud.recording");
    detail = slotLabel(slot);
    progress = Math.min(1f, ticks / (float) StorageManager.MAX_FRAMES);
    color = UiTheme.RED;
    card.icon = "record";
    card.left = time(ticks);
    card.right = Lang.t("hud.remaining_time", time(left));
    card.meta = Lang.t("hud.frames_recorded", ticks);
  }

  /** Only read playback state: the HUD never advances clocks or changes operation settings. */
  private void showPlayback(int slot) {
    int frames = Math.max(1, manager.getSlotFrameCount(slot));
    int current = Math.min(frames, manager.getCurrentFrameIndex() + 1);
    boolean looping = returner != null && returner.isLooping();
    title = Lang.t("hud.playback");
    detail = slotLabel(slot);
    color =
        0xFF000000
            | config.getPathLineR() << 16
            | config.getPathLineG() << 8
            | config.getPathLineB();
    progress = current / (float) frames;
    card.metric = Math.round(progress * 100f) + "%";
    String speed = String.format(Locale.ROOT, "%.2f×", config.getPlaybackSpeed());
    if (looping) {
      int limit = returner.getLoopLimit();
      card.meta =
          Lang.t(
              "hud.loop_meta",
              returner.getCycle(),
              limit == 0 ? "∞" : String.valueOf(limit),
              speed);
      card.icon = "reload";
    } else card.meta = Lang.t("hud.speed_meta", speed);
    card.left = time(current) + " / " + time(frames);
    long remaining = (long) Math.ceil((frames - current) / (double) config.getPlaybackSpeed());
    card.right = Lang.t("hud.remaining_time", time(remaining));
    if (manager.isPaused()) {
      title = Lang.t("hud.paused");
      color = UiTheme.AMBER;
      card.icon = "pause";
      card.paused = true;
    }
  }

  private String slotLabel(int slot) {
    String name = manager.getSlotName(slot);
    String label = Lang.t("cmd.slot_2") + slot;
    return name == null || name.trim().isEmpty() ? label : label + "  ·  " + name;
  }

  /** Recovery: the player has to step onto the start point themselves. */
  private void showRecovery() {
    title = Lang.t("hud.start_point_required");
    detail = Lang.t("hud.stand_on_the_saved_position");
    color = UiTheme.AMBER;
    card.icon = "flag";
  }

  /** Return or preparation before playback, including runs with auto-return disabled. */
  private void showReturn() {
    title = Lang.t("hud.auto_return");
    detail = Lang.t("hud.returning_to_start_slot") + returner.getSlot();
    color = UiTheme.GREEN;
    card.icon = "return_start";
  }

  /** Countdown to the start of recording or playback. */
  private void showPending() {
    int sec = manager.getDelaySecondsRemaining();
    title = Lang.t("hud.starting");
    detail =
        (sec > 0 ? Lang.t("hud.left") + sec + Lang.t("hud.s") : "")
            + Lang.t("cmd.slot_2")
            + manager.getPendingSlot();
    color = UiTheme.AMBER;
    card.icon = "timer";
  }

  @SubscribeEvent
  public void onRender(RenderGameOverlayEvent.Post e) {
    if (e.getType() != RenderGameOverlayEvent.ElementType.ALL || mc.fontRenderer == null) return;
    if (!TransientChat.messagesEnabled()) {
      hudVisible = false;
      dragging = false;
      return;
    }
    String t = title, d = detail;
    boolean preview =
        mc.currentScreen != null
            && mc.currentScreen.getClass().getName().startsWith("com.mirror.recorder.gui.");
    if (t.isEmpty() && preview) {
      t = Lang.t("hud.status_indicator");
      d = Lang.t("hud.shift_drag_to_move_the");
      color = UiTheme.ACCENT;
    }
    hudVisible = !t.isEmpty();
    if (!hudVisible) return;
    ScaledResolution sr = new ScaledResolution(mc);
    int screenW = sr.getScaledWidth(), screenH = sr.getScaledHeight();
    FontRenderer fr = UiFonts.get(mc);
    card.title = t;
    card.context = d;
    card.color = color;
    card.progress = progress;
    card.dragging = dragging;
    // Scale the original card as a whole: wrapping, artwork and relative spacing stay unchanged.
    ReplayHudLayout layout =
        new ReplayHudLayout(fr, card, (int) Math.floor(screenW / HUD_SCALE), dragging ? dragW : 0);
    cardWidth = layout.width;
    drawW = (int) Math.ceil(layout.width * HUD_SCALE);
    drawH = (int) Math.ceil(layout.height * HUD_SCALE);

    drawX =
        MathHelper.clamp(
            config.getHudX(), HUD_MARGIN, Math.max(HUD_MARGIN, screenW - drawW - HUD_MARGIN));
    drawY =
        MathHelper.clamp(
            config.getHudY(), HUD_MARGIN, Math.max(HUD_MARGIN, screenH - drawH - HUD_MARGIN));
    GlStateManager.pushMatrix();
    try {
      GlStateManager.translate(drawX, drawY, 0f);
      GlStateManager.scale(HUD_SCALE, HUD_SCALE, 1f);
      ReplayHudRenderer.draw(
          fr, card, layout, 0, 0, UiTheme.nowMs(), config.isUiAnimationsEnabled());
    } finally {
      GlStateManager.popMatrix();
    }
  }

  private String time(long ticks) {
    long sec = Math.max(0L, ticks / (long) RecorderManager.TICKS_PER_SECOND);
    if (sec >= 3600L)
      return String.format(Locale.ROOT, "%d:%02d:%02d", sec / 3600L, sec / 60L % 60L, sec % 60L);
    return String.format(Locale.ROOT, "%02d:%02d", sec / 60L, sec % 60L);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void onGuiMouse(GuiScreenEvent.MouseInputEvent.Pre e) {
    if (!hudVisible
        || mc.currentScreen == null
        || !mc.currentScreen.getClass().getName().startsWith("com.mirror.recorder.gui.")) {
      if (dragging) {
        dragging = false;
        dragW = 0;
        config.saveHudPosition();
      }
      return;
    }
    ScaledResolution sr = new ScaledResolution(mc);
    int mx = Mouse.getEventX() * sr.getScaledWidth() / Math.max(1, mc.displayWidth),
        my =
            sr.getScaledHeight()
                - Mouse.getEventY() * sr.getScaledHeight() / Math.max(1, mc.displayHeight)
                - 1,
        button = Mouse.getEventButton();
    if (button == 0
        && Mouse.getEventButtonState()
        && GuiScreen.isShiftKeyDown()
        && mx >= drawX
        && mx < drawX + drawW
        && my >= drawY
        && my < drawY + drawH) {
      dragging = true;
      dragW = cardWidth;
      dragDX = mx - drawX;
      dragDY = my - drawY;
      e.setCanceled(true);
    }
    if (dragging) {
      int
          nx =
              MathHelper.clamp(
                  mx - dragDX,
                  HUD_MARGIN,
                  Math.max(HUD_MARGIN, sr.getScaledWidth() - drawW - HUD_MARGIN)),
          ny =
              MathHelper.clamp(
                  my - dragDY,
                  HUD_MARGIN,
                  Math.max(HUD_MARGIN, sr.getScaledHeight() - drawH - HUD_MARGIN));
      config.setHudPosition(nx, ny);
      e.setCanceled(true);
      if (button == 0 && !Mouse.getEventButtonState()) {
        dragging = false;
        dragW = 0;
        config.saveHudPosition();
      }
    }
  }
}
