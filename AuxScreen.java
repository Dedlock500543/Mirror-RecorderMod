package com.mirror.recorder.gui;

import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.input.Keyboard;

/** Shared workstation modal shell and explicit, visible keyboard button focus. */
public abstract class AuxScreen extends ModScreen {
  protected int focusedId = -1;

  protected void focusButton(int id) {
    focusedId = id;
  }

  protected void modal(int x, int y, int w, int h, String title, String subtitle, int accent) {
    drawRect(0, 0, width, height, UiTheme.GLASS_BACKDROP);
    UiTheme.window(x, y, x + w, y + h, 32);
    UiTheme.header(
        fontRenderer,
        x,
        y,
        x + w,
        32,
        title,
        subtitle == null || subtitle.isEmpty() ? null : subtitle,
        accent);
    // The footer has its own quiet rule, shared by every dialog.
    drawRect(x + 12, y + h - 36, x + w - 12, y + h - 35, UiTheme.BORDER);
  }

  @Override
  public void drawScreen(int mx, int my, float pt) {
    super.drawScreen(mx, my, pt);
    for (GuiButton b : buttonList) {
      if (b.id == focusedId && b.enabled && b.visible) {
        UiTheme.border(b.x - 2, b.y - 2, b.x + b.width + 2, b.y + b.height + 2, UiTheme.AMBER);
        break;
      }
    }
  }

  @Override
  protected void mouseClicked(int mx, int my, int button) throws IOException {
    if (button == 0) {
      focusedId = -1;
      for (GuiButton b : buttonList) {
        if (b.enabled
            && b.visible
            && mx >= b.x
            && mx < b.x + b.width
            && my >= b.y
            && my < b.y + b.height) {
          focusedId = b.id;
          break;
        }
      }
    }
    super.mouseClicked(mx, my, button);
  }

  protected boolean focusKey(int key) throws IOException {
    if (key == Keyboard.KEY_TAB) {
      int direction = isShiftKeyDown() ? -1 : 1;
      int current = -1;
      for (int i = 0; i < buttonList.size(); i++)
        if (buttonList.get(i).id == focusedId) current = i;
      if (current < 0 && direction < 0) current = 0;
      for (int n = 1; n <= buttonList.size(); n++) {
        int i = (current + direction * n + buttonList.size() * 2) % buttonList.size();
        GuiButton b = buttonList.get(i);
        if (b.enabled && b.visible) {
          focusedId = b.id;
          return true;
        }
      }
      return true;
    }
    if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
      if (Keyboard.isRepeatEvent()) return true;
      for (GuiButton b : buttonList)
        if (b.id == focusedId && b.enabled && b.visible) {
          b.playPressSound(mc.getSoundHandler());
          actionPerformed(b);
          return true;
        }
      return true;
    }
    return false;
  }

  @Override
  protected void keyTyped(char c, int key) throws IOException {
    if (!focusKey(key)) super.keyTyped(c, key);
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }
}
