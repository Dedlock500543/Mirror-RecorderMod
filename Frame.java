package com.mirror.recorder.model;

import java.util.Arrays;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

/**
 * One recording tick: position, camera, input (K_* key mask), clicks, window events and chat. Built
 * via {@link Builder}, which copies every input array. Read-only by convention: the array fields
 * are public final, but their contents can still be written, so never modify them; a frame may
 * already be queued for saving or shared with playback.
 */
public final class Frame {
  public final double x, y, z, motionX, motionY, motionZ;
  public final float yaw, pitch, moveForward, moveStrafe, guiX, guiY;
  public final boolean onGround, sneak, sprint, leftClick, rightClick, jump, guiClick;
  public final String chatMessage, guiScreen;
  /** Shift held during an old-format UI click (guiClick). */
  public final boolean guiShift;

  /**
   * The recording stores the open screen (format 3+), so an empty openScreen really means "no
   * screen".
   */
  public final boolean hasScreenState;

  /** The drop key threw the whole stack (Ctrl). */
  public final boolean dropAll;

  /**
   * First frame after a world change: playback resets the camera, stabilization and route origin.
   */
  public final boolean worldReset;

  public final int worldSequence, worldDimension;
  /**
   * Screen open at the end of the tick (class name, the mod's own windows are not stored); if no
   * screen is open but the tick has window keys, the screen of the last window event. Not the same
   * as guiScreen: that is only the screen of an old-format click (guiClick, guiX, guiY), new
   * recordings do not write it.
   */
  public final String openScreen;
  /**
   * Old-format UI click button: 2 for a middle click (0/1 come from leftClick/rightClick, so they
   * are not stored).
   */
  public final int guiButton;

  /** Window events, 6 ints per event. */
  public final int[] guiKeys;
  /**
   * Screen of each guiKeys event (one name per 6 ints). null means an old recording: all events
   * belong to openScreen.
   */
  public final String[] guiKeyScreens;
  /** Semantic editor and component payload triples; old files contain an empty array. */
  public final String[] guiTextPayloads;

  public final long tickIndex;
  public static final int K_ATTACK = 1,
      K_USE = 2,
      K_FORWARD = 4,
      K_BACK = 8,
      K_LEFT = 16,
      K_RIGHT = 32,
      K_JUMP = 64,
      K_SNEAK = 128,
      K_SPRINT = 256,
      K_DROP = 512,
      K_SWAP = 1024,
      K_INVENTORY = 2048,
      K_PICK = 4096;
  /** Instant actions: in a frame this is always "pressed now", there is no hold. */
  public static final int K_INSTANT = K_DROP | K_SWAP | K_INVENTORY | K_PICK;
  /** Codes for instant actions stored in actions. */
  public static final int ACT_DROP = 1,
      ACT_DROP_ALL = 2,
      ACT_SWAP = 3,
      ACT_PICK = 4,
      ACT_INVENTORY = 5,
      ACT_PICK_CONTENTS = 6;
  /**
   * Whether the frame stores instant actions. hasActions=false means an old recording: instant
   * actions are restored there from the rising edges of keyMask bits.
   */
  public final boolean hasActions;
  /** ACT_* codes of the tick in press order, one entry per press, at most MAX_ACTIONS. */
  public final int[] actions;

  public final int keyMask, hotbarSlot;
  public final int attackClicks, useClicks;
  public final float dYaw, dPitch;
  /** Ordered render-time camera samples [partial tick, yaw bits, pitch bits], or null for legacy. */
  public final int[] lookSamples;
  public static final int MAX_LOOK_SAMPLES = 128;
  public final boolean hasKeyMask;
  /** Per-tick caps (clicks, click order, keys, GUI events, actions) and max screen name length. */
  public static final int MAX_CLICKS = 100,
      MAX_CLICK_SEQ = 200,
      MAX_KEY_EVENTS = 64,
      MAX_GUI_EVENTS = 64,
      MAX_ACTIONS = 64,
      MAX_SCREEN_NAME = 160;
  /**
   * Click order within a tick: 0 is attack, 1 is use. The "pressed/not pressed" state does not keep
   * the order, so it is stored separately: two clicks in one tick replay in the same sequence they
   * were recorded. An empty array means no click sequence was stored.
   */
  public final int[] clickSeq;
  /**
   * Cursor offset from the screen center in UI pixels: the same input, but independent of the
   * window size.
   */
  public final int guiCenterX, guiCenterY;

  public final boolean hasGuiCenter;
  /** Recorded raw key events of the tick: 2 ints per event, the code and 1 pressed / 0 released. */
  public final int[] keyEvents;
  /** Whether curX and curY store the drawn cursor position this tick, in UI pixels. */
  public final boolean hasCursor;

  public final float curX, curY;
  /**
   * Elytra flight this tick: the glide is steered by velocity and look, not keys, so such a frame
   * is replayed by velocity.
   */
  public final boolean elytra;

  public boolean key(int bit) {
    return (keyMask & bit) != 0;
  }

  private Frame(Builder b) {
    x = b.x;
    y = b.y;
    z = b.z;
    yaw = b.yaw;
    pitch = b.pitch;
    motionX = b.motionX;
    motionY = b.motionY;
    motionZ = b.motionZ;
    onGround = b.onGround;
    sneak = b.sneak;
    sprint = b.sprint;
    leftClick = b.leftClick;
    rightClick = b.rightClick;
    jump = b.jump;
    moveForward = b.moveForward;
    moveStrafe = b.moveStrafe;
    guiX = b.guiX;
    guiY = b.guiY;
    guiClick = b.guiClick;
    guiScreen = b.guiScreen;
    chatMessage = b.chatMessage;
    tickIndex = b.tickIndex;
    keyMask = b.keyMask;
    hotbarSlot = b.hotbarSlot;
    dYaw = b.dYaw;
    dPitch = b.dPitch;
    lookSamples = b.lookSamples;
    hasKeyMask = b.hasKeyMask;
    guiCenterX = b.guiCenterX;
    guiCenterY = b.guiCenterY;
    hasGuiCenter = b.hasGuiCenter;
    guiShift = b.guiShift;
    openScreen = b.openScreen;
    hasScreenState = b.hasScreenState;
    guiButton = b.guiButton;
    guiKeys = b.guiKeys;
    guiTextPayloads = b.guiTextPayloads;
    guiKeyScreens =
        b.guiKeyScreens != null && b.guiKeyScreens.length * 6 == b.guiKeys.length
            ? b.guiKeyScreens
            : null;
    hasActions = b.actions != null;
    actions = b.actions == null ? EMPTY_INTS : b.actions;
    dropAll = b.dropAll;
    worldReset = b.worldReset;
    worldSequence = b.worldSequence;
    worldDimension = b.worldDimension;
    attackClicks = b.attackClicks > 0 ? b.attackClicks : (b.leftClick ? 1 : 0);
    useClicks = b.useClicks > 0 ? b.useClicks : (b.rightClick ? 1 : 0);
    clickSeq = b.clickSeq;
    keyEvents = b.keyEvents == null ? new int[0] : b.keyEvents;
    hasCursor = b.hasCursor;
    curX = b.curX;
    curY = b.curY;
    elytra = b.elytra;
  }

  private static final int[] EMPTY_INTS = new int[0];

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private double x, y, z, motionX, motionY, motionZ;
    private float yaw, pitch, moveForward, moveStrafe, guiX, guiY;
    private boolean onGround, sneak, sprint, leftClick, rightClick, jump, guiClick;
    private String chatMessage, guiScreen;
    private long tickIndex;
    private int keyMask = 0, hotbarSlot = -1;
    private float dYaw = 0f, dPitch = 0f;
    private int[] lookSamples = null;
    private boolean hasKeyMask = false;
    private int guiCenterX = 0, guiCenterY = 0;
    private boolean hasGuiCenter = false;
    private boolean guiShift = false, hasScreenState = false;
    private String openScreen = null;
    private static final int[] EMPTY = new int[0];
    private int guiButton = 0;
    private int[] guiKeys = EMPTY;
    private String[] guiTextPayloads = new String[0];
    private String[] guiKeyScreens = null;
    private int[] actions = null;
    private boolean dropAll = false, worldReset = false;
    private int worldSequence = -1, worldDimension = 0;
    private int attackClicks = 0, useClicks = 0;
    private int[] clickSeq = EMPTY;
    private int[] keyEvents = EMPTY;
    private boolean hasCursor = false;
    private float curX = 0f, curY = 0f;
    private boolean elytra = false;

    public Builder x(double v) {
      x = v;
      return this;
    }

    public Builder y(double v) {
      y = v;
      return this;
    }

    public Builder z(double v) {
      z = v;
      return this;
    }

    public Builder yaw(float v) {
      yaw = v;
      return this;
    }

    public Builder pitch(float v) {
      pitch = v;
      return this;
    }

    public Builder motionX(double v) {
      motionX = v;
      return this;
    }

    public Builder motionY(double v) {
      motionY = v;
      return this;
    }

    public Builder motionZ(double v) {
      motionZ = v;
      return this;
    }

    public Builder onGround(boolean v) {
      onGround = v;
      return this;
    }

    public Builder sneak(boolean v) {
      sneak = v;
      return this;
    }

    public Builder sprint(boolean v) {
      sprint = v;
      return this;
    }

    public Builder leftClick(boolean v) {
      leftClick = v;
      return this;
    }

    public Builder rightClick(boolean v) {
      rightClick = v;
      return this;
    }

    public Builder jump(boolean v) {
      jump = v;
      return this;
    }

    public Builder moveForward(float v) {
      moveForward = v;
      return this;
    }

    public Builder moveStrafe(float v) {
      moveStrafe = v;
      return this;
    }

    public Builder guiClick(boolean v) {
      guiClick = v;
      return this;
    }

    public Builder guiX(float v) {
      guiX = v;
      return this;
    }

    public Builder guiY(float v) {
      guiY = v;
      return this;
    }

    public Builder guiScreen(String v) {
      guiScreen = v != null && v.length() > 160 ? v.substring(0, 160) : v;
      return this;
    }

    public Builder chatMessage(String v) {
      chatMessage = v;
      return this;
    }

    public Builder tickIndex(long v) {
      tickIndex = v;
      return this;
    }

    public Builder keyMask(int v) {
      keyMask = v;
      hasKeyMask = true;
      return this;
    }

    public Builder hotbarSlot(int v) {
      hotbarSlot = v < -1 ? -1 : (v > 8 ? 8 : v);
      return this;
    }

    public Builder dYaw(float v) {
      dYaw = v;
      return this;
    }

    public Builder dPitch(float v) {
      dPitch = v;
      return this;
    }

    public Builder guiCenter(boolean has, int x, int y) {
      if (!has) return this;
      guiCenterX = x;
      guiCenterY = y;
      hasGuiCenter = true;
      return this;
    }

    public Builder guiShift(boolean v) {
      guiShift = v;
      return this;
    }

    public Builder openScreen(String v) {
      if (v == null || v.isEmpty()) {
        openScreen = null;
        return this;
      }
      openScreen = v.length() > MAX_SCREEN_NAME ? v.substring(0, MAX_SCREEN_NAME) : v;
      return this;
    }

    public Builder screenState(boolean v) {
      hasScreenState = v;
      return this;
    }

    public Builder guiButton(int v) {
      guiButton = v;
      return this;
    }
    /**
     * Window events come in blocks of 6 numbers: an incomplete tail and anything above the cap are
     * dropped (importing a foreign .mrr).
     */
    public Builder guiKeys(int[] v) {
      if (v == null || v.length < 6) {
        guiKeys = EMPTY;
        return this;
      }
      int cap = MAX_GUI_EVENTS * 6;
      int room = v.length < cap ? v.length : cap;
      room -= room % 6;
      if (room <= 0) {
        guiKeys = EMPTY;
        return this;
      }
      int[] out = new int[room];
      System.arraycopy(v, 0, out, 0, room);
      guiKeys = out;
      return this;
    }

    /**
     * Window event screens; the length must match the number of guiKeys events, otherwise
     * openScreen is used.
     */
    public Builder guiTextPayloads(String[] values) {
      guiTextPayloads = values == null ? new String[0] : Arrays.copyOf(values, values.length);
      return this;
    }

    public Builder guiKeyScreens(String[] v) {
      if (v == null) {
        guiKeyScreens = null;
        return this;
      }
      String[] out = new String[v.length];
      for (int i = 0; i < v.length; i++) {
        String n = v[i] == null ? "" : v[i];
        out[i] = n.length() > MAX_SCREEN_NAME ? n.substring(0, MAX_SCREEN_NAME) : n;
      }
      guiKeyScreens = out;
      return this;
    }
    /**
     * Instant actions in order; unknown codes are dropped. null means an old recording without this
     * field.
     */
    public Builder actions(int[] v) {
      if (v == null) {
        actions = null;
        return this;
      }
      int[] out = new int[Math.min(v.length, MAX_ACTIONS)];
      int n = 0;
      for (int i = 0; i < v.length && n < out.length; i++)
        if (v[i] >= ACT_DROP && v[i] <= ACT_PICK_CONTENTS) out[n++] = v[i];
      actions = n == out.length ? out : Arrays.copyOf(out, n);
      return this;
    }

    public Builder dropAll(boolean v) {
      dropAll = v;
      return this;
    }

    public Builder worldIdentity(int sequence, int dimension) {
      worldSequence = sequence;
      worldDimension = dimension;
      return this;
    }

    public Builder worldReset(boolean v) {
      worldReset = v;
      return this;
    }
    // Click cap per tick (MAX_CLICKS): high enough for macros and fast clickers.
    public Builder attackClicks(int v) {
      attackClicks = v < 0 ? 0 : (v > MAX_CLICKS ? MAX_CLICKS : v);
      return this;
    }

    public Builder useClicks(int v) {
      useClicks = v < 0 ? 0 : (v > MAX_CLICKS ? MAX_CLICKS : v);
      return this;
    }
    /**
     * Click order of the tick: unknown codes are dropped, the length is capped at MAX_CLICK_SEQ.
     */
    public Builder clickSeq(int[] v) {
      if (v == null || v.length == 0) {
        clickSeq = EMPTY;
        return this;
      }
      int room = v.length < MAX_CLICK_SEQ ? v.length : MAX_CLICK_SEQ;
      int[] out = new int[room];
      int n = 0;
      for (int i = 0; i < v.length && n < room; i++) if (v[i] == 0 || v[i] == 1) out[n++] = v[i];
      if (n == room) {
        clickSeq = out;
        return this;
      }
      int[] exact = new int[n];
      System.arraycopy(out, 0, exact, 0, n);
      clickSeq = exact;
      return this;
    }
    /** All keys of the tick: an odd tail and anything above the cap are dropped. */
    public Builder keyEvents(int[] v) {
      if (v == null || v.length < 2) {
        keyEvents = EMPTY;
        return this;
      }
      int cap = MAX_KEY_EVENTS * 2;
      int room = v.length < cap ? v.length : cap;
      room -= room % 2;
      if (room <= 0) {
        keyEvents = EMPTY;
        return this;
      }
      int[] out = new int[room];
      System.arraycopy(v, 0, out, 0, room);
      keyEvents = out;
      return this;
    }

    /** Invalid optional camera data falls back to legacy playback, never invalidating a frame. */
    public Builder lookSamples(int[] values) {
      if (values == null || values.length > MAX_LOOK_SAMPLES * 3 || values.length % 3 != 0) {
        lookSamples = null;
        return this;
      }
      int previous = -1;
      for (int i = 0; i < values.length; i += 3) {
        int time = values[i];
        float yaw = Float.intBitsToFloat(values[i + 1]);
        float pitch = Float.intBitsToFloat(values[i + 2]);
        if (time < previous || time < 0 || time > 10000 || Float.isNaN(yaw)
            || Float.isInfinite(yaw) || Float.isNaN(pitch) || Float.isInfinite(pitch)
            || Math.abs(pitch) > 90.001f) {
          lookSamples = null;
          return this;
        }
        previous = time;
      }
      lookSamples = Arrays.copyOf(values, values.length);
      return this;
    }

    public Builder cursor(boolean has, float x, float y) {
      if (!has) return this;
      hasCursor = true;
      curX = x;
      curY = y;
      return this;
    }

    public Builder elytra(boolean v) {
      elytra = v;
      return this;
    }

    public Builder readKeys(NBTTagCompound t) {
      if (t != null && t.hasKey("KeyMask")) {
        hasKeyMask = true;
        keyMask = t.getInteger("KeyMask");
        hotbarSlot = t.hasKey("Hotbar") ? t.getInteger("Hotbar") : -1;
        if (hotbarSlot < -1) hotbarSlot = -1;
        if (hotbarSlot > 8) hotbarSlot = 8;
        dYaw = t.hasKey("DYaw") ? t.getFloat("DYaw") : 0f;
        dPitch = t.hasKey("DPitch") ? t.getFloat("DPitch") : 0f;
      }
      return this;
    }

    public Frame build() {
      return new Frame(this);
    }
  }

  public NBTTagCompound toNBT() {
    NBTTagCompound t = new NBTTagCompound();
    t.setDouble("X", x);
    t.setDouble("Y", y);
    t.setDouble("Z", z);
    t.setFloat("Yaw", yaw);
    t.setFloat("Pitch", pitch);
    t.setDouble("MX", motionX);
    t.setDouble("MY", motionY);
    t.setDouble("MZ", motionZ);
    t.setBoolean("OnGround", onGround);
    t.setBoolean("Sneak", sneak);
    t.setBoolean("Sprint", sprint);
    t.setBoolean("LeftClick", leftClick);
    t.setBoolean("RightClick", rightClick);
    // The click count is written when it cannot be derived from the flag on reading (0/1 from
    // LeftClick/RightClick).
    if (attackClicks != (leftClick ? 1 : 0)) t.setByte("LClicks", (byte) attackClicks);
    if (useClicks != (rightClick ? 1 : 0)) t.setByte("RClicks", (byte) useClicks);
    t.setBoolean("Jump", jump);
    t.setLong("TickIndex", tickIndex);
    t.setFloat("MoveForward", moveForward);
    t.setFloat("MoveStrafe", moveStrafe);
    if (hasKeyMask) {
      t.setInteger("KeyMask", keyMask);
      t.setInteger("Hotbar", hotbarSlot);
      if (dYaw != 0f) t.setFloat("DYaw", dYaw);
      if (dPitch != 0f) t.setFloat("DPitch", dPitch);
      if (dropAll) t.setBoolean("DropAll", true);
    }
    if (chatMessage != null && !chatMessage.isEmpty()) t.setString("ChatMsg", chatMessage);
    if (guiClick && guiScreen != null && !guiScreen.isEmpty()) {
      t.setBoolean("GuiClick", true);
      t.setFloat("GuiX", guiX);
      t.setFloat("GuiY", guiY);
      t.setString("GuiScreen", guiScreen);
      if (hasGuiCenter) {
        t.setInteger("GuiCX", guiCenterX);
        t.setInteger("GuiCY", guiCenterY);
      }
      if (guiShift) t.setBoolean("GuiShift", true);
      if (guiButton != 0) t.setByte("GuiBtn", (byte) guiButton);
    }
    if (openScreen != null && !openScreen.isEmpty()) t.setString("GuiOpenScreen", openScreen);
    if (guiKeys.length > 0) t.setIntArray("GuiKeys", guiKeys);
    if (guiTextPayloads.length > 0) {
      NBTTagList payloads = new NBTTagList();
      for (String text : guiTextPayloads) payloads.appendTag(new NBTTagString(text));
      t.setTag("GuiTextPayloads", payloads);
    }
    if (guiKeyScreens != null && guiKeyScreens.length > 0) {
      NBTTagList screens = new NBTTagList();
      for (String n : guiKeyScreens) screens.appendTag(new NBTTagString(n));
      t.setTag("GuiKeyScreens", screens);
    }
    // Written even when empty: the field's presence distinguishes a new recording from an old one
    // (where actions come from keyMask).
    if (hasActions) t.setIntArray("Actions", actions);
    if (clickSeq.length > 0) t.setIntArray("ClickSeq", clickSeq);
    if (worldReset) t.setBoolean("WorldReset", true);
    if (worldSequence >= 0) {
      t.setInteger("WorldSequence", worldSequence);
      t.setInteger("WorldDimension", worldDimension);
    }
    if (keyEvents != null && keyEvents.length > 0) t.setIntArray("KeyEvents", keyEvents);
    if (hasCursor) {
      t.setFloat("CurX", curX);
      t.setFloat("CurY", curY);
    }
    // TgtX/TgtY/TgtZ/TgtFace/HitX/HitY/HitZ (removed exact placement) are no longer written; old
    // files that still carry them are read and the tags are ignored.
    if (elytra) t.setBoolean("Elytra", true);
    if (lookSamples != null) t.setIntArray("LookSamples", lookSamples);
    return t;
  }

  private static String[] readStrings(NBTTagCompound t, String key) {
    if (!t.hasKey(key, 9)) return null;
    NBTTagList list = t.getTagList(key, 8);
    if (list.tagCount() == 0) return null;
    String[] out = new String[list.tagCount()];
    for (int i = 0; i < out.length; i++) out[i] = list.getStringTagAt(i);
    return out;
  }

  private static String[] readTextPayloads(NBTTagCompound tag) {
    NBTTagList list = tag.getTagList("GuiTextPayloads", 8);
    String[] out = new String[list.tagCount()];
    for (int i = 0; i < out.length; i++) out[i] = list.getStringTagAt(i);
    return out;
  }

  public static Frame fromNBT(NBTTagCompound t) {
    return fromNBT(t, false);
  }
  /**
   * Reads a frame from NBT. screenStateKnown=true only for format 3+ recordings: old files did not
   * store the open screen.
   */
  public static Frame fromNBT(NBTTagCompound t, boolean screenStateKnown) {
    return builder()
        .screenState(screenStateKnown)
        .lookSamples(t.hasKey("LookSamples", 11) ? t.getIntArray("LookSamples") : null)
        .guiShift(t.hasKey("GuiShift") && t.getBoolean("GuiShift"))
        .openScreen(t.hasKey("GuiOpenScreen") ? t.getString("GuiOpenScreen") : null)
        .guiButton(t.hasKey("GuiBtn") ? t.getByte("GuiBtn") : 0)
        .guiKeys(t.hasKey("GuiKeys") ? t.getIntArray("GuiKeys") : null)
        .guiTextPayloads(readTextPayloads(t))
        .guiKeyScreens(readStrings(t, "GuiKeyScreens"))
        .actions(t.hasKey("Actions", 11) ? t.getIntArray("Actions") : null)
        .clickSeq(t.hasKey("ClickSeq") ? t.getIntArray("ClickSeq") : null)
        .dropAll(t.hasKey("DropAll") && t.getBoolean("DropAll"))
        .worldReset(t.hasKey("WorldReset") && t.getBoolean("WorldReset"))
        .worldIdentity(
            t.hasKey("WorldSequence", 3) ? t.getInteger("WorldSequence") : -1,
            t.getInteger("WorldDimension"))
        .x(t.hasKey("X") ? t.getDouble("X") : 0)
        .y(t.hasKey("Y") ? t.getDouble("Y") : 0)
        .z(t.hasKey("Z") ? t.getDouble("Z") : 0)
        .yaw(t.hasKey("Yaw") ? t.getFloat("Yaw") : 0)
        .pitch(t.hasKey("Pitch") ? t.getFloat("Pitch") : 0)
        .motionX(t.hasKey("MX") ? t.getDouble("MX") : 0)
        .motionY(t.hasKey("MY") ? t.getDouble("MY") : 0)
        .motionZ(t.hasKey("MZ") ? t.getDouble("MZ") : 0)
        .onGround(t.hasKey("OnGround") && t.getBoolean("OnGround"))
        .sneak(t.hasKey("Sneak") && t.getBoolean("Sneak"))
        .sprint(t.hasKey("Sprint") && t.getBoolean("Sprint"))
        .leftClick(t.hasKey("LeftClick") && t.getBoolean("LeftClick"))
        .rightClick(t.hasKey("RightClick") && t.getBoolean("RightClick"))
        .jump(t.hasKey("Jump") && t.getBoolean("Jump"))
        .moveForward(t.hasKey("MoveForward") ? t.getFloat("MoveForward") : 0)
        .moveStrafe(t.hasKey("MoveStrafe") ? t.getFloat("MoveStrafe") : 0)
        .guiClick(t.hasKey("GuiClick") && t.getBoolean("GuiClick"))
        .guiX(t.hasKey("GuiX") ? t.getFloat("GuiX") : 0)
        .guiY(t.hasKey("GuiY") ? t.getFloat("GuiY") : 0)
        .guiScreen(t.hasKey("GuiScreen") ? t.getString("GuiScreen") : null)
        .guiCenter(
            t.hasKey("GuiCX") && t.hasKey("GuiCY"),
            t.hasKey("GuiCX") ? t.getInteger("GuiCX") : 0,
            t.hasKey("GuiCY") ? t.getInteger("GuiCY") : 0)
        .tickIndex(t.hasKey("TickIndex") ? t.getLong("TickIndex") : 0)
        .chatMessage(t.hasKey("ChatMsg") ? t.getString("ChatMsg") : null)
        .attackClicks(t.hasKey("LClicks") ? t.getByte("LClicks") : 0)
        .useClicks(t.hasKey("RClicks") ? t.getByte("RClicks") : 0)
        .keyEvents(t.hasKey("KeyEvents") ? t.getIntArray("KeyEvents") : null)
        .cursor(
            t.hasKey("CurX") && t.hasKey("CurY"),
            t.hasKey("CurX") ? t.getFloat("CurX") : 0f,
            t.hasKey("CurY") ? t.getFloat("CurY") : 0f)
        .elytra(t.hasKey("Elytra") && t.getBoolean("Elytra"))
        .readKeys(t)
        .build();
  }
}
