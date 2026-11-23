package com.mirror.recorder.handler;

import static com.mirror.recorder.handler.GuiEventQueue.*;

import com.mirror.recorder.debug.MirrorDebug;
import com.mirror.recorder.gui.Lang;
import com.mirror.recorder.manager.RecorderManager;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Recording window events: clicks, container drags, slider gestures, list scrolling and keys go
 * into the current tick's event stream.
 */
@SideOnly(Side.CLIENT)
final class GuiEventRecorder implements ReplayState.Feature {
  private final RecordingHandler handler;
  private final Minecraft mc = Minecraft.getMinecraft();
  private final RecorderManager manager;
  private final ReplayState state;

  GuiEventRecorder(RecordingHandler handler, ReplayState state) {
    this.handler = handler;
    this.state = state;
    this.manager = state.manager;
  }

  private final List<int[]> pendingGuiKeys = new ArrayList<int[]>();
  /**
   * Screen of each pendingGuiKeys event (same index); pendingGuiKeyScreen is the screen of the last
   * one.
   */
  private final List<String> pendingGuiEventScreens = new ArrayList<String>();

  private final List<String> pendingTextPayloads = new ArrayList<String>();
  private TextEditors.Editor pendingEditor;
  private String beforeText;
  private int beforeCursor, beforeSelection;
  private boolean componentClick;
  private boolean semanticChatSend;

  String[] textPayloads() {
    return pendingTextPayloads.toArray(new String[pendingTextPayloads.size()]);
  }

  int addPayload(String owner, String before, String inserted) {
    if (!SemanticGuiEvents.validText(owner)
        || !SemanticGuiEvents.validText(before)
        || !SemanticGuiEvents.validText(inserted)
        || pendingTextPayloads.size() + 3 > SemanticGuiEvents.MAX_PAYLOADS) {
      abortSemanticCapture("Semantic payload exceeds NBT capacity");
      throw new IllegalArgumentException("Semantic payload exceeds NBT capacity");
    }
    int index = pendingTextPayloads.size();
    pendingTextPayloads.add(owner);
    pendingTextPayloads.add(before);
    pendingTextPayloads.add(inserted);
    return index;
  }

  boolean isComponentClick() {
    return componentClick || semanticChatSend;
  }

  private volatile String pendingGuiKeyScreen = null;
  private long recordTickStartNs = 0L;
  /**
   * Container edges use the owning screen instance; polled mouse state already includes future
   * events in the same batch.
   */
  private final ContainerMouseSequence containerMouse = new ContainerMouseSequence();

  private GuiScreen recContainerScreen;
  private int[] recContainerPress;

  private long recDragLastMoveNs = 0L;
  /** Slider points may be throttled because intermediate values do not select inventory slots. */
  private static final long MOVE_THROTTLE_NS = 12_000_000L;
  /**
   * Slider gesture in the settings: only what started with a press on the slider in this same
   * window instance is recorded.
   */
  private GuiScreen recSliderScreen = null;

  private GuiScreen recScrollScreen = null;
  private int[] recScrollLast = new int[0];

  /** Tick start: timestamps of this frame's events are counted from it (0 means not recording). */
  void beginTick() {
    recordTickStartNs = manager.isRecording() ? System.nanoTime() : 0L;
  }

  private int eventOffsetMs() {
    long base = recordTickStartNs;
    if (base <= 0L) return 0;
    long ms = (System.nanoTime() - base) / 1000000L;
    return (int)
        (ms < 0L
            ? 0L
            : (ms > SemanticGuiEvents.MAX_EVENT_OFFSET_MS
                ? SemanticGuiEvents.MAX_EVENT_OFFSET_MS
                : ms));
  }
  /**
   * A window event together with its screen: playback delivers each one to its screen and in the
   * recorded order.
   */
  void addGuiEvent(String screen, int[] event) {
    if (!manager.isRecording()) return;
    pendingGuiKeyScreen = screen;
    pendingGuiKeys.add(event);
    pendingGuiEventScreens.add(screen);
  }

  boolean hasRoom() {
    return pendingGuiKeys.size() < GUI_KEY_LIMIT;
  }

  void clearGuiEvents() {
    pendingGuiKeys.clear();
    pendingTextPayloads.clear();
    pendingGuiEventScreens.clear();
    pendingGuiKeyScreen = null;
  }

  int[] flattenGuiKeys() {
    int n = pendingGuiKeys.size();
    if (n == 0) return null;
    int[] flat = new int[n * 6];
    for (int i = 0; i < n; i++) System.arraycopy(pendingGuiKeys.get(i), 0, flat, i * 6, 6);
    return flat;
  }
  /** Screen of each tick event (same order as flattenGuiKeys). */
  String[] eventScreens() {
    return pendingGuiEventScreens.toArray(new String[pendingGuiEventScreens.size()]);
  }

  String lastEventScreen() {
    return pendingGuiKeyScreen;
  }
  /** An unreleased container drag is no longer recorded. */
  void cancelDrag() {
    containerMouse.reset();
    recContainerScreen = null;
    recContainerPress = null;
  }
  /** The drag window quietly closed (server, hotkey): the unreleased drag is no longer recorded. */
  void dropDragIfScreenClosed(String openScreen) {
    if (recContainerScreen != null
        && (mc.currentScreen != recContainerScreen || openScreen == null)) cancelDrag();
  }
  /** Discarded slider and scroll gestures do not carry over to the next recorded frame. */
  void clearGestures() {
    recSliderScreen = null;
    recScrollScreen = null;
  }

  @Override
  public void reset() {
    clearGuiEvents();
    recordTickStartNs = 0L;
    cancelDrag();
    pendingEditor = null;
    beforeText = null;
    componentClick = false;
    semanticChatSend = false;
  }

  void recordGuiMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
    if (!manager.isRecording()) return;
    GuiScreen screen = event.getGui();
    if (screen == null) return;
    String name = screen.getClass().getName();
    componentClick = false;
    handler.chat.injectChatPrefix();
    if (!manager.isRecording()) return;
    if (screen instanceof GuiChat) recordChatComponent(screen);
    if (!replayableScreen(name)
        || name.startsWith("com.mirror.recorder.gui.")
        || menuScreen(name)
        || screen instanceof GuiChat) return;
    int btn = Mouse.getEventButton();
    boolean down = Mouse.getEventButtonState();
    int gw = Math.max(1, screen.width),
        gh = Math.max(1, screen.height),
        px = MathHelper.clamp(Mouse.getEventX() * gw / Math.max(1, mc.displayWidth), 0, gw - 1),
        py =
            MathHelper.clamp(
                gh - Mouse.getEventY() * gh / Math.max(1, mc.displayHeight) - 1, 0, gh - 1);
    int cx = px - gw / 2, cy = py - gh / 2;
    if (screen instanceof GuiContainer && btn != 2)
      trackContainerDrag(screen, name, btn, down, cx, cy);
    else recordGuiClick(name, btn, down, cx, cy);
    recordSliderGesture(screen, name, btn, down, px, py, cx, cy);
  }

  /**
   * A mouse button press in a window is a GA_CLICK event. Coordinates (cx, cy) are relative to the
   * window center.
   */
  private void recordGuiClick(String name, int btn, boolean down, int cx, int cy) {
    // Every window click is a separate event with its own point and screen in the shared window
    // event stream: several clicks per tick are not merged into one target and do not overtake keys
    // recorded before them.
    if (!down || btn < 0 || btn > 2) return;
    if (pendingGuiKeys.size() < GUI_KEY_LIMIT)
      addGuiEvent(
          name,
          new int[] {
            GA_CLICK,
            eventOffsetMs(),
            btn,
            GUI_FLAG_TIMED | (GuiScreen.isShiftKeyDown() ? GUI_FLAG_SHIFT : 0),
            cx,
            cy
          });
    else
      MirrorDebug.log(
          "GUI", "key event cap " + GUI_KEY_LIMIT + " reached in one tick, click dropped");
  }

  /**
   * Preserve every container edge and visited slot; polling or throttling loses batched gestures.
   */
  private void trackContainerDrag(
      GuiScreen screen, String name, int btn, boolean down, int cx, int cy) {
    if (recContainerScreen != screen) cancelDrag();
    if (down && (btn == 0 || btn == 1)) {
      if (!admitContainerEvent()) return;
      recContainerScreen = screen;
      containerMouse.press(screen, btn);
      recContainerPress =
          new int[] {
            GA_CPRESS,
            eventOffsetMs(),
            btn,
            GUI_FLAG_TIMED | (GuiScreen.isShiftKeyDown() ? GUI_FLAG_SHIFT : 0),
            cx,
            cy
          };
      addGuiEvent(name, recContainerPress);
    } else if (!down && (btn == 0 || btn == 1)) {
      if (!containerMouse.release(screen, btn)) return;
      recordContainerEdge(name, GA_CUP, btn, cx, cy);
    } else if (btn == -1) {
      int button = containerMouse.moveButton(screen);
      if (button >= 0) recordContainerEdge(name, GA_CMOVE, button, cx, cy);
    }
  }

  private void recordContainerEdge(String name, int action, int button, int cx, int cy) {
    if (!admitContainerEvent()) return;
    addGuiEvent(
        name,
        new int[] {
          action,
          eventOffsetMs(),
          button,
          GUI_FLAG_TIMED | (GuiScreen.isShiftKeyDown() ? GUI_FLAG_SHIFT : 0),
          cx,
          cy
        });
  }

  private boolean admitContainerEvent() {
    return ContainerMouseSequence.admitEvent(
        pendingGuiKeys.size(), GUI_KEY_LIMIT, this::abortContainerCapture);
  }

  private void abortContainerCapture() {
    // Container and editor streams share one atomic capture failure policy.
    abortSemanticCapture("Recording stopped: container event cap reached; current frame discarded");
  }

  /** Slider gesture in a settings window: GA_DRAG along the way and GA_DRAG_END on release. */
  private void recordSliderGesture(
      GuiScreen screen, String name, int btn, boolean down, int px, int py, int cx, int cy) {
    boolean dragMove = btn == -1 && Mouse.isButtonDown(0),
        dragEnd = btn == 0 && !down,
        dragStart = btn == 0 && down;
    if (!settingsScreen(screen) || !(dragStart || dragMove || dragEnd)) return;
    // The slider gesture is recorded only if the press was on a slider in this same window: the
    // release of a button that opened a new settings window arrives in the new window and must not
    // become "drag the slider to here", a misclick in playback.
    if (dragStart) {
      recSliderScreen = handler.lists.findReplaySlider(screen, px, py) != null ? screen : null;
      if (recSliderScreen == null) return;
    } else if (recSliderScreen != screen) {
      if (dragEnd) recSliderScreen = null;
      return;
    }
    if (dragEnd) recSliderScreen = null;
    long nowNs = System.nanoTime();
    if (dragMove && nowNs - recDragLastMoveNs < MOVE_THROTTLE_NS) return;
    int[] ev =
        new int[] {dragEnd ? GA_DRAG_END : GA_DRAG, eventOffsetMs(), 0, GUI_FLAG_TIMED, cx, cy};
    if (pendingGuiKeys.size() >= GUI_KEY_LIMIT) {
      // The release must not be lost: without it the slider stays "stuck" to the cursor in
      // playback. It replaces the last drag point.
      int last = pendingGuiKeys.size() - 1;
      if (dragEnd && last >= 0 && pendingGuiKeys.get(last)[0] == GA_DRAG) {
        pendingGuiKeys.set(last, ev);
        pendingGuiEventScreens.set(last, name);
      } else
        MirrorDebug.log(
            "GUI", "mouse event cap " + GUI_KEY_LIMIT + " reached in one tick, drag event dropped");
      return;
    }
    recDragLastMoveNs = dragMove ? nowNs : 0L;
    addGuiEvent(name, ev);
  }

  void recordGuiMouseInputPost(GuiScreenEvent.MouseInputEvent.Post event) {
    if (!manager.isRecording()) return;
    GuiScreen screen = event.getGui();
    if (screen == null || mc.currentScreen != screen) {
      componentClick = false;
      return;
    }
    if (screen instanceof GuiContainer
        && recContainerPress != null
        && recContainerScreen == screen
        && Mouse.getEventButtonState()
        && Mouse.getEventButton() == recContainerPress[2]) {
      // Read vanilla's decision after the press, rather than guessing from replay wall-clock time.
      if (handler.containers.recordedDoubleClick(screen)) recContainerPress[0] = GA_CPRESS_DOUBLE;
      recContainerPress = null;
    }
    if (Mouse.getEventDWheel() != 0) {
      handler.lists.recordWheel(screen, this, eventOffsetMs());
      componentClick = false;
      return;
    }
    if (Mouse.getEventButton() == 0 && Mouse.getEventButtonState() && !componentClick)
      recordEditorCursor(screen);
    componentClick = false;
    if (screen instanceof GuiChat) return;
    if (!settingsScreen(screen)) return;
    Field[] lists = handler.lists.slotListsOf(screen.getClass());
    if (lists.length == 0) return;
    // A new window starts lists from zero, both in recording and in playback.
    if (recScrollScreen != screen || recScrollLast.length != lists.length) {
      recScrollScreen = screen;
      recScrollLast = new int[lists.length];
    }
    String name = screen.getClass().getName();
    for (int i = 0; i < lists.length; i++) {
      int v;
      try {
        v = handler.lists.slotAmount(lists[i].get(screen));
      } catch (Exception e) {
        continue;
      }
      if (v == Integer.MIN_VALUE || v == recScrollLast[i]) continue;
      recScrollLast[i] = v;
      int last = pendingGuiKeys.size() - 1;
      // Consecutive scrolling of one list is merged into one event: the final position matters, not
      // every wheel notch.
      if (last >= 0
          && pendingGuiKeys.get(last)[0] == GA_SCROLL
          && pendingGuiKeys.get(last)[2] == i
          && name.equals(pendingGuiEventScreens.get(last))) {
        int[] e = pendingGuiKeys.get(last);
        e[1] = eventOffsetMs();
        e[4] = v;
        continue;
      }
      if (pendingGuiKeys.size() >= GUI_KEY_LIMIT) {
        MirrorDebug.log(
            "GUI", "key event cap " + GUI_KEY_LIMIT + " reached in one tick, list scroll dropped");
        continue;
      }
      addGuiEvent(name, new int[] {GA_SCROLL, eventOffsetMs(), i, GUI_FLAG_TIMED, v, 0});
    }
  }

  void recordGuiKeyInput(GuiScreenEvent.KeyboardInputEvent.Pre event) {
    if (!manager.isRecording() || !Keyboard.getEventKeyState()) return;
    GuiScreen screen = event.getGui();
    if (screen == null || mc.gameSettings == null) return;
    String name = screen.getClass().getName();
    if (!replayableScreen(name) || name.startsWith("com.mirror.recorder.gui.") || menuScreen(name))
      return;
    handler.chat.injectChatPrefix();
    if (!manager.isRecording()) return;
    int key = Keyboard.getEventKey();
    char chr = Keyboard.getEventCharacter();
    pendingEditor = null;
    semanticChatSend = false;
    if (screen instanceof GuiChat
        && (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER)) {
      recordChatSubmit(screen);
      return;
    }
    if (captureEditor(screen, key)) return;
    GameSettings gs = mc.gameSettings;
    boolean containerKey = screen instanceof GuiContainer;
    boolean textFocus = handler.keyboard.hasFocusedTextField(screen);
    boolean printable = chr >= 32 && chr != 127,
        nav =
            key == Keyboard.KEY_BACK
                || key == Keyboard.KEY_HOME
                || key == Keyboard.KEY_LEFT
                || key == Keyboard.KEY_RIGHT
                || key == Keyboard.KEY_END
                || key == Keyboard.KEY_DELETE
                || key == Keyboard.KEY_UP
                || key == Keyboard.KEY_DOWN;
    boolean textWins = textFocus;
    int action = -1, aux = chr;
    if (key == Keyboard.KEY_ESCAPE) action = GA_CLOSE;
    else if (!containerKey) action = GA_TYPE;
    else if (gs.keyBindInventory.isActiveAndMatches(key)) {
      if (!textWins) action = GA_CLOSE;
    } else if (gs.keyBindDrop.isActiveAndMatches(key)) {
      if (!textWins) action = GA_DROP;
    } else if (gs.keyBindPickBlock.isActiveAndMatches(key)) {
      if (!textWins) action = GA_PICK;
    } else {
      int hb = handler.keyboard.hotbarKeyIndex(gs, key);
      if (hb >= 0 && !textWins) {
        action = GA_SWAP;
        aux = hb;
      }
    }
    if (action < 0) {
      if (printable || nav) action = GA_TYPE;
      else return;
    }
    if (pendingGuiKeys.size() >= GUI_KEY_LIMIT) {
      MirrorDebug.log(
          "GUI", "key event cap " + GUI_KEY_LIMIT + " reached in one tick, event dropped");
      return;
    }
    int cx = 0, cy = 0;
    if (action == GA_DROP || action == GA_PICK || action == GA_SWAP) {
      int gw = Math.max(1, screen.width), gh = Math.max(1, screen.height);
      float fx = MathHelper.clamp((float) Mouse.getX() / Math.max(1, mc.displayWidth), 0f, 1f),
          fy = MathHelper.clamp(1f - (float) Mouse.getY() / Math.max(1, mc.displayHeight), 0f, 1f);
      cx = Math.round(fx * gw) - gw / 2;
      cy = Math.round(fy * gh) - gh / 2;
    }
    int flags =
        (GuiScreen.isShiftKeyDown() ? GUI_FLAG_SHIFT : 0)
            | (GuiScreen.isCtrlKeyDown() ? GUI_FLAG_CTRL : 0);
    if (screen instanceof GuiChat) {
      flags |= GUI_FLAG_TIMED;
      cx = eventOffsetMs();
    }
    addGuiEvent(name, new int[] {action, key, aux, flags, cx, cy});
  }

  private boolean captureEditor(GuiScreen screen, int key) {
    if (key == Keyboard.KEY_ESCAPE) return false;
    try {
      TextEditors.Editor editor = TextEditors.focused(screen);
      if (editor != null
          && (editor.key.startsWith("sign:") || editor.key.equals("book:title"))
          && (key == Keyboard.KEY_RETURN
              || key == Keyboard.KEY_NUMPADENTER
              || key == Keyboard.KEY_UP
              || key == Keyboard.KEY_DOWN)) return false;
      if (editor == null
          || (editor.field != null
              && (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER))) return false;
      pendingEditor = editor;
      beforeText = editor.text();
      beforeCursor = editor.cursor();
      beforeSelection = editor.selection();
      return true;
    } catch (ReflectiveOperationException | IllegalArgumentException e) {
      abortSemanticCapture("Editor capture failed: " + e);
      return true;
    }
  }

  void recordGuiKeyInputPost(GuiScreenEvent.KeyboardInputEvent.Post event) {
    semanticChatSend = false;
    TextEditors.Editor editor = pendingEditor;
    pendingEditor = null;
    if (!manager.isRecording() || editor == null || editor.screen != event.getGui()) return;
    try {
      String after = editor.text();
      int cursor = editor.cursor(), selection = editor.selection();
      boolean changed = !beforeText.equals(after);
      if (!changed && cursor == beforeCursor && selection == beforeSelection) return;
      int needed = changed ? 2 : 1;
      if (pendingGuiKeys.size() + needed > GUI_KEY_LIMIT
          || after.length() > SemanticGuiEvents.MAX_TEXT) {
        abortSemanticCapture("Semantic edit exceeds frame capacity");
        return;
      }
      SemanticGuiEvents.Delta delta = SemanticGuiEvents.delta(beforeText, after);
      String name = editor.screen.getClass().getName();
      if (changed) {
        int payload = addPayload(editor.key, beforeText, delta.inserted);
        addGuiEvent(
            name,
            new int[] {
              GA_EDIT_RANGE, eventOffsetMs(), payload, GUI_FLAG_TIMED, delta.start, delta.end
            });
      }
      int cursorPayload = addPayload(editor.key, after, "");
      addGuiEvent(
          name,
          new int[] {
            GA_EDITOR_CURSOR, eventOffsetMs(), cursorPayload, GUI_FLAG_TIMED, cursor, selection
          });
    } catch (ReflectiveOperationException | IllegalArgumentException e) {
      abortSemanticCapture("Editor result capture failed: " + e);
    }
  }

  private void recordEditorCursor(GuiScreen screen) {
    try {
      TextEditors.Editor editor = TextEditors.focused(screen);
      if (editor == null) return;
      if (!hasRoom()) {
        abortSemanticCapture("Editor cursor exceeds frame capacity");
        return;
      }
      int payload = addPayload(editor.key, editor.text(), "");
      addGuiEvent(
          screen.getClass().getName(),
          new int[] {
            GA_EDITOR_CURSOR,
            eventOffsetMs(),
            payload,
            GUI_FLAG_TIMED,
            editor.cursor(),
            editor.selection()
          });
    } catch (ReflectiveOperationException | IllegalArgumentException e) {
      abortSemanticCapture("Editor mouse capture failed: " + e);
    }
  }

  private void recordChatComponent(GuiScreen screen) {
    if (Mouse.getEventButton() != 0 || !Mouse.getEventButtonState()) return;
    String[] click;
    try {
      click = handler.chat.componentAtMouse();
    } catch (ReflectiveOperationException | IllegalStateException e) {
      abortSemanticCapture("Chat component capture failed: " + e);
      return;
    }
    componentClick = click != null;
    if (click == null) return;
    if (!hasRoom()) {
      abortSemanticCapture("Chat component exceeds frame capacity");
      return;
    }
    if (!SemanticGuiEvents.validText(click[0])
        || !SemanticGuiEvents.validText(click[1])
        || !SemanticGuiEvents.validText(click[2])) {
      abortSemanticCapture("Chat component exceeds NBT capacity");
      return;
    }
    int payload = addPayload(click[0], click[1], click[2]);
    addGuiEvent(
        screen.getClass().getName(),
        new int[] {
          GA_CHAT_CLICK,
          eventOffsetMs(),
          payload,
          GUI_FLAG_TIMED | (GuiScreen.isShiftKeyDown() ? GUI_FLAG_SHIFT : 0),
          0,
          0
        });
  }

  private void recordChatSubmit(GuiScreen screen) {
    semanticChatSend = true;
    if (!hasRoom()) {
      abortSemanticCapture("Semantic chat submit exceeds frame capacity");
      return;
    }
    try {
      TextEditors.Editor editor = TextEditors.focused(screen);
      if (editor == null) return;
      int payload = addPayload(editor.key, editor.text(), "");
      addGuiEvent(
          screen.getClass().getName(),
          new int[] {GA_CHAT_SUBMIT, eventOffsetMs(), payload, GUI_FLAG_TIMED, 0, 0});
    } catch (ReflectiveOperationException | IllegalArgumentException e) {
      abortSemanticCapture("Chat submit capture failed: " + e);
    }
  }

  void abortSemanticCapture(String reason) {
    MirrorDebug.log("GUI", reason);
    clearGuiEvents();
    cancelDrag();
    clearGestures();
    pendingEditor = null;
    beforeText = null;
    if (manager.isRecording()) {
      manager.stopRecording();
      state.sendMsg(Lang.t("msg.gui_input_capacity"));
    }
  }

  void recordChatOpen(GuiScreen screen) {
    try {
      TextEditors.Editor editor = TextEditors.focused(screen);
      if (editor == null) return;
      String initial = editor.text();
      int needed = initial.isEmpty() ? 1 : 2;
      if (pendingGuiKeys.size() + needed > GUI_KEY_LIMIT) {
        abortSemanticCapture("Chat open exceeds frame capacity");
        return;
      }
      if (initial.isEmpty())
        addGuiEvent(screen.getClass().getName(), new int[] {GA_TYPE, 0, 0, GUI_FLAG_TIMED, 0, 0});
      else {
        int payload = addPayload(editor.key, "", initial);
        addGuiEvent(
            screen.getClass().getName(),
            new int[] {GA_EDIT_RANGE, eventOffsetMs(), payload, GUI_FLAG_TIMED, 0, 0});
        int cursorPayload = addPayload(editor.key, initial, "");
        addGuiEvent(
            screen.getClass().getName(),
            new int[] {
              GA_EDITOR_CURSOR,
              eventOffsetMs(),
              cursorPayload,
              GUI_FLAG_TIMED,
              editor.cursor(),
              editor.selection()
            });
      }
    } catch (ReflectiveOperationException | IllegalArgumentException e) {
      abortSemanticCapture("Chat initial editor capture failed: " + e);
    }
  }
}
