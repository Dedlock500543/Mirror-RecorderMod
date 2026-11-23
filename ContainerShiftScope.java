package com.mirror.recorder.handler;

import java.nio.ByteBuffer;
import org.lwjgl.input.Keyboard;

/** Vanilla reads LWJGL directly; scope the recorded modifier to one synchronous GUI callback. */
final class ContainerShiftScope implements AutoCloseable {
  private final ByteBuffer keys;
  private final byte left;
  private final byte right;

  ContainerShiftScope(ByteBuffer keys, boolean shift) {
    this.keys = keys;
    left = keys.get(Keyboard.KEY_LSHIFT);
    right = keys.get(Keyboard.KEY_RSHIFT);
    keys.put(Keyboard.KEY_LSHIFT, (byte) (shift ? 1 : 0));
    keys.put(Keyboard.KEY_RSHIFT, (byte) 0);
  }

  interface Callback {
    void invoke() throws Exception;
  }

  interface DoubleClickWriter {
    void set(boolean doubleClick) throws Exception;
  }

  static void dispatch(
      ByteBuffer keys,
      boolean shift,
      boolean press,
      boolean doubleClick,
      Callback callback,
      DoubleClickWriter writer)
      throws Exception {
    try (ContainerShiftScope ignored = new ContainerShiftScope(keys, shift)) {
      callback.invoke();
      // Vanilla consumes this on release. Its press-time wall-clock guess must not replace the
      // recording's decision when speed, pauses or batching change the spacing between presses.
      if (press) writer.set(doubleClick);
    }
  }

  @Override
  public void close() {
    keys.put(Keyboard.KEY_LSHIFT, left);
    keys.put(Keyboard.KEY_RSHIFT, right);
  }
}
