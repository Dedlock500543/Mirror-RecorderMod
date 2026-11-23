package com.mirror.recorder.gui;

import com.mirror.recorder.manager.RecorderManager;
import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.input.Mouse;

/** Copying a slot's recording to another slot. */
public class GuiMirrorDuplicate extends ModScreen {

  private static final int CANCEL = 20, PREV = 21, NEXT = 22, PAGE_SIZE = 10, MAX_SLOT = 100;
  private final GuiMirrorSlotTools parent;
  private final RecorderManager manager;
  private final int source;
  private int wx, wy, ww, wh, page;

  public GuiMirrorDuplicate(GuiMirrorSlotTools p, RecorderManager m, int s) {
    parent = p;
    manager = m;
    source = s;
    page = Math.max(0, Math.min(9, (s - 1) / PAGE_SIZE));
  }

  @Override
  public void initGui() {
    buttonList.clear();
    ww = Math.min(width - 20, 430);
    wh = Math.min(188, height - 8);
    wx = (width - ww) / 2;
    wy = Math.max(0, (height - wh) / 2);
    int gap = 6, x = wx + 18, w = ww - 36, cell = (w - gap * 4) / 5, from = page * PAGE_SIZE + 1;
    for (int n = 0; n < PAGE_SIZE; n++) {
      int slot = from + n, col = n % 5, row = n / 5;
      StyledButton b =
          new StyledButton(
                  101 + n,
                  x + col * (cell + gap),
                  wy + 55 + row * 27,
                  cell,
                  21,
                  (Lang.t("cmd.slot_2")) + slot)
              .accent(UiTheme.PURPLE);
      b.enabled =
          slot != source && manager.getSlotFrameCount(slot) == 0 && !manager.isSlotBusy(slot);
      b.tip(
          Lang.t("duplicate.slot_2", slot),
          Lang.t("duplicate.the_copy_will_be_placed"),
          Lang.t("duplicate.the_original_stays_where_it"));
      buttonList.add(b);
    }
    int navW = 28;
    StyledButton
        prev =
            new StyledButton(PREV, x, wy + 124, navW, 20, "‹")
                .accent(UiTheme.GRAY)
                .tip(Lang.t("duplicate.back"), Lang.t("duplicate.show_the_previous_10_slots")),
        next =
            new StyledButton(NEXT, x + w - navW, wy + 124, navW, 20, "›")
                .accent(UiTheme.GRAY)
                .tip(Lang.t("duplicate.next"), Lang.t("duplicate.show_the_next_10_slots"));
    prev.enabled = page > 0;
    next.enabled = page < 9;
    buttonList.add(prev);
    buttonList.add(next);
    buttonList.add(
        new StyledButton(CANCEL, x, wy + wh - 32, w, 20, Lang.t("common.cancel"))
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
        Lang.t("duplicate.copy_slot_where", source),
        wx + 15,
        wy + 11,
        ww - 30,
        UiTheme.TEXT);
    UiTheme.drawFitted(
        fontRenderer,
        Lang.t("duplicate.busy_and_active_slots_are"),
        wx + 18,
        wy + 39,
        ww - 36,
        UiTheme.TEXT_MUTE);
    drawCenteredString(
        fontRenderer,
        (page + 1) + " / 10  ·  " + (page * 10 + 1) + "–" + (page * 10 + 10),
        wx + ww / 2,
        wy + 128,
        UiTheme.TEXT_DIM);
    super.drawScreen(mx, my, pt);
    UiTheme.buttonTooltip(fontRenderer, buttonList, mx, my, width, height);
  }

  @Override
  protected void actionPerformed(GuiButton b) throws IOException {
    if (b.id == CANCEL) {
      mc.displayGuiScreen(parent);
      return;
    }
    if (b.id == PREV && page > 0) {
      page--;
      initGui();
      return;
    }
    if (b.id == NEXT && page < 9) {
      page++;
      initGui();
      return;
    }
    if (b.id >= 101 && b.id <= 110) {
      int target = page * PAGE_SIZE + (b.id - 100);
      if (manager.duplicateSlot(source, target))
        parent.finish(
            Lang.t("duplicate.slot") + source + Lang.t("duplicate.copied_to_slot") + target);
      else parent.finish(Lang.t("duplicate.copy_failed"));
    }
  }

  @Override
  public void handleMouseInput() throws IOException {
    super.handleMouseInput();
    int wheel = Mouse.getEventDWheel();
    if (wheel < 0 && page < 9) {
      page++;
      initGui();
    } else if (wheel > 0 && page > 0) {
      page--;
      initGui();
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
