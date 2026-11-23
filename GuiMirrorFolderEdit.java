package com.mirror.recorder.gui;

import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.input.Keyboard;

/** Create, rename, or remove a folder without touching recordings. */
final class GuiMirrorFolderEdit extends ModScreen {
  private static final int SAVE = 1, CANCEL = 2, DELETE = 3;
  private final GuiMirrorMain parent;
  private final String id;
  private FolderNameField name;
  private String error = "";
  private boolean confirmDelete;
  private int x, y, w, h;

  GuiMirrorFolderEdit(GuiMirrorMain parent, String id) {
    this.parent = parent;
    this.id = id;
  }

  @Override
  public void initGui() {
    Keyboard.enableRepeatEvents(true);
    String draft = name == null ? (id == null ? "" : parent.folders().name(id)) : name.getText();
    buttonList.clear();
    w = Math.min(270, width - 20);
    h = id == null ? 120 : 148;
    x = (width - w) / 2;
    y = Math.max(4, (height - h) / 2);
    name = new FolderNameField(1, fontRenderer, x + 28, y + 53, w - 56, 18);
    name.setFocusRail(false);
    name.setMaxStringLength(RecordingFolders.MAX_NAME);
    name.setText(draft == null ? "" : draft);
    name.setFocused(true);
    int by = y + h - 28, bw = (w - 50) / 2;
    buttonList.add(
        new StyledButton(CANCEL, x + 20, by, bw, 20, Lang.t("common.cancel")).accent(UiTheme.GRAY));
    buttonList.add(
        new StyledButton(SAVE, x + 30 + bw, by, bw, 20, Lang.t("common.save"))
            .primary()
            .accent(UiTheme.GREEN));
    if (id != null)
      buttonList.add(
          new StyledButton(
                  DELETE,
                  x + 20,
                  y + 78,
                  w - 40,
                  20,
                  Lang.t(confirmDelete ? "folders.confirm_delete" : "folders.delete"))
              .accent(UiTheme.RED)
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
        Lang.t(id == null ? "folders.create" : "folders.edit"),
        Lang.t("folders.name_hint"),
        UiTheme.ACCENT);
    UiTheme.drawFitted(
        fontRenderer, Lang.t("folders.name_label"), x + 20, y + 38, w - 40, UiTheme.TEXT_DIM);
    UiTheme.inset(x + 24, y + 51, x + w - 24, y + 73);
    if (name.isFocused()) UiTheme.border(x + 24, y + 51, x + w - 24, y + 73, UiTheme.BORDER_HI);
    name.drawTextBox();
    if (!error.isEmpty())
      UiTheme.drawFitted(fontRenderer, error, x + 18, y + h - 42, w - 36, UiTheme.RED);
    super.drawScreen(mx, my, pt);
  }

  private void save() {
    RecordingFolders f = parent.folders();
    if (!f.isReadable()) {
      error = Lang.t("folders.read_error");
      return;
    }
    if (!f.validName(name.getText(), id)) {
      error = Lang.t("folders.invalid_name");
      return;
    }
    if (!(id == null ? f.create(name.getText()) : f.rename(id, name.getText()))) {
      error = Lang.t("folders.save_error");
      return;
    }
    parent.folderChanged();
    mc.displayGuiScreen(parent);
  }

  @Override
  protected void actionPerformed(GuiButton b) {
    if (b.id == CANCEL) mc.displayGuiScreen(parent);
    else if (b.id == SAVE) save();
    else if (b.id == DELETE) {
      if (!confirmDelete) {
        confirmDelete = true;
        error = Lang.t("folders.delete_hint");
        initGui();
      } else if (parent.folders().delete(id)) {
        parent.folderChanged();
        mc.displayGuiScreen(parent);
      } else error = Lang.t("folders.save_error");
    }
  }

  @Override
  protected void mouseClicked(int mx, int my, int b) throws IOException {
    name.mouseClicked(mx, my, b);
    super.mouseClicked(mx, my, b);
  }

  @Override
  protected void keyTyped(char c, int k) throws IOException {
    if (k == Keyboard.KEY_ESCAPE) {
      mc.displayGuiScreen(parent);
      return;
    }
    if (k == Keyboard.KEY_RETURN || k == Keyboard.KEY_NUMPADENTER) {
      save();
      return;
    }
    name.textboxKeyTyped(c, k);
  }

  @Override
  public void updateScreen() {
    name.updateCursorCounter();
  }

  @Override
  public void onGuiClosed() {
    Keyboard.enableRepeatEvents(false);
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }
}
