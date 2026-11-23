package com.mirror.recorder.gui;

import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.input.Keyboard;

/** Slot deletion confirmation. */
public class GuiMirrorConfirm extends ModScreen {

  private static final int YES = 1, NO = 2;
  private final GuiMirrorMain parent;
  private final int slot;
  private final String name;
  private int winX, winY, winW, winH;

  public GuiMirrorConfirm(GuiMirrorMain parent, int slot, String name) {
    this.parent = parent;
    this.slot = slot;
    this.name = name == null ? "" : name;
  }

  @Override
  public void initGui() {
    buttonList.clear();
    winW = Math.min(width - 24, 326);
    winH = Math.min(126, height - 8);
    winX = (width - winW) / 2;
    winY = Math.max(0, (height - winH) / 2);
    int y = winY + winH - 32, half = (winW - 30) / 2;
    buttonList.add(
        new StyledButton(NO, winX + 12, y, half, 20, Lang.t("common.cancel")).accent(UiTheme.GRAY));
    buttonList.add(
        new StyledButton(YES, winX + 18 + half, y, half, 20, Lang.t("confirm.delete"))
            .primary()
            .accent(UiTheme.RED)
            .tip(Lang.t("confirm.yes_delete"), Lang.t("confirm.the_recording_goes_to_the")));
  }

  @Override
  public void drawScreen(int mx, int my, float pt) {
    {
      int bd = UiTheme.GLASS_BACKDROP;
      drawGradientRect(0, 0, width, height, bd, bd);
    }
    UiTheme.window(winX, winY, winX + winW, winY + winH, 30);
    UiTheme.drawFitted(
        fontRenderer,
        Lang.t("confirm.delete_recording"),
        winX + 14,
        winY + 11,
        winW - 40,
        UiTheme.TEXT);
    UiTheme.dot(winX + winW - 22, winY + 12, UiTheme.RED);
    UiTheme.drawCenteredFitted(
        fontRenderer,
        Lang.t("confirm.delete_slot", slot),
        winX + winW / 2f,
        winY + 43,
        winW - 28,
        0xFFFFFFFF,
        false);
    UiTheme.drawCenteredFitted(
        fontRenderer, name, winX + winW / 2f, winY + 57, winW - 36, UiTheme.TEXT_DIM, false);
    UiTheme.drawCenteredFitted(
        fontRenderer,
        Lang.t("confirm.the_recording_goes_to_the_2"),
        winX + winW / 2f,
        winY + 70,
        winW - 30,
        UiTheme.TEXT_MUTE,
        false);
    super.drawScreen(mx, my, pt);
    UiTheme.buttonTooltip(fontRenderer, buttonList, mx, my, width, height);
  }

  @Override
  protected void actionPerformed(GuiButton b) throws IOException {
    if (b.id == YES) parent.confirmDelete(slot, true);
    else if (b.id == NO) parent.confirmDelete(slot, false);
  }

  @Override
  protected void keyTyped(char c, int key) throws IOException {
    if (key == Keyboard.KEY_ESCAPE) {
      parent.confirmDelete(slot, false);
      return;
    }
    super.keyTyped(c, key);
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }
}
