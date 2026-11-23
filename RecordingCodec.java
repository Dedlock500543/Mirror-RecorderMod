package com.mirror.recorder.storage;

import com.mirror.recorder.handler.SemanticGuiEvents;
import com.mirror.recorder.model.Frame;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

/**
 * Codec: serialization/deserialization of frames to NBT and validation. Knows nothing about the
 * disk.
 */
public class RecordingCodec {
  public static final int FORMAT_VERSION = 5, MAX_FRAMES = 72000;

  /**
   * Result of decoding frames: frames is non-empty; damaged is how many frames were replaced by a
   * pause or dropped at the start; valid is how many survived.
   */
  public static final class Decoded {
    public final List<Frame> frames;
    public final int damaged, valid;

    Decoded(List<Frame> frames, int damaged, int valid) {
      this.frames = frames;
      this.damaged = damaged;
      this.valid = valid;
    }

    public boolean intact() {
      return damaged == 0;
    }
  }
  /** Structural check: Version and the frame count. */
  public boolean validStructure(NBTTagCompound r) {
    if (r == null) return false;
    int v = r.hasKey("Version") ? r.getInteger("Version") : 0;
    if (v < 0 || v > FORMAT_VERSION) return false;
    NBTTagList list = r.getTagList("Frames", Constants.NBT.TAG_COMPOUND);
    int actual = list.tagCount();
    return actual >= 1 && actual <= MAX_FRAMES;
  }
  /** Full check: structure + every frame. */
  public boolean validRoot(NBTTagCompound r) {
    if (!validStructure(r)) return false;
    NBTTagList list = r.getTagList("Frames", Constants.NBT.TAG_COMPOUND);
    for (int i = 0; i < list.tagCount(); i++) {
      try {
        if (!validFrame(Frame.fromNBT(list.getCompoundTagAt(i)))) return false;
      } catch (Exception e) {
        return false;
      }
    }
    return true;
  }
  /**
   * Decoding frames. A damaged frame is not thrown away but replaced by a pause ({@link
   * #gapAfter}): playback goes by frame index, and without the replacement all following actions
   * would shift earlier than the recorded time. Damaged frames before the first intact one are
   * dropped: there is nothing to shift before the recording starts. null means the structure is
   * invalid or there is not a single intact frame.
   */
  public Decoded decodeRoot(NBTTagCompound r) {
    if (!validStructure(r)) return null;
    NBTTagList list = r.getTagList("Frames", Constants.NBT.TAG_COMPOUND);
    List<Frame> out = new ArrayList<Frame>(list.tagCount());
    int damaged = 0, valid = 0;
    boolean screenState = (r.hasKey("Version") ? r.getInteger("Version") : 0) >= 3;
    for (int i = 0; i < list.tagCount(); i++) {
      Frame f;
      try {
        f = Frame.fromNBT(list.getCompoundTagAt(i), screenState);
      } catch (Exception e) {
        f = null;
      }
      if (validFrame(f)) {
        out.add(f);
        valid++;
        continue;
      }
      damaged++;
      if (!out.isEmpty()) out.add(gapAfter(out.get(out.size() - 1)));
    }
    return valid > 0 ? new Decoded(out, damaged, valid) : null;
  }
  /** A pause in place of a damaged frame: the previous frame's pose, no input and no events. */
  private static Frame gapAfter(Frame previous) {
    return Frame.builder()
        .x(previous.x)
        .y(previous.y)
        .z(previous.z)
        .yaw(previous.yaw)
        .pitch(previous.pitch)
        .onGround(previous.onGround)
        .keyMask(0)
        .screenState(previous.hasScreenState)
        .openScreen(previous.openScreen)
        .tickIndex(Math.min(previous.tickIndex + 1, MAX_FRAMES - 1))
        .build();
  }
  /** Builds the root NBT from a list of frames. Returns null if any frame fails validation. */
  public NBTTagCompound encodeRecording(
      List<Frame> frames, NBTTagCompound settings, String name, String desc) {
    NBTTagCompound root = new NBTTagCompound();
    root.setInteger("Version", FORMAT_VERSION);
    if (name != null && !name.isEmpty()) root.setString("SlotName", name);
    if (desc != null && !desc.isEmpty()) root.setString("SlotDesc", desc);
    NBTTagList list = new NBTTagList();
    for (Frame f : frames) {
      if (!validFrame(f)) return null;
      list.appendTag(f.toNBT());
    }
    root.setInteger("FrameCount", list.tagCount());
    root.setTag("Frames", list);
    if (settings != null) root.setTag("SlotSettings", settings);
    return root;
  }
  /**
   * A build in which invalid frames are replaced by a pause, by the same rule as on reading
   * (decodeRoot).
   */
  public NBTTagCompound encodeRecordingLenient(
      List<Frame> frames, NBTTagCompound settings, String name, String desc) {
    NBTTagCompound root = new NBTTagCompound();
    root.setInteger("Version", FORMAT_VERSION);
    if (name != null && !name.isEmpty()) root.setString("SlotName", name);
    if (desc != null && !desc.isEmpty()) root.setString("SlotDesc", desc);
    NBTTagList list = new NBTTagList();
    Frame lastValid = null;
    for (Frame f : frames) {
      if (validFrame(f)) lastValid = f;
      else if (lastValid != null) f = gapAfter(lastValid);
      else continue;
      list.appendTag(f.toNBT());
    }
    if (list.tagCount() == 0) return null;
    root.setInteger("FrameCount", list.tagCount());
    root.setTag("Frames", list);
    if (settings != null) root.setTag("SlotSettings", settings);
    return root;
  }
  /** Largest sane coordinate: the vanilla world border (30 million blocks) plus a margin. */
  private static final double MAX_COORD = 31000000d;

  /**
   * Largest sane motion in blocks per tick: far above any real speed, so only corrupted values
   * fail.
   */
  private static final double MAX_MOTION = 100d;

  /** Largest sane pitch: vanilla clamps it to ±90°, the extra 0.001 absorbs float rounding. */
  private static final float MAX_PITCH = 90.001f;

  /** Longest chat message: the vanilla chat line limit. */
  private static final int MAX_CHAT = 256;

  /** Validation of a single frame: coordinates, angles, velocities, events, GUI. */
  public boolean validFrame(Frame f) {
    return (f != null
            && finite(f.x)
            && finite(f.y)
            && finite(f.z)
            && Math.abs(f.x) <= MAX_COORD
            && Math.abs(f.y) <= MAX_COORD
            && Math.abs(f.z) <= MAX_COORD
            && finite(f.motionX)
            && finite(f.motionY)
            && finite(f.motionZ)
            && Math.abs(f.motionX) <= MAX_MOTION
            && Math.abs(f.motionY) <= MAX_MOTION
            && Math.abs(f.motionZ) <= MAX_MOTION
            && finite(f.yaw)
            && finite(f.pitch)
            && Math.abs(f.pitch) <= MAX_PITCH
            && finite(f.moveForward)
            && finite(f.moveStrafe)
            && Math.abs(f.moveForward) <= 1.001f
            && Math.abs(f.moveStrafe) <= 1.001f
            && f.tickIndex >= 0
            && f.tickIndex < MAX_FRAMES
            && f.keyMask >= 0
            && f.hotbarSlot >= -1
            && f.hotbarSlot <= 8
            && finite(f.dYaw)
            && finite(f.dPitch)
            && Math.abs(f.dYaw) <= 360.001f
            && Math.abs(f.dPitch) <= 180.001f
            && (f.chatMessage == null || f.chatMessage.length() <= MAX_CHAT)
            && (!f.guiClick
                || (finite(f.guiX)
                    && finite(f.guiY)
                    && f.guiX >= 0f
                    && f.guiX <= 1f
                    && f.guiY >= 0f
                    && f.guiY <= 1f
                    && f.guiScreen != null
                    && f.guiScreen.length() > 0
                    && f.guiScreen.length() <= Frame.MAX_SCREEN_NAME
                    && within(f.guiCenterX, 16384)
                    && within(f.guiCenterY, 16384))))
        && f.worldSequence >= -1
        && f.worldSequence < MAX_FRAMES
        && f.keyEvents != null
        && f.keyEvents.length % 2 == 0
        && f.keyEvents.length <= Frame.MAX_KEY_EVENTS * 2
        && f.guiKeys != null
        && f.guiKeys.length % 6 == 0
        && f.guiKeys.length <= Frame.MAX_GUI_EVENTS * 6
        && SemanticGuiEvents.valid(f.guiKeys, f.guiTextPayloads)
        && (f.openScreen == null || f.openScreen.length() <= Frame.MAX_SCREEN_NAME)
        && (f.guiKeyScreens == null || f.guiKeyScreens.length * 6 == f.guiKeys.length)
        && f.actions.length <= Frame.MAX_ACTIONS
        && finite(f.curX)
        && finite(f.curY)
        && Math.abs(f.curX) <= 16384f
        && Math.abs(f.curY) <= 16384f;
  }

  /**
   * {@code |v| <= limit} without Math.abs: for Integer.MIN_VALUE the absolute value overflows and
   * stays negative.
   */
  private static boolean within(int v, int limit) {
    return v >= -limit && v <= limit;
  }

  private boolean finite(double v) {
    return !Double.isNaN(v) && !Double.isInfinite(v);
  }

  public String clean(String v, int max) {
    if (v == null) return "";
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < v.length() && b.length() < max; i++) {
      char c = v.charAt(i);
      if (c != '\u00a7' && !Character.isISOControl(c)) b.append(c);
    }
    return b.toString().trim();
  }

  public String safeName(String v) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < v.length() && b.length() < 28; i++) {
      char c = v.charAt(i);
      b.append(Character.isLetterOrDigit(c) || c == '-' || c == '_' ? c : '_');
    }
    return fileSystemSafe(b.toString().replaceAll("_+", "_"));
  }

  /**
   * The file name is encoded with the system path encoding. If it cannot represent the letters of
   * the name (Linux with a POSIX locale, old builds), the file is created but cannot be opened
   * back. Such a name is replaced with Latin characters.
   */
  private static String fileSystemSafe(String v) {
    if (v.isEmpty() || FS_CHARSET == null || FS_CHARSET.newEncoder().canEncode(v)) return v;
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < v.length(); i++) {
      char c = v.charAt(i);
      if (c < 128) b.append(c);
      else {
        int idx = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя".indexOf(Character.toLowerCase(c));
        b.append(
            idx < 0
                ? "_"
                : new String[] {
                      "a", "b", "v", "g", "d", "e", "e", "zh", "z", "i", "y", "k", "l", "m", "n",
                      "o", "p", "r", "s", "t", "u", "f", "h", "c", "ch", "sh", "sch", "", "y", "",
                      "e", "yu", "ya"
                    }
                    [idx]);
      }
    }
    String out = b.toString().replaceAll("_+", "_");
    return out.isEmpty() || "_".equals(out) ? "" : out;
  }

  private static final Charset FS_CHARSET = fsCharset();

  private static Charset fsCharset() {
    try {
      String n = System.getProperty("sun.jnu.encoding");
      if (n == null || n.isEmpty()) n = System.getProperty("file.encoding");
      return n == null ? null : Charset.forName(n);
    } catch (Exception e) {
      return null;
    }
  }
}
