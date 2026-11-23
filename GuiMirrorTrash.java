package com.mirror.recorder.gui;

import com.mirror.recorder.manager.RecorderManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiButton;

/**
 * Trash: first pick a recording, then one main action — restore. Deleting and emptying are
 * secondary actions so they do not compete with restoring.
 */
public class GuiMirrorTrash extends ModScreen {

  /** List rows are buttons LIST_BASE + 0 .. LIST_BASE + PER_PAGE - 1. */
  private static final int LIST_BASE = 100;

  private static final int PREV = 1,
      NEXT = 2,
      RESTORE = 3,
      DELETE = 4,
      UNDO = 5,
      CLEAR = 6,
      BACK = 7,
      PER_PAGE = 4;
  private final GuiMirrorSettings parent;
  private final RecorderManager manager;
  private List<String> files = new ArrayList<String>();
  private int[] slots = new int[0], frames = new int[0];
  private long[] times = new long[0];
  private String[] titles = new String[0];
  private int page = 0, selected = -1, wx, wy, ww, wh, listTop, listBottom, statusY;
  private String notice = "";
  private int noticeColor = UiTheme.TEXT_MUTE;
  private boolean confirmClear = false;
  private String confirmDeleteFile = null;

  public GuiMirrorTrash(GuiMirrorSettings p, RecorderManager m) {
    parent = p;
    manager = m;
  }

  @Override
  public void initGui() {
    reload();
  }

  private void reload() {
    files = manager.getTrashFiles();
    int n = files.size();
    slots = new int[n];
    frames = new int[n];
    times = new long[n];
    titles = new String[n];
    for (int i = 0; i < n; i++) {
      String f = files.get(i);
      slots[i] = manager.getTrashSlot(f);
      frames[i] = manager.getTrashFrames(f);
      times[i] = manager.getTrashTime(f);
      titles[i] = manager.getTrashTitle(f);
    }
    confirmClear = false;
    confirmDeleteFile = null;
    if (selected >= n) selected = -1;
    if (n > 0 && page * PER_PAGE >= n) page = (n - 1) / PER_PAGE;
    if (n == 0) page = 0;
    build();
  }

  private String titleOf(int i) {
    if (titles[i] != null && !titles[i].isEmpty()) return titles[i];
    return Lang.t("trash.slot_recording", slots[i]);
  }

  private String metaOf(int i) {
    StringBuilder b = new StringBuilder();
    b.append(Lang.t("cmd.slot_2"))
        .append(slots[i])
        .append("  ·  ")
        .append(
            String.format(
                Locale.ROOT, "%.1f", frames[i] / (float) RecorderManager.TICKS_PER_SECOND))
        .append(Lang.t("edit.s"));
    if (times[i] > 0L) b.append("  ·  ").append(UiTheme.formatShortDate(times[i]));
    return b.toString();
  }

  private String countLabel() {
    int n = files.size();
    if (n == 0) return Lang.t("trash.empty");
    return Lang.plural("trash.copies_count", n);
  }

  private void build() {
    buttonList.clear();
    ww = Math.min(width - 20, 460);
    wh = Math.min(height - 14, 300);
    wx = (width - ww) / 2;
    wy = (height - wh) / 2;
    int x = wx + 16, w = ww - 32;
    addPageButtons(x, w);
    int footerY = wy + wh - 32, mainY = files.isEmpty() ? footerY : footerY - 26;
    listTop = wy + 58;
    listBottom = mainY - 18;
    statusY = mainY - 14;
    addCards(x, w);
    if (!files.isEmpty()) addMainActions(x, w, mainY);
    addFooter(x, w, footerY);
  }

  /** Previous/next page arrows, shown only when the trash has more than one page. */
  private void addPageButtons(int x, int w) {
    int pages = Math.max(1, (files.size() + PER_PAGE - 1) / PER_PAGE);
    int nav = 22, pageY = wy + 34;
    StyledButton prev =
        new StyledButton(PREV, x + w - nav * 2 - 6, pageY, nav, 20, "\u2039")
            .accent(UiTheme.GRAY)
            .compact()
            .tip(Lang.t("trash.previous_page"), Lang.t("trash.show_earlier_copies"));
    StyledButton next =
        new StyledButton(NEXT, x + w - nav, pageY, nav, 20, "\u203a")
            .accent(UiTheme.GRAY)
            .compact()
            .tip(Lang.t("trash.next_page"), Lang.t("trash.show_later_copies"));
    prev.enabled = page > 0;
    next.enabled = page + 1 < pages;
    prev.visible = files.size() > PER_PAGE;
    next.visible = files.size() > PER_PAGE;
    buttonList.add(prev);
    buttonList.add(next);
  }

  /** Cards of the current page; the selected copy is highlighted. */
  private void addCards(int x, int w) {
    int slotStep = Math.max(22, Math.min(32, (listBottom - listTop) / Math.max(1, PER_PAGE)));
    int slotH = Math.max(18, slotStep - 6), start = page * PER_PAGE;
    for (int i = 0; i < PER_PAGE; i++) {
      int idx = start + i;
      if (idx >= files.size()) break;
      boolean sel = idx == selected;
      StyledButton card =
          new StyledButton(LIST_BASE + i, x, listTop + i * slotStep, w, slotH, titleOf(idx))
              .accent(sel ? UiTheme.GREEN : UiTheme.BLUE)
              .sub(sel ? Lang.t("trash.selected") + metaOf(idx) : metaOf(idx))
              .tip(Lang.t("trash.select_this_copy"), Lang.t("trash.after_this_the_restore_button"));
      if (sel) card.primary();
      buttonList.add(card);
    }
  }

  /** "Restore to slot N" and, for a selected copy, "Delete". */
  private void addMainActions(int x, int w, int mainY) {
    int gap = 6, restoreW = selected >= 0 ? (w - gap) * 3 / 4 : w;
    String restoreLabel =
        selected >= 0
            ? Lang.t("trash.restore_to_slot") + slots[selected]
            : Lang.t("trash.select_a_recording_first");
    StyledButton restore =
        new StyledButton(RESTORE, x, mainY, restoreW, 20, restoreLabel)
            .primary()
            .accent(UiTheme.GREEN)
            .tip(Lang.t("trash.restore_recording"), Lang.t("trash.puts_the_copy_back_into"));
    restore.enabled = selected >= 0 && selected < files.size() && slots[selected] >= 1;
    buttonList.add(restore);
    if (selected >= 0) {
      StyledButton del =
          new StyledButton(
                  DELETE,
                  x + restoreW + gap,
                  mainY,
                  w - restoreW - gap,
                  20,
                  confirmDeleteFile != null
                      ? Lang.t("trash.click_again")
                      : Lang.t("confirm.delete"))
              .accent(UiTheme.RED)
              .tip(Lang.t("trash.delete_forever"), Lang.t("trash.this_copy_disappears_for_good"));
      del.enabled = true;
      buttonList.add(del);
    }
  }

  /** Footer: undo the last deletion, empty the trash and back. */
  private void addFooter(int x, int w, int footerY) {
    int third = (w - gapOf()) / 3;
    StyledButton undo =
        new StyledButton(UNDO, x, footerY, third, 20, Lang.t("trash.last_deletion"))
            .accent(UiTheme.AMBER)
            .compact()
            .tip(Lang.t("trash.undo_last"), Lang.t("trash.restores_the_recording_you_just"));
    undo.enabled = !files.isEmpty();
    buttonList.add(undo);
    StyledButton clear =
        new StyledButton(
                CLEAR,
                x + third + 6,
                footerY,
                third,
                20,
                confirmClear ? Lang.t("trash.click_again") : Lang.t("trash.empty_all"))
            .accent(confirmClear ? UiTheme.RED : UiTheme.GRAY)
            .compact()
            .tip(Lang.t("trash.empty_trash"), Lang.t("trash.deletes_every_copy_for_good"));
    clear.enabled = !files.isEmpty();
    buttonList.add(clear);
    buttonList.add(
        new StyledButton(
                BACK, x + (third + 6) * 2, footerY, w - third * 2 - 12, 20, Lang.t("common.back"))
            .accent(UiTheme.GRAY)
            .compact()
            .tip(Lang.t("duplicate.back"), Lang.t("trash.return_to_settings")));
  }

  private static int gapOf() {
    return 12;
  }

  @Override
  public void drawScreen(int mx, int my, float pt) {
    {
      int bd = UiTheme.GLASS_BACKDROP;
      drawGradientRect(0, 0, width, height, bd, bd);
    }
    UiTheme.window(wx, wy, wx + ww, wy + wh, 32);
    int cw = UiTheme.pillWidth(fontRenderer, countLabel());
    UiTheme.drawFitted(
        fontRenderer,
        Lang.t("trash.trash"),
        wx + 16,
        wy + 11,
        Math.max(40, ww - 16 - cw - 20),
        UiTheme.TEXT);
    UiTheme.chip(
        fontRenderer,
        countLabel(),
        wx + ww - 16 - cw,
        wy + (32 - UiTheme.PILL_H) / 2,
        files.isEmpty() ? UiTheme.GRAY : UiTheme.AMBER);
    int pages = Math.max(1, (files.size() + PER_PAGE - 1) / PER_PAGE);
    if (files.isEmpty()) {
      drawCenteredString(
          fontRenderer,
          Lang.t("trash.deleted_recordings_will_show_up"),
          wx + ww / 2,
          wy + wh / 2 - 18,
          UiTheme.TEXT_DIM);
      drawCenteredString(
          fontRenderer,
          Lang.t("trash.you_can_put_them_back"),
          wx + ww / 2,
          wy + wh / 2 - 6,
          UiTheme.TEXT_MUTE);
    } else {
      String hint =
          files.size() > PER_PAGE
              ? Lang.t("trash.page")
                  + (page + 1)
                  + "/"
                  + pages
                  + Lang.t("trash.tap_a_card_then_restore")
              : Lang.t("trash.tap_a_card_then_restore_2");
      UiTheme.drawFitted(
          fontRenderer,
          hint,
          wx + 16,
          wy + 38,
          Math.max(40, ww - (files.size() > PER_PAGE ? 90 : 36)),
          UiTheme.TEXT_MUTE);
    }
    if (!notice.isEmpty())
      UiTheme.drawFitted(fontRenderer, notice, wx + 16, statusY, ww - 32, noticeColor);
    super.drawScreen(mx, my, pt);
    UiTheme.buttonTooltip(fontRenderer, buttonList, mx, my, width, height);
  }

  private void setNotice(String text, int color) {
    notice = text == null ? "" : text;
    noticeColor = color;
  }

  @Override
  protected void actionPerformed(GuiButton b) throws IOException {
    if (b.id != CLEAR) confirmClear = false;
    if (b.id != DELETE) confirmDeleteFile = null;
    if (b.id >= LIST_BASE && b.id < LIST_BASE + PER_PAGE) {
      selected = page * PER_PAGE + (b.id - LIST_BASE);
      setNotice("", UiTheme.TEXT_MUTE);
      build();
      return;
    }
    if (b.id == PREV && page > 0) {
      page--;
      selected = -1;
      build();
      return;
    }
    if (b.id == NEXT && (page + 1) * PER_PAGE < files.size()) {
      page++;
      selected = -1;
      build();
      return;
    }
    if (b.id == BACK) {
      parent.finishTrash();
      return;
    }
    if (b.id == UNDO) {
      int slot = manager.undoLastDelete();
      if (slot > 0)
        setNotice(Lang.t("trash.restored_the_last_recording_into") + slot, UiTheme.GREEN);
      else if (manager.isBusy()) setNotice(Lang.t("trash.please_wait_the_mod_is"), UiTheme.RED);
      else setNotice(Lang.t("trash.file_is_damaged_or_the"), UiTheme.RED);
      selected = -1;
      reload();
      return;
    }
    if (b.id == CLEAR && !confirmClear) {
      confirmClear = true;
      setNotice(Lang.t("trash.click_again_to_delete_every"), UiTheme.AMBER);
      build();
      return;
    }
    if (b.id == CLEAR) {
      confirmClear = false;
      if (manager.clearTrash()) setNotice(Lang.t("trash.trash_emptied"), UiTheme.AMBER);
      else setNotice(Lang.t("trash.some_files_could_not_be"), UiTheme.RED);
      selected = -1;
      reload();
      return;
    }
    if (selected < 0 || selected >= files.size()) return;
    String file = files.get(selected);
    if (b.id == RESTORE) {
      int origSlot = slots[selected];
      if (origSlot < 1) {
        setNotice(Lang.t("trash.unknown_original_slot_the_file"), UiTheme.RED);
        selected = -1;
        reload();
        return;
      }
      if (manager.isBusy()) {
        setNotice(Lang.t("trash.please_wait_the_mod_is"), UiTheme.RED);
        selected = -1;
        reload();
        return;
      }
      if (manager.getSlotFrameCount(origSlot) > 0) {
        setNotice(Lang.t("trash.slot_is_taken_delete_or", origSlot), UiTheme.RED);
        selected = -1;
        reload();
        return;
      }
      int slot = manager.restoreTrashToOwnSlot(file);
      if (slot > 0) setNotice(Lang.t("trash.recording_restored_into_slot") + slot, UiTheme.GREEN);
      else setNotice(Lang.t("trash.file_is_damaged_restoration_is"), UiTheme.RED);
      selected = -1;
      reload();
      return;
    }
    if (b.id == DELETE) {
      if (!file.equals(confirmDeleteFile)) {
        confirmDeleteFile = file;
        setNotice(
            Lang.t("trash.delete_forever") + " — " + Lang.t("trash.click_again"), UiTheme.RED);
        build();
        return;
      }
      confirmDeleteFile = null;
      if (manager.deleteTrash(file))
        setNotice(Lang.t("trash.copy_deleted_for_good"), UiTheme.AMBER);
      else setNotice(Lang.t("trash.the_file_could_not_be"), UiTheme.RED);
      selected = -1;
      reload();
    }
  }

  @Override
  protected void keyTyped(char c, int k) throws IOException {
    if (k == 1) {
      if (confirmClear || confirmDeleteFile != null) {
        confirmClear = false;
        confirmDeleteFile = null;
        setNotice("", UiTheme.TEXT_MUTE);
        build();
        return;
      }
      parent.finishTrash();
      return;
    }
    super.keyTyped(c, k);
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }
}
