package com.mirror.recorder.handler;

/** Manual evidence belongs to the operation, not to a particular replay/return phase. */
public final class OperationStopPolicy {
  private OperationStopPolicy() {}

  public static boolean playbackOperation(
      boolean recording, boolean playing, boolean returnOrLoop) {
    return !recording && (playing || returnOrLoop);
  }

  public static boolean movement(
      boolean operation,
      boolean enabled,
      boolean paused,
      boolean typing,
      boolean focused,
      boolean physicalKey) {
    return operation && enabled && !paused && !typing && focused && physicalKey;
  }
}
