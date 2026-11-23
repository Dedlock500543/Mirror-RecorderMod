package com.mirror.recorder.gui;

import com.mirror.recorder.config.RecorderConfig;
import com.mirror.recorder.manager.RecorderManager;
import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.input.Keyboard;

/** Compact slot menu on right click. */
public class GuiMirrorSlotContext extends ModScreen {

  private static final int OPEN = 1, PRIMARY = 2, LOOP = 3, DELETE = 4, MOVE = 5;
  private final GuiMirrorMain parent;
  private final RecorderManager manager;
  private final RecorderConfig config;
  private final int slot, anchorX, anchorY;
  private int x, y, w = 198, h;
  private boolean filled;

  public GuiMirrorSlotContext(
      GuiMirrorMain p, RecorderManager m, RecorderConfig c, int s, int mx, int my) {
    parent = p;
    manager = m;
    config = c;
    slot = s;
    anchorX = mx;
    anchorY = my;
  }

  @Override
  public void initGui() {
    if (parent != null && mc != null) parent.setWorldAndResolution(mc, width, height);
    buttonList.clear();
    filled = manager.getSlotFrameCount(slot) > 0;
    h = filled ? 178 : 118;
    x = Math.max(8, Math.min(width - w - 8, anchorX));
    y = Math.max(8, Math.min(height - h - 8, anchorY));
    int by = y + 34, bw = w - 24;
    buttonList.add(
        new StyledButton(OPEN, x + 12, by, bw, 22, Lang.t("slotContext.open_slot_settings"))
            .accent(UiTheme.ACCENT)
            .icon("\u2699")
            .compact()
            .tip(Lang.t("slotContext.slot_data_name_description_start")));
    by += 28;
    buttonList.add(
        new StyledButton(
                PRIMARY,
                x + 12,
                by,
                bw,
                22,
                filled ? (Lang.t("main.play")) : (Lang.t("slotContext.start_recording")))
            .accent(filled ? UiTheme.BLUE : UiTheme.GREEN)
            .icon(filled ? "\u25b6" : "\u25cf")
            .compact()
            .tip(
                filled
                    ? (Lang.t("slotContext.play_the_mod_repeats_your"))
                    : (Lang.t("slotContext.start_recording_the_mod_remembers"))));
    if (filled) {
      by += 28;
      buttonList.add(
          new StyledButton(LOOP, x + 12, by, bw, 22, Lang.t("slotContext.loop"))
              .accent(UiTheme.PURPLE)
              .icon("\u21bb")
              .compact()
              .tip(Lang.t("slotContext.loop_repeat_the_recording_over")));
      by += 28;
      buttonList.add(
          new StyledButton(DELETE, x + 12, by, bw, 22, Lang.t("slotContext.delete_recording"))
              .accent(UiTheme.RED)
              .icon("trash")
              .compact()
              .tip(Lang.t("slotContext.delete_recording_you_will_be")));
    }
    by += 28;
    buttonList.add(
        new StyledButton(MOVE, x + 12, by, bw, 22, Lang.t("folders.move"))
            .accent(UiTheme.ACCENT)
            .icon("folder")
            .compact());
  }

  @Override
  public void drawScreen(int mx, int my, float pt) {
    // The popup is modal: the screen below is drawn without cursor coordinates, so its hover does
    // not shine through the transparent background.
    parent.drawScreen(-10000, -10000, pt);
    UiTheme.shadow(x, y, x + w, y + h);
    UiTheme.card(x, y, x + w, y + h, UiTheme.PANEL, UiTheme.BORDER_HI);
    fontRenderer.drawString(
        "\u00a7l" + Lang.t("slot.title") + " " + slot, x + 12, y + 11, UiTheme.TEXT);
    super.drawScreen(mx, my, pt);
    if (config.isShowTooltips())
      for (GuiButton raw : buttonList)
        if (raw instanceof StyledButton) {
          StyledButton b = (StyledButton) raw;
          if (b.isHover(mx, my) && b.tooltip != null) {
            if (UiTheme.tipReady(b))
              UiTheme.tooltip(fontRenderer, b.tooltip, mx, my, width, height);
            break;
          }
        }
  }

  @Override
  protected void mouseClicked(int mx, int my, int b) throws IOException {
    if (mx < x || mx >= x + w || my < y || my >= y + h) {
      mc.displayGuiScreen(parent);
      return;
    }
    super.mouseClicked(mx, my, b);
  }

  @Override
  protected void actionPerformed(GuiButton b) throws IOException {
    if (b.id == OPEN) mc.displayGuiScreen(new GuiMirrorSlotTools(parent, manager, config, slot));
    else if (b.id == PRIMARY) {
      if (filled) parent.contextPlay(slot, false);
      else parent.contextRecord(slot);
    } else if (b.id == LOOP) parent.contextPlay(slot, true);
    else if (b.id == DELETE) parent.contextDelete(slot);
    else if (b.id == MOVE) parent.openFolderMove(slot);
  }

  @Override
  protected void keyTyped(char c, int key) throws IOException {
    if (key == Keyboard.KEY_ESCAPE) {
      mc.displayGuiScreen(parent);
      return;
    }
    super.keyTyped(c, key);
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }
}
