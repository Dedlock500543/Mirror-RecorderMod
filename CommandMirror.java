package com.mirror.recorder.command;

import com.mirror.recorder.MirrorRecorder;
import com.mirror.recorder.config.RecorderConfig;
import com.mirror.recorder.gui.GuiMirrorMain;
import com.mirror.recorder.gui.Lang;
import com.mirror.recorder.handler.BaritoneReturnController;
import com.mirror.recorder.handler.TransientChat;
import com.mirror.recorder.manager.RecorderManager;
import com.mirror.recorder.storage.StorageManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * The /mirror command: record, stop, play, loop, pause, limit, delete, list, name, marker, gui (mod
 * window).
 */
public class CommandMirror extends CommandBase {
  private static final String PREFIX = "§8[§bMirror§8]§r ";
  private static final int MIN_SLOT = RecorderManager.MIN_SLOT,
      MAX_SLOT = RecorderManager.MAX_SLOT,
      TPS = RecorderManager.TICKS_PER_SECOND;
  private final RecorderManager manager;
  private final RecorderConfig config;
  private final BaritoneReturnController returner;
  private volatile boolean guiOpenPending = false;

  public CommandMirror(RecorderManager m, RecorderConfig c, BaritoneReturnController r) {
    manager = m;
    config = c;
    returner = r;
  }

  @Override
  @Nonnull
  public String getName() {
    return "mirror";
  }

  @Override
  @Nonnull
  public String getUsage(@Nonnull ICommandSender s) {
    return "/mirror <record|play|loop|pause|limit|stop|delete|list|name|marker|gui>";
  }

  @Override
  public int getRequiredPermissionLevel() {
    return 0;
  }

  @Override
  public boolean checkPermission(@Nonnull MinecraftServer srv, @Nonnull ICommandSender s) {
    return true;
  }

  @Override
  public void execute(
      @Nonnull MinecraftServer srv, @Nonnull ICommandSender sender, @Nonnull String[] args)
      throws CommandException {
    MirrorRecorder mod = MirrorRecorder.getInstance();
    // Only commands that stop recording or open a pausing mod screen need an immediate flush.
    // Other commands stay in their normal tick rather than adding an extra movement frame.
    if (args.length > 0
        && (args[0].equalsIgnoreCase("stop") || args[0].equalsIgnoreCase("gui"))
        && mod != null
        && mod.getRecordingHandler() != null) mod.getRecordingHandler().captureBeforeModCommand();
    if (args.length == 0) {
      help(sender);
      return;
    }
    switch (args[0].toLowerCase(Locale.ROOT)) {
      case "record":
        doRecord(sender, args);
        break;
      case "play":
        doPlay(sender, args);
        break;
      case "loop":
        doLoop(sender, args);
        break;
      case "limit":
        doLimit(sender, args);
        break;
      case "pause":
        doPause(sender);
        break;
      case "stop":
        doStop(sender);
        break;
      case "delete":
        doDelete(sender, args);
        break;
      case "list":
        doList(sender, args);
        break;
      case "name":
        doMeta(sender, args, true);
        break;
      case "marker":
        doMeta(sender, args, false);
        break;
      case "gui":
        doGui(sender);
        break;
      default:
        err(sender, Lang.t("cmd.unknown_command_type_mirror_for"));
    }
  }

  private void help(ICommandSender s) {
    raw(s, "§8§m──────────── §r §b§lMirror Recorder §8§m────────────");
    raw(s, "§b" + Lang.t("cmd.actions"));
    line(s, "record <" + Lang.t("cmd.slot") + ">", Lang.t("cmd.start_recording"));
    line(s, "play <" + Lang.t("cmd.slot") + ">", Lang.t("cmd.play_once"));
    line(s, "loop <" + Lang.t("cmd.slot") + ">", Lang.t("cmd.play_in_a_loop"));
    line(
        s,
        "limit <" + Lang.t("cmd.slot") + "> <" + Lang.t("cmd.repeats") + ">",
        Lang.t("cmd.loop_limit_0_infinite"));
    line(s, "stop", Lang.t("cmd.stop_the_current_action_also"));
    line(s, "pause", Lang.t("cmd.pause_and_resume_playback"));
    raw(s, "§b" + Lang.t("cmd.slots"));
    line(s, "list [" + Lang.t("cmd.page") + "]", Lang.t("cmd.show_slots_1_100"));
    line(s, "delete <" + Lang.t("cmd.slot") + ">", Lang.t("cmd.delete_the_recording"));
    line(
        s,
        "name <" + Lang.t("cmd.slot") + "> [" + Lang.t("cmd.text") + "]",
        Lang.t("cmd.recording_name"));
    line(
        s,
        "marker <" + Lang.t("cmd.slot") + "> [" + Lang.t("cmd.text") + "]",
        Lang.t("cmd.recording_description"));
    line(s, "gui", Lang.t("cmd.open_the_interface"));
    raw(s, Lang.t("cmd.the_slot_number_is_always"));
  }

  private void line(ICommandSender s, String cmd, String desc) {
    raw(s, "§8 • §f/mirror " + cmd + " §8— §7" + desc);
  }

  private int requiredSlot(ICommandSender s, String[] a) {
    if (a.length < 2) {
      chooseSlot(s);
      return -1;
    }
    return parseSlot(s, a[1]);
  }

  private void chooseSlot(ICommandSender s) {
    warn(s, Lang.t("cmd.specify_a_slot_from_1"));
  }

  private boolean exactArgs(ICommandSender s, String[] a, int count, String usage) {
    if (a.length == count) return true;
    warn(s, Lang.t("cmd.usage") + usage);
    return false;
  }

  private void doRecord(ICommandSender s, String[] a) {
    int slot = requiredSlot(s, a);
    if (slot < 1) return;
    if (!exactArgs(s, a, 2, "/mirror record <" + Lang.t("cmd.slot") + ">")) return;
    if (returner.isRecovering()) returner.cancel();
    if (returner.isBusy()) {
      warn(s, Lang.t("cmd.stop_the_baritone_return_first"));
      return;
    }
    if (manager.isBusy()) {
      busyMessage(s);
      return;
    }
    if (manager.getSlotFrameCount(slot) > 0) {
      warn(s, Lang.t("cmd.slot_2") + slot + Lang.t("cmd.is_occupied_delete_the_recording"));
      return;
    }
    if (!selectTarget(s, slot)) return;
    if (manager.scheduleRecording(slot))
      ok(s, Lang.t("cmd.recording_in") + config.getStartDelay() + Lang.t("cmd.slot_3") + slot);
    else err(s, Lang.t("cmd.could_not_start_recording"));
  }

  private void doPlay(ICommandSender s, String[] a) {
    int slot = requiredSlot(s, a);
    if (slot < 1) return;
    if (!exactArgs(s, a, 2, "/mirror play <" + Lang.t("cmd.slot") + ">")) return;
    if (returner.isRecovering()) returner.cancel();
    if (manager.isBusy() || returner.isBusy()) {
      busyMessage(s);
      return;
    }
    if (manager.getSlotFrameCount(slot) == 0) {
      warn(s, Lang.t("cmd.slot_2") + slot + Lang.t("cmd.is_empty_or_corrupted"));
      return;
    }
    if (!selectTarget(s, slot)) return;
    returner.request(slot, false);
  }

  private void doLoop(ICommandSender s, String[] a) {
    int slot = requiredSlot(s, a);
    if (slot < 1) return;
    if (!exactArgs(s, a, 2, "/mirror loop <" + Lang.t("cmd.slot") + ">")) return;
    if (returner.isRecovering()) returner.cancel();
    if (manager.isBusy() || returner.isBusy()) {
      busyMessage(s);
      return;
    }
    if (manager.getSlotFrameCount(slot) == 0) {
      warn(s, Lang.t("cmd.slot_2") + slot + Lang.t("cmd.is_empty_or_corrupted"));
      return;
    }
    if (!selectTarget(s, slot)) return;
    returner.request(slot, true);
  }

  private void doLimit(ICommandSender s, String[] a) {
    if (returner.isBusy()) {
      warn(s, Lang.t("cmd.the_limit_cannot_be_changed"));
      return;
    }
    if (!exactArgs(s, a, 3, "/mirror limit <" + Lang.t("cmd.slot_1_100") + "> <0|2-1000>")) return;
    if (manager.isBusy()) {
      warn(s, Lang.t("cmd.change_the_limit_after_stopping"));
      return;
    }
    int slot, v;
    try {
      slot = Integer.parseInt(a[1]);
      v = Integer.parseInt(a[2]);
    } catch (NumberFormatException e) {
      warn(s, Lang.t("cmd.format_mirror_limit_slot_1"));
      return;
    }
    if (slot < RecorderManager.MIN_SLOT || slot > RecorderManager.MAX_SLOT) {
      warn(s, Lang.t("cmd.slot_must_be_between_1"));
      return;
    }
    if (v < 0 || v > 1000 || v == 1) {
      warn(s, Lang.t("cmd.limit_0_or_2_1000"));
      return;
    }
    if (manager.getSlotFrameCount(slot) <= 0) {
      warn(s, Lang.t("cmd.slot_4") + slot + Lang.t("cmd.has_no_recording"));
      return;
    }
    if (!manager.setSlotLoopLimit(slot, v)) {
      err(s, Lang.t("cmd.could_not_save_the_limit") + slot + ".");
      return;
    }
    config.save();
    ok(
        s,
        Lang.t("cmd.slot_5")
            + slot
            + Lang.t("cmd.loop_limit")
            + (v == 0 ? "∞" : String.valueOf(v)));
  }
  /**
   * Makes the given slot active before starting; current settings are kept if it is already active
   * or if the slot is imported.
   */
  private boolean selectTarget(ICommandSender s, int slot) {
    if (config.getActiveSlot() == slot) return true;
    if (!manager.selectSlot(slot)) {
      err(s, Lang.t("cmd.could_not_select_slot") + slot + ".");
      return false;
    }
    config.save();
    return true;
  }

  private void doStop(ICommandSender s) {
    if (returner.isRecovering() && !manager.isBusy()) {
      returner.cancel();
      return;
    }
    if (!manager.isBusy() && !returner.isBusy() && !returner.isRecovering()) {
      warn(s, Lang.t("cmd.nothing_is_running"));
      return;
    }
    returner.cancel();
    manager.stopAll();
    MirrorRecorder mod = MirrorRecorder.getInstance();
    if (mod != null && mod.getRecordingHandler() != null)
      mod.getRecordingHandler().resetAfterCommandStop();
    send(s, "§e" + Lang.t("cmd.stopped"));
  }
  /**
   * Playback pause: the frame stays put, resuming continues from the same frame, not from the
   * start.
   */
  private void doPause(ICommandSender s) {
    if (!manager.isPlaying()) {
      warn(s, Lang.t("cmd.nothing_is_playing"));
      return;
    }
    boolean pause = !manager.isPaused();
    manager.setPlaybackPaused(pause);
    if (pause)
      warn(
          s,
          Lang.t("cmd.paused_frame")
              + Math.min(
                  manager.getCurrentFrameIndex() + 1,
                  manager.getSlotFrameCount(manager.getActiveOperationSlot()))
              + Lang.t("cmd.resume_mirror_pause"));
    else ok(s, Lang.t("cmd.resuming_from_the_same_frame"));
  }

  private void doDelete(ICommandSender s, String[] a) {
    int slot = requiredSlot(s, a);
    if (slot < 1) return;
    if (!exactArgs(s, a, 2, "/mirror delete <" + Lang.t("cmd.slot") + ">")) return;
    if (returner.isSlotBusy(slot)) {
      warn(s, Lang.t("cmd.slot_2") + slot + Lang.t("cmd.is_busy_with_a_baritone"));
      return;
    }
    if (returner.isRecoveringSlot(slot)) {
      warn(s, Lang.t("cmd.slot_2") + slot + Lang.t("cmd.is_waiting_for_you_at"));
      return;
    }
    if (manager.hasPendingAction() && manager.getPendingSlot() == slot) {
      warn(s, Lang.t("cmd.slot_2") + slot + Lang.t("cmd.is_scheduled_to_start_run"));
      return;
    }
    if (manager.isPlaying() && manager.getActiveOperationSlot() == slot) {
      warn(s, Lang.t("cmd.stop_playback_of_slot") + slot + ".");
      return;
    }
    if (manager.isRecording() && manager.getActiveOperationSlot() == slot) {
      warn(s, Lang.t("cmd.stop_recording_of_slot") + slot + ".");
      return;
    }
    if (warnIfSaving(s, slot)) return;
    if (manager.getSlotFrameCount(slot) == 0) {
      warn(s, Lang.t("cmd.slot_2") + slot + Lang.t("cmd.is_already_empty"));
      return;
    }
    int del = manager.deleteSlotStatus(slot);
    if (del != 0)
      ok(
          s,
          Lang.t("cmd.slot_2")
              + slot
              + Lang.t("cmd.deleted")
              + (del == StorageManager.DELETE_NO_COPY ? Lang.t("cmd.no_trash_copy_the_file") : ""));
    else
      err(s, Lang.t("cmd.could_not_delete_slot") + slot + Lang.t("cmd.the_file_was_not_changed"));
  }

  private void doList(ICommandSender s, String[] a) {
    int page = (Math.max(1, config.getActiveSlot()) - 1) / 10 + 1;
    if (a.length > 2) {
      warn(s, Lang.t("cmd.usage_mirror_list_1_10"));
      return;
    }
    if (a.length == 2)
      try {
        page = Integer.parseInt(a[1]);
      } catch (NumberFormatException e) {
        warn(s, Lang.t("cmd.page_1_10"));
        return;
      }
    if (page < 1 || page > 10) {
      warn(s, Lang.t("cmd.page_1_10"));
      return;
    }
    int used = 0;
    for (int i = MIN_SLOT; i <= MAX_SLOT; i++) if (manager.getSlotFrameCount(i) > 0) used++;
    int from = (page - 1) * 10 + 1, to = Math.min(MAX_SLOT, from + 9);
    raw(
        s,
        "§8§m──── §r §b§l"
            + Lang.t("cmd.slots_2")
            + " "
            + from
            + "-"
            + to
            + " §7"
            + used
            + "/100 §8· "
            + Lang.t("cmd.page_2")
            + " "
            + page
            + "/10");
    for (int i = from; i <= to; i++) {
      int frames = manager.getSlotFrameCount(i);
      boolean active = manager.isSlotBusy(i) || returner.isSlotBusy(i);
      String name = manager.getSlotName(i);
      if (frames == 0) raw(s, "§8○  " + i + "  §7" + Lang.t("cmd.empty"));
      else {
        if (name == null || name.isEmpty()) name = Lang.t("cmd.recording") + i;
        String mark = active ? "§e▶" : "§a●";
        raw(
            s,
            mark
                + "  §f"
                + i
                + "  "
                + name
                + "  §8· §7"
                + fmtDur(frames)
                + "  §8· "
                + frames
                + Lang.t("cmd.f"));
      }
    }
  }

  private String fmtDur(int frames) {
    int sec = Math.max(0, frames / TPS);
    return sec >= 60
        ? (sec / 60) + Lang.t("cmd.m") + (sec % 60) + Lang.t("cmd.s")
        : sec + Lang.t("cmd.s");
  }

  private void doMeta(ICommandSender s, String[] a, boolean isName) {
    int slot = requiredSlot(s, a);
    if (slot < 1) return;
    String kind = isName ? Lang.t("cmd.name") : Lang.t("cmd.description");
    if (manager.getSlotFrameCount(slot) == 0) {
      warn(s, Lang.t("cmd.slot_2") + slot + Lang.t("cmd.is_empty_record_something_first"));
      return;
    }
    if (manager.isSlotBusy(slot) || returner.isSlotBusy(slot)) {
      warn(s, Lang.t("cmd.cannot_edit_the_data_of") + slot + ".");
      return;
    }
    if (a.length < 3) {
      String cur = isName ? manager.getSlotName(slot) : manager.getSlotDescription(slot);
      info(
          s,
          kind
              + " "
              + Lang.t("cmd.of_slot")
              + " "
              + slot
              + ": "
              + ((cur == null || cur.isEmpty()) ? "§7" + Lang.t("cmd.not_set") : "§f" + cur));
      return;
    }
    if (warnIfSaving(s, slot)) return;
    String text = sanitize(join(a, 2));
    int max = isName ? 40 : 120;
    if (text.length() > max) {
      warn(s, kind + Lang.t("cmd.is_too_long_maximum") + max + Lang.t("cmd.characters"));
      return;
    }
    boolean saved =
        isName ? manager.setSlotName(slot, text) : manager.setSlotDescription(slot, text);
    if (saved)
      ok(
          s,
          kind
              + " "
              + Lang.t("cmd.of_slot")
              + " "
              + slot
              + " §a→ §f"
              + (text.isEmpty() ? Lang.t("cmd.not_set") : text));
    else err(s, Lang.t("cmd.could_not_save_the_data") + slot + ".");
  }

  private void busyMessage(ICommandSender s) {
    if (returner.isBusy()) warn(s, Lang.t("cmd.baritone_is_already_returning_or"));
    else if (manager.hasPendingAction()) warn(s, Lang.t("cmd.another_action_is_already_scheduled"));
    else if (manager.isRecording()) warn(s, Lang.t("cmd.stop_the_current_recording_first"));
    else if (manager.isPlaying()) warn(s, Lang.t("cmd.stop_the_current_playback_first"));
    else warn(s, Lang.t("cmd.another_action_is_currently_unavailable"));
  }

  private void doGui(ICommandSender s) {
    if (FMLCommonHandler.instance().getSide() != Side.CLIENT) {
      err(s, Lang.t("cmd.the_interface_is_only_available"));
      return;
    }
    Minecraft mc = Minecraft.getMinecraft();
    if (mc.player == null) {
      err(s, Lang.t("cmd.enter_a_world_first"));
      return;
    }
    guiOpenPending = true;
  }

  @SubscribeEvent
  public void onClientTick(TickEvent.ClientTickEvent e) {
    if (!guiOpenPending || e.phase != TickEvent.Phase.END) return;
    Minecraft mc = Minecraft.getMinecraft();
    if (mc.player == null || mc.world == null) {
      guiOpenPending = false;
      return;
    }
    if (mc.currentScreen instanceof GuiChat) return;
    guiOpenPending = false;
    mc.displayGuiScreen(new GuiMirrorMain(manager, config));
  }

  private int parseSlot(ICommandSender s, String arg) {
    try {
      int v = Integer.parseInt(arg);
      if (v < MIN_SLOT || v > MAX_SLOT) {
        chooseSlot(s);
        return -1;
      }
      return v;
    } catch (NumberFormatException e) {
      chooseSlot(s);
      return -1;
    }
  }

  /**
   * The background saver is still writing the slot files: reject the operation right away and say
   * why instead of waiting on the client thread.
   */
  private boolean warnIfSaving(ICommandSender s, int slot) {
    if (!manager.isSlotSaving(slot)) return false;
    warn(s, Lang.t("cmd.slot_6") + slot + Lang.t("cmd.is_still_being_saved_to"));
    return true;
  }

  private String sanitize(String value) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (ch == '§' || Character.isISOControl(ch)) continue;
      out.append(ch);
    }
    return out.toString().trim();
  }

  private static String join(String[] a, int from) {
    StringBuilder sb = new StringBuilder();
    for (int i = from; i < a.length; i++) {
      if (i > from) sb.append(' ');
      sb.append(a[i]);
    }
    return sb.toString();
  }

  private void raw(ICommandSender s, String msg) {
    TransientChat.show(s, msg);
  }

  private void send(ICommandSender s, String msg) {
    TransientChat.show(s, PREFIX + msg);
  }

  private void ok(ICommandSender s, String m) {
    send(s, "§a" + m);
  }

  private void info(ICommandSender s, String m) {
    send(s, "§b" + m);
  }

  private void warn(ICommandSender s, String m) {
    send(s, "§e" + m);
  }

  private void err(ICommandSender s, String m) {
    send(s, "§c" + m);
  }

  @Override
  @Nonnull
  public List<String> getTabCompletions(
      @Nonnull MinecraftServer srv,
      @Nonnull ICommandSender s,
      @Nonnull String[] args,
      BlockPos pos) {
    if (args.length == 1)
      return getListOfStringsMatchingLastWord(
          args,
          Arrays.asList(
              "record", "play", "loop", "pause", "limit", "stop", "delete", "list", "name",
              "marker", "gui"));
    if (args.length == 2 && "limit".equalsIgnoreCase(args[0]))
      return getListOfStringsMatchingLastWord(args, Arrays.asList("1", "2", "3", "4", "5"));
    if (args.length == 3 && "limit".equalsIgnoreCase(args[0]))
      return getListOfStringsMatchingLastWord(
          args, Arrays.asList("0", "2", "3", "5", "10", "25", "50", "100"));
    if (args.length == 2 && "list".equalsIgnoreCase(args[0]))
      return getListOfStringsMatchingLastWord(
          args, Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"));
    if (args.length == 2
        && Arrays.asList("record", "play", "loop", "delete", "name", "marker")
            .contains(args[0].toLowerCase(Locale.ROOT))) {
      List<String> slots = new ArrayList<String>();
      for (int i = MIN_SLOT; i <= MAX_SLOT; i++) slots.add(String.valueOf(i));
      return getListOfStringsMatchingLastWord(args, slots);
    }
    return Collections.emptyList();
  }
}
