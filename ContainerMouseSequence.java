package com.mirror.recorder.handler;

/** Event ownership must follow queued edges, not LWJGL's already-polled button state. */
final class ContainerMouseSequence {
  private Object owner;
  private int buttons;
  private int moveButton = -1;

  static boolean admitEvent(int pending, int limit, Runnable abort) {
    if (pending < limit) return true;
    // An incomplete edge stream is not a recording: end capture instead of losing its release.
    abort.run();
    return false;
  }

  void press(Object screen, int button) {
    if (owner != screen) reset();
    owner = screen;
    buttons |= 1 << button;
    moveButton = button;
  }

  boolean owns(Object screen, int button) {
    return owner == screen && button >= 0 && (buttons & (1 << button)) != 0;
  }

  boolean release(Object screen, int button) {
    if (!owns(screen, button)) return false;
    buttons &= ~(1 << button);
    // GuiScreen stops mouseClickMove on any release, even if another button is still down.
    moveButton = -1;
    if (buttons == 0) owner = null;
    return true;
  }

  int moveButton(Object screen) {
    return owner == screen ? moveButton : -1;
  }

  Object owner() {
    return owner;
  }

  void dropIfScreenChanged(Object screen) {
    if (owner != screen) reset();
  }

  void reset() {
    owner = null;
    buttons = 0;
    moveButton = -1;
  }
}
