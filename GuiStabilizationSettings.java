package com.mirror.recorder.gui;

import com.mirror.recorder.config.RecorderConfig;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

/**
 * Legacy compatibility screen for existing callers. Settings exposes the same controls inline in
 * the original Return to start group, without a separate Advanced navigation action.
 */
public final class GuiStabilizationSettings extends ModScreen {

  private static final int MASTER = 1,
      CURSOR = 2,
      SAVE = 4,
      CANCEL = 5,
      POS_DEC = 10,
      POS_INC = 11,
      SPEED_DEC = 12,
      SPEED_INC = 13,
      TIME_DEC = 14,
      TIME_INC = 15;
  private final GuiScreen parent;
  private final RecorderConfig config;
  private int x, y, w, h, paramTop, rowH, paramStep = 24;
  private boolean oldMaster, oldCursor, saved = false;
  private float oldPos, oldSpeed;
  private int oldTime;
  private StyledButton master, cursor;
  private final List<StyledButton> dependent = new ArrayList<StyledButton>();

  public GuiStabilizationSettings(GuiScreen parent, RecorderConfig config) {
    this.parent = parent;
    this.config = config;
    snapshot();
  }

  private void snapshot() {
    oldMaster = config.isAutoReturnEnabled();
    oldCursor = config.isCursorStabilization();
    oldPos = config.getReturnPositionTolerance();
    oldSpeed = config.getReturnAlignSpeed();
    oldTime = config.getReturnTimeoutSeconds();
  }

  private void restore() {
    config.setAutoReturnEnabled(oldMaster);
    config.setCursorStabilization(oldCursor);
    config.setReturnPositionTolerance(oldPos);
    config.setReturnAlignSpeed(oldSpeed);
    config.setReturnTimeoutSeconds(oldTime);
  }

  private boolean changed() {
    return oldMaster != config.isAutoReturnEnabled()
        || oldCursor != config.isCursorStabilization()
        || oldPos != config.getReturnPositionTolerance()
        || oldSpeed != config.getReturnAlignSpeed()
        || oldTime != config.getReturnTimeoutSeconds();
  }

  private StyledButton add(StyledButton b) {
    buttonList.add(b);
    return b;
  }

  private StyledButton dep(StyledButton b) {
    return add(b);
  }

  private void addStepper(int decId, int incId, int rowY, String title, String detail) {
    int bw = 22,
        chip = 58,
        gap = 4,
        right = x + w - 12,
        incX = right - bw,
        chipX = incX - gap - chip,
        decX = chipX - gap - bw;
    dep(
        new StyledButton(decId, decX, rowY, bw, rowH, "\u2212")
            .accent(UiTheme.GRAY)
            .tip(
                Lang.t("stabilizationSettings.decrease") + title,
                detail,
                Lang.t("stabilizationSettings.click_to_decrease_the_value")));
    dep(
        new StyledButton(incId, incX, rowY, bw, rowH, "+")
            .accent(UiTheme.GRAY)
            .tip(
                Lang.t("stabilizationSettings.increase") + title,
                detail,
                Lang.t("stabilizationSettings.click_to_increase_the_value")));
  }

  @Override
  public void initGui() {
    buttonList.clear();
    dependent.clear();
    w = Math.min(width - 20, 390);
    h = Math.min(height - 12, 277);
    x = (width - w) / 2;
    y = (height - h) / 2;
    int left = x + 12, fullW = w - 24, toggleH = 20, ty = y + 48;
    master =
        add(
            new StyledButton(
                    MASTER,
                    left,
                    ty,
                    fullW,
                    toggleH,
                    Lang.t("stabilizationSettings.baritone_auto_return"))
                .asToggle(config.isAutoReturnEnabled())
                .tip(
                    Lang.t("stabilizationSettings.return_to_the_start_point"),
                    Lang.t("stabilizationSettings.on_before_playback_the_mod"),
                    Lang.t("stabilizationSettings.off_playback_starts_where_you"),
                    Lang.t("stabilizationSettings.requires_baritone_if_it_cannot")));
    ty += toggleH + 5;
    cursor =
        dep(
            new StyledButton(
                    CURSOR,
                    left,
                    ty,
                    fullW,
                    toggleH,
                    Lang.t("stabilizationSettings.restore_the_view_to_the"))
                .asToggle(config.isCursorStabilization())
                .tip(
                    Lang.t("stabilizationSettings.view_as_at_the_recording"),
                    Lang.t("stabilizationSettings.before_the_start_the_camera"),
                    Lang.t("stabilizationSettings.works_both_with_and_without"),
                    Lang.t("stabilizationSettings.after_that_you_control_the")));
    ty += toggleH + 12;
    paramTop = ty + 11;
    rowH = 18;
    int fy = y + h - 32;
    paramStep = Math.max(19, Math.min(24, (fy - 6 - paramTop - rowH) / 2));
    int step = paramStep;
    addStepper(
        POS_DEC,
        POS_INC,
        paramTop,
        Lang.t("stabilizationSettings.return_accuracy"),
        Lang.t("stabilizationSettings.how_close_to_the_start"));
    addStepper(
        SPEED_DEC,
        SPEED_INC,
        paramTop + step,
        Lang.t("stabilizationSettings.approach_speed"),
        Lang.t("stabilizationSettings.how_fast_to_walk_on"));
    addStepper(
        TIME_DEC,
        TIME_INC,
        paramTop + step * 2,
        Lang.t("stabilizationSettings.how_long_to_wait"),
        Lang.t("stabilizationSettings.if_it_cannot_get_there"));
    int bw = (fullW - 6) / 2;
    add(
        new StyledButton(CANCEL, left, fy, bw, 20, Lang.t("common.cancel"))
            .accent(UiTheme.GRAY)
            .tip(
                Lang.t("confirm.cancel"),
                Lang.t("stabilizationSettings.restores_the_values_you_had")));
    add(
        new StyledButton(
                SAVE, left + bw + 6, fy, fullW - bw - 6, 20, Lang.t("stabilizationSettings.apply"))
            .primary()
            .accent(UiTheme.GREEN)
            .tip(
                Lang.t("stabilizationSettings.apply_2"),
                Lang.t("stabilizationSettings.carries_these_values_over_to"),
                Lang.t("stabilizationSettings.to_keep_them_for_good")));
    sync();
  }

  private static float snap2(float v) {
    return Math.round(v * 100f) / 100f;
  }

  private static float snap1(float v) {
    return Math.round(v * 10f) / 10f;
  }

  private void sync() {
    master.setOn(config.isAutoReturnEnabled());
    cursor.setOn(config.isCursorStabilization());
  }

  @Override
  public void drawScreen(int mx, int my, float pt) {
    {
      int bd = UiTheme.GLASS_BACKDROP;
      drawGradientRect(0, 0, width, height, bd, bd);
    }
    UiTheme.window(x, y, x + w, y + h, 30);
    int hx = x + 12, hy = y + 11;
    Gui.drawRect(hx, hy, hx + 3, hy + 10, UiTheme.ACCENT);
    UiTheme.drawFitted(
        fontRenderer,
        Lang.t("stabilizationSettings.stabilization_and_auto_return"),
        hx + 9,
        hy,
        w - 40,
        UiTheme.TEXT);
    UiTheme.sectionLabel(
        fontRenderer, Lang.t("stabilizationSettings.behavior"), x + 16, y + 36, w - 32);
    UiTheme.sectionLabel(
        fontRenderer,
        Lang.t("stabilizationSettings.baritone_approach"),
        x + 16,
        paramTop - 12,
        w - 24);
    String[] names = {
      Lang.t("stabilizationSettings.position_accuracy"),
      Lang.t("stabilizationSettings.approach_speed_2"),
      Lang.t("stabilizationSettings.timeout")
    };
    String[] values = {
      String.format(Locale.ROOT, "%.2f", config.getReturnPositionTolerance()),
      String.format(Locale.ROOT, "%.1f", config.getReturnAlignSpeed()),
      config.getReturnTimeoutSeconds() + Lang.t("cmd.s")
    };
    int step = paramStep,
        chip = 58,
        bw = 22,
        gap = 4,
        right = x + w - 12,
        chipX = right - bw - gap - chip;
    for (int i = 0; i < 3; i++) {
      int ry = paramTop + step * i;
      UiTheme.drawFitted(
          fontRenderer, names[i], x + 18, ry + 5, Math.max(20, chipX - x - 24), UiTheme.TEXT_DIM);
      UiTheme.inset(chipX, ry, chipX + chip, ry + rowH);
      UiTheme.drawCenteredFitted(
          fontRenderer, values[i], chipX + chip / 2f, ry + 5, chip - 6, UiTheme.TEXT, false);
    }
    if (y + h - 40 >= paramTop + step * 2 + rowH + 2)
      fontRenderer.drawString(
          changed()
              ? Lang.t("stabilizationSettings.unsaved_changes")
              : Lang.t("stabilizationSettings.esc_cancel"),
          x + 16,
          y + h - 40,
          UiTheme.TEXT_MUTE);
    sync();
    super.drawScreen(mx, my, pt);
    for (GuiButton raw : buttonList)
      if (raw instanceof StyledButton) {
        StyledButton b = (StyledButton) raw;
        if (b.visible && b.isMouseOver() && b.tooltip != null) {
          if (UiTheme.tipReady(b)) UiTheme.tooltip(fontRenderer, b.tooltip, mx, my, width, height);
          break;
        }
      }
  }

  @Override
  protected void actionPerformed(GuiButton b) throws IOException {
    switch (b.id) {
      case MASTER:
        config.setAutoReturnEnabled(!config.isAutoReturnEnabled());
        break;
      case CURSOR:
        config.setCursorStabilization(!config.isCursorStabilization());
        break;
      case POS_DEC:
        config.setReturnPositionTolerance(snap2(config.getReturnPositionTolerance() - 0.01f));
        break;
      case POS_INC:
        config.setReturnPositionTolerance(snap2(config.getReturnPositionTolerance() + 0.01f));
        break;
      case SPEED_DEC:
        config.setReturnAlignSpeed(snap1(config.getReturnAlignSpeed() - 0.1f));
        break;
      case SPEED_INC:
        config.setReturnAlignSpeed(snap1(config.getReturnAlignSpeed() + 0.1f));
        break;
      case TIME_DEC:
        config.setReturnTimeoutSeconds(config.getReturnTimeoutSeconds() - 15);
        break;
      case TIME_INC:
        config.setReturnTimeoutSeconds(config.getReturnTimeoutSeconds() + 15);
        break;
      case SAVE:
        saved = true;
        snapshot();
        mc.displayGuiScreen(parent);
        break;
      case CANCEL:
        restore();
        mc.displayGuiScreen(parent);
        break;
    }
    sync();
  }

  @Override
  protected void keyTyped(char c, int key) throws IOException {
    if (key == Keyboard.KEY_ESCAPE) {
      restore();
      mc.displayGuiScreen(parent);
    } else super.keyTyped(c, key);
  }

  @Override
  public void onGuiClosed() {
    if (!saved) restore();
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }
}
