package com.mirror.recorder.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundEvent;

/**
 * Mod sound cues: countdown, recording start and stop, playback start, loop lap and end, stop,
 * error. Fully disabled by the "Mod sounds" setting.
 */
public final class SoundFx {
  private SoundFx() {}

  private static volatile boolean enabled = true;

  public static void setEnabled(boolean v) {
    enabled = v;
  }

  public static boolean isEnabled() {
    return enabled;
  }

  private static void play(SoundEvent event, float pitch) {
    if (!enabled || event == null) return;
    Minecraft mc = Minecraft.getMinecraft();
    if (mc == null || mc.getSoundHandler() == null) return;
    try {
      mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(event, pitch));
    } catch (Exception ignored) {
    }
  }

  public static void countdown(int secondsLeft) {
    play(SoundEvents.BLOCK_NOTE_HAT, secondsLeft <= 1 ? 1.7f : 1.1f);
  }

  public static void recordStart() {
    play(SoundEvents.BLOCK_NOTE_PLING, 1.9f);
  }

  public static void recordStop() {
    play(SoundEvents.BLOCK_NOTE_HAT, 0.9f);
  }

  public static void playStart() {
    play(SoundEvents.BLOCK_NOTE_PLING, 1.4f);
  }

  public static void cycle() {
    play(SoundEvents.BLOCK_NOTE_HAT, 1.9f);
  }

  public static void finish() {
    play(SoundEvents.BLOCK_NOTE_PLING, 1.0f);
  }

  public static void stop() {
    play(SoundEvents.BLOCK_NOTE_BASS, 0.8f);
  }

  public static void error() {
    play(SoundEvents.BLOCK_NOTE_BASS, 0.5f);
  }
}
