package com.mirror.recorder.handler;

import com.mirror.recorder.model.Frame;

/** Recorded pick semantics must not depend on the physical Ctrl key during playback. */
final class PickBlockReplayPolicy {
  private PickBlockReplayPolicy() {}

  static int recordedAction(boolean controlDown) {
    return controlDown ? Frame.ACT_PICK_CONTENTS : Frame.ACT_PICK;
  }

  static boolean copiesTileEntity(int action, boolean creative, boolean hasTileEntity) {
    return action == Frame.ACT_PICK_CONTENTS && creative && hasTileEntity;
  }
}
