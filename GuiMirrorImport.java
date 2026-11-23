package com.mirror.recorder.gui;

import com.mirror.recorder.manager.RecorderManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;

/** Import a recording from a file into the selected slot. */
public class GuiMirrorImport extends ModScreen {

  private static final int PREV = 1, NEXT = 2, IMPORT = 3, CANCEL = 4, PER_PAGE = 5;
  private final GuiMirrorSlotTools parent;
  private final RecorderManager manager;
  private final int target;
  private List<String> files = new ArrayList<String>();
  private int page = 0, selected = -1, wx, wy, ww, wh;
  private StyledButton importButton;

  public GuiMirrorImport(GuiMirrorSlotTools p, RecorderManager m, int t) {
    parent = p;
    manager = m;
    target = t;
  }

  @Override
  public void initGui() {
    // Preserve the selected file, not its old index, when the directory changes on resize.
    String chosen = selected >= 0 && selected < files.size() ? files.get(selected) : null;
    files = manager.getExportFiles();
    selected = chosen == null ? -1 : files.indexOf(chosen);
    if (selected >= 0) page = selected / PER_PAGE;
    build();
  }

  static int boundedPage(int requested, int fileCount) {
    return Math.max(0, Math.min(requested, Math.max(0, (fileCount - 1) / PER_PAGE)));
  }

  private void build() {
    page = boundedPage(page, files.size());
    buttonList.clear();
    ww = Math.min(width - 20, 470);
    wh = Math.min(height - 14, 255);
    wx = (width - ww) / 2;
    wy = (height - wh) / 2;
    int x = wx + 18,
        w = ww - 36,
        pages = Math.max(1, (files.size() + PER_PAGE - 1) / PER_PAGE),
        nav = 22,
        pageY = wy + 32;
    StyledButton prev =
        new StyledButton(PREV, x + w - nav * 2 - 6, pageY, nav, 20, "\u2039")
            .accent(UiTheme.GRAY)
            .tip(Lang.t("duplicate.back"), Lang.t("import.previous_page_of_the_list"));
    StyledButton next =
        new StyledButton(NEXT, x + w - nav, pageY, nav, 20, "\u203a")
            .accent(UiTheme.GRAY)
            .tip(Lang.t("duplicate.next"), Lang.t("import.next_page_of_the_list"));
    prev.enabled = page > 0;
    next.enabled = page + 1 < pages;
    buttonList.add(prev);
    buttonList.add(next);

    int footerY = wy + wh - 32, pathY = wy + wh - 52, listY = wy + 55;
    int step = Math.max(17, Math.min(26, (pathY - 5 - listY) / PER_PAGE)),
        rowH = Math.max(14, step - 5),
        start = page * PER_PAGE;
    for (int i = 0; i < PER_PAGE; i++) {
      int idx = start + i;
      if (idx >= files.size()) break;
      StyledButton b =
          new StyledButton(100 + i, x, listY + i * step, w, rowH, files.get(idx))
              .accent(idx == selected ? UiTheme.GREEN : UiTheme.BLUE)
              .tip(files.get(idx), Lang.t("import.then_press_the_green_import"));
      buttonList.add(b);
    }

    int gap = 6, cancelW = Math.min(105, (w - gap) * 2 / 5), importW = w - gap - cancelW;
    importButton =
        new StyledButton(IMPORT, x, footerY, importW, 20, Lang.t("import.import"))
            .primary()
            .accent(UiTheme.GREEN)
            .tip(
                Lang.t("import.load"),
                Lang.t("import.puts_the_chosen_recording_into"),
                Lang.t("import.the_file_stays_in_the"));
    importButton.enabled = selected >= 0 && selected < files.size();
    buttonList.add(importButton);
    buttonList.add(
        new StyledButton(CANCEL, x + importW + gap, footerY, cancelW, 20, Lang.t("common.cancel"))
            .accent(UiTheme.GRAY));
  }

  @Override
  public void drawScreen(int mx, int my, float pt) {
    {
      int bd = UiTheme.GLASS_BACKDROP;
      drawGradientRect(0, 0, width, height, bd, bd);
    }
    UiTheme.window(wx, wy, wx + ww, wy + wh, 32);
    UiTheme.drawFitted(
        fontRenderer,
        Lang.t("import.import_into_slot", target),
        wx + 15,
        wy + 11,
        ww - 30,
        UiTheme.TEXT);
    int pages = Math.max(1, (files.size() + PER_PAGE - 1) / PER_PAGE);
    if (files.isEmpty())
      drawCenteredString(
          fontRenderer,
          Lang.t("import.no_mrr_files_in_the"),
          wx + ww / 2,
          wy + 93,
          UiTheme.TEXT_MUTE);
    else
      UiTheme.drawFitted(
          fontRenderer,
          (Lang.t("import.pick_a_file_page")) + (page + 1) + "/" + pages,
          wx + 18,
          wy + 38,
          Math.max(50, ww - 104),
          UiTheme.TEXT_DIM);
    UiTheme.drawFitted(
        fontRenderer,
        manager.getExportDirectoryPath(),
        wx + 18,
        wy + wh - 52,
        ww - 36,
        UiTheme.TEXT_MUTE);
    super.drawScreen(mx, my, pt);
    UiTheme.buttonTooltip(fontRenderer, buttonList, mx, my, width, height);
  }

  @Override
  protected void actionPerformed(GuiButton b) throws IOException {
    if (b.id >= 100 && b.id < 100 + PER_PAGE) {
      selected = page * PER_PAGE + (b.id - 100);
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
    if (b.id == CANCEL) {
      mc.displayGuiScreen(parent);
      return;
    }
    if (b.id == IMPORT && selected >= 0 && selected < files.size()) {
      String file = files.get(selected);
      if (manager.importSlot(target, file))
        parent.finish(Lang.t("import.file_imported_into_slot") + target);
      else parent.finish(Lang.t("import.import_cancelled_the_file_is"));
    }
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
