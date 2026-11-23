package com.mirror.recorder.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Choose a folder for a slot. Slot numbers and recording files do not change. */
final class GuiMirrorFolderMove extends ModScreen {
  private static final int CANCEL = 1, PREV = 2, NEXT = 3, BASE = 100, PAGE = 6;
  private final GuiMirrorMain parent;
  private final int slot;
  private int x, y, w, h, offset;
  private String error = "";
  private List<String> choices = new ArrayList<String>();

  GuiMirrorFolderMove(GuiMirrorMain parent, int slot) {
    this.parent = parent;
    this.slot = slot;
  }

  @Override
  public void initGui() {
    buttonList.clear();
    choices.clear();
    choices.add(null);
    choices.addAll(parent.folders().ids());
    w = Math.min(width - 20, 360);
    h = Math.min(height - 8, 258);
    x = (width - w) / 2;
    y = Math.max(4, (height - h) / 2);
    int rows = Math.min(PAGE, Math.max(1, (h - 94) / 24));
    offset = Math.max(0, Math.min(offset, Math.max(0, choices.size() - rows)));
    for (int i = 0; i < rows && offset + i < choices.size(); i++) {
      String id = choices.get(offset + i);
      String title = id == null ? Lang.t("folders.unfiled") : parent.folders().name(id);
      boolean selected =
          id == null
              ? parent.folders().folderOf(slot) == null
              : id.equals(parent.folders().folderOf(slot));
      StyledButton choice =
          new StyledButton(BASE + i, x + 18, y + 43 + i * 24, w - 36, 20, title)
              .accent(UiTheme.ACCENT)
              .icon("folder")
              .compact();
      if (selected) choice.selectedMark();
      buttonList.add(choice);
    }
    int by = y + h - 27;
    buttonList.add(new StyledButton(PREV, x + 18, by, 28, 20, "‹").accent(UiTheme.GRAY).compact());
    buttonList.add(new StyledButton(NEXT, x + 50, by, 28, 20, "›").accent(UiTheme.GRAY).compact());
    buttonList.add(
        new StyledButton(CANCEL, x + w - 120, by, 102, 20, Lang.t("common.cancel"))
            .accent(UiTheme.GRAY)
            .compact());
  }

  @Override
  public void drawScreen(int mx, int my, float pt) {
    int bd = UiTheme.GLASS_BACKDROP;
    drawGradientRect(0, 0, width, height, bd, bd);
    UiTheme.window(x, y, x + w, y + h, 34);
    UiTheme.header(
        fontRenderer,
        x,
        y,
        x + w,
        34,
        Lang.t("folders.move"),
        Lang.t("folders.move_hint", slot),
        UiTheme.ACCENT);
    if (!error.isEmpty())
      UiTheme.drawFitted(fontRenderer, error, x + 18, y + h - 40, w - 36, UiTheme.RED);
    super.drawScreen(mx, my, pt);
  }

  @Override
  protected void actionPerformed(GuiButton b) {
    if (b.id == CANCEL) mc.displayGuiScreen(parent);
    else if (b.id == PREV) {
      offset = Math.max(0, offset - 1);
      initGui();
    } else if (b.id == NEXT) {
      offset = Math.min(Math.max(0, choices.size() - 1), offset + 1);
      initGui();
    } else if (b.id >= BASE && b.id < BASE + PAGE && offset + b.id - BASE < choices.size()) {
      if (parent.folders().move(slot, choices.get(offset + b.id - BASE))) {
        parent.folderChanged();
        mc.displayGuiScreen(parent);
      } else error = Lang.t("folders.save_error");
    }
  }

  @Override
  public void handleMouseInput() throws IOException {
    super.handleMouseInput();
    int wheel = Mouse.getEventDWheel();
    if (wheel != 0) {
      offset += wheel < 0 ? 1 : -1;
      initGui();
    }
  }

  @Override
  protected void keyTyped(char c, int k) throws IOException {
    if (k == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent);
    else super.keyTyped(c, k);
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }
}
