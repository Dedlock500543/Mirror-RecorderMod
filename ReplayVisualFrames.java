package com.mirror.recorder.handler;

import com.mirror.recorder.model.Frame;

/** Visual look-ahead must stop before coordinates are rebased into another world. */
final class ReplayVisualFrames {
  private ReplayVisualFrames() {}

  static int visibleCount(Frame[] frames, int requested) {
    int count = Math.min(frames.length, Math.max(0, requested));
    if (count == 0 || frames[0] == null || frames[0].worldReset) return 0;
    for (int i = 1; i < count; i++) {
      if (frames[i] == null || frames[i].worldReset || !sameWorld(frames[0], frames[i])) return i;
    }
    return count;
  }

  static boolean sameWorld(Frame first, Frame current) {
    if (first == null || current == null) return false;
    if (first.worldSequence < 0 && current.worldSequence < 0) return true;
    return first.worldSequence == current.worldSequence
        && first.worldDimension == current.worldDimension;
  }
}
