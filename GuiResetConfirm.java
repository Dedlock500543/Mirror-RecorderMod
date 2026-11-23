package com.mirror.recorder.gui;

import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.input.Keyboard;

/**
 * Confirmation of resetting all mod settings to factory defaults. Slot recordings are not affected:
 * only parameters are reset.
 */
public class GuiResetConfirm extends ModScreen {

  private static final int YES = 1, NO = 2;
  private final GuiMirrorSettings parent;
  private int winX, winY, winW, winH;

  public GuiResetConfirm(GuiMirrorSettings parent) {
    this.parent = parent;
  }

  @Override
  public void initGui() {
    buttonList.clear();
    winW = Math.min(width - 24, 344);
    winH = Math.min(132, height - 8);
    winX = (width - winW) / 2;
    winY = Math.max(0, (height - winH) / 2);
    int y = winY + winH - 32, half = (winW - 30) / 2;
    buttonList.add(
        new StyledButton(NO, winX + 12, y, half, 20, Lang.t("common.cancel")).accent(UiTheme.GRAY));
    buttonList.add(
        new StyledButton(YES, winX + 18 + half, y, half, 20, Lang.t("resetConfirm.reset"))
            .primary()
            .icon("reset")
            .accent(UiTheme.RED)
            .tip(
                Lang.t("resetConfirm.yes_reset"),
                Lang.t("resetConfirm.resets_every_setting_to_factory"),
                Lang.t("resetConfirm.recordings_in_the_slots_are")));
  }

  @Override
  public void drawScreen(int mx, int my, float pt) {
    {
      int bd = UiTheme.GLASS_BACKDROP;
      drawGradientRect(0, 0, width, height, bd, bd);
    }
    UiTheme.window(winX, winY, winX + winW, winY + winH, 30);
    fontRenderer.drawString(
        "\u00a7l" + Lang.t("resetConfirm.factory_settings"), winX + 14, winY + 11, UiTheme.TEXT);
    UiTheme.dot(winX + winW - 22, winY + 12, UiTheme.RED);
    drawCenteredString(
        fontRenderer,
        Lang.t("resetConfirm.reset_every_setting_to_factory"),
        winX + winW / 2,
        winY + 45,
        0xFFFFFFFF);
    drawCenteredString(
        fontRenderer,
        Lang.t("resetConfirm.this_action_cannot_be_undone"),
        winX + winW / 2,
        winY + 60,
        UiTheme.TEXT_DIM);
    drawCenteredString(
        fontRenderer,
        Lang.t("resetConfirm.recordings_in_the_slots_are_2"),
        winX + winW / 2,
        winY + 73,
        UiTheme.TEXT_MUTE);
    super.drawScreen(mx, my, pt);
    UiTheme.buttonTooltip(fontRenderer, buttonList, mx, my, width, height);
  }

  @Override
  protected void actionPerformed(GuiButton b) throws IOException {
    parent.finishFactoryReset(b.id == YES);
  }

  @Override
  protected void keyTyped(char c, int key) throws IOException {
    if (key == Keyboard.KEY_ESCAPE) {
      parent.finishFactoryReset(false);
      return;
    }
    super.keyTyped(c, key);
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }
}
