package com.mirror.recorder.handler;

import com.mirror.recorder.model.Frame;
import net.minecraft.util.MovementInput;

/** Boat controls use four independent directions, including opposing presses in modern frames. */
final class BoatReplayInput {
  private BoatReplayInput() {}

  static void apply(MovementInput input, Frame frame) {
    int mask = frame.hasKeyMask ? frame.keyMask : legacyMask(frame.moveForward, frame.moveStrafe);
    input.forwardKeyDown = (mask & Frame.K_FORWARD) != 0;
    input.backKeyDown = (mask & Frame.K_BACK) != 0;
    input.leftKeyDown = (mask & Frame.K_LEFT) != 0;
    input.rightKeyDown = (mask & Frame.K_RIGHT) != 0;
  }

  static int captureMask(MovementInput input, int mask) {
    // END key bindings can already be cleared by a GUI; these are the booleans updateRidden sent.
    int directions = Frame.K_FORWARD | Frame.K_BACK | Frame.K_LEFT | Frame.K_RIGHT;
    mask &= ~directions;
    if (input.forwardKeyDown) mask |= Frame.K_FORWARD;
    if (input.backKeyDown) mask |= Frame.K_BACK;
    if (input.leftKeyDown) mask |= Frame.K_LEFT;
    if (input.rightKeyDown) mask |= Frame.K_RIGHT;
    return mask;
  }

  private static int legacyMask(float forward, float strafe) {
    // Analog-only recordings cannot preserve opposing keys; their signs retain active directions.
    int mask = 0;
    if (forward > 0f) mask |= Frame.K_FORWARD;
    else if (forward < 0f) mask |= Frame.K_BACK;
    if (strafe > 0f) mask |= Frame.K_LEFT;
    else if (strafe < 0f) mask |= Frame.K_RIGHT;
    return mask;
  }

  static void clear(MovementInput input) {
    input.forwardKeyDown = false;
    input.backKeyDown = false;
    input.leftKeyDown = false;
    input.rightKeyDown = false;
  }
}
