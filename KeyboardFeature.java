package com.mirror.recorder.handler;

import static com.mirror.recorder.handler.ReplayState.setKeyState;

import com.mirror.recorder.MirrorRecorder;
import com.mirror.recorder.config.RecorderConfig;
import com.mirror.recorder.debug.MirrorDebug;
import com.mirror.recorder.gui.GuiMirrorMain;
import com.mirror.recorder.gui.Lang;
import com.mirror.recorder.manager.RecorderManager;
import com.mirror.recorder.model.Frame;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.client.gui.inventory.GuiEditSign;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketHeldItemChange;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Keyboard: hotbar, instant actions (Q/F/E/MMB, inventory), other keys and mod hotkeys. */
@SideOnly(Side.CLIENT)
final class KeyboardFeature implements ReplayState.Feature {
  private final RecordingHandler handler;
  private final ReplayState state;
  private final Minecraft mc = Minecraft.getMinecraft();
  private final RecorderManager manager;
  private final RecorderConfig config;

  KeyboardFeature(RecordingHandler handler, ReplayState state) {
    this.handler = handler;
    this.state = state;
    this.manager = state.manager;
    this.config = state.config;
  }

  /**
   * Windows-key filter: the Windows and menu keys switch focus away from the game, so they are
   * neither recorded nor replayed.
   */
  private static final int WIN_LEFT = 219, WIN_RIGHT = 220, WIN_MENU = 221;
  /** Raw keys of the current tick and keys held down by playback itself. */
  private final ArrayDeque<int[]> keyLog = new ArrayDeque<int[]>();

  private final HashSet<Integer> replayedKeys = new HashSet<Integer>();
  private int pendingKeyMask = 0;
  private int lastHotbar = -1;
  /** Instant actions of the tick (Frame.ACT_*) in press order. */
  private final List<Integer> pendingActions = new ArrayList<Integer>();

  private boolean pendingDropAll = false;
  // Vanilla processes pick presses after attack/use; queue both variants to retain their order.
  private final List<Integer> replayPicks = new ArrayList<Integer>();
  /**
   * Instant actions of the frame (drop, swap hands, pick block, inventory) in the recorded order.
   */
  void replayInstantActions(Frame frame, int mask, boolean guiKeyCtx, boolean hasGuiClose) {
    if (frame.hasActions) {
      // Every recorded press is exactly one action in the recorded order; holding does not repeat
      // them.
      for (int a : frame.actions) {
        if (a == Frame.ACT_INVENTORY) {
          if (mc.currentScreen == null && !hasGuiClose) openInventory();
        } else if (mc.currentScreen == null && !guiKeyCtx) {
          if (a == Frame.ACT_DROP || a == Frame.ACT_DROP_ALL) dropItem(a == Frame.ACT_DROP_ALL);
          else if (a == Frame.ACT_PICK || a == Frame.ACT_PICK_CONTENTS)
            replayPicks.add(Integer.valueOf(a));
          else if (a == Frame.ACT_SWAP) swapHands();
        }
      }
    } else {
      // Old recording: the action is restored from the rising edge of the mask bit.
      if (mc.currentScreen == null && !guiKeyCtx) {
        if (rising(mask, Frame.K_DROP)) dropItem(frame.dropAll);
        pulse(
            mc.gameSettings.keyBindSwapHands,
            (mask & Frame.K_SWAP) != 0,
            (state.lastMask & Frame.K_SWAP) != 0);
        pulse(
            mc.gameSettings.keyBindPickBlock,
            (mask & Frame.K_PICK) != 0,
            (state.lastMask & Frame.K_PICK) != 0);
      }
      if (mc.currentScreen == null && !hasGuiClose && rising(mask, Frame.K_INVENTORY))
        openInventory();
    }
  }

  /** Flush after world clicks, as in vanilla processKeyBinds, without a second key pulse. */
  void replayQueuedPicks() {
    if (replayPicks.isEmpty()) return;
    drainPressQueue(mc.gameSettings.keyBindPickBlock);
    for (Integer action : replayPicks) replayPick(action.intValue());
    replayPicks.clear();
  }

  private void replayPick(int action) {
    if (mc.currentScreen != null
        || mc.player == null
        || mc.world == null
        || mc.playerController == null
        || mc.player.isHandActive()) return;
    if (mc.entityRenderer != null) mc.entityRenderer.getMouseOver(1.0F);
    RayTraceResult target = mc.objectMouseOver;
    if (target == null || target.typeOfHit == RayTraceResult.Type.MISS) return;
    if (target.typeOfHit == RayTraceResult.Type.BLOCK && mc.player.capabilities.isCreativeMode)
      pickCreativeBlock(target, action);
    else ForgeHooks.onPickBlock(target, mc.player, mc.world);
  }

  private void pickCreativeBlock(RayTraceResult target, int action) {
    BlockPos pos = target.getBlockPos();
    IBlockState blockState = mc.world.getBlockState(pos);
    if (blockState.getBlock().isAir(blockState, mc.world, pos)) return;
    // Forge reads Ctrl directly from LWJGL; replay must instead use the recorded semantic action.
    // Keep its block hook and Minecraft's TE serializer, including modded pick results.
    TileEntity tile =
        PickBlockReplayPolicy.copiesTileEntity(
                action,
                mc.player.capabilities.isCreativeMode,
                blockState.getBlock().hasTileEntity(blockState))
            ? mc.world.getTileEntity(pos)
            : null;
    ItemStack result =
        blockState.getBlock().getPickBlock(blockState, target, mc.world, pos, mc.player);
    if (result.isEmpty()) return;
    if (tile != null) mc.storeTEInStack(result, tile);
    mc.player.inventory.setPickedItemStack(result);
    mc.playerController.sendSlotPacket(
        mc.player.getHeldItem(EnumHand.MAIN_HAND), 36 + mc.player.inventory.currentItem);
  }

  private void swapHands() {
    if (mc.player == null || mc.player.isSpectator() || mc.player.connection == null) return;
    // A deferred swap key would run after the direct pick and swap away the newly picked result.
    // Use vanilla's packet before world clicks/picks, without leaving a second press queued.
    drainPressQueue(mc.gameSettings.keyBindSwapHands);
    mc.player.connection.sendPacket(
        new CPacketPlayerDigging(
            CPacketPlayerDigging.Action.SWAP_HELD_ITEMS, BlockPos.ORIGIN, EnumFacing.DOWN));
  }

  private void dropItem(boolean all) {
    if (mc.player != null && !mc.player.isSpectator()) mc.player.dropItem(all);
  }

  private void openInventory() {
    if (mc.player == null) return;
    // Open directly: vanilla's queued inventory press can be lost when windows flicker.
    if (mc.playerController.isRidingHorse()) mc.player.sendHorseInventory();
    else mc.displayGuiScreen(new GuiInventory(mc.player));
  }

  boolean rising(int mask, int bit) {
    return (mask & bit) != 0 && (state.lastMask & bit) == 0;
  }
  /** Replays the selected hotbar slot directly without restoring the inventory contents. */
  void applyHotbar(int slot) {
    if (slot < 0 || slot > 8) return;
    lastHotbar = slot;
    // Check against the player's actual slot, not the cache: if the item was changed manually or
    // reverted by the server, playback restores the recorded state instead of assuming it is
    // already applied.
    if (mc.player != null
        && mc.player.inventory != null
        && mc.player.inventory.currentItem != slot) {
      mc.player.inventory.currentItem = slot;
      if (mc.player.connection != null)
        mc.player.connection.sendPacket(new CPacketHeldItemChange(slot));
    }
  }
  /**
   * The mod's own keys (record, play, loop, stop, menu) are not recorded: they control the mod
   * itself.
   */
  private boolean ownKey(int code) {
    try {
      MirrorRecorder mod = MirrorRecorder.getInstance();
      if (mod == null) return false;
      return code == mod.getKeyRecord().getKeyCode()
          || code == mod.getKeyPlay().getKeyCode()
          || code == mod.getKeyLoop().getKeyCode()
          || code == mod.getKeyStop().getKeyCode()
          || code == mod.getKeyGui().getKeyCode();
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean systemKey(int code) {
    return code == WIN_LEFT || code == WIN_RIGHT || code == WIN_MENU;
  }
  /**
   * Returns queued key events as code/state pairs, or null when recording all keys is disabled or
   * the queue is empty.
   */
  int[] collectKeyEvents() {
    if (!config.isRecordAllKeys() || keyLog.isEmpty()) return null;
    int cap = Frame.MAX_KEY_EVENTS * 2;
    int[] out = new int[cap];
    int n = 0;
    while (!keyLog.isEmpty() && n < cap - 1) {
      int[] e = keyLog.poll();
      if (e == null) continue;
      out[n++] = e[0];
      out[n++] = e[1];
    }
    if (!keyLog.isEmpty()) {
      MirrorDebug.log("INPUT", "key event overflow: " + keyLog.size() + " events dropped");
      keyLog.clear();
    }
    if (n == 0) return null;
    int[] exact = new int[n];
    System.arraycopy(out, 0, exact, 0, n);
    return exact;
  }
  /**
   * Keys that playback drives itself: movement, clicks, drop, slots, inventory, chat. They stay in
   * the recording, but must not be pressed a second time: that would double-drop the item and
   * double-switch the slot.
   */
  private boolean drivenKey(int code) {
    GameSettings gs = mc.gameSettings;
    if (gs == null) return false;
    try {
      if (code == gs.keyBindAttack.getKeyCode()
          || code == gs.keyBindUseItem.getKeyCode()
          || code == gs.keyBindForward.getKeyCode()
          || code == gs.keyBindBack.getKeyCode()
          || code == gs.keyBindLeft.getKeyCode()
          || code == gs.keyBindRight.getKeyCode()
          || code == gs.keyBindJump.getKeyCode()
          || code == gs.keyBindSneak.getKeyCode()
          || code == gs.keyBindSprint.getKeyCode()
          || code == gs.keyBindDrop.getKeyCode()
          || code == gs.keyBindPickBlock.getKeyCode()
          || code == gs.keyBindSwapHands.getKeyCode()
          || code == gs.keyBindInventory.getKeyCode()
          || code == gs.keyBindChat.getKeyCode()
          || code == gs.keyBindCommand.getKeyCode()) return true;
      if (gs.keyBindsHotbar != null)
        for (int i = 0; i < gs.keyBindsHotbar.length; i++)
          if (gs.keyBindsHotbar[i] != null && code == gs.keyBindsHotbar[i].getKeyCode())
            return true;
    } catch (Exception e) {
      return true;
    }
    return false;
  }
  /**
   * Key replay: set the state and remember that we pressed it ourselves, so it can be released on
   * stop.
   */
  void applyKeyEvents(Frame frame) {
    if (frame == null || frame.keyEvents == null || frame.keyEvents.length < 2) return;
    for (int i = 0; i + 1 < frame.keyEvents.length; i += 2) {
      int code = frame.keyEvents[i];
      boolean down = frame.keyEvents[i + 1] != 0;
      if (code <= 0 || systemKey(code) || ownKey(code) || drivenKey(code)) continue;
      try {
        KeyBinding.setKeyBindState(code, down);
        if (down) {
          KeyBinding.onTick(code);
          replayedKeys.add(Integer.valueOf(code));
        } else replayedKeys.remove(Integer.valueOf(code));
      } catch (Exception e) {
        MirrorDebug.log("INPUT", "key replay failed for code " + code + ": " + e);
      }
    }
  }

  /**
   * Removes playback's contribution: the key stays pressed only if the player holds it; presses
   * accumulated by playback are reset.
   */
  void releaseReplayedKeys() {
    if (replayedKeys.isEmpty()) return;
    if (mc.gameSettings != null)
      for (KeyBinding k : mc.gameSettings.keyBindings)
        if (k != null && replayedKeys.contains(Integer.valueOf(k.getKeyCode()))) drainPressQueue(k);
    for (Integer code : replayedKeys) setKeyState(code.intValue(), physicalDown(code.intValue()));
    replayedKeys.clear();
  }

  void applyHold(KeyBinding key, boolean down) {
    if (key != null) KeyBinding.setKeyBindState(key.getKeyCode(), down);
  }

  private void pulse(KeyBinding key, boolean down, boolean was) {
    if (key == null || !down || was) return;
    KeyBinding.onTick(key.getKeyCode());
  }

  /** Restores keys held by playback to their physical state and drains press buffers. */
  @Override
  public void reset() {
    releaseReplayedKeys();
    keyLog.clear();
    if (mc.gameSettings != null) {
      restorePhysical(mc.gameSettings.keyBindAttack);
      restorePhysical(mc.gameSettings.keyBindUseItem);
      if (mc.gameSettings.keyBindsHotbar != null)
        for (KeyBinding h : mc.gameSettings.keyBindsHotbar) drainPressQueue(h);
      drainPressQueue(mc.gameSettings.keyBindDrop);
      drainPressQueue(mc.gameSettings.keyBindSwapHands);
      drainPressQueue(mc.gameSettings.keyBindPickBlock);
      drainPressQueue(mc.gameSettings.keyBindInventory);
    }
    lastHotbar = -1;
    pendingActions.clear();
    replayPicks.clear();
  }
  /**
   * Resets the presses accumulated inside KeyBinding: each isPressed() consumes one. The limiter
   * prevents an endless loop if the counter does not decrease.
   */
  void drainPressQueue(KeyBinding key) {
    if (key == null) return;
    for (int guard = 0; guard < 64 && key.isPressed(); guard++) {}
  }

  void restorePhysical(KeyBinding key) {
    if (key == null) return;
    drainPressQueue(key);
    KeyBinding.setKeyBindState(key.getKeyCode(), physicalDown(key.getKeyCode()));
  }

  /** Vanilla KeyBinding stores a mouse button as a negative key code: button - 100. */
  static final int MOUSE_CODE_OFFSET = 100;

  private boolean physicalDown(int code) {
    try {
      if (code >= 0) return Keyboard.isKeyDown(code);
      int button = code + MOUSE_CODE_OFFSET;
      return button >= 0 && button < Mouse.getButtonCount() && Mouse.isButtonDown(button);
    } catch (Exception e) {
      return false;
    }
  }

  void handleGuiHotkey(GuiScreenEvent.KeyboardInputEvent.Pre event) {
    if (!Keyboard.getEventKeyState()) return;
    GuiScreen screen = event.getGui();
    if (screen == null) return;
    String name = screen.getClass().getName();
    if (name.startsWith("com.mirror.recorder.gui.")) return;
    if (typingScreen(screen)) return;
    MirrorRecorder mod = MirrorRecorder.getInstance();
    if (mod == null) return;
    int code = Keyboard.getEventKey();
    if (code <= 0) return;
    BaritoneReturnController returner = mod.getReturnController();
    if (boundTo(mod.getKeyStop(), code)) {
      if (!(manager.isBusy()
          || (returner != null && (returner.isBusy() || returner.isRecovering())))) return;
      MirrorDebug.log("INPUT", "stop hotkey with open screen " + name);
      SoundFx.stop();
      handler.resetAll();
      if (returner != null) returner.cancel();
      manager.stopAll();
      state.sendMsg(Lang.t("msg.stopped"));
      event.setCanceled(true);
      return;
    }
    if (boundTo(mod.getKeyGui(), code)) {
      mc.displayGuiScreen(new GuiMirrorMain(manager, config));
      event.setCanceled(true);
      return;
    }
    // Stop on WASD with the inventory/chest/settings open: vanilla isKeyDown() is always false
    // here, so the press itself is caught. Typing into text (chat, sign, creative search) is
    // filtered out by typingScreen above.
    if (handler.hasPlaybackOperation()
        && !manager.isPaused()
        && config.isStopOnMove()
        && handler.stops.movementKey(code)
        && handler.stops.isRealMovement())
      handler.stops.stopByManualMovement(
          "key "
              + code
              + " with open screen "
              + name
              + ", frame "
              + manager.getCurrentFrameIndex());
  }

  private static boolean boundTo(KeyBinding key, int code) {
    return key != null && key.getKeyCode() != 0 && key.getKeyCode() == code;
  }
  /** Screens where the key belongs to text or rebinding, not to the mod. */
  private boolean typingScreen(GuiScreen screen) {
    if (screen instanceof GuiChat || screen instanceof GuiScreenBook) return true;
    if (screen instanceof GuiEditSign || screen instanceof GuiControls) return true;
    return hasFocusedTextField(screen);
  }

  int hotbarKeyIndex(GameSettings gs, int key) {
    if (gs.keyBindsHotbar == null) return -1;
    for (int i = 0; i < gs.keyBindsHotbar.length && i < 9; i++)
      if (gs.keyBindsHotbar[i].isActiveAndMatches(key)) return i;
    return -1;
  }
  /**
   * Text field focus decides that the key is typing (like the vanilla anvil: textboxKeyTyped goes
   * first).
   */
  boolean hasFocusedTextField(GuiScreen screen) {
    try {
      return TextEditors.focused(screen) != null;
    } catch (ReflectiveOperationException e) {
      MirrorDebug.log("GUI", "Editor focus lookup failed: " + e);
      return false;
    }
  }

  void handleKeyInput(InputEvent.KeyInputEvent event) {
    // Record eligible raw key events when enabled: keys from other mods may be part of an action.
    recordRawKey();
    // A quick tap of an instant-action key is not visible in the end-of-tick mask (the key is
    // already
    // released): the press is caught via the event.
    if (Keyboard.getEventKeyState()) recordInstantPress(Keyboard.getEventKey());
    MirrorRecorder mod = MirrorRecorder.getInstance();
    if (mod == null) return;
    if (mod.getKeyRecord().isPressed()) {
      onRecordHotkey(mod);
    }
    if (mod.getKeyPlay().isPressed()) {
      int slot = config.getActiveSlot();
      BaritoneReturnController r = mod.getReturnController();
      if (r != null) {
        r.request(slot, false);
      }
    }
    if (mod.getKeyLoop().isPressed()) {
      int slot = config.getActiveSlot();
      BaritoneReturnController r = mod.getReturnController();
      if (r != null) {
        r.request(slot, true);
      }
    }
    if (mod.getKeyStop().isPressed()) {
      onStopHotkey(mod);
    }
    if (mod.getKeyGui().isPressed()) mc.displayGuiScreen(new GuiMirrorMain(manager, config));
  }

  /** The raw key of the event goes into the tick's key log (except system and mod keys). */
  private void recordRawKey() {
    if (manager.isRecording() && config.isRecordAllKeys()) {
      try {
        int code = Keyboard.getEventKey();
        boolean down = Keyboard.getEventKeyState();
        if (code > 0 && !systemKey(code) && !ownKey(code)) {
          if (keyLog.size() < Frame.MAX_KEY_EVENTS) keyLog.add(new int[] {code, down ? 1 : 0});
          else
            MirrorDebug.log(
                "INPUT",
                "key event cap "
                    + Frame.MAX_KEY_EVENTS
                    + " reached in one tick, dropped code "
                    + code);
        }
      } catch (Exception e) {
      }
    }
  }

  /**
   * Record hotkey: countdown and start into the active slot unless a Baritone return is in
   * progress.
   */
  private void onRecordHotkey(MirrorRecorder mod) {
    if (mod.getReturnController() != null && mod.getReturnController().isBusy())
      state.sendMsg(Lang.t("msg.stop_the_baritone_return_first"));
    else {
      int slot = config.getActiveSlot();
      if (manager.scheduleRecording(slot))
        state.sendMsg(Lang.t("msg.recording_in") + config.getStartDelay() + Lang.t("msg.sec"));
      else state.sendMsg(Lang.t("msg.slot") + slot + Lang.t("msg.is_occupied_or_another_action"));
    }
  }

  /** Stop hotkey: sound, reset of the parts, return cancellation and stopping everything. */
  private void onStopHotkey(MirrorRecorder mod) {
    SoundFx.stop();
    handler.resetAll();
    if (mod.getReturnController() != null) mod.getReturnController().cancel();
    manager.stopAll();
    state.sendMsg(Lang.t("msg.stopped"));
  }
  /**
   * Press of an instant action without a screen (keyboard or mouse): a mask bit for the old format
   * and a separate action list entry, so two presses in a row replay as two actions.
   */
  void recordInstantPress(int code) {
    if (!manager.isRecording() || mc.currentScreen != null || mc.gameSettings == null) return;
    GameSettings g = mc.gameSettings;
    int action = 0;
    if (g.keyBindInventory.isActiveAndMatches(code)) {
      pendingKeyMask |= Frame.K_INVENTORY;
      action = Frame.ACT_INVENTORY;
    } else if (g.keyBindDrop.isActiveAndMatches(code)) {
      boolean all = GuiScreen.isCtrlKeyDown();
      pendingKeyMask |= Frame.K_DROP;
      pendingDropAll = pendingDropAll || all;
      action = all ? Frame.ACT_DROP_ALL : Frame.ACT_DROP;
    } else if (g.keyBindSwapHands.isActiveAndMatches(code)) {
      pendingKeyMask |= Frame.K_SWAP;
      action = Frame.ACT_SWAP;
    } else if (g.keyBindPickBlock.isActiveAndMatches(code)) {
      pendingKeyMask |= Frame.K_PICK;
      action = PickBlockReplayPolicy.recordedAction(GuiScreen.isCtrlKeyDown());
    }
    if (action == 0) return;
    if (pendingActions.size() < Frame.MAX_ACTIONS) pendingActions.add(Integer.valueOf(action));
    else MirrorDebug.log("INPUT", "action cap " + Frame.MAX_ACTIONS + " reached in one tick");
  }

  int[] consumeActions() {
    int[] out = new int[pendingActions.size()];
    for (int i = 0; i < out.length; i++) out[i] = pendingActions.get(i).intValue();
    pendingActions.clear();
    return out;
  }
  /** Returns and clears the tick's instant-key mask. */
  int consumeKeyMask() {
    int m = pendingKeyMask;
    pendingKeyMask = 0;
    return m;
  }
  /** Returns and clears the whole-stack drop flag (Ctrl+Q) for this tick. */
  boolean consumeDropAll() {
    boolean d = pendingDropAll;
    pendingDropAll = false;
    return d;
  }
  /** Input from frozen ticks is not merged into the next frame. */
  void clearPending() {
    keyLog.clear();
    pendingKeyMask = 0;
    pendingActions.clear();
    pendingDropAll = false;
    replayPicks.clear();
  }
  /**
   * Pause start: keys held by playback return to the physical state (the list is kept for
   * resuming).
   */
  void restoreReplayedToPhysical() {
    for (Integer code : replayedKeys) setKeyState(code.intValue(), physicalDown(code.intValue()));
  }
  /** Pause end: keys held by playback are pressed again. */
  void reapplyReplayedKeys() {
    for (Integer code : replayedKeys) setKeyState(code.intValue(), true);
  }
}
