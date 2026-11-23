package com.mirror.recorder.handler;

import static com.mirror.recorder.handler.GuiEventQueue.replayableScreen;

import com.mirror.recorder.debug.MirrorDebug;
import com.mirror.recorder.model.Frame;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Playback clicks (LMB/RMB, holding with a screen open). Every world click goes through the vanilla
 * crosshair target (objectMouseOver), so obstacles, entities and reach work as in normal play.
 */
@SideOnly(Side.CLIENT)
final class ClickFeature implements ReplayState.Feature {
  private final RecordingHandler handler;
  private final ReplayState state;
  private final Minecraft mc = Minecraft.getMinecraft();

  ClickFeature(RecordingHandler handler, ReplayState state) {
    this.handler = handler;
    this.state = state;
  }

  private boolean clickMethodsResolved = false;
  private Method clickMouseMethod, rightClickMouseMethod;
  private Field leftClickCounterField;
  private Method sendClickBlockMethod;
  private Field rightClickDelayField;
  private int useHoldFallbackDelay = 0;
  private boolean useTimerAlign = false;
  /**
   * End of a tick with a replayed RMB. Vanilla sets the RMB repeat timer (rightClickDelayTimer) to
   * 4 after the tick countdown; our click fires before the countdown, so the timer is already 3
   * here. We put it back to 4 so the next RMB waits 4 ticks, like normal play.
   */
  void alignUseTimer() {
    if (useTimerAlign) {
      useTimerAlign = false;
      if (rightClickDelayField != null)
        try {
          if (rightClickDelayField.getInt(mc) == 3) rightClickDelayField.setInt(mc, 4);
        } catch (Exception e) {
          state.warnClickFailure("Cannot align use timer", e);
        }
    }
  }
  /** Fallback RMB hold timer (when the vanilla timer field is unavailable) ticks once per tick. */
  void tickUseHoldFallback() {
    if (useHoldFallbackDelay > 0) --useHoldFallbackDelay;
  }
  /**
   * A tick's clicks are replayed exactly in the recorded order (LMB/RMB do not swap). Old
   * recordings have no order, their behavior stays the same.
   */
  void fireOrderedClicks(
      Frame frame, KeyBinding attack, KeyBinding use, int attackShots, int useShots) {
    int[] seq = frame.clickSeq;
    int left = attackShots, right = useShots;
    for (int i = 0; i < seq.length; i++) {
      if (seq[i] == 0) {
        if (left <= 0) continue;
        left--;
        fireRecordedClick(frame, attack, false);
      } else if (seq[i] == 1) {
        if (right <= 0) continue;
        right--;
        fireRecordedClick(frame, use, true);
      }
    }
    for (int i = 0; i < left; i++) fireRecordedClick(frame, attack, false);
    for (int i = 0; i < right; i++) fireRecordedClick(frame, use, true);
  }
  /** Lifts the vanilla attack lock (leftClickCounter): the recorded click must fire. */
  void clearLeftClickBlock() {
    if (!clickMethodsResolved) resolveClickMethods();
    if (leftClickCounterField == null) return;
    try {
      leftClickCounterField.setInt(mc, 0);
    } catch (Exception e) {
      state.warnClickFailure("Cannot clear the left click block", e);
    }
  }

  void forceReleaseClicks() {
    if (mc.gameSettings == null) return;
    handler.keyboard.restorePhysical(mc.gameSettings.keyBindAttack);
    handler.keyboard.restorePhysical(mc.gameSettings.keyBindUseItem);
  }
  /**
   * A discrete click is invoked directly: the KeyBinding press queue is wiped by unPressAllKeys
   * when the pause menu/inventory opens, while on a server the world keeps ticking at that moment.
   * The crosshair target is updated before the click so the hit matches the current frame. Swing as
   * in vanilla: one press, one swing; holding is handled by the game itself (mining keeps its own
   * rhythm).
   */
  private void fireRecordedClick(Frame frame, KeyBinding key, boolean right) {
    handler.keyboard.drainPressQueue(key);
    if (frame.guiClick && replayableScreen(frame.guiScreen)) {
      handler.gui.queueGuiClick(frame, right ? 1 : 0);
      return;
    }
    if (mc.player == null || mc.world == null) return;
    if (!clickMethodsResolved) {
      resolveClickMethods();
      MirrorDebug.probe("Minecraft.clickMouse", clickMouseMethod != null, "world click replay");
      MirrorDebug.probe(
          "Minecraft.rightClickMouse", rightClickMouseMethod != null, "world use replay");
      MirrorDebug.probe(
          "Minecraft.leftClickCounter", leftClickCounterField != null, "left click unblock");
      MirrorDebug.probe(
          "Minecraft.sendClickBlockToController",
          sendClickBlockMethod != null,
          "held attack with open screen");
    }
    if (mc.entityRenderer != null) mc.entityRenderer.getMouseOver(1.0F);
    if (!right && leftClickCounterField != null)
      try {
        leftClickCounterField.setInt(mc, 0);
      } catch (Exception e) {
        state.warnClickFailure("Cannot unblock recorded left click", e);
      }
    Method method = right ? rightClickMouseMethod : clickMouseMethod;
    if (method != null)
      try {
        method.invoke(mc);
        if (right) useTimerAlign = true;
      } catch (Exception e) {
        state.warnClickFailure("Recorded world click failed", e);
      }
  }

  private void resolveClickMethods() {
    clickMethodsResolved = true;
    for (Method m : Minecraft.class.getDeclaredMethods()) {
      String n = m.getName();
      Class<?>[] a = m.getParameterTypes();
      if (a.length == 0 && (n.equals("clickMouse") || n.equals("func_147116_af")))
        clickMouseMethod = m;
      else if (a.length == 0 && (n.equals("rightClickMouse") || n.equals("func_147121_ag")))
        rightClickMouseMethod = m;
      else if (a.length == 1
          && a[0] == boolean.class
          && (n.equals("sendClickBlockToController") || n.equals("func_147115_a")))
        sendClickBlockMethod = m;
    }
    for (Field f : Minecraft.class.getDeclaredFields()) {
      String n = f.getName();
      if (f.getType() != int.class) continue;
      if (n.equals("leftClickCounter") || n.equals("field_71429_W")) leftClickCounterField = f;
      else if (n.equals("rightClickDelayTimer") || n.equals("field_71467_ac"))
        rightClickDelayField = f;
    }
    try {
      if (clickMouseMethod != null) clickMouseMethod.setAccessible(true);
      if (rightClickMouseMethod != null) rightClickMouseMethod.setAccessible(true);
      if (sendClickBlockMethod != null) sendClickBlockMethod.setAccessible(true);
      if (leftClickCounterField != null) leftClickCounterField.setAccessible(true);
      if (rightClickDelayField != null) rightClickDelayField.setAccessible(true);
    } catch (Exception e) {
      clickMouseMethod = null;
      rightClickMouseMethod = null;
      sendClickBlockMethod = null;
      leftClickCounterField = null;
      rightClickDelayField = null;
      state.warnClickFailure("Cannot access Minecraft click methods", e);
    }
  }
  /**
   * An open screen suppresses vanilla block breaking (currentScreen check). On a server the world
   * keeps ticking, so the call is repeated directly.
   */
  void driveScreenAttackHold() {
    if (mc.isGamePaused() || mc.player == null || mc.world == null) return;
    if (!clickMethodsResolved) resolveClickMethods();
    if (leftClickCounterField != null)
      try {
        leftClickCounterField.setInt(mc, 0);
      } catch (Exception e) {
        state.warnClickFailure("Cannot unblock held left click", e);
      }
    if (mc.entityRenderer != null) mc.entityRenderer.getMouseOver(1.0F);
    if (sendClickBlockMethod != null)
      try {
        sendClickBlockMethod.invoke(mc, Boolean.TRUE);
      } catch (Exception e) {
        state.warnClickFailure("Held attack with open screen failed", e);
      }
  }
  /**
   * An open screen suppresses vanilla RMB repeat through the key context. The same rhythm is
   * repeated manually: call when the delay timer reaches zero and the hand is free.
   */
  void driveScreenUseHold() {
    EntityPlayerSP player = mc.player;
    if (mc.isGamePaused() || player == null || mc.world == null || player.isHandActive()) return;
    if (!clickMethodsResolved) resolveClickMethods();
    int delay;
    if (rightClickDelayField != null) {
      try {
        delay = rightClickDelayField.getInt(mc);
      } catch (Exception e) {
        delay = useHoldFallbackDelay;
      }
    } else delay = useHoldFallbackDelay;
    if (delay > 0) return;
    if (mc.entityRenderer != null) mc.entityRenderer.getMouseOver(1.0F);
    if (rightClickMouseMethod != null) {
      try {
        rightClickMouseMethod.invoke(mc);
        useHoldFallbackDelay = 4;
      } catch (Exception e) {
        state.warnClickFailure("Held use with open screen failed", e);
      }
    }
  }

  @Override
  public void reset() {
    useHoldFallbackDelay = 0;
    useTimerAlign = false;
  }
}
