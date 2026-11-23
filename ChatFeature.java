package com.mirror.recorder.handler;

import static com.mirror.recorder.handler.ReplayState.LOG;

import com.mirror.recorder.config.RecorderConfig;
import com.mirror.recorder.debug.MirrorDebug;
import com.mirror.recorder.gui.Lang;
import com.mirror.recorder.manager.RecorderManager;
import com.mirror.recorder.model.Frame;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Chat: playback message queue, live typing in the chat window and recording the line prefix. */
@SideOnly(Side.CLIENT)
final class ChatFeature implements ReplayState.Feature {
  private final RecordingHandler handler;
  private final ReplayState state;
  private final Minecraft mc = Minecraft.getMinecraft();
  private final RecorderManager manager;
  private final RecorderConfig config;

  ChatFeature(RecordingHandler handler, ReplayState state) {
    this.handler = handler;
    this.state = state;
    this.manager = state.manager;
    this.config = state.config;
  }

  private int typeWait = 0;
  private boolean chatWarned = false;
  private boolean chatKeyReplay = false;
  private boolean prevChatOpen = false;
  private static final int CHAT_OPEN_WAIT = 20;
  /** Live chat: the text is typed into the real chat window letter by letter. */
  private String typeText = null;
  /** Live chat is still typing. */
  boolean isTyping() {
    return typeText != null;
  }
  /** Playback opened the chat window: further letters arrive as recorded keys. */
  void markChatKeyReplay() {
    chatKeyReplay = true;
  }
  /**
   * A new recording snapshots an already open chat too; a previous recording must not own its
   * initial text.
   */
  void syncChatOpen() {
    prevChatOpen = false;
  }

  private int typeAt = 0;
  private long typeNextNs = 0L;
  private static final long CHAT_FALLBACK_CHAR_NS = 85000000L;
  private Field chatField = null;
  private static final String[] CHAT_FIELDS = {"field_146415_a", "inputField"};
  private static final int CHAT_QUEUE_LIMIT = 256;

  static final class ChatOutput {
    final String message, context;
    final boolean keyFallback;

    ChatOutput(String message, String context) {
      this(message, context, false);
    }

    ChatOutput(String message, String context, boolean keyFallback) {
      this.message = message;
      this.context = context;
      this.keyFallback = keyFallback;
    }

    boolean needsVanillaSend() {
      // Legacy ChatMsg already passed the client event; semantic submit still holds raw input.
      return context != null || !keyFallback;
    }
  }

  private final ArrayDeque<ChatOutput> chatQueue = new ArrayDeque<ChatOutput>();
  /** Recorded messages are sent one at a time: a burst in one tick leads to a kick for spam. */
  private long lastChatSentAt = 0L;

  boolean allowCurrentSubmit(GuiScreen screen) {
    GuiTextField field = chatInput(screen);
    return field != null && allows(field.getText());
  }

  private boolean allows(String message) {
    return ModCommandPolicy.allowed(
        message, config.isPlaybackChat(), config.isPlaybackModCommands());
  }

  /** Queues the frame's message when chat playback is enabled, regardless of the slot's origin. */
  void enqueueRecordedChat(Frame frame) {
    if (allows(frame.chatMessage) && frame.chatMessage != null && !frame.chatMessage.isEmpty()) {
      if (chatQueue.size() < CHAT_QUEUE_LIMIT)
        chatQueue.add(new ChatOutput(frame.chatMessage, null, true));
      else {
        LOG.warn("Chat queue overflow, recorded message dropped");
        MirrorDebug.log(
            "CHAT",
            "queue overflow at limit "
                + CHAT_QUEUE_LIMIT
                + ", dropped a message of "
                + frame.chatMessage.length()
                + " chars");
      }
    }
  }
  /**
   * Chat queue: one message per interval; visible chat types it unless recorded keys already sent
   * it.
   */
  void sendQueuedChat(EntityPlayerSP player) {
    if (!chatQueue.isEmpty()) {
      long nowMs = System.currentTimeMillis();
      if (nowMs < lastChatSentAt) lastChatSentAt = 0L;
      if (nowMs - lastChatSentAt >= config.getChatSendIntervalMs()) {
        ChatOutput output =
            (config.isVisibleChat()
                    && (typeText != null
                        || chatBlockingScreen()
                        || (chatKeyReplay && mc.currentScreen instanceof GuiChat)))
                ? null
                : chatQueue.poll();
        if (output != null && !output.message.isEmpty()) {
          if (output.keyFallback && chatKeyReplay) {
            chatKeyReplay = false;
          } else if (output.keyFallback && config.isVisibleChat()) startChatTyping(output.message);
          else sendInvisibleOutput(player, output);
          lastChatSentAt = nowMs;
        }
      }
    }
  }

  private void sendInvisibleOutput(EntityPlayerSP player, ChatOutput output) {
    if (!allows(output.message)) return;
    if (!output.needsVanillaSend() && !ModCommandPolicy.isMirror(output.message)) {
      player.sendChatMessage(output.message);
      return;
    }
    try {
      if (output.context != null
          && !componentExists("RUN_COMMAND", output.message, output.context)) {
        state.sendMsg(Lang.t("msg.chat_component_missing"));
        return;
      }
      String message = ForgeEventFactory.onClientSendMessage(output.message);
      if (!message.isEmpty() && ClientCommandHandler.instance.executeCommand(player, message) == 0)
        player.sendChatMessage(message);
    } catch (ReflectiveOperationException e) {
      state.warnClickFailure("Invisible chat command replay failed", e);
    }
  }

  /** Chat input field: the field name differs in obf and dev, so both names are tried. */
  GuiTextField chatInput(GuiScreen sc) {
    try {
      if (!(sc instanceof GuiChat)) return null;
      if (chatField == null) {
        for (String nm : CHAT_FIELDS) {
          try {
            Field f = GuiChat.class.getDeclaredField(nm);
            f.setAccessible(true);
            chatField = f;
            break;
          } catch (Exception e) {
          }
        }
      }
      if (chatField == null) return null;
      Object o = chatField.get(sc);
      return o instanceof GuiTextField ? (GuiTextField) o : null;
    } catch (Exception e) {
      return null;
    }
  }
  /**
   * Replaying a mouse click inside the chat line: set the same cursor (and selection) as in the
   * recording.
   */
  void deliverChatCursor(GuiScreen screen, int cursor, int selection) {
    GuiTextField f = chatInput(screen);
    if (f == null) return;
    try {
      f.setCursorPosition(cursor);
      f.setSelectionPos(selection);
    } catch (Exception e) {
      state.warnClickFailure("Recorded chat cursor failed", e);
    }
  }
  /**
   * Letter-by-letter typing in the real chat window. If it fails, the message is sent at once,
   * without loss.
   */
  private void startChatTyping(String msg) {
    if (msg == null || msg.isEmpty()) return;
    typeText = msg;
    typeAt = 0;
    typeNextNs = 0L;
    typeWait = CHAT_OPEN_WAIT;
    MirrorDebug.log("CHAT", "visible typing started: " + msg);
    openChatScreen();
  }

  private void failTyping(String msg) {
    typeText = null;
    if (mc.player != null && msg != null && !msg.isEmpty() && allows(msg))
      sendInvisibleOutput(mc.player, new ChatOutput(msg, null));
  }

  void tickChatTyping(boolean tickWait) {
    if (typeText == null) return;
    if (!manager.isPlaying()) {
      typeText = null;
      typeNextNs = 0L;
      return;
    }
    if (!(mc.currentScreen instanceof GuiChat)) {
      if (typeWait > 0) {
        if (tickWait) typeWait--;
        openChatScreen();
        return;
      }
      MirrorDebug.log("CHAT", "chat screen did not open in " + CHAT_OPEN_WAIT + " ticks");
      warnVisibleChat();
      failTyping(typeText);
      return;
    }
    long now = System.nanoTime();
    if (typeNextNs > 0L && now < typeNextNs) return;
    if (typeAt < typeText.length()) {
      typeWait = CHAT_OPEN_WAIT;
      char tc = typeText.charAt(typeAt);
      typeAt++;
      if (!typeChar(tc) && !setChatText(typeText.substring(0, typeAt))) {
        MirrorDebug.log("CHAT", "chat input field is not reachable");
        warnVisibleChat();
        failTyping(typeText);
        return;
      }
      typeNextNs = now + CHAT_FALLBACK_CHAR_NS;
      return;
    }
    String done = typeText;
    typeText = null;
    typeNextNs = 0L;
    if (allows(done) && !sendChatKey() && mc.player != null)
      sendInvisibleOutput(mc.player, new ChatOutput(done, null));
    if (mc.currentScreen instanceof GuiChat) mc.displayGuiScreen(null);
    handler.clicks.clearLeftClickBlock();
  }
  // Typing goes the same way as for the player: GuiChat.keyTyped -> input field. This uses the
  // input field's normal character insertion logic.
  private boolean typeChar(char c) {
    try {
      GuiScreen sc = mc.currentScreen;
      if (!(sc instanceof GuiChat)) return false;
      state.reflect.ensureResolved();
      if (state.reflect.keyTyped() == null) return false;
      state.reflect.keyTyped().invoke(sc, Character.valueOf(c), Integer.valueOf(0));
      return true;
    } catch (Exception e) {
      return false;
    }
  }
  // Enter in chat: vanilla itself sends the line, adds it to history and closes the window.
  private boolean sendChatKey() {
    try {
      GuiScreen sc = mc.currentScreen;
      if (!(sc instanceof GuiChat)) return false;
      state.reflect.ensureResolved();
      if (state.reflect.keyTyped() == null) return false;
      state
          .reflect
          .keyTyped()
          .invoke(sc, Character.valueOf('\r'), Integer.valueOf(Keyboard.KEY_RETURN));
      return !(mc.currentScreen instanceof GuiChat);
    } catch (Exception e) {
      return false;
    }
  }

  private void openChatScreen() {
    try {
      if (!(mc.currentScreen instanceof GuiChat)) mc.displayGuiScreen(new GuiChat());
    } catch (Exception e) {
      MirrorDebug.log("CHAT", "cannot open chat screen: " + e);
    }
  }

  /** Live chat failed: say so once per handler instance so the failure does not go unnoticed. */
  private void warnVisibleChat() {
    if (chatWarned) return;
    chatWarned = true;
    state.sendMsg(Lang.t("msg.visible_chat_is_unavailable_the"));
  }

  /** Capture the initial field before the first edit, not its already edited end-of-tick text. */
  void injectChatPrefix() {
    boolean chatNow = mc.currentScreen instanceof GuiChat;
    if (chatNow && !prevChatOpen) handler.guiRec.recordChatOpen(mc.currentScreen);
    prevChatOpen = chatNow;
  }

  /** Live chat does not start over a foreign window, but an already open chat is no obstacle. */
  private boolean chatBlockingScreen() {
    GuiScreen s = mc.currentScreen;
    return s != null && !(s instanceof GuiChat);
  }

  private boolean setChatText(String text) {
    GuiTextField field = chatInput(mc.currentScreen);
    if (field == null) return false;
    field.setText(text);
    return true;
  }

  @Override
  public void reset() {
    typeText = null;
    typeNextNs = 0L;
    chatKeyReplay = false;
    chatQueue.clear();
    lastChatSentAt = 0L;
  }

  String[] componentAtMouse() throws ReflectiveOperationException {
    ITextComponent hit = mc.ingameGUI.getChatGUI().getChatComponent(Mouse.getX(), Mouse.getY());
    if (hit == null) return null;
    String[] action = componentAction(hit, GuiScreen.isShiftKeyDown());
    if (action == null) return null;
    for (ChatLine line : chatLines(true)) {
      for (ITextComponent part : line.getChatComponent()) {
        if (part == hit) return new String[] {action[0], action[1], sourceContext(line, action)};
      }
    }
    throw new IllegalStateException("Interactive chat component has no source line");
  }

  private String sourceContext(ChatLine drawn, String[] action)
      throws ReflectiveOperationException {
    String context = null;
    String fragment = drawn.getChatComponent().getUnformattedText();
    for (ChatLine line : chatLines(false)) {
      String text = line.getChatComponent().getUnformattedText();
      if (line.getUpdatedCounter() != drawn.getUpdatedCounter() || !text.contains(fragment))
        continue;
      if (!lineHasAction(line, action[0], action[1])) continue;
      if (context != null && !context.equals(text))
        throw new IllegalStateException("Ambiguous chat source context");
      context = text;
    }
    if (context == null) throw new IllegalStateException("Chat source context not found");
    return context;
  }

  private boolean lineHasAction(ChatLine line, String action, String value) {
    String context = line.getChatComponent().getUnformattedText();
    for (ITextComponent part : line.getChatComponent()) {
      String[] candidate = componentAction(part, action.equals("INSERTION"));
      if (SemanticGuiEvents.matchesComponent(action, value, context, candidate, context))
        return true;
    }
    return false;
  }

  private static String[] componentAction(ITextComponent component, boolean shift) {
    if (shift) {
      String insertion = component.getStyle().getInsertion();
      return insertion == null ? null : new String[] {"INSERTION", insertion};
    }
    ClickEvent click = component.getStyle().getClickEvent();
    return click == null ? null : new String[] {click.getAction().name(), click.getValue()};
  }

  @SuppressWarnings("unchecked")
  private List<ChatLine> chatLines(boolean drawn) throws ReflectiveOperationException {
    Object chat = mc.ingameGUI.getChatGUI();
    String dev = drawn ? "drawnChatLines" : "chatLines";
    String srg = drawn ? "field_146253_i" : "field_146252_h";
    return (List<ChatLine>) TextEditors.member(chat, dev, srg).get(chat);
  }

  private boolean componentExists(String action, String value, String context)
      throws ReflectiveOperationException {
    for (ChatLine line : chatLines(false)) {
      if (line.getChatComponent().getUnformattedText().equals(context)
          && lineHasAction(line, action, value)) return true;
    }
    return false;
  }

  void deliverComponent(GuiScreen screen, String action, String value, String context) {
    try {
      if (!componentExists(action, value, context)) {
        state.sendMsg(Lang.t("msg.chat_component_missing"));
        MirrorDebug.log("CHAT", "Recorded component context not found; click skipped");
        return;
      }
      if (action.equals("RUN_COMMAND")) {
        if (allows(value)) screen.sendChatMessage(value, false);
      } else if (action.equals("INSERTION") || action.equals("SUGGEST_COMMAND")) {
        TextEditors.method(
                screen.getClass(),
                new Class<?>[] {String.class, boolean.class},
                "setText",
                "func_175274_a")
            .invoke(screen, value, action.equals("SUGGEST_COMMAND"));
      } else if (action.equals("OPEN_URL") || action.equals("OPEN_FILE")) {
        state.reflect.deliverSemanticLink(screen, action, value);
      }

    } catch (ReflectiveOperationException | IllegalArgumentException e) {
      state.warnClickFailure("Recorded chat component failed", e);
    }
  }

  void enqueueInvisibleComponent(String action, String value, String context) {
    if (!allows(value) || !action.equals("RUN_COMMAND")) return;
    if (chatQueue.size() < CHAT_QUEUE_LIMIT) chatQueue.add(new ChatOutput(value, context));
    else MirrorDebug.log("CHAT", "Semantic command queue overflow");
  }

  /** Command replay is independent of the clicks/items switch even for semantic recordings. */
  void enqueueCommandsWithoutGui(Frame frame) {
    if (!config.isPlaybackModCommands()) return;
    for (int i = 0; i + 6 <= frame.guiKeys.length; i += 6) {
      int action = frame.guiKeys[i], payload = frame.guiKeys[i + 2];
      if (payload < 0 || payload > frame.guiTextPayloads.length - 3) continue;
      String message = frame.guiTextPayloads[payload + 1];
      if (!ModCommandPolicy.isMirror(message)) continue;
      chatKeyReplay = false;
      if (action == GuiEventQueue.GA_CHAT_SUBMIT) enqueueSemanticMessage(message);
      else if (action == GuiEventQueue.GA_CHAT_CLICK
          && frame.guiTextPayloads[payload].equals("RUN_COMMAND"))
        enqueueInvisibleComponent("RUN_COMMAND", message, frame.guiTextPayloads[payload + 2]);
    }
  }

  void enqueueSemanticMessage(String message) {
    if (!allows(message) || message.trim().isEmpty()) return;
    if (chatQueue.size() < CHAT_QUEUE_LIMIT) chatQueue.add(new ChatOutput(message.trim(), null));
    else MirrorDebug.log("CHAT", "Semantic chat queue overflow");
  }

  void deliverSemanticSubmit(GuiScreen screen, String expected) {
    chatKeyReplay = false;
    GuiTextField field = chatInput(screen);
    if (field == null) return;
    if (!allows(expected)) {
      mc.displayGuiScreen(null);
      return;
    }
    if (!field.getText().equals(expected)) {
      state.sendMsg(Lang.t("msg.gui_editor_mismatch"));
      return;
    }
    state.reflect.ensureResolved();
    try {
      if (state.reflect.keyTyped() != null)
        state.reflect.keyTyped().invoke(screen, '\r', Keyboard.KEY_RETURN);
    } catch (ReflectiveOperationException e) {
      state.warnClickFailure("Semantic chat submit failed", e);
    }
  }
}
