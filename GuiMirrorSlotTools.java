package com.mirror.recorder.gui;

import com.mirror.recorder.config.RecorderConfig;
import com.mirror.recorder.manager.RecorderManager;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.client.gui.GuiButton;

/** Slot card: header with state, action grid and export block. */
public class GuiMirrorSlotTools extends ModScreen {

  private static final int DUP = 2, EXPORT = 3, IMPORT = 4, BACK = 5, DELETE = 6;
  private final GuiMirrorMain parent;
  private final RecorderManager manager;
  private final RecorderConfig config;
  private final int slot;
  private int wx, wy, ww, wh, headH, padX, innerW, infoY, infoH, pathY, pathH;
  private boolean hasExportsCache = false;
  private int lastFilled = -1;
  private StyledButton dup, export, imp, delete;

  public GuiMirrorSlotTools(GuiMirrorMain p, RecorderManager m, RecorderConfig c, int s) {
    parent = p;
    manager = m;
    config = c;
    slot = s;
  }

  @Override
  public void initGui() {
    buttonList.clear();
    hasExportsCache = !manager.getExportFiles().isEmpty();
    lastFilled = -1;
    ww = Math.min(width - 16, 470);
    wh = Math.min(height - 10, 368);
    wx = (width - ww) / 2;
    wy = (height - wh) / 2;
    headH = Math.min(34, Math.max(26, wh / 9));
    padX = 12;
    innerW = ww - padX * 2;
    int x = wx + padX, half = (innerW - 6) / 2;
    int backH = 20, backY = wy + wh - 12 - backH, bh = 32, delH = 22, avail = wh - headH - 10;
    int fixed = 20 + bh * 2 + 6 + 12 + delH + 10 + backH + 12;
    if (avail - fixed < 46) {
      bh = 24;
      delH = 20;
      fixed = 20 + bh * 2 + 6 + 12 + delH + 10 + backH + 12;
    }
    if (avail - fixed < 46) {
      bh = 20;
      delH = 16;
      fixed = 20 + bh * 2 + 6 + 12 + delH + 10 + backH + 12;
    }
    // Keep all six rows before showing the optional export-folder panel.
    int tableSpace = UiTheme.tableHeight(6) + 14;
    pathH = avail - fixed - tableSpace >= 50 ? 40 : 0;
    infoY = wy + headH + 10;
    infoH = Math.max(28, avail - fixed - (pathH > 0 ? pathH + 10 : 0));
    int gridY = infoY + infoH + 20;
    dup =
        add(
            new StyledButton(DUP, x, gridY, innerW, bh, Lang.t("slotTools.duplicate"))
                .accent(UiTheme.PURPLE)
                .icon("copy")
                .sub(Lang.t("slotTools.copy_to_a_free_slot")));
    int row2 = gridY + bh + 6;
    export =
        add(
            new StyledButton(EXPORT, x, row2, half, bh, Lang.t("slotTools.export"))
                .accent(UiTheme.BLUE)
                .icon("export")
                .sub(Lang.t("slotTools.mrr_file")));
    imp =
        add(
            new StyledButton(IMPORT, x + half + 6, row2, half, bh, Lang.t("import.import"))
                .accent(UiTheme.GREEN)
                .icon("import")
                .sub(Lang.t("slotTools.from_exports_folder")));
    int delY = row2 + bh + 12;
    delete =
        add(
            new StyledButton(DELETE, x, delY, innerW, delH, Lang.t("slotContext.delete_recording"))
                .accent(UiTheme.RED)
                .icon("trash"));
    pathY = delY + delH + 10;
    add(
        new StyledButton(BACK, x, backY, innerW, backH, Lang.t("common.back"))
            .accent(UiTheme.GRAY)
            .tip(Lang.tip("slotTools.back_go_back_to_the")));
    sync();
  }

  private StyledButton add(StyledButton b) {
    buttonList.add(b);
    return b;
  }

  private static String[] withLine(String[] lines, String extra) {
    String[] r = Arrays.copyOf(lines, lines.length + 1);
    r[lines.length] = extra;
    return r;
  }

  private void sync() {
    boolean saving = manager.isSlotSaving(slot),
        ready = !manager.isBusy() && !saving,
        filled = manager.getSlotFrameCount(slot) > 0,
        hasExports = hasExportsCache;
    dup.enabled = ready && filled;
    export.enabled = ready && filled;
    imp.enabled = ready && !filled && hasExports;
    delete.enabled = ready && filled && !manager.isSlotBusy(slot);
    int filledKey = (filled ? 1 : 0) | (ready ? 2 : 0) | (hasExports ? 4 : 0) | (saving ? 8 : 0);
    if (filledKey == lastFilled) return;
    lastFilled = filledKey;
    dup.tooltip =
        filled
            ? Lang.tip("slotTools.duplicate_makes_the_same_recording")
            : Lang.tip("slotTools.duplicate_record_something_into_this");
    export.tooltip =
        filled
            ? Lang.tip("slotTools.save_to_a_file_creates", manager.getExportDirectoryPath())
            : Lang.tip("slotTools.save_to_a_file_this");
    imp.tooltip =
        !ready
            ? Lang.tip("slotTools.load_from_a_file_please")
            : filled
                ? Lang.tip("slotTools.load_from_a_file_an")
                : !hasExports
                    ? Lang.tip("slotTools.load_from_a_file_there")
                    : Lang.tip("slotTools.load_from_a_file_pick");
    delete.tooltip =
        filled
            ? Lang.tip("slotTools.delete_recording_the_recording_goes")
            : Lang.tip("slotTools.delete_this_slot_is_already");
    if (saving) {
      String wait = Lang.t("slotTools.the_recording_is_still_being");
      dup.tooltip = withLine(dup.tooltip, wait);
      export.tooltip = withLine(export.tooltip, wait);
      delete.tooltip = withLine(delete.tooltip, wait);
    }
  }

  @Override
  public void drawScreen(int mx, int my, float pt) {
    {
      int bd = UiTheme.GLASS_BACKDROP;
      drawGradientRect(0, 0, width, height, bd, bd);
    }
    UiTheme.window(wx, wy, wx + ww, wy + wh, headH);
    int frames = manager.getSlotFrameCount(slot);
    boolean filled = frames > 0;
    String name = manager.getSlotName(slot);
    if (name == null || name.isEmpty())
      name = filled ? Lang.t("cmd.recording") + slot : Lang.t("main.empty_slot");
    UiTheme.header(
        fontRenderer,
        wx,
        wy,
        wx + ww,
        headH,
        Lang.t("cmd.slot_2") + slot,
        name,
        filled ? UiTheme.ACCENT : UiTheme.GRAY);
    int l = wx + padX, r = wx + ww - padX;
    UiTheme.card(l, infoY, r, infoY + infoH, UiTheme.CARD, UiTheme.BORDER);
    if (filled) {
      String desc = manager.getSlotDescription(slot);
      if (desc == null || desc.isEmpty()) desc = Lang.t("main.no_description");
      String[] values =
          new String[] {
            config.isPreferredLoopMode() ? Lang.t("main.loop_2") : Lang.t("main.once"),
            desc,
            frames + Lang.t("slotTools.frames"),
            sizeDur(frames),
            size(manager.getSlotFileSize(slot)),
            date(manager.getSlotModified(slot))
          };
      UiTheme.slotInfoTable(fontRenderer, values, l + 10, infoY + 8, r - 10, infoY + infoH - 6);
    } else
      UiTheme.drawFitted(
          fontRenderer,
          Lang.t("slotTools.no_recording_import_available"),
          l + 10,
          infoY + infoH - 16,
          r - l - 20,
          UiTheme.TEXT_DIM);

    UiTheme.sectionLabel(fontRenderer, Lang.t("cmd.actions"), l, infoY + infoH + 6, innerW);
    if (pathH > 0) {
      UiTheme.card(l, pathY, r, pathY + pathH, UiTheme.CARD, UiTheme.BORDER);
      UiTheme.drawFitted(
          fontRenderer,
          Lang.t("slotTools.export_and_import"),
          l + 10,
          pathY + 6,
          innerW - 20,
          UiTheme.TEXT);
      UiTheme.drawFitted(
          fontRenderer,
          manager.getExportDirectoryPath(),
          l + 10,
          pathY + 17,
          innerW - 20,
          UiTheme.BLUE);
      if (pathH >= 34)
        UiTheme.drawFitted(
            fontRenderer,
            Lang.t("slotTools.mrr_route_name_and_description"),
            l + 10,
            pathY + pathH - 12,
            innerW - 20,
            UiTheme.TEXT_MUTE);
    }
    sync();
    super.drawScreen(mx, my, pt);
    drawTips(mx, my);
  }

  private void drawTips(int mx, int my) {
    if (!config.isShowTooltips()) return;
    for (GuiButton g : buttonList)
      if (g instanceof StyledButton) {
        StyledButton b = (StyledButton) g;
        if (b.visible
            && b.tooltip != null
            && mx >= b.x
            && my >= b.y
            && mx < b.x + b.width
            && my < b.y + b.height) {
          if (UiTheme.tipReady(b)) UiTheme.tooltip(fontRenderer, b.tooltip, mx, my, width, height);
          return;
        }
      }
  }

  private String dec(double v) {
    String out = String.format(Locale.US, "%.1f", v);
    return Lang.dec(out);
  }

  private String sizeDur(int frames) {
    return dec(Math.max(0, frames) / (double) RecorderManager.TICKS_PER_SECOND)
        + Lang.t("main.sec");
  }

  private String size(long b) {
    if (b < 1024) return b + " B";
    if (b < 1048576) return dec(b / 1024d) + " KB";
    return dec(b / 1048576d) + " MB";
  }

  private String date(long t) {
    return UiTheme.formatDate(t);
  }

  @Override
  protected void actionPerformed(GuiButton b) throws IOException {
    if (b.id == DUP) mc.displayGuiScreen(new GuiMirrorDuplicate(this, manager, slot));
    else if (b.id == EXPORT) {
      String f = manager.exportSlot(slot);
      finish(
          f == null
              ? Lang.t("slotTools.export_failed")
              : (Lang.t("slotTools.export_saved"))
                  + manager.getExportDirectoryPath()
                  + File.separator
                  + f);
    } else if (b.id == IMPORT) mc.displayGuiScreen(new GuiMirrorImport(this, manager, slot));
    else if (b.id == DELETE) {
      String n = manager.getSlotName(slot);
      if (n == null || n.isEmpty()) n = Lang.t("cmd.recording") + slot;
      mc.displayGuiScreen(new GuiMirrorConfirm(parent, slot, n));
    } else if (b.id == BACK) mc.displayGuiScreen(parent);
  }

  public void finish(String message) {
    mc.displayGuiScreen(this);
    if (message != null) parent.notifyResult(message);
  }

  @Override
  protected void keyTyped(char c, int k) throws IOException {
    if (k == 1) {
      mc.displayGuiScreen(parent);
      return;
    }
    super.keyTyped(c, k);
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }
}
