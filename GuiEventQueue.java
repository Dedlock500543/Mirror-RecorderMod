package com.mirror.recorder.handler;

import com.mirror.recorder.config.RecorderConfig;
import com.mirror.recorder.debug.MirrorDebug;
import com.mirror.recorder.gui.Lang;
import com.mirror.recorder.manager.RecorderManager;
import com.mirror.recorder.model.Frame;
import java.util.ArrayDeque;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiEditSign;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

/**
 * Window event playback: a single queue of clicks and keys, timing and delivery of each event to
 * its screen. Recording is done by GuiEventRecorder.
 */
@SideOnly(Side.CLIENT)
final class GuiEventQueue implements ReplayState.Feature {
  private final RecordingHandler handler;
  private final ReplayState state;
  private final Minecraft mc = Minecraft.getMinecraft();
  private final RecorderManager manager;
  private final RecorderConfig config;

  GuiEventQueue(RecordingHandler handler, ReplayState state) {
    this.handler = handler;
    this.state = state;
    this.manager = state.manager;
    this.config = state.config;
  }

  private boolean guiKeyProbeLogged = false;
  /**
   * How many ticks (40 = 2 s) an event waits for its screen before it is dropped. One queue holds
   * window clicks AND keys: events are delivered strictly in the order they were recorded.
   */
  static final int GUI_EVENT_TTL = 40;

  /** The most entries the queue holds. */
  static final int GUI_QUEUE_LIMIT = 128;

  private final ArrayDeque<Object> guiQueue = new ArrayDeque<Object>();

  void clearQueue() {
    guiQueue.clear();
  }

  boolean hasQueuedEvents() {
    return !guiQueue.isEmpty();
  }

  int queuedCount() {
    return guiQueue.size();
  }
  /** End of a pause: timestamps of pending window events are shifted by its duration. */
  void shiftTimedEvents(long pausedNs) {
    for (Object o : guiQueue) if (o instanceof GuiKeyEvent) ((GuiKeyEvent) o).startedNs += pausedNs;
  }

  private boolean noticeGuiDrop = false;
  private int screenMismatchTicks = 0;
  private static final int SCREEN_CLOSE_DELAY = 10;
  private final Frame[] lookAhead = new Frame[100];
  /**
   * A window event in a recording is six consecutive ints in {@code Frame.guiKeys}: {@code [action,
   * a, b, flags, x, y]}.
   *
   * <ul>
   *   <li>action is one of GA_*;
   *   <li>flags are GUI_FLAG_SHIFT / GUI_FLAG_CTRL / GUI_FLAG_TIMED;
   *   <li>x, y is a point relative to the screen center (for timed chat keys, GA_CHATCUR and
   *       GA_SCROLL x is a value, see below).
   * </ul>
   *
   * The meaning of a and b depends on action:
   *
   * <ul>
   *   <li>GA_TYPE, GA_CLOSE, GA_DROP, GA_PICK, GA_SWAP (keys): a is the key code, b is the
   *       character or hotbar slot; for chat keys with the TIMED flag the timestamp is in x;
   *   <li>GA_CLICK, GA_CMOVE, GA_CRELEASE: a is the offset from the tick start in ms, b is the
   *       mouse button;
   *   <li>GA_DRAG, GA_DRAG_END (slider): a is the offset in ms, b is unused;
   *   <li>GA_CHATCUR: a is the offset in ms, b is the cursor position, x is the selection end;
   *   <li>GA_SCROLL: a is the offset in ms, b is the list index, x is the scroll position;
   *   <li>GA_EDIT_RANGE: b indexes [editor owner, expected text, inserted text], x/y are the range;
   *   <li>GA_EDITOR_CURSOR: b indexes [editor owner, expected text, empty], x/y are
   *       cursor/selection;
   *   <li>GA_CHAT_CLICK: b indexes [action name, value, original unwrapped chat line];
   *   <li>GA_WHEEL: b is -1 for chat, -2 for creative, otherwise a GuiSlot index; x is the position
   *       (float bits for creative), y is its tab index;
   *   <li>GA_CHAT_SUBMIT: b indexes [editor owner, submitted text, empty]. New semantic events use
   *       a as their timestamp and share Frame.guiTextPayloads.
   * </ul>
   *
   * The format is stored in recording files: the field order and constant values must not change.
   */
  static final int GA_TYPE = 0,
      GA_DROP = 1,
      GA_PICK = 2,
      GA_CLOSE = 3,
      GA_SWAP = 4,
      GA_DRAG = 5,
      GA_DRAG_END = 6,
      GA_CMOVE = 7,
      GA_CRELEASE = 8,
      GA_CHATCUR = 9,
      GA_SCROLL = 10,
      GA_CLICK = 11,
      GA_EDIT_RANGE = 12,
      GA_EDITOR_CURSOR = 13,
      GA_CHAT_CLICK = 14,
      GA_WHEEL = 15,
      GA_CHAT_SUBMIT = 16,
      GA_CPRESS = 18,
      GA_CPRESS_DOUBLE = 19,
      GA_CUP = 20,
      GUI_FLAG_SHIFT = 1,
      GUI_FLAG_CTRL = 2,
      GUI_FLAG_TIMED = 4,
      GUI_KEY_LIMIT = 64;
  /** Window clicks are queued in the same order they were recorded within the tick. */
  void queueOrderedGuiClicks(Frame frame, boolean attackPress, boolean usePress) {
    int[] seq = frame.clickSeq;
    if (seq.length > 0) {
      boolean queued = false;
      for (int i = 0; i < seq.length; i++) {
        if (seq[i] == 0 && attackPress) {
          queueGuiClick(frame, 0);
          queued = true;
        } else if (seq[i] == 1 && usePress) {
          queueGuiClick(frame, 1);
          queued = true;
        }
      }
      if (queued) return;
    }
    if (attackPress) queueGuiClick(frame, 0);
    if (usePress) queueGuiClick(frame, 1);
  }

  static final class GuiClickEvent {
    final String screen;
    final float x, y;
    final int button, cx, cy;
    final boolean hasCenter, shift;
    int waited = 0;

    GuiClickEvent(
        String screen,
        float x,
        float y,
        int button,
        boolean hasCenter,
        int cx,
        int cy,
        boolean shift) {
      this.screen = screen;
      this.x = x;
      this.y = y;
      this.button = button;
      this.hasCenter = hasCenter;
      this.cx = cx;
      this.cy = cy;
      this.shift = shift;
    }
  }
  /** A group of window events waits for its screen just like a legacy click. */
  static final class GuiKeyEvent {
    final String screen;
    final int[] data;
    final String[] payloads;
    int waited = 0, at = 0;
    /** Clock for recorded offsets; a pause shifts it by its duration. */
    long startedNs = System.nanoTime();

    GuiKeyEvent(String screen, int[] data, String[] payloads) {
      this.screen = screen;
      this.data = data;
      this.payloads = payloads;
    }
  }
  /**
   * Playback accepts a click for any open screen. Which screens get clicks recorded at all (not the
   * mod UI, menus or chat) is decided by GuiEventRecorder.
   */
  static boolean replayableScreen(String className) {
    return className != null && !className.isEmpty();
  }

  void queueGuiClick(Frame frame, int button) {
    if (frame == null || !replayableScreen(frame.guiScreen)) return;
    if (guiQueue.size() >= GUI_QUEUE_LIMIT) {
      MirrorDebug.log("GUI", "event queue overflow, dropped click for " + frame.guiScreen);
      return;
    }
    guiQueue.add(
        new GuiClickEvent(
            frame.guiScreen,
            frame.guiX,
            frame.guiY,
            button,
            frame.hasGuiCenter,
            frame.guiCenterX,
            frame.guiCenterY,
            frame.guiShift));
  }
  /**
   * Window clicks and keys go in one queue: an event waits for its screen and cannot overtake the
   * previous one.
   */
  void processGuiQueue() {
    processGuiQueue(true);
  }
  /**
   * Delivers due window events. ageWait=false is a call from a render frame: due events are
   * delivered, but the wait counter (in ticks) does not grow.
   */
  void processGuiQueue(boolean ageWait) {
    int guard = 0;
    while (!guiQueue.isEmpty() && guard++ < 16) {
      Object head = guiQueue.peek();
      GuiScreen screen = mc.currentScreen;
      String want =
          head instanceof GuiClickEvent
              ? ((GuiClickEvent) head).screen
              : ((GuiKeyEvent) head).screen;
      if (screen != null && screen.getClass().getName().equals(want)) {
        if (head instanceof GuiClickEvent) {
          guiQueue.poll();
          if (replayableScreen(want)) deliverGuiClick(screen, (GuiClickEvent) head);
          continue;
        }
        if (deliverGuiKeys(screen, (GuiKeyEvent) head)) {
          if (guiQueue.peek() == head) guiQueue.poll();
          continue;
        }
        break;
      }
      if (!ageWait) break;
      int waited =
          head instanceof GuiClickEvent
              ? ++((GuiClickEvent) head).waited
              : ++((GuiKeyEvent) head).waited;
      if (waited > GUI_EVENT_TTL) {
        guiQueue.poll();
        MirrorDebug.log("GUI", "dropped recorded GUI event: " + want + " did not open");
        warnGuiDrop();
        continue;
      }
      break;
    }
  }
  /** A lost window event is a divergence from the recording: say so once per run. */
  void warnGuiDrop() {
    if (noticeGuiDrop) return;
    noticeGuiDrop = true;
    state.sendMsg(Lang.t("msg.some_window_actions_were_skipped"));
  }

  private void deliverGuiClick(GuiScreen screen, GuiClickEvent c) {
    if (!state.reflect.isResolved()) {
      state.reflect.resolve();
      MirrorDebug.probe(
          "GuiScreen.mouseClicked", state.reflect.mouseClicked() != null, "GUI click replay");
      MirrorDebug.probe(
          "GuiScreen.mouseReleased", state.reflect.mouseReleased() != null, "GUI release replay");
    }
    if (state.reflect.mouseClicked() == null) return;
    int x, y;
    if (c.hasCenter) {
      x = MathHelper.clamp(screen.width / 2 + c.cx, 0, Math.max(0, screen.width - 1));
      y = MathHelper.clamp(screen.height / 2 + c.cy, 0, Math.max(0, screen.height - 1));
    } else {
      x =
          MathHelper.clamp(
              (int) (c.x * Math.max(1, screen.width)), 0, Math.max(0, screen.width - 1));
      y =
          MathHelper.clamp(
              (int) (c.y * Math.max(1, screen.height)) - 1, 0, Math.max(0, screen.height - 1));
    }
    handler.pointer.pointerSnap(x, y);
    if (c.button == 0) handler.lists.grabSlider(screen, x, y);
    handler.containers.flushDeferredPress();
    if (c.button == 2 && handler.containers.deliverClone(screen, x, y)) return;
    if (c.shift && handler.containers.deliverQuickMove(screen, x, y, c.button)) return;
    try {
      state
          .reflect
          .mouseClicked()
          .invoke(screen, Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(c.button));
    } catch (Exception e) {
      state.warnClickFailure("Recorded GUI click failed", e);
      return;
    }
    handler.lists.deliverListClick(screen, x, y, c.button);
    // A press on a slider is released immediately (see releaseGrabbedSlider).
    if (handler.lists.releaseGrabbedSlider(screen, x, y, c.button)) {
      handler.containers.cancelDeferredPress();
      return;
    }
    if (handler.containers.shouldDeferContainerRelease(screen, x, y, c)) {
      handler.containers.armDeferredPress(screen, x, y, c.button);
      return;
    }
    if (state.reflect.mouseReleased() == null) return;
    try {
      state
          .reflect
          .mouseReleased()
          .invoke(screen, Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(c.button));
    } catch (Exception e) {
      state.warnClickFailure("Recorded GUI release failed", e);
    }
    if (mc.currentScreen != screen) handler.clicks.clearLeftClickBlock();
  }

  /**
   * A frame's window events are queued in consecutive groups of one screen: each waits for its
   * screen, the recording order is kept. In old recordings there is one screen per frame:
   * openScreen.
   */
  void queueGuiKeys(Frame frame) {
    int[] d = frame.guiKeys;
    for (int start = 0; start < d.length; ) {
      String screen = guiEventScreen(frame, start);
      int end = groupEnd(frame, start);
      if (screen != null && screen.endsWith(".GuiChat") && !config.isVisibleChat())
        queueInvisibleChat(frame, start, end);
      if (screen != null && (config.isVisibleChat() || !screen.endsWith(".GuiChat"))) {
        // Shared queue with clicks: on overflow the new event is dropped, not the oldest, so the
        // order of queued ones is not broken.
        if (guiQueue.size() >= GUI_QUEUE_LIMIT)
          MirrorDebug.log("GUI", "event queue overflow, dropped key event for " + screen);
        else guiQueue.add(keyGroup(frame, start, end));
      }
      start = end;
    }
  }

  static int groupEnd(Frame frame, int start) {
    String screen = guiEventScreen(frame, start);
    int end = start + 6;
    while (end < frame.guiKeys.length && same(screen, guiEventScreen(frame, end))) end += 6;
    return end;
  }

  static GuiKeyEvent keyGroup(Frame frame, int start, int end) {
    return new GuiKeyEvent(
        guiEventScreen(frame, start),
        Arrays.copyOfRange(frame.guiKeys, start, end),
        frame.guiTextPayloads);
  }

  private void queueInvisibleChat(Frame frame, int start, int end) {
    for (int i = start; i < end; i += 6) {
      int action = frame.guiKeys[i], payload = frame.guiKeys[i + 2];
      if (!validPayload(frame.guiTextPayloads, payload)) continue;
      if (action == GA_CHAT_SUBMIT)
        handler.chat.enqueueSemanticMessage(frame.guiTextPayloads[payload + 1]);
      else if (action == GA_CHAT_CLICK && frame.guiTextPayloads[payload].equals("RUN_COMMAND"))
        handler.chat.enqueueInvisibleComponent(
            frame.guiTextPayloads[payload],
            frame.guiTextPayloads[payload + 1],
            frame.guiTextPayloads[payload + 2]);
    }
  }

  private static boolean validPayload(String[] payloads, int index) {
    return index >= 0 && index <= payloads.length - 3;
  }

  private static String guiEventScreen(Frame frame, int offset) {
    if (frame.guiKeyScreens == null) return frame.openScreen;
    String n = frame.guiKeyScreens[offset / 6];
    return n == null || n.isEmpty() ? null : n;
  }

  private static boolean same(String a, String b) {
    return a == null ? b == null : a.equals(b);
  }
  /**
   * Replaying window key presses: typing goes through keyTyped, slot actions go directly to
   * handleMouseClick, because vanilla looks at the physical cursor and the physical Ctrl.
   */
  private boolean deliverGuiKeys(GuiScreen screen, GuiKeyEvent e) {
    state.reflect.ensureResolved();
    if (!guiKeyProbeLogged) {
      guiKeyProbeLogged = true;
      MirrorDebug.probe("GuiScreen.keyTyped", state.reflect.keyTyped() != null, "GUI key replay");
    }
    int[] d = e.data;
    while (e.at + 6 <= d.length) {
      int i = e.at,
          action = d[i],
          key = d[i + 1],
          aux = d[i + 2],
          flags = d[i + 3],
          cx = d[i + 4],
          cy = d[i + 5];
      if (!guiKeyDue(e, action, key, flags, cx)) return false;
      if (action == GA_DRAG || action == GA_DRAG_END) deliverSliderRun(screen, e);
      else {
        e.at += 6;
        deliverEvent(screen, e, action, key, aux, flags, cx, cy);
      }
      if (!manager.isPlaying()) {
        handler.resetAll();
        return true;
      }
      // A failed container callback clears the queue: do not continue the detached event group.
      if (guiQueue.peek() != e) return true;
      if (mc.currentScreen != screen) {
        handler.clicks.clearLeftClickBlock();
        // The rest of the group was recorded in a window of the same class: it waits until such a
        // window opens again.
        return e.at >= d.length;
      }
    }
    return true;
  }
  /** A series of slider drag points starting at e.at: only the last due one is delivered. */
  private void deliverSliderRun(GuiScreen screen, GuiKeyEvent e) {
    int[] d = e.data;
    int i = e.at, cx = d[i + 4], cy = d[i + 5];
    // Continuous value: out of all consecutive due drag points only the last one is delivered. The
    // intermediate ones would be applied microseconds apart: eye and ear cannot perceive them, but
    // they cause a volume staircase (crackling) and twitching numbers. Order and final value are
    // preserved.
    int j = i;
    boolean end = (d[i] == GA_DRAG_END);
    // The release closes the gesture: the next drag may follow, it is not swallowed.
    if (!end)
      while (j + 12 <= d.length) {
        int na = d[j + 6];
        if (na != GA_DRAG && na != GA_DRAG_END) break;
        if (!guiKeyDue(e, na, d[j + 7], d[j + 9], d[j + 10])) break;
        j += 6;
        cx = d[j + 4];
        cy = d[j + 5];
        end = (d[j] == GA_DRAG_END);
        if (end) break;
      }
    e.at = j + 6;
    handler.lists.deliverSliderDrag(screen, cx, cy, end);
  }

  /**
   * A single window event (except slider drag). A new event type means a new case here, a GA_*
   * constant and recording in GuiEventRecorder; see AGENTS.md.
   */
  private void deliverEvent(
      GuiScreen screen, GuiKeyEvent e, int action, int key, int aux, int flags, int cx, int cy) {
    switch (action) {
      case GA_TYPE:
        if (screen instanceof GuiChat
            && (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER)
            && !handler.chat.allowCurrentSubmit(screen)) {
          mc.displayGuiScreen(null);
          break;
        }
        // Legacy files have no pasted text; consulting today's clipboard would invent an edit.
        if (key == Keyboard.KEY_V && ((flags & GUI_FLAG_CTRL) != 0 || GuiScreen.isCtrlKeyDown())) {
          state.sendMsg(Lang.t("msg.gui_legacy_paste_unavailable"));
          break;
        }
        if (state.reflect.keyTyped() != null)
          try {
            state
                .reflect
                .keyTyped()
                .invoke(screen, Character.valueOf((char) aux), Integer.valueOf(key));
          } catch (Exception ex) {
            state.warnClickFailure("Recorded key replay failed", ex);
          }
        break;
      case GA_CLOSE:
        if (screen instanceof GuiContainer) closeRecordedScreen(screen);
        else if (state.reflect.keyTyped() != null) {
          try {
            state.reflect.keyTyped().invoke(screen, Character.valueOf('\0'), Integer.valueOf(1));
          } catch (Exception ex) {
            state.warnClickFailure("Recorded Esc replay failed", ex);
          }
        } else closeRecordedScreen(screen);
        break;
      case GA_CPRESS:
      case GA_CPRESS_DOUBLE:
        handler.containers.deliverContainerPress(
            screen, cx, cy, aux, (flags & GUI_FLAG_SHIFT) != 0, action == GA_CPRESS_DOUBLE);
        break;
      case GA_CUP:
        handler.containers.deliverContainerUp(screen, cx, cy, aux, (flags & GUI_FLAG_SHIFT) != 0);
        break;
      case GA_CMOVE:
        handler.containers.deliverContainerMove(screen, cx, cy, aux, (flags & GUI_FLAG_SHIFT) != 0);
        break;
      case GA_CRELEASE:
        handler.containers.deliverContainerRelease(screen, cx, cy, aux);
        break;
      case GA_EDIT_RANGE:
      case GA_EDITOR_CURSOR:
        deliverEditor(screen, e.payloads, action, aux, cx, cy);
        break;
      case GA_CHAT_CLICK:
        if (validPayload(e.payloads, aux))
          handler.chat.deliverComponent(
              screen, e.payloads[aux], e.payloads[aux + 1], e.payloads[aux + 2]);
        break;
      case GA_CHAT_SUBMIT:
        if (validPayload(e.payloads, aux))
          handler.chat.deliverSemanticSubmit(screen, e.payloads[aux + 1]);
        break;
      case GA_WHEEL:
        handler.lists.deliverWheel(screen, aux, cx, cy);
        break;
      case GA_CHATCUR:
        handler.chat.deliverChatCursor(screen, aux, cx);
        break;
      case GA_SCROLL:
        handler.lists.deliverListScroll(screen, aux, cx);
        break;
      case GA_CLICK:
        deliverGuiClick(
            screen,
            new GuiClickEvent(e.screen, 0f, 0f, aux, true, cx, cy, (flags & GUI_FLAG_SHIFT) != 0));
        break;
      default:
        // The rest are container slot actions: GA_DROP, GA_PICK, GA_SWAP.
        handler.containers.deliverContainerKey(
            screen, action, aux, (flags & GUI_FLAG_CTRL) != 0, cx, cy);
    }
  }

  private void deliverEditor(
      GuiScreen screen, String[] payloads, int action, int index, int x, int y) {
    if (!validPayload(payloads, index)) return;
    try {
      TextEditors.Editor editor = TextEditors.find(screen, payloads[index]);
      if (editor == null) {
        state.sendMsg(Lang.t("msg.gui_editor_mismatch"));
        return;
      }
      String expected = payloads[index + 1];
      String inserted = payloads[index + 2];
      if (action == GA_EDIT_RANGE) {
        if (!SemanticGuiEvents.editorStateMatches(editor.text(), expected, x, y)) {
          state.sendMsg(Lang.t("msg.gui_editor_mismatch"));
          return;
        }
        editor.text(SemanticGuiEvents.apply(expected, x, y, inserted));
      } else {
        // Cursor events carry their own expected text so a rejected edit cannot move a foreign
        // cursor.
        String current = editor.text();
        if (!SemanticGuiEvents.editorStateMatches(current, expected, x, y)) {
          state.sendMsg(Lang.t("msg.gui_editor_mismatch"));
          return;
        }
        editor.cursor(x, y);
      }
    } catch (ReflectiveOperationException | IllegalArgumentException ex) {
      state.warnClickFailure("Semantic editor replay failed", ex);
    }
  }

  /** Whether the event is due by its recorded timestamp (accounting for playback speed). */
  private boolean guiKeyDue(GuiKeyEvent e, int action, int key, int flags, int cx) {
    if ((flags & GUI_FLAG_TIMED) == 0) return true;
    int due = action == GA_TYPE ? cx : key;
    float speed = Math.max(0.25f, config.getPlaybackSpeed());
    due = Math.max(0, Math.round(due / speed));
    long elapsed = (System.nanoTime() - e.startedNs) / 1000000L;
    return elapsed >= due;
  }
  /**
   * Closing as in vanilla: a container sends a packet to the server, a book and a sign close
   * locally (the sign sends its text in onGuiClosed).
   */
  private void closeRecordedScreen(GuiScreen screen) {
    // Closing cancels the deferred release: no release is sent to a screen that is closing.
    handler.containers.cancelDeferredPress();
    if (screen instanceof GuiContainer) {
      if (mc.player != null) mc.player.closeScreen();
    } else mc.displayGuiScreen(null);
  }

  /**
   * Identifies settings screens that playback can open itself. closeUnexpectedScreen closes them if
   * they remain open after the recording has left them.
   */
  static boolean settingsScreen(GuiScreen s) {
    return s != null && settingsScreen(s.getClass().getName());
  }

  static boolean settingsScreen(String n) {
    if (n == null || !n.startsWith("net.minecraft.")) return false;
    for (String a : SETTINGS_SCREENS) if (n.endsWith("." + a)) return true;
    return false;
  }
  /** GUI frames have their own clock; an unrelated/manual world pause must not consume gameplay. */
  static boolean runsWhileWorldPaused(Frame frame, boolean atEnd, String currentScreen) {
    if (frame == null) return atEnd && settingsScreen(currentScreen);
    if (settingsScreen(frame.openScreen)) return true;
    // Closing events can have a null openScreen: they still belong to the recorded menu.
    if (frame.guiKeys.length > 0) {
      for (String screen : frame.guiKeyScreens) if (settingsScreen(screen)) return true;
    }
    return frame.guiClick && settingsScreen(frame.guiScreen);
  }

  /**
   * Pure replay of the screen state: a window that was not in the recording at this moment is
   * closed by us, otherwise a villager or chest UI hangs until the end of playback and blocks
   * everything else.
   */
  void closeUnexpectedScreen(Frame frame) {
    GuiScreen screen = mc.currentScreen;
    boolean container = screen instanceof GuiContainer;
    boolean closable =
        container
            || screen instanceof GuiScreenBook
            || screen instanceof GuiEditSign
            || settingsScreen(screen);
    if (frame == null || screen == null || mc.player == null || !guiQueue.isEmpty() || !closable) {
      screenMismatchTicks = 0;
      return;
    }
    String name = screen.getClass().getName();
    // Only vanilla screens are closed: the mod does not understand or touch windows of other mods
    // (or its own).
    if (!name.startsWith("net.minecraft.")) {
      screenMismatchTicks = 0;
      return;
    }
    // The recording is itself inside the settings: moving between their screens is expected.
    // Without this the pause menu kept opening and closing in a loop.
    if (settingsScreen(screen) && frame.hasScreenState && settingsScreen(frame.openScreen)) {
      screenMismatchTicks = 0;
      return;
    }
    if (frame.hasScreenState ? name.equals(frame.openScreen) : screenClickAhead(name)) {
      screenMismatchTicks = 0;
      return;
    }
    if (++screenMismatchTicks < SCREEN_CLOSE_DELAY) return;
    screenMismatchTicks = 0;
    MirrorDebug.log("GUI", "closing screen absent from the recording: " + name);
    handler.containers.cancelDeferredPress();
    if (container) mc.player.closeScreen();
    else mc.displayGuiScreen(null);
    handler.clicks.clearLeftClickBlock();
  }
  /**
   * Format 2 recordings do not store the open screen: there the reference is recorded clicks of the
   * same window ahead.
   */
  private boolean screenClickAhead(String name) {
    int count = manager.copyPlaybackFramesAhead(lookAhead);
    for (int i = 0; i < count; i++) {
      Frame f = lookAhead[i];
      if (f != null && f.guiClick && name.equals(f.guiScreen)) return true;
    }
    return false;
  }
  /**
   * If the recording has a settings screen open, the pause menu is opened, otherwise the cursor has
   * nothing to click. Recorded settings use client ticks even while the singleplayer world is
   * paused. A recorded chat window is reopened separately.
   */
  void openRecordedMenu(Frame frame) {
    if (frame == null || frame.openScreen == null || frame.openScreen.isEmpty()) return;
    if (mc.currentScreen != null || mc.player == null) return;
    // Live chat from the recording: the window opens per frame, letters and arrows arrive as
    // recorded keys.
    if (frame.openScreen.endsWith(".GuiChat")) {
      if (config.isVisibleChat() && !handler.chat.isTyping() && frame.guiKeys.length > 0) {
        try {
          mc.displayGuiScreen(new GuiChat());
          handler.chat.markChatKeyReplay();
        } catch (Exception e) {
          MirrorDebug.log("CHAT", "cannot replay chat screen: " + e);
        }
      }
      return;
    }
    boolean allowed = false;
    for (String a : SETTINGS_SCREENS)
      if (frame.openScreen.endsWith("." + a)) {
        allowed = true;
        break;
      }
    if (!allowed) return;
    try {
      mc.displayInGameMenu();
    } catch (Exception e) {
    }
  }

  /**
   * Settings screens: their input is recorded and replayed, the user changes them with the cursor
   * as usual. The main menu, server list and world selection are not here: playback would wait for
   * a screen that never opens.
   */
  private static final String[] SETTINGS_SCREENS = {
    "GuiIngameMenu",
    "GuiOptions",
    "GuiScreenOptions",
    "GuiScreenOptionsSounds",
    "GuiVideoSettings",
    "ScreenChatOptions",
    "GuiControls",
    "GuiLanguage",
    "GuiCustomizeSkin",
    "GuiKeyBindingList",
    "GuiScreenResourcePacks"
  };

  private static final String[] MENU_SCREENS = {
    "GuiIngameMenu",
    "GuiOptions",
    "GuiScreenOptions",
    "GuiScreenOptionsSounds",
    "GuiVideoSettings",
    "GuiControls",
    "GuiLanguage",
    "GuiCustomizeSkin",
    "GuiShareToLan",
    "GuiScreenResourcePacks",
    "GuiMainMenu",
    "GuiMultiplayer",
    "GuiSelectWorld",
    "GuiWorldSelection",
    "GuiCreateWorld",
    "GuiCustomizeWorldScreen",
    "GuiScreenCustomizePresets",
    "GuiDisconnected",
    "GuiDownloadTerrain",
    "GuiScreenWorking",
    "GuiYesNo",
    "GuiGameOver",
    "GuiWinGame",
    "GuiStats",
    "GuiAchievements",
    "GuiScreenAddServer",
    "GuiScreenServerList",
    "GuiScreenDemo",
    "GuiScreenBackup",
    "GuiKeyBindingList"
  };
  /**
   * Vanilla menus (world selection, server list, "you died" and the like) and all Forge screens are
   * game control, not gameplay. Settings screens (SETTINGS_SCREENS) are excluded. Menu clicks and
   * keys are not recorded: otherwise playback waits for a screen that never opens and holds the
   * event queue. The fact that a screen is open (openScreenName) is still saved: world hotkeys stay
   * suppressed in such frames.
   */
  static boolean menuScreen(String className) {
    if (className == null) return false;
    if (className.startsWith("net.minecraftforge.")) return true;
    if (!className.startsWith("net.minecraft.")) return false;
    // Settings screens are a player action like any other: their input is recorded.
    for (String allow : SETTINGS_SCREENS) if (className.endsWith("." + allow)) return false;
    String simple = className.substring(className.lastIndexOf('.') + 1);
    int inner = simple.indexOf('$');
    if (inner > 0) simple = simple.substring(0, inner);
    for (int i = 0; i < MENU_SCREENS.length; i++) if (MENU_SCREENS[i].equals(simple)) return true;
    return false;
  }

  @Override
  public void reset() {
    guiQueue.clear();
    screenMismatchTicks = 0;
    noticeGuiDrop = false;
  }
}
