package com.mirror.recorder.handler;

import static com.mirror.recorder.handler.ReplayState.windowActive;

import com.mirror.recorder.config.RecorderConfig;
import com.mirror.recorder.debug.MirrorDebug;
import com.mirror.recorder.gui.Lang;
import com.mirror.recorder.manager.RecorderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.client.gui.inventory.GuiEditSign;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Playback stop conditions: manual movement and mouse movement. */
@SideOnly(Side.CLIENT)
final class StopConditions implements ReplayState.Feature {
  private final RecordingHandler handler;
  private final Minecraft mc = Minecraft.getMinecraft();
  private final RecorderManager manager;
  private final RecorderConfig config;

  StopConditions(RecordingHandler handler, ReplayState state) {
    this.handler = handler;
    this.manager = state.manager;
    this.config = state.config;
  }

  private final MouseStopEvidence mouse = new MouseStopEvidence();
  /** The mod itself put the cursor at (ex, ey): our own shift does not count as hand movement. */
  void cursorMovedByMod(int ex, int ey) {
    if (!mouseEnabled() || mc.currentScreen == null) return;
    mouse.warp(mc.currentScreen, ex, ey, mc.displayWidth, mc.displayHeight);
  }
  /**
   * Before the mod moves the cursor, count what the hand managed to move it since the mod's last
   * position.
   */
  void sampleBeforeModWarp() {
    if (!mouseEnabled() || !windowActive() || mc.currentScreen == null) return;
    sampleFreeCursor();
  }
  /** The mod restored the mouse grab in game. */
  void onMouseRegrabbed() {
    mouse.regrab();
  }
  /** The window is focused again. */
  void onFocusReturned() {
    mouse.regrab();
  }

  private boolean mouseEnabled() {
    if (config.isStopOnMouseMove() && handler.hasPlaybackOperation() && !manager.isPaused())
      return true;
    mouse.reset();
    return false;
  }

  /** Forge delivers each native event once; never reuse MouseHelper's last render delta. */
  void recordMouseMovement(int dx, int dy, boolean active) {
    if (!mouseEnabled()) return;
    if (!active) {
      mouse.reset();
      return;
    }
    if (mc.currentScreen == null && mc.inGameHasFocus) {
      mouse.forgetCursor();
      mouse.delta(dx, dy);
    }
  }

  /** Open windows use a cursor baseline; game motion is observed through fresh Forge events. */
  void sampleMouseMovement() {
    if (!mouseEnabled()) return;
    sampleMouseMovement(windowActive());
  }

  void sampleMouseMovement(boolean active) {
    if (!mouseEnabled()) return;
    if (!active) {
      mouse.reset();
      return;
    }
    if (mc.currentScreen == null && mc.inGameHasFocus) {
      mouse.forgetCursor();
      return;
    }
    if (mc.currentScreen != null) sampleFreeCursor();
    else mouse.reset();
  }

  private void sampleFreeCursor() {
    try {
      mouse.cursor(mc.currentScreen, Mouse.getX(), Mouse.getY(), mc.displayWidth, mc.displayHeight);
    } catch (Exception e) {
      mouse.reset();
    }
  }
  /**
   * Threshold in pixels: hand jitter does not break playback, deliberate movement does. The
   * accumulator decays by a quarter per tick instead of being zeroed: with stabilization on the
   * camera does not move and the player moves the mouse slowly, so the threshold was never reached
   * within one tick and the stop waited for a jerk. Only a cursor regrab has a grace: beginning a
   * return or countdown must not hide deliberate manual input.
   */
  private boolean mouseStopTriggered() {
    return mouseEnabled() && mouse.triggered(config.getStopOnMouseThreshold());
  }
  /**
   * The mouse stop must work both with a screen open and in a frozen world (singleplayer without
   * LAN + a screen that pauses the game): otherwise playback cannot be stopped from the Minecraft
   * settings at all, and that is what the setting is for.
   */
  boolean stopByMouseMove() {
    if (!mouseEnabled()) return false;
    if (!windowActive()) {
      mouse.reset();
      return false;
    }
    if (!mouseStopTriggered()) return false;
    MirrorDebug.log(
        "PLAYBACK",
        "STOP: manual mouse movement at frame "
            + manager.getCurrentFrameIndex()
            + " (run "
            + manager.getPlaybackRunId()
            + ", screen "
            + (mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName())
            + ")");
    MirrorDebug.csvClose();
    handler.cancelPlaybackOperation(Lang.t("msg.playback_stopped_mouse_movement_detected"));
    return true;
  }
  /** Drops owned cursor evidence on stop; a new operation starts without a timed blind window. */
  void resetMouseStop() {
    mouse.reset();
  }

  @Override
  public void reset() {
    resetMouseStop();
  }

  /** Manual player movement breaks playback: one path for the world and for an open window. */
  void stopByManualMovement(String where) {
    MirrorDebug.log("PLAYBACK", "STOP: manual movement detected at " + where);
    MirrorDebug.csvClose();
    handler.cancelPlaybackOperation(Lang.t("msg.playback_stopped_manual_movement_detected"));
  }
  /**
   * Movement keys by code: with a window open vanilla isKeyDown() is silent (IN_GAME context), so
   * the event code is compared.
   */
  boolean movementKey(int code) {
    GameSettings g = mc.gameSettings;
    if (g == null || code == 0) return false;
    return code == g.keyBindForward.getKeyCode()
        || code == g.keyBindBack.getKeyCode()
        || code == g.keyBindLeft.getKeyCode()
        || code == g.keyBindRight.getKeyCode()
        || code == g.keyBindJump.getKeyCode();
  }

  boolean stopByUserInput() {
    if (OperationStopPolicy.movement(
        handler.hasPlaybackOperation(),
        config.isStopOnMove(),
        manager.isPaused(),
        typing(),
        windowActive(),
        isRealMovement())) {
      stopByManualMovement("operation input before replay overrides");
      return true;
    }
    return stopByMouseMove();
  }

  void advanceMouseTick() {
    mouse.tick();
  }

  private boolean typing() {
    return mc.currentScreen instanceof GuiChat
        || mc.currentScreen instanceof GuiScreenBook
        || mc.currentScreen instanceof GuiEditSign
        || mc.currentScreen instanceof GuiControls
        || (mc.currentScreen != null && handler.keyboard.hasFocusedTextField(mc.currentScreen));
  }

  boolean isRealMovement() {
    GameSettings g = mc.gameSettings;
    return g != null
        && !typing()
        && windowActive()
        && (physical(g.keyBindForward)
            || physical(g.keyBindBack)
            || physical(g.keyBindLeft)
            || physical(g.keyBindRight)
            || physical(g.keyBindJump));
  }

  private boolean physical(KeyBinding binding) {
    int code = binding.getKeyCode();
    if (!binding.getKeyModifier().isActive(binding.getKeyConflictContext())) return false;
    if (code < 0) {
      int button = code + KeyboardFeature.MOUSE_CODE_OFFSET;
      return Mouse.isCreated()
          && button >= 0
          && button < Mouse.getButtonCount()
          && Mouse.isButtonDown(button);
    }
    return code > 0
        && code < Keyboard.KEYBOARD_SIZE
        && Keyboard.isCreated()
        && Keyboard.isKeyDown(code);
  }
}
