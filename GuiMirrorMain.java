package com.mirror.recorder.gui;

import com.mirror.recorder.MirrorRecorder;
import com.mirror.recorder.config.RecorderConfig;
import com.mirror.recorder.handler.BaritoneReturnController;
import com.mirror.recorder.handler.TransientChat;
import com.mirror.recorder.manager.RecorderManager;
import com.mirror.recorder.storage.StorageManager;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Main screen: the recording list on the left; the selected slot's card and a vertical action block
 * on the right. Only UI: recording and playback are delegated to RecorderManager and
 * BaritoneReturnController.
 */
public class GuiMirrorMain extends ModScreen {

  private static final int BTN_RECORD = 101,
      BTN_PLAY = 102,
      BTN_LOOP = 103,
      BTN_STOP = 104,
      BTN_SETTINGS = 106,
      BTN_CLOSE = 107;
  private static final int MAX_SLOTS = 100,
      VISIBLE_SLOTS = 10,
      TPS = RecorderManager.TICKS_PER_SECOND;
  private static final String PREFIX = "\u00a78[\u00a7bMirror\u00a78]\u00a7r ";
  private final RecorderManager manager;
  private final RecorderConfig config;
  private int selectedSlot = 1, scrollOffset = 0;
  private RecordingFolders folders;
  private Set<String> collapsed = new HashSet<String>();
  private boolean unfiledCollapsed = false, foldersInitialized = false;

  private static final class ListRow {
    final String folder;
    final int slot;

    ListRow(String folder, int slot) {
      this.folder = folder;
      this.slot = slot;
    }
  }

  private int visibleRows() {
    return folders == null ? VISIBLE_SLOTS : 13;
  }

  private List<ListRow> listRows() {
    List<ListRow> rows = new ArrayList<ListRow>();
    if (folders == null) {
      for (int slot = 1; slot <= MAX_SLOTS; slot++) rows.add(new ListRow(null, slot));
      return rows;
    }
    for (String id : folders.ids()) {
      rows.add(new ListRow(id, 0));
      if (!collapsed.contains(id))
        for (int slot = 1; slot <= MAX_SLOTS; slot++)
          if (id.equals(folders.folderOf(slot))) rows.add(new ListRow(id, slot));
    }
    rows.add(new ListRow(null, 0));
    if (!unfiledCollapsed)
      for (int slot = 1; slot <= MAX_SLOTS; slot++)
        if (folders.folderOf(slot) == null) rows.add(new ListRow(null, slot));
    return rows;
  }

  RecordingFolders folders() {
    return folders;
  }

  void folderChanged() {
    int max = Math.max(0, listRows().size() - visibleRows());
    scrollOffset = Math.min(scrollOffset, max);
    visScroll = scrollOffset;
    scrollFrom = visScroll;
    showSelected();
    initGui();
  }

  void openFolderMove(int slot) {
    mc.displayGuiScreen(new GuiMirrorFolderMove(this, slot));
  }

  private void showSelected() {
    if (folders == null) return;
    String id = folders.folderOf(selectedSlot);
    if (id == null) unfiledCollapsed = false;
    else collapsed.remove(id);
    List<ListRow> rows = listRows();
    for (int i = 0; i < rows.size(); i++)
      if (rows.get(i).slot == selectedSlot) {
        if (i < scrollOffset) setScroll(i);
        else if (i >= scrollOffset + visibleRows()) setScroll(i - visibleRows() + 1);
        return;
      }
  }

  private float visScroll = 0f, scrollFrom = 0f;
  private long scrollAt = 0L;
  // initGui also runs on selection and resize; only construction starts the opening cascade.
  private final long openAnimAt = UiTheme.nowMs();
  private long infoAnimAt = openAnimAt;
  private final float[] hovA = new float[MAX_SLOTS + 2], hovFrom = new float[MAX_SLOTS + 2];
  private final long[] hovAt = new long[MAX_SLOTS + 2];
  private final boolean[] hovT = new boolean[MAX_SLOTS + 2];
  private int winX,
      winY,
      winW,
      winH,
      headH,
      listX,
      listRight,
      listTop,
      listBottom,
      rowH,
      infoX,
      infoRight,
      infoY,
      infoH,
      actionsY;
  private StyledButton bRecord, bPlay, bLoop, bStop, bSettings, bClose;
  // Drawn inside the selected card so it shares the card fade, not the action cascade.
  private StyledButton bEdit;

  public GuiMirrorMain(RecorderManager m, RecorderConfig c) {
    manager = m;
    config = c;
    selectedSlot = Math.max(1, Math.min(MAX_SLOTS, c.getActiveSlot()));
    scrollOffset = ((selectedSlot - 1) / VISIBLE_SLOTS) * VISIBLE_SLOTS;
  }

  private void layout() {
    winW = Math.min(width - 14, 564);
    winH = Math.min(height - 14, 362);
    winX = (width - winW) / 2;
    winY = (height - winH) / 2;
    headH = Math.min(38, Math.max(24, winH / 9));
    int pad = 12, gap = 13;
    listX = winX + pad;
    int listW = Math.max(150, (int) ((winW - pad * 2 - gap) * 0.44f));
    listRight = listX + listW;
    listTop = winY + headH + 26;
    listBottom = winY + winH - 13;
    rowH = Math.max(11, (listBottom - listTop) / visibleRows());
    infoX = listRight + gap;
    infoRight = winX + winW - pad;
    infoY = listTop;
  }

  private StyledButton add(StyledButton b) {
    b.appear(openAnimAt + buttonList.size() * UiTheme.STAGGER_MS);
    buttonList.add(b);
    return b;
  }

  @Override
  public void initGui() {
    usedSlotsCache = -1;
    if (!foldersInitialized && mc != null && mc.mcDataDir != null) {
      folders = new RecordingFolders(new File(mc.mcDataDir, "mirror_recorder/folders.properties"));
      foldersInitialized = true;
      if (collapsed == null) collapsed = new HashSet<String>();
      showSelected();
    }
    buttonList.clear();
    layout();
    int colW = infoRight - infoX, colRoom = Math.max(0, listBottom - infoY);
    int mainGap = colRoom < 178 ? 2 : 4,
        secH = colRoom < 178 ? 13 : 17,
        secGap = colRoom < 178 ? 3 : 4;
    int secBlock = secH * 2 + secGap;
    int mainH = Math.max(10, Math.min(26, (colRoom - 46 - mainGap * 3 - 6 - secBlock) / 4));
    int blockH = mainH * 4 + mainGap * 3 + 6 + secBlock;
    // Column below the minimum: the card is sacrificed, but the buttons must stay inside the
    // window.
    if (blockH > colRoom) {
      mainH = Math.max(9, (colRoom - mainGap * 3 - 6 - secBlock) / 4);
      blockH = mainH * 4 + mainGap * 3 + 6 + secBlock;
    }
    actionsY = Math.max(infoY, listBottom - blockH);
    infoH = Math.max(0, actionsY - 16 - infoY);
    int by = actionsY;
    bRecord =
        add(
            new StyledButton(BTN_RECORD, infoX, by, colW, mainH, Lang.t("main.record"))
                .primary()
                .accent(UiTheme.GREEN)
                .icon("\u25cf")
                .sub(Lang.t("main.create_a_new_recording_in"))
                .tip(Lang.tip("tip.record")));
    by += mainH + mainGap;
    bPlay =
        add(
            new StyledButton(BTN_PLAY, infoX, by, colW, mainH, Lang.t("main.play"))
                .primary()
                .accent(UiTheme.BLUE)
                .icon("\u25b6")
                .sub(Lang.t("main.play_the_selected_recording_once"))
                .tip(Lang.tip("tip.play")));
    by += mainH + mainGap;
    bLoop =
        add(
            new StyledButton(BTN_LOOP, infoX, by, colW, mainH, Lang.t("main.loop"))
                .primary()
                .accent(UiTheme.PURPLE)
                .icon("\u21bb")
                .sub(Lang.t("main.repeat_the_selected_recording_in"))
                .tip(Lang.tip("tip.loop")));
    by += mainH + mainGap;
    bStop =
        add(
            new StyledButton(BTN_STOP, infoX, by, colW, mainH, Lang.t("main.stop"))
                .primary()
                .accent(UiTheme.darken(UiTheme.RED, 0.78f))
                .icon("\u25a0")
                .sub(Lang.t("main.stop_the_current_recording_or"))
                .tip(Lang.tip("tip.stop")));
    by += mainH + 6;
    // Settings take the whole row; help is available via F1 and inside the settings.
    bSettings =
        add(
            new StyledButton(BTN_SETTINGS, infoX, by, colW, secH, Lang.t("main.settings"))
                .accent(UiTheme.ACCENT)
                .icon("settings")
                .compact());
    by += secH + secGap;
    bClose =
        add(
            new StyledButton(BTN_CLOSE, infoX, by, colW, secH, Lang.t("common.close"))
                .accent(UiTheme.GRAY)
                .icon("exit")
                .compact());
    bEdit =
        new StyledButton(108, editX(), editY(), editSize(), editSize(), "")
            .icon("edit")
            .iconScale(Math.max(0.75f, editSize() / 10f))
            .accent(UiTheme.BLUE)
            .tip(Lang.tip("slotTools.rename_give_the_recording_a"));
    syncEditButton();
  }

  private BaritoneReturnController returner() {
    MirrorRecorder mod = MirrorRecorder.getInstance();
    return mod == null ? null : mod.getReturnController();
  }

  private int usedSlotsCache = -1;
  private long usedSlotsAt = 0L;

  private int usedSlots() {
    long now = UiTheme.nowMs();
    if (usedSlotsCache >= 0 && now - usedSlotsAt < 500L) return usedSlotsCache;
    int used = 0;
    for (int i = 1; i <= MAX_SLOTS; i++) if (manager.getSlotFrameCount(i) > 0) used++;
    usedSlotsCache = used;
    usedSlotsAt = now;
    return used;
  }

  private void updateButtonStates() {
    BaritoneReturnController r = returner();
    boolean autoBusy = r != null && r.isBusy(),
        recovering = r != null && r.isRecovering(),
        ready = !manager.isBusy() && !autoBusy;
    int frames = manager.getSlotFrameCount(selectedSlot);
    bRecord.enabled = ready && frames == 0;
    bPlay.enabled = ready && frames > 0;
    bLoop.enabled = ready && frames > 0;
    bStop.enabled = manager.isBusy() || autoBusy || recovering;
    bSettings.enabled = ready;
  }

  @Override
  public void drawScreen(int mx, int my, float pt) {
    int bd = UiTheme.GLASS_BACKDROP;
    drawGradientRect(0, 0, width, height, bd, bd);
    UiTheme.window(winX, winY, winX + winW, winY + winH, headH);
    UiTheme.header(
        fontRenderer,
        winX,
        winY,
        winX + winW,
        headH,
        "Mirror Recorder",
        Lang.t("main.1_pick_a_slot_2"),
        UiTheme.ACCENT,
        56);
    String ver = "v" + MirrorRecorder.VERSION;
    int vw = UiTheme.pillWidth(fontRenderer, ver);
    UiTheme.chip(
        fontRenderer,
        ver,
        winX + winW - 12 - vw,
        winY + (headH - UiTheme.PILL_H) / 2,
        UiTheme.ACCENT);
    UiTheme.inset(listX - 3, listTop - 22, listRight + 1, rowsBottom() + 4);
    String count = usedSlots() + "/100";
    int countX = listRight - 8 - fontRenderer.getStringWidth(count);
    UiTheme.sectionLabel(
        fontRenderer,
        Lang.t("main.recordings").trim(),
        listX + 8,
        listTop - 16,
        Math.max(0, countX - listX - 49));
    UiTheme.drawFitted(
        fontRenderer, count, countX, listTop - 16, listRight - countX - 8, UiTheme.TEXT_MUTE);
    int fx = countX - 25, fy = listTop - 11;
    FolderIcons.draw(true, fx, fy, 2.0f, UiTheme.BLUE);
    UiTheme.rect(countX - 11, listTop - 19, countX - 10, listTop - 3, UiTheme.BORDER);
    UiTheme.sectionLabel(
        fontRenderer,
        Lang.t("main.selected_recording"),
        infoX + 10,
        listTop - 16,
        infoRight - infoX - 20);
    drawSlotList(mx, my);
    if (infoH > 0) drawAnimatedInfo(mx, my);
    if (actionsY - 11 >= infoY)
      UiTheme.sectionLabel(
          fontRenderer, Lang.t("cmd.actions"), infoX, actionsY - 11, infoRight - infoX);
    updateButtonStates();
    super.drawScreen(mx, my, pt);
    drawTooltips(mx, my);
  }

  private String fmtDur(int frames) {
    int sec = Math.max(0, frames / TPS);
    return sec >= 60
        ? (sec / 60) + Lang.t("cmd.m") + (sec % 60) + Lang.t("cmd.s")
        : sec + Lang.t("cmd.s");
  }

  private String decimal(double value) {
    String out = String.format(Locale.US, "%.1f", value);
    return Lang.dec(out);
  }

  private String fmtDurTable(int frames) {
    return decimal(Math.max(0, frames) / (double) TPS) + Lang.t("main.sec");
  }

  private String fmtSize(long b) {
    if (b < 1024) return b + " B";
    if (b < 1048576) return decimal(b / 1024d) + " KB";
    return decimal(b / 1048576d) + " MB";
  }

  private String fmtDate(long t) {
    return UiTheme.formatDate(t);
  }
  /** Default recording name in the current language plus the slot number. */
  private static String defaultName(int slot) {
    return Lang.t("cmd.recording") + slot;
  }

  private void drawAnimatedInfo(int mx, int my) {
    float ap = Appearance.progress(infoAnimAt, UiTheme.nowMs());
    if (ap <= 0f) return;
    GlStateManager.pushMatrix();
    GlStateManager.translate((1f - ap) * Appearance.INFO_SLIDE_X, 0f, 0f);
    UiTheme.opacity(ap);
    drawInfo(mx, my);
    UiTheme.opacityReset();
    GlStateManager.popMatrix();
  }

  private void drawInfo(int mx, int my) {
    int slot = selectedSlot, frames = manager.getSlotFrameCount(slot);
    boolean active =
        manager.isSlotBusy(slot) || (returner() != null && returner().isSlotBusy(slot));
    String name = manager.getSlotName(slot), desc = manager.getSlotDescription(slot);
    if (name == null || name.isEmpty())
      name = frames > 0 ? defaultName(slot) : Lang.t("main.empty_slot");
    if (desc == null || desc.isEmpty())
      desc = frames > 0 ? Lang.t("main.no_description") : Lang.t("main.ready_for_a_new_recording");
    int accent = active ? UiTheme.AMBER : frames > 0 ? UiTheme.GREEN : UiTheme.GRAY;
    UiTheme.slotCard(infoX, infoY, infoRight, infoY + infoH, false, true, accent);
    boolean showStats = infoH >= 46, table = frames > 0 && infoH >= 94;
    int barY = infoY + infoH - 12;
    String slotText = String.valueOf(slot);
    // Keep the title baseline, but give the number a balanced inset instead of a tight square.
    int badgeH = Math.min(18, Math.max(10, infoH - 12)), badgeY = infoY + 8;
    int badgeW = Math.max(badgeH + 2, fontRenderer.getStringWidth(slotText) + 10);
    int titleY = UiTheme.centerTextY(fontRenderer, badgeY, badgeY + badgeH);
    int tx = infoX + 10 + badgeW + 8;
    UiTheme.slotNumber(
        fontRenderer,
        slotText,
        infoX + 10,
        badgeY,
        infoX + 10 + badgeW,
        badgeY + badgeH,
        accent,
        true);
    String tag = active ? Lang.t("main.in_use") : "";
    int tw = tag.isEmpty() ? 0 : UiTheme.pillWidth(fontRenderer, tag);
    boolean showEdit = frames > 0 && infoH >= 28;
    int headerRight = showEdit ? editX() - 8 : infoRight - 10;
    if (!tag.isEmpty()) UiTheme.chip(fontRenderer, tag, headerRight - tw, infoY + 9, accent);
    syncEditButton();
    if (showEdit) bEdit.drawButton(mc, mx, my, 0f);
    UiTheme.beginClip(mc, infoX + 6, infoY + 4, infoRight - 6, infoY + infoH - 4);
    UiTheme.drawFitted(
        fontRenderer, name, tx, titleY, Math.max(0, headerRight - tw - tx - 8), UiTheme.TEXT);
    int descRoom = Math.max(0, (showEdit ? editX() - 8 : infoRight - 14) - tx);
    int descLimit = (showStats ? barY - 6 : infoY + infoH - 4) - 8;
    if (table) {
      String[] values =
          new String[] {
            config.isPreferredLoopMode() ? Lang.t("main.loop_2") : Lang.t("main.once"),
            desc,
            String.valueOf(frames),
            fmtDurTable(frames),
            fmtSize(manager.getSlotFileSize(slot)),
            fmtDate(manager.getSlotModified(slot))
          };
      UiTheme.slotInfoPairs(
          fontRenderer, values, infoX + 10, infoY + 32, infoRight - 10, infoY + infoH - 6);
      UiTheme.endClip();
    } else {
      if (infoY + 26 <= descLimit) {
        List<String> lines = fontRenderer.listFormattedStringToWidth(desc, descRoom);
        if (!lines.isEmpty())
          UiTheme.drawFitted(
              fontRenderer, lines.get(0), tx, infoY + 26, descRoom, UiTheme.TEXT_DIM);
      }
      if (showStats) {
        String stats =
            frames > 0
                ? ((config.isPreferredLoopMode() ? Lang.t("main.loop_2") : "1×")
                    + "  \u00b7  "
                    + frames
                    + Lang.t("main.fr")
                    + "  \u00b7  "
                    + fmtDur(frames)
                    + "  \u00b7  "
                    + fmtSize(manager.getSlotFileSize(slot))
                    + "  \u00b7  "
                    + fmtDate(manager.getSlotModified(slot)))
                : Lang.t("main.press_record_to_start");
        UiTheme.drawFitted(
            fontRenderer,
            stats,
            infoX + 9,
            barY,
            Math.max(24, infoRight - infoX - 18),
            frames > 0 ? UiTheme.TEXT_DIM : UiTheme.TEXT_MUTE);
      }
      UiTheme.endClip();
    }
  }

  private void setScroll(int target) {
    int max = Math.max(0, listRows().size() - visibleRows()),
        t = Math.max(0, Math.min(max, target));
    if (t == scrollOffset) return;
    scrollFrom = visScroll;
    scrollAt = UiTheme.nowMs();
    scrollOffset = t;
  }

  private float rowHover(int slot, boolean hovered, long now) {
    if (hovered != hovT[slot]) {
      hovFrom[slot] = hovA[slot];
      hovT[slot] = hovered;
      hovAt[slot] = now;
    }
    hovA[slot] =
        UiTheme.transition(hovFrom[slot], hovered ? 1f : 0f, hovAt[slot], now, UiTheme.HOVER_MS);
    return hovA[slot];
  }
  /** Hit testing uses the same flattened rows and scroll animation as drawing. */
  private int rowAt(int mx, int my) {
    if (mx < listX || mx > listRight || my < listTop || my >= rowsBottom() || rowH <= 0) return -1;
    List<ListRow> rows = listRows();
    int first = Math.max(0, (int) Math.floor(visScroll));
    int last = Math.min(rows.size() - 1, first + visibleRows() + 1);
    long now = UiTheme.nowMs();
    for (int i = first; i <= last; i++) {
      int top = Math.round(listTop + (i - visScroll) * rowH);
      if (Appearance.contains(
          mx, my, listX, top, listRight - listX, rowH - 2, rowAppearanceAt(i, now))) return i;
    }
    return -1;
  }

  private int slotAt(int mx, int my) {
    int index = rowAt(mx, my);
    if (index < 0) return -1;
    int slot = listRows().get(index).slot;
    return slot > 0 ? slot : -1;
  }

  private float rowAppearanceAt(int index, long now) {
    int visualRow = Math.max(0, Math.min(visibleRows() - 1, (int) Math.floor(index - visScroll)));
    return Appearance.progress(openAnimAt + visualRow * UiTheme.STAGGER_MS, now);
  }

  private int rowsBottom() {
    return listTop + visibleRows() * rowH;
  }

  private void drawSlotList(int mx, int my) {
    long now = UiTheme.nowMs();
    visScroll = UiTheme.transition(scrollFrom, scrollOffset, scrollAt, now, UiTheme.SCROLL_MS);
    int hovered = slotAt(mx, my);
    List<ListRow> rows = listRows();
    int first = Math.max(0, (int) Math.floor(visScroll)),
        last = Math.min(rows.size() - 1, first + visibleRows() + 1);
    UiTheme.beginClip(mc, listX - 3, listTop - 3, listRight + 1, rowsBottom());
    for (int index = first; index <= last; index++) {
      ListRow row = rows.get(index);
      int i = row.slot;
      int top = Math.round(listTop + (index - visScroll) * rowH), bottom = top + rowH - 2;
      if (bottom < listTop - 2 || top >= rowsBottom()) continue;
      float ap = rowAppearanceAt(index, now);
      if (ap <= 0f) continue;
      GlStateManager.pushMatrix();
      GlStateManager.translate(0f, Appearance.offsetY(ap), 0f);
      UiTheme.opacity(ap);
      if (i == 0) {
        boolean isOpen = row.folder == null ? !unfiledCollapsed : !collapsed.contains(row.folder);
        boolean hover = mx >= listX && mx < listRight && my >= top && my < bottom;
        UiTheme.slotCard(listX, top, listRight, bottom, hover, false, UiTheme.GRAY);
        UiTheme.chevron(listX + 10, (top + bottom) / 2f, 7, isOpen ? 1 : 0, UiTheme.TEXT_DIM);
        FolderIcons.draw(false, listX + 25, (top + bottom) / 2f, 1.75f, UiTheme.TEXT_DIM);
        String name = row.folder == null ? Lang.t("folders.unfiled") : folders.name(row.folder);
        UiTheme.drawFitted(
            fontRenderer,
            name,
            listX + 39,
            UiTheme.centerTextY(fontRenderer, top, bottom),
            Math.max(8, listRight - listX - 47),
            UiTheme.TEXT_DIM);
        UiTheme.opacityReset();
        GlStateManager.popMatrix();
        continue;
      }
      int frames = manager.getSlotFrameCount(i);
      boolean sel = i == selectedSlot;
      boolean active = manager.isSlotBusy(i) || (returner() != null && returner().isSlotBusy(i));
      float hv = rowHover(i, i == hovered, now);
      int color =
          active
              ? UiTheme.lerp(
                  UiTheme.AMBER,
                  UiTheme.lerp(UiTheme.AMBER, 0xFFFFFFFF, 0.42f),
                  UiTheme.pulse(1200L))
              : frames > 0 ? UiTheme.GREEN : UiTheme.GRAY;
      UiTheme.slotCard(listX, top, listRight, bottom, hv, sel, color);
      int ty = UiTheme.centerTextY(fontRenderer, top, bottom);
      UiTheme.dot(listX + 8, top + (bottom - top - 8) / 2, color);
      int numberH = Math.max(10, Math.min(16, bottom - top - 4)),
          numberY = top + (bottom - top - numberH) / 2;
      if (((bottom - top - numberH) & 1) != 0) {
        numberH--;
        numberY = top + (bottom - top - numberH) / 2;
      }
      UiTheme.slotNumber(
          fontRenderer,
          String.valueOf(i),
          listX + 18,
          numberY,
          listX + 44,
          numberY + numberH,
          color,
          sel);
      int nameX = listX + 50;
      if (frames == 0)
        UiTheme.drawFitted(
            fontRenderer,
            Lang.t("cmd.empty"),
            nameX,
            ty,
            Math.max(8, listRight - 8 - nameX),
            UiTheme.TEXT_MUTE);
      else {
        String meta = fmtDur(frames), name = manager.getSlotName(i);
        if (name == null || name.isEmpty()) name = defaultName(i);
        int metaW = fontRenderer.getStringWidth(meta), metaX = listRight - 8 - metaW;
        UiTheme.drawFitted(
            fontRenderer,
            name,
            nameX,
            ty,
            Math.max(8, metaX - nameX - 6),
            sel ? UiTheme.TEXT : UiTheme.lerp(UiTheme.TEXT_DIM, UiTheme.TEXT, UiTheme.ease(hv)));
        UiTheme.drawFitted(
            fontRenderer, meta, metaX, ty, metaW, sel ? UiTheme.TEXT_DIM : UiTheme.TEXT_MUTE);
      }
      UiTheme.opacityReset();
      GlStateManager.popMatrix();
    }
    UiTheme.endClip();
  }

  private int editSize() {
    return Math.max(10, Math.min(20, infoH - 16));
  }

  private int editX() {
    return infoRight - editSize() - 10;
  }

  private int editY() {
    return infoY + 8;
  }

  private boolean canEditSlot(int slot) {
    BaritoneReturnController r = returner();
    return manager.getSlotFrameCount(slot) > 0
        && !manager.isBusy()
        && !manager.isSlotSaving(slot)
        && (r == null || !r.isBusy());
  }

  private void syncEditButton() {
    bEdit.visible = infoH >= 28 && manager.getSlotFrameCount(selectedSlot) > 0;
    bEdit.enabled = bEdit.visible && canEditSlot(selectedSlot);
  }

  private void openEditor() {
    if (canEditSlot(selectedSlot))
      mc.displayGuiScreen(new GuiMirrorEdit(this, manager, selectedSlot));
  }

  private boolean editAt(int slot, int mx, int my) {
    if (slot != selectedSlot || slot < 1 || infoH < 28 || manager.getSlotFrameCount(slot) == 0)
      return false;
    return bEdit.isHover(mx, my);
  }

  private void drawTooltips(int mx, int my) {
    if (!config.isShowTooltips()) return;
    for (GuiButton g : buttonList)
      if (g instanceof StyledButton) {
        StyledButton b = (StyledButton) g;
        if (b.tooltip != null && b.isHover(mx, my)) {
          if (UiTheme.tipReady(b)) UiTheme.tooltip(fontRenderer, b.tooltip, mx, my, width, height);
          return;
        }
      }
    if (editAt(selectedSlot, mx, my)) {
      if (UiTheme.tipReady("slotEdit" + selectedSlot))
        UiTheme.tooltip(
            fontRenderer, Lang.tip("slotTools.rename_give_the_recording_a"), mx, my, width, height);
      return;
    }
    // Slot rows deliberately have no hover tooltip. Action buttons keep their tips.
  }

  private void selectSlot(int slot) {
    int target = Math.max(1, Math.min(MAX_SLOTS, slot));
    // Re-clicking the current slot must not reload saved settings over the user's latest edits.
    if (target == selectedSlot) return;
    if (manager.selectSlot(target)) {
      if (selectedSlot != target) infoAnimAt = UiTheme.nowMs();
      selectedSlot = target;
      if (folders == null) {
        if (target <= scrollOffset) setScroll(target - 1);
        else if (target > scrollOffset + VISIBLE_SLOTS) setScroll(target - VISIBLE_SLOTS);
      } else showSelected();
      try {
        config.save();
      } catch (Exception ignored) {
      }
      initGui();
    } else if (manager.isBusy() || (returner() != null && returner().isBusy()))
      msg("\u00a7e" + Lang.t("main.the_slot_cannot_be_changed"));
  }

  @Override
  protected void mouseClicked(int mx, int my, int btn) throws IOException {
    if (btn == 0 && editAt(selectedSlot, mx, my)) {
      syncEditButton();
      if (bEdit.mousePressed(mc, mx, my)) {
        bEdit.playPressSound(mc.getSoundHandler());
        openEditor();
      }
      return;
    }
    int countX = listRight - 8 - fontRenderer.getStringWidth(usedSlots() + "/100");
    if (btn == 0
        && folders != null
        && mx >= countX - 35
        && mx < countX - 14
        && my >= listTop - 21
        && my < listTop - 1) {
      mc.displayGuiScreen(new GuiMirrorFolderEdit(this, null));
      return;
    }
    int index = rowAt(mx, my);
    List<ListRow> rows = listRows();
    if (index >= 0 && rows.get(index).slot == 0) {
      ListRow row = rows.get(index);
      if (btn == 1 && row.folder != null)
        mc.displayGuiScreen(new GuiMirrorFolderEdit(this, row.folder));
      else if (btn == 0) {
        if (row.folder == null) unfiledCollapsed = !unfiledCollapsed;
        else if (!collapsed.add(row.folder)) collapsed.remove(row.folder);
        int max = Math.max(0, listRows().size() - visibleRows());
        scrollOffset = Math.min(scrollOffset, max);
        visScroll = scrollOffset;
        scrollFrom = visScroll;
      }
      return;
    }
    int slot = slotAt(mx, my);
    super.mouseClicked(mx, my, btn);
    if (slot > 0) {
      if (btn == 0 || btn == 1) UiBubbles.burst(mx, my, UiTheme.BLUE);
      if (btn == 1) openSlotContext(slot, mx, my);
      else selectSlot(slot);
    }
  }

  private void openSlotContext(int slot, int mx, int my) {
    if (manager.isBusy() || (returner() != null && returner().isBusy())) {
      msg("\u00a7e" + Lang.t("main.slot_menu_is_unavailable_during"));
      return;
    }
    if (slot != selectedSlot) selectSlot(slot);
    if (selectedSlot == slot)
      mc.displayGuiScreen(new GuiMirrorSlotContext(this, manager, config, slot, mx, my));
  }

  @Override
  public void handleMouseInput() throws IOException {
    super.handleMouseInput();
    int wheel = Mouse.getEventDWheel();
    if (wheel == 0) return;
    ScaledResolution sr = new ScaledResolution(mc);
    int mx = Mouse.getEventX() * sr.getScaledWidth() / Math.max(1, mc.displayWidth),
        my =
            sr.getScaledHeight()
                - Mouse.getEventY() * sr.getScaledHeight() / Math.max(1, mc.displayHeight)
                - 1;
    if (mx >= listX - 4 && mx <= listRight + 12 && my >= listTop && my < listBottom)
      setScroll(scrollOffset + (wheel < 0 ? 3 : -3));
  }

  @Override
  protected void keyTyped(char c, int key) throws IOException {
    if (key == Keyboard.KEY_ESCAPE) {
      mc.displayGuiScreen(null);
      return;
    }
    if (key == Keyboard.KEY_F2) {
      openEditor();
      return;
    }
    if (key == Keyboard.KEY_F1) {
      mc.displayGuiScreen(new GuiMirrorSettings(manager, config, this, 4));
      return;
    }
    if (key == Keyboard.KEY_UP) {
      selectSlot(selectedSlot - 1);
      return;
    }
    if (key == Keyboard.KEY_DOWN) {
      selectSlot(selectedSlot + 1);
      return;
    }
    if (key == Keyboard.KEY_PRIOR) {
      setScroll(scrollOffset - visibleRows());
      return;
    }
    if (key == Keyboard.KEY_NEXT) {
      setScroll(scrollOffset + visibleRows());
      return;
    }
    if (key == Keyboard.KEY_HOME) {
      selectSlot(1);
      return;
    }
    if (key == Keyboard.KEY_END) {
      selectSlot(MAX_SLOTS);
      return;
    }
    if (key >= Keyboard.KEY_1 && key <= Keyboard.KEY_9) {
      selectSlot(key - Keyboard.KEY_1 + 1);
      return;
    }
    if (key == Keyboard.KEY_0) {
      selectSlot(10);
      return;
    }
    super.keyTyped(c, key);
  }

  @Override
  protected void actionPerformed(GuiButton b) throws IOException {
    int slot = selectedSlot;
    if ((b.id == BTN_RECORD || b.id == BTN_PLAY || b.id == BTN_LOOP)
        && returner() != null
        && returner().isRecovering()) returner().cancel();
    switch (b.id) {
      case BTN_RECORD:
        if (manager.scheduleRecording(slot)) {
          msg("\u00a7a" + Lang.t("main.recording_of_slot_starts_in", slot, config.getStartDelay()));
          mc.displayGuiScreen(null);
        } else msg("\u00a7e" + Lang.t("main.this_slot_cannot_be_recorded"));
        break;
      case BTN_PLAY:
        {
          BaritoneReturnController r = returner();
          if (r != null && r.request(slot, false)) mc.displayGuiScreen(null);
          break;
        }
      case BTN_LOOP:
        {
          BaritoneReturnController r = returner();
          if (r != null && r.request(slot, true)) mc.displayGuiScreen(null);
          break;
        }
      case BTN_STOP:
        {
          BaritoneReturnController r = returner();
          if (manager.isBusy() || (r != null && (r.isBusy() || r.isRecovering()))) {
            if (r != null) r.cancel();
            manager.stopAll();
            msg("\u00a7e" + Lang.t("main.action_stopped"));
          }
          break;
        }
      case BTN_SETTINGS:
        if (!manager.isBusy()) mc.displayGuiScreen(new GuiMirrorSettings(manager, config, this));
        break;
      case BTN_CLOSE:
        mc.displayGuiScreen(null);
        break;
    }
  }

  public void contextRecord(int slot) {
    if (manager.scheduleRecording(slot)) {
      msg("\u00a7a" + Lang.t("main.recording_of_slot_starts_in", slot, config.getStartDelay()));
      mc.displayGuiScreen(null);
    } else {
      mc.displayGuiScreen(this);
      msg("\u00a7e" + Lang.t("main.this_slot_cannot_be_recorded"));
    }
  }

  public void contextPlay(int slot, boolean loop) {
    BaritoneReturnController r = returner();
    if (r != null && r.request(slot, loop)) mc.displayGuiScreen(null);
    else mc.displayGuiScreen(this);
  }

  public void contextDelete(int slot) {
    String n = manager.getSlotName(slot);
    if (n == null || n.isEmpty()) n = Lang.t("cmd.recording") + slot;
    mc.displayGuiScreen(new GuiMirrorConfirm(this, slot, n));
  }

  public void confirmDelete(int slot, boolean confirmed) {
    mc.displayGuiScreen(this);
    if (!confirmed) return;
    if ((returner() != null && (returner().isSlotBusy(slot) || returner().isRecoveringSlot(slot)))
        || !manager.canDeleteSlot(slot)) {
      msg("\u00a7e" + Lang.t("main.deletion_cancelled_the_slot_is"));
      return;
    }
    int del = manager.deleteSlotStatus(slot);
    if (del == StorageManager.DELETE_TRASHED)
      msg("\u00a7a" + Lang.t("main.slot_deleted_a_copy_is", slot));
    else if (del == StorageManager.DELETE_NO_COPY)
      msg("\u00a7e" + Lang.t("main.slot_deleted_no_trash_copy", slot));
    else if (del == StorageManager.DELETE_PARTIAL)
      msg("\u00a7e" + Lang.t("main.slot_deleted_but_some_service", slot));
    else msg("\u00a7c" + Lang.t("main.could_not_delete_the_slot"));
  }

  public void notifyResult(String text) {
    msg(text);
  }

  private void msg(String text) {
    TransientChat.show(PREFIX + text);
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }
}
