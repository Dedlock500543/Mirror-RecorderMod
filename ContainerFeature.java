package com.mirror.recorder.handler;

import static com.mirror.recorder.handler.GuiEventQueue.GA_DROP;
import static com.mirror.recorder.handler.GuiEventQueue.GA_PICK;
import static com.mirror.recorder.handler.GuiEventQueue.GA_SWAP;

import com.mirror.recorder.debug.MirrorDebug;
import com.mirror.recorder.handler.GuiEventQueue.GuiClickEvent;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Inventory and chests: deferred press, drag, shift-click, slot keys and middle click. */
@SideOnly(Side.CLIENT)
final class ContainerFeature implements ReplayState.Feature {
  private final RecordingHandler handler;
  private final ReplayState state;
  private final Minecraft mc = Minecraft.getMinecraft();

  ContainerFeature(RecordingHandler handler, ReplayState state) {
    this.handler = handler;
    this.state = state;
    gestures = new ContainerGesturePlayback(state.reflect);
  }

  private final ContainerGesturePlayback gestures;
  private final int[] recordedX = new int[2], recordedY = new int[2];
  private final boolean[] recordedShift = new boolean[2];

  boolean recordedDoubleClick(GuiScreen screen) {
    try {
      return gestures.doubleClick(screen);
    } catch (ReflectiveOperationException | SecurityException e) {
      state.warnClickFailure("Cannot record container double click", e);
      return false;
    }
  }

  void deliverContainerPress(
      GuiScreen screen, int cx, int cy, int button, boolean shift, boolean doubleClick) {
    if (!(screen instanceof GuiContainer) || (button != 0 && button != 1)) return;
    // Only legacy presses have an implicit release; new presses retain all recorded button edges.
    flushDeferredPress();
    recordedX[button] = cx;
    recordedY[button] = cy;
    recordedShift[button] = shift;
    invokeContainerEdge(screen, cx, cy, button, shift, true, doubleClick);
  }

  void deliverContainerUp(GuiScreen screen, int cx, int cy, int button, boolean shift) {
    if (!gestures.owns(screen, button)) return;
    invokeContainerEdge(screen, cx, cy, button, shift, false, false);
  }

  private boolean invokeContainerEdge(
      GuiScreen screen,
      int cx,
      int cy,
      int button,
      boolean shift,
      boolean press,
      boolean doubleClick) {
    state.reflect.ensureResolved();
    int x = MathHelper.clamp(screen.width / 2 + cx, 0, Math.max(0, screen.width - 1));
    int y = MathHelper.clamp(screen.height / 2 + cy, 0, Math.max(0, screen.height - 1));
    handler.pointer.pointerSnap(x, y);
    try {
      return press
          ? gestures.press(screen, x, y, button, shift, doubleClick)
          : gestures.release(screen, x, y, button, shift);
    } catch (Exception e) {
      state.warnClickFailure("Recorded container gesture failed", e);
      stopFailedGesture();
      return false;
    } finally {
      if (mc.currentScreen != screen) handler.clicks.clearLeftClickBlock();
    }
  }

  private void stopFailedGesture() {
    if (state.manager != null) state.manager.stopPlayback();
    if (handler != null) handler.gui.clearQueue();
    reset();
  }

  private boolean quickMoveResolved = false;
  private Method handleMouseClickMethod, slotAtPositionMethod;
  /**
   * Deferred press in a container: the release comes as a separate event (GA_CRELEASE) or with the
   * next press.
   */
  private GuiScreen defPressScreen = null;

  private int defPressX = 0, defPressY = 0, defPressButton = 0;
  private boolean defPressActive = false;
  private long defPressStartNs = 0L;
  /**
   * Press on a slot: the release is deferred because a drag may follow. Old recordings without
   * GA_CRELEASE are released by the next press.
   */
  boolean shouldDeferContainerRelease(GuiScreen screen, int x, int y, GuiClickEvent c) {
    if (!(screen instanceof GuiContainer)) return false;
    if (c == null || c.shift || (c.button != 0 && c.button != 1)) return false;
    if (!quickMoveResolved) resolveQuickMove();
    if (slotAtPositionMethod == null) return false;
    try {
      Object slot = slotAtPositionMethod.invoke(screen, Integer.valueOf(x), Integer.valueOf(y));
      return slot instanceof Slot;
    } catch (Exception e) {
      return false;
    }
  }

  void armDeferredPress(GuiScreen screen, int x, int y, int button) {
    defPressScreen = screen;
    defPressX = x;
    defPressY = y;
    defPressButton = button;
    defPressActive = true;
    defPressStartNs = System.nanoTime();
  }
  /** Forget the deferred press without releasing. */
  void cancelDeferredPress() {
    clearLegacyPress();
    gestures.reset();
  }

  private void clearLegacyPress() {
    defPressActive = false;
    defPressScreen = null;
  }
  /** The window of the deferred press closed or changed: the press is cancelled. */
  void dropStaleDeferredPress() {
    gestures.dropIfScreenChanged(mc.currentScreen);
    if (defPressActive && (mc.currentScreen == null || mc.currentScreen != defPressScreen)) {
      cancelDeferredPress();
    }
  }
  /**
   * Release the deferred press in its own window; a foreign window is left alone and the press is
   * simply cancelled.
   */
  void flushDeferredPress() {
    GuiScreen s = defPressScreen;
    clearLegacyPress();
    if (s == null || mc.currentScreen != s || state.reflect.mouseReleased() == null) return;
    try {
      state
          .reflect
          .mouseReleased()
          .invoke(
              s,
              Integer.valueOf(defPressX),
              Integer.valueOf(defPressY),
              Integer.valueOf(defPressButton));
    } catch (Exception e) {
      state.warnClickFailure("Recorded GUI release failed", e);
    }
    if (mc.currentScreen != s) handler.clicks.clearLeftClickBlock();
  }

  void deliverContainerMove(GuiScreen screen, int cx, int cy, int button, boolean shift) {
    if (!(screen instanceof GuiContainer)) return;
    if (gestures.moveButton(screen) != button
        && (!defPressActive || defPressScreen != screen || defPressButton != button)) return;
    if (!state.reflect.isResolved()) {
      state.reflect.resolve();
      MirrorDebug.probe(
          "GuiScreen.mouseClickMove",
          state.reflect.mouseClickMove() != null,
          "container drag replay");
    }
    if (state.reflect.mouseClickMove() == null) return;
    int x = MathHelper.clamp(screen.width / 2 + cx, 0, Math.max(0, screen.width - 1)),
        y = MathHelper.clamp(screen.height / 2 + cy, 0, Math.max(0, screen.height - 1));
    handler.pointer.pointerGlide(x, y);
    if (gestures.owns(screen, button)) {
      recordedX[button] = cx;
      recordedY[button] = cy;
      recordedShift[button] = shift;
    }
    long since =
        !defPressActive || defPressStartNs <= 0L
            ? 0L
            : Math.max(0L, (System.nanoTime() - defPressStartNs) / 1000000L);
    try {
      if (gestures.owns(screen, button)) gestures.move(screen, x, y, button, shift, since);
      else state.reflect.mouseClickMove().invoke(screen, x, y, button, since);
    } catch (Exception e) {
      state.warnClickFailure("Recorded container drag failed", e);
      stopFailedGesture();
    }
  }

  void deliverContainerRelease(GuiScreen screen, int cx, int cy, int button) {
    // The release itself closes the deferred press: there will be no double release.
    cancelDeferredPress();
    if (!(screen instanceof GuiContainer)) return;
    state.reflect.ensureResolved();
    if (state.reflect.mouseReleased() == null) return;
    int x = MathHelper.clamp(screen.width / 2 + cx, 0, Math.max(0, screen.width - 1)),
        y = MathHelper.clamp(screen.height / 2 + cy, 0, Math.max(0, screen.height - 1));
    handler.pointer.pointerSnap(x, y);
    try {
      state
          .reflect
          .mouseReleased()
          .invoke(screen, Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(button));
    } catch (Exception e) {
      state.warnClickFailure("Recorded GUI release failed", e);
    }
    if (mc.currentScreen != screen) handler.clicks.clearLeftClickBlock();
  }

  void deliverContainerKey(GuiScreen screen, int action, int aux, boolean ctrl, int cx, int cy) {
    if (!(screen instanceof GuiContainer)) return;
    if (!quickMoveResolved) resolveQuickMove();
    if (handleMouseClickMethod == null || slotAtPositionMethod == null || mc.player == null) return;
    try {
      int x = screen.width / 2 + cx, y = screen.height / 2 + cy;
      Object slot = slotAtPositionMethod.invoke(screen, Integer.valueOf(x), Integer.valueOf(y));
      if (!(slot instanceof Slot)) return;
      Slot s = (Slot) slot;
      if (action == GA_DROP) {
        if (s.getHasStack())
          handleMouseClickMethod.invoke(
              screen,
              s,
              Integer.valueOf(s.slotNumber),
              Integer.valueOf(ctrl ? 1 : 0),
              ClickType.THROW);
      } else if (action == GA_PICK) {
        if (s.getHasStack())
          handleMouseClickMethod.invoke(
              screen, s, Integer.valueOf(s.slotNumber), Integer.valueOf(0), ClickType.CLONE);
      } else if (action == GA_SWAP) {
        if (mc.player.inventory.getItemStack().isEmpty())
          handleMouseClickMethod.invoke(
              screen, s, Integer.valueOf(s.slotNumber), Integer.valueOf(aux), ClickType.SWAP);
      }
    } catch (Exception e2) {
      state.warnClickFailure("Recorded container key failed", e2);
    }
  }
  /**
   * Vanilla detects a middle click in a container via the pick-block binding, so CLONE is invoked
   * directly: the binding may have been remapped.
   */
  boolean deliverClone(GuiScreen screen, int x, int y) {
    if (!quickMoveResolved) resolveQuickMove();
    if (handleMouseClickMethod == null || slotAtPositionMethod == null) return false;
    try {
      Object slot = slotAtPositionMethod.invoke(screen, Integer.valueOf(x), Integer.valueOf(y));
      if (!(slot instanceof Slot)) return false;
      Slot s = (Slot) slot;
      if (s.getHasStack())
        handleMouseClickMethod.invoke(
            screen, s, Integer.valueOf(s.slotNumber), Integer.valueOf(0), ClickType.CLONE);
      return true;
    } catch (Exception e) {
      state.warnClickFailure("Recorded middle click failed", e);
      return false;
    }
  }
  /**
   * Vanilla detects shift-click in a container by the physical key, which is not pressed during
   * playback: without this the item is not moved but stays on the cursor.
   */
  boolean deliverQuickMove(GuiScreen screen, int x, int y, int button) {
    if (!(screen instanceof GuiContainer)) return false;
    if (!quickMoveResolved) {
      resolveQuickMove();
      MirrorDebug.probe(
          "GuiContainer.handleMouseClick", handleMouseClickMethod != null, "shift-click replay");
      MirrorDebug.probe(
          "GuiContainer.getSlotAtPosition",
          slotAtPositionMethod != null,
          "shift-click slot lookup");
    }
    if (handleMouseClickMethod == null || slotAtPositionMethod == null) return false;
    try {
      Object slot = slotAtPositionMethod.invoke(screen, Integer.valueOf(x), Integer.valueOf(y));
      if (!(slot instanceof Slot)) return false;
      int id = ((Slot) slot).slotNumber;
      handleMouseClickMethod.invoke(
          screen, slot, Integer.valueOf(id), Integer.valueOf(button), ClickType.QUICK_MOVE);
      return true;
    } catch (Exception e) {
      state.warnClickFailure("Recorded shift-click failed", e);
      return false;
    }
  }

  private void resolveQuickMove() {
    quickMoveResolved = true;
    for (Method m : GuiContainer.class.getDeclaredMethods()) {
      String n = m.getName();
      Class<?>[] a = m.getParameterTypes();
      if (a.length == 4
          && a[0] == Slot.class
          && a[1] == int.class
          && a[2] == int.class
          && a[3] == ClickType.class
          && (n.equals("handleMouseClick") || n.equals("func_184098_a")))
        handleMouseClickMethod = m;
      else if (a.length == 2
          && a[0] == int.class
          && a[1] == int.class
          && m.getReturnType() == Slot.class
          && (n.equals("getSlotAtPosition") || n.equals("func_146975_c"))) slotAtPositionMethod = m;
    }
    try {
      if (handleMouseClickMethod != null) handleMouseClickMethod.setAccessible(true);
      if (slotAtPositionMethod != null) slotAtPositionMethod.setAccessible(true);
    } catch (Exception e) {
      handleMouseClickMethod = null;
      slotAtPositionMethod = null;
      state.warnClickFailure("Cannot access container click methods", e);
    }
  }

  private void flushRecordedPresses() {
    Object owner = gestures.owner();
    if (owner != mc.currentScreen || !(owner instanceof GuiContainer)) {
      gestures.reset();
      return;
    }
    GuiScreen screen = (GuiScreen) owner;
    // Stop must finish owned button pairs through vanilla, not leave dragSplitting in a live GUI.
    for (int button = 0; button < 2; button++) {
      if (mc.currentScreen != screen) {
        gestures.reset();
        return;
      }
      if (gestures.owns(screen, button)) {
        int x =
            MathHelper.clamp(
                screen.width / 2 + recordedX[button], 0, Math.max(0, screen.width - 1));
        int y =
            MathHelper.clamp(
                screen.height / 2 + recordedY[button], 0, Math.max(0, screen.height - 1));
        try {
          // Cleanup needs a paired callback, not a cursor-rendering side effect.
          gestures.release(screen, x, y, button, recordedShift[button]);
        } catch (Exception e) {
          state.warnClickFailure("Recorded container cleanup failed", e);
          if (state.manager != null) state.manager.stopPlayback();
          if (handler != null) handler.gui.clearQueue();
        }
      }
    }
  }

  /** A deferred press in a container is released in its own window. */
  @Override
  public void reset() {
    flushRecordedPresses();
    flushDeferredPress();
  }
}
