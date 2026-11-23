package com.mirror.recorder.handler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Set;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.lwjgl.input.Keyboard;

/** Vanilla gesture delivery without cursor rendering or native mouse polling. */
final class ContainerGesturePlayback {
  private final GuiReflection reflect;
  private final ContainerMouseSequence mouse = new ContainerMouseSequence();
  private Field doubleClickField;
  private Field keyDownBufferField;
  private Field dragSplittingField, dragSplittingSlotsField;

  ContainerGesturePlayback(GuiReflection reflect) {
    this.reflect = reflect;
  }

  boolean doubleClick(GuiScreen screen) throws ReflectiveOperationException {
    if (doubleClickField == null) {
      try {
        doubleClickField = GuiContainer.class.getDeclaredField("doubleClick");
      } catch (NoSuchFieldException devMissing) {
        doubleClickField = GuiContainer.class.getDeclaredField("field_146993_M");
      }
      doubleClickField.setAccessible(true);
    }
    return doubleClickField.getBoolean(screen);
  }

  boolean press(GuiScreen screen, int x, int y, int button, boolean shift, boolean doubleClick)
      throws Exception {
    if (button < 0 || button > 1) return false;
    reflect.ensureResolved();
    if (reflect.mouseClicked() == null) return false;
    // A callback can mutate vanilla state and then throw. Cleanup must still own that press.
    mouse.press(screen, button);
    return invoke(screen, reflect.mouseClicked(), x, y, button, shift, true, doubleClick, 0L);
  }

  boolean release(GuiScreen screen, int x, int y, int button, boolean shift) throws Exception {
    if (!mouse.owns(screen, button)) return false;
    try {
      if (reflect.mouseReleased() == null)
        throw new IllegalStateException("Container release callback unavailable");
      return invoke(screen, reflect.mouseReleased(), x, y, button, shift, false, false, 0L);
    } catch (Exception failure) {
      // Do not retry a callback whose slot/network side effects may already have happened.
      // Only abandon local drag bookkeeping; do not change cursor stacks or container slots.
      try {
        cancelDrag(screen);
      } catch (ReflectiveOperationException | RuntimeException cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      }
      throw failure;
    } finally {
      mouse.release(screen, button);
    }
  }

  private void cancelDrag(GuiScreen screen) throws ReflectiveOperationException {
    if (dragSplittingField == null) {
      Field splitting = guiField("dragSplitting", "field_147007_t");
      Field slots = guiField("dragSplittingSlots", "field_147008_s");
      dragSplittingField = splitting;
      dragSplittingSlotsField = slots;
    }
    dragSplittingField.setBoolean(screen, false);
    ((Set<?>) dragSplittingSlotsField.get(screen)).clear();
  }

  private static Field guiField(String dev, String srg) throws ReflectiveOperationException {
    Field field;
    try {
      field = GuiContainer.class.getDeclaredField(dev);
    } catch (NoSuchFieldException missing) {
      field = GuiContainer.class.getDeclaredField(srg);
    }
    field.setAccessible(true);
    return field;
  }

  boolean move(GuiScreen screen, int x, int y, int button, boolean shift, long elapsed)
      throws Exception {
    if (mouse.moveButton(screen) != button) return false;
    return invoke(screen, reflect.mouseClickMove(), x, y, button, shift, false, false, elapsed);
  }

  private boolean invoke(
      GuiScreen screen,
      Method callback,
      int x,
      int y,
      int button,
      boolean shift,
      boolean press,
      boolean recordedDouble,
      long elapsed)
      throws Exception {
    if (callback == null) return false;
    doubleClick(screen);
    if (keyDownBufferField == null) {
      keyDownBufferField = Keyboard.class.getDeclaredField("keyDownBuffer");
      keyDownBufferField.setAccessible(true);
    }
    ByteBuffer keys = (ByteBuffer) keyDownBufferField.get(null);
    ContainerShiftScope.dispatch(
        keys,
        shift,
        press,
        recordedDouble,
        () -> {
          if (callback.getParameterTypes().length == 4)
            callback.invoke(screen, x, y, button, elapsed);
          else callback.invoke(screen, x, y, button);
        },
        value -> doubleClickField.setBoolean(screen, value));
    return true;
  }

  boolean owns(GuiScreen screen, int button) {
    return mouse.owns(screen, button);
  }

  int moveButton(GuiScreen screen) {
    return mouse.moveButton(screen);
  }

  Object owner() {
    return mouse.owner();
  }

  void dropIfScreenChanged(GuiScreen screen) {
    mouse.dropIfScreenChanged(screen);
  }

  void reset() {
    mouse.reset();
  }
}
