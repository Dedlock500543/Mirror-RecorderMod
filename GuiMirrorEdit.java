package com.mirror.recorder.gui;

import com.mirror.recorder.manager.RecorderManager;
import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.input.Keyboard;

/** Slot settings: name, description and start delay. */
public class GuiMirrorEdit extends ModScreen {

  private static final int SAVE = 1, CANCEL = 2, DELAY_DEC = 3, DELAY_INC = 4;
  private final GuiMirrorMain parent;
  private final RecorderManager manager;
  private final int slot;
  private CenteredTextField name, desc;
  private int wx, wy, ww, wh, delay;
  private String error = "";

  private int fieldY(int original) {
    int compression = Math.max(0, 238 - wh);
    int shift = original < 90 ? compression / 4 : original < 140 ? compression / 2 : compression;
    return wy + original - shift;
  }

  public GuiMirrorEdit(GuiMirrorMain p, RecorderManager m, int s) {
    parent = p;
    manager = m;
    slot = s;
  }

  @Override
  public void initGui() {
    Keyboard.enableRepeatEvents(true);
    buttonList.clear();
    ww = Math.min(width - 20, 410);
    wh = Math.min(238, height - 8);
    wx = (width - ww) / 2;
    wy = Math.max(0, (height - wh) / 2);
    // The draft is read once: resizing the window recreates the fields but does not reset the
    // edits.
    String nameText = name == null ? manager.getSlotName(slot) : name.getText(),
        descText = desc == null ? manager.getSlotDescription(slot) : desc.getText();
    boolean descFocused = desc != null && desc.isFocused();
    if (name == null) delay = manager.getSlotStartDelay(slot);
    // Each field spans the height of its visible frame (the inset drawn in drawScreen):
    // CenteredTextField
    // draws text at the vertical center.
    name = new CenteredTextField(10, fontRenderer, wx + 27, fieldY(65), ww - 54, 23);
    name.setFocusRail(false);
    name.setMaxStringLength(40);
    name.setText(nameText);
    name.setFocused(!descFocused);
    desc = new CenteredTextField(11, fontRenderer, wx + 27, fieldY(113), ww - 54, 23);
    desc.setMaxStringLength(120);
    desc.setText(descText);
    desc.setFocused(descFocused);
    int sy = fieldY(158), bs = 20, vw = 48, right = wx + ww - 20;
    buttonList.add(
        new StyledButton(DELAY_DEC, right - vw - bs * 2, sy, bs, bs, "−")
            .flat()
            .compact()
            .tip(
                Lang.t("edit.less_delay"),
                Lang.t("edit.wait_one_second_less_before"),
                Lang.t("edit.hold_ctrl_to_change_by")));
    buttonList.add(
        new StyledButton(DELAY_INC, right - bs, sy, bs, bs, "+")
            .flat()
            .compact()
            .tip(
                Lang.t("edit.more_delay"),
                Lang.t("edit.wait_one_second_more_before"),
                Lang.t("edit.hold_ctrl_to_change_by")));
    int half = (ww - 47) / 2, y = wy + wh - 31;
    buttonList.add(
        new StyledButton(CANCEL, wx + 20, y, half, 20, Lang.t("common.cancel"))
            .accent(UiTheme.GRAY));
    buttonList.add(
        new StyledButton(SAVE, wx + 27 + half, y, half, 20, Lang.t("common.save"))
            .primary()
            .accent(UiTheme.GREEN));
  }

  @Override
  public void drawScreen(int mx, int my, float pt) {
    {
      int bd = UiTheme.GLASS_BACKDROP;
      drawGradientRect(0, 0, width, height, bd, bd);
    }
    UiTheme.window(wx, wy, wx + ww, wy + wh, 34);
    UiTheme.header(
        fontRenderer,
        wx,
        wy,
        wx + ww,
        34,
        Lang.t("cmd.slot_2") + slot,
        Lang.t("edit.name_description_and_delay"),
        UiTheme.ACCENT);
    UiTheme.card(wx + 14, wy + 44, wx + ww - 14, fieldY(190), UiTheme.CARD, UiTheme.BORDER);
    label(Lang.t("cmd.name"), name.getText().length() + "/40", fieldY(54));
    UiTheme.inset(wx + 20, fieldY(65), wx + ww - 20, fieldY(88));
    name.drawTextBox();
    label(Lang.t("cmd.description"), desc.getText().length() + "/120", fieldY(102));
    UiTheme.inset(wx + 20, fieldY(113), wx + ww - 20, fieldY(136));
    desc.drawTextBox();
    int bs = 20, vw = 48, right = wx + ww - 20, vx = right - vw - bs * 2;
    UiTheme.counterFrame(vx, fieldY(158), bs, vw);
    UiTheme.drawFitted(
        fontRenderer,
        Lang.t("edit.start_delay"),
        wx + 22,
        fieldY(162),
        Math.max(24, vx - wx - 28),
        UiTheme.TEXT);
    UiTheme.drawFitted(
        fontRenderer,
        Lang.t("edit.0_60_seconds"),
        wx + 22,
        fieldY(174),
        Math.max(24, vx - wx - 28),
        UiTheme.TEXT_MUTE);
    UiTheme.drawCenteredFitted(
        fontRenderer,
        delay + (Lang.t("edit.s")),
        vx + bs + vw / 2f,
        fieldY(164),
        vw - 4,
        UiTheme.TEXT,
        false);
    if (!error.isEmpty())
      UiTheme.drawCenteredFitted(
          fontRenderer, error, wx + ww / 2f, fieldY(195), ww - 28, UiTheme.RED, false);
    super.drawScreen(mx, my, pt);
    UiTheme.buttonTooltip(fontRenderer, buttonList, mx, my, width, height);
    if (mx >= wx + 20
        && mx < wx + ww - 20
        && my >= fieldY(65)
        && my < fieldY(88)
        && UiTheme.tipReady("editName"))
      UiTheme.tooltip(
          fontRenderer,
          new String[] {
            Lang.t("edit.name"),
            Lang.t("edit.how_the_recording_will_be"),
            Lang.t("edit.for_example_path_to_the")
          },
          mx,
          my,
          width,
          height);
    else if (mx >= wx + 20
        && mx < wx + ww - 20
        && my >= fieldY(113)
        && my < fieldY(136)
        && UiTheme.tipReady("editDesc"))
      UiTheme.tooltip(
          fontRenderer,
          new String[] {
            Lang.t("edit.description"),
            Lang.t("edit.a_few_words_to_tell"),
            Lang.t("edit.can_be_left_empty")
          },
          mx,
          my,
          width,
          height);
  }

  private void label(String left, String right, int y) {
    fontRenderer.drawString(left, wx + 22, y, UiTheme.TEXT_DIM);
    fontRenderer.drawString(
        right, wx + ww - 22 - fontRenderer.getStringWidth(right), y, UiTheme.TEXT_MUTE);
  }

  @Override
  public void updateScreen() {
    name.updateCursorCounter();
    desc.updateCursorCounter();
  }

  @Override
  protected void mouseClicked(int x, int y, int b) throws IOException {
    name.mouseClicked(x, y, b);
    desc.mouseClicked(x, y, b);
    super.mouseClicked(x, y, b);
  }

  @Override
  protected void keyTyped(char c, int k) throws IOException {
    if (k == 1) {
      mc.displayGuiScreen(parent);
      return;
    }
    if (k == Keyboard.KEY_TAB) {
      boolean n = !name.isFocused();
      name.setFocused(n);
      desc.setFocused(!n);
      return;
    }
    if (k == Keyboard.KEY_RETURN || k == Keyboard.KEY_NUMPADENTER) {
      save();
      return;
    }
    if (name.textboxKeyTyped(c, k)) return;
    desc.textboxKeyTyped(c, k);
  }

  private void save() {
    if (manager.saveSlotEdit(slot, name.getText(), desc.getText(), delay)) {
      String message = "§a" + Lang.t("edit.slot_settings_updated", slot);
      mc.displayGuiScreen(parent);
      parent.notifyResult(message);
      return;
    }
    error = Lang.t("edit.could_not_save_the_slot");
  }

  @Override
  protected void actionPerformed(GuiButton b) throws IOException {
    if (b.id == SAVE) save();
    else if (b.id == DELAY_DEC)
      delay =
          Math.max(
              0,
              delay
                  - (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)
                          || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)
                      ? 5
                      : 1));
    else if (b.id == DELAY_INC)
      delay =
          Math.min(
              60,
              delay
                  + (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)
                          || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)
                      ? 5
                      : 1));
    else mc.displayGuiScreen(parent);
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
