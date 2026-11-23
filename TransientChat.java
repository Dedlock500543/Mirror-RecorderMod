package com.mirror.recorder.handler;

import com.mirror.recorder.MirrorRecorder;
import com.mirror.recorder.config.RecorderConfig;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Central emission and lifetime tracking for the mod's own notices only. */
public final class TransientChat {
  private static final class Entry {
    final int id;
    final long createdNs;

    Entry(int value, long time) {
      id = value;
      createdNs = time;
    }
  }

  private static final List<Entry> entries = new ArrayList<Entry>();
  private static int nextId = 730000;

  private static RecorderConfig config() {
    MirrorRecorder mod = MirrorRecorder.getInstance();
    return mod == null ? null : mod.getConfig();
  }

  public static boolean messagesEnabled() {
    RecorderConfig config = config();
    return config == null || config.isSendModMessages();
  }

  /** Command feedback is subject to the same mute gate as unsolicited notices. */
  public static void show(ICommandSender sender, String text) {
    if (!messagesEnabled() || sender == null || text == null) return;
    if (sender == Minecraft.getMinecraft().player) show(text);
    else sender.sendMessage(new TextComponentString(text));
  }

  public static synchronized void show(String text) {
    if (!messagesEnabled()) return;
    Minecraft mc = Minecraft.getMinecraft();
    if (mc.ingameGUI == null || text == null) return;
    int id = nextId++;
    if (nextId > 739999) nextId = 730000;
    // Reuse of an ID replaces that line. An old timer must never delete its replacement.
    Iterator<Entry> old = entries.iterator();
    while (old.hasNext()) if (old.next().id == id) old.remove();
    mc.ingameGUI
        .getChatGUI()
        .printChatMessageWithOptionalDeletion(new TextComponentString(text), id);
    entries.add(new Entry(id, System.nanoTime()));
  }

  @SubscribeEvent
  public void onTick(TickEvent.ClientTickEvent e) {
    if (e.phase != TickEvent.Phase.END) return;
    Minecraft mc = Minecraft.getMinecraft();
    RecorderConfig config = config();
    boolean enabled = config == null || config.isSendModMessages();
    boolean autoDelete = config == null || config.isAutoDeleteModMessages();
    int seconds = config == null ? 5 : config.getModMessageLifetimeSeconds();
    long now = System.nanoTime();
    synchronized (TransientChat.class) {
      Iterator<Entry> it = entries.iterator();
      while (it.hasNext()) {
        Entry entry = it.next();
        if (ModMessagePolicy.shouldDelete(enabled, autoDelete, seconds, entry.createdNs, now)) {
          if (mc.ingameGUI != null) mc.ingameGUI.getChatGUI().deleteChatLine(entry.id);
          it.remove();
        }
      }
    }
  }
}
