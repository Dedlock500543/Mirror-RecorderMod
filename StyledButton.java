package com.mirror.recorder.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.input.Mouse;

/**
 * A button of the unified style: accent, toggle, icon on the left and a second line with a short
 * description. The description is visible right away, without hovering; the tooltip remains the
 * full version.
 */
public class StyledButton extends GuiButton {
  public int accent = UiTheme.ACCENT;
  public boolean primary = false, toggle = false, on = false;
  public String[] tooltip = null;
  public String subtitle = null, icon = null, value = null;
  public boolean compact = false, flat = false;
  private float hover = 0f, press = 0f, knob = -1f;
  private float iconOnlyScale = 1f;
  private final UiMotion.Channel hoverMotion = new UiMotion.Channel(0f);
  private final UiMotion.Channel pressMotion = new UiMotion.Channel(0f);
  private final UiMotion.Channel knobMotion = new UiMotion.Channel(0f);
  /** Inline counter strip: it neither presses nor highlights the button itself. */
  private int guardL = 0, guardR = 0;
  /**
   * Inline "− N +" counter: the frame and number are drawn here, the "−"/"+" themselves are
   * separate buttons on top.
   */
  public String embedValue = null;

  public boolean embedDim = false;
  private int embedX, embedY, embedBtn, embedValW;
  private boolean wrapText, stackedCounter, selectedMark, boxedSpeedReset;

  /** A clear selected-item tick next to the label, without changing toggle behavior. */
  public StyledButton boxedSpeedReset() {
    boxedSpeedReset = true;
    return this;
  }

  public StyledButton selectedMark() {
    selectedMark = true;
    return this;
  }

  private boolean hasExpander, expanderOpen;

  public StyledButton expander(boolean open) {
    hasExpander = true;
    expanderOpen = open;
    return this;
  }

  static int expanderX(int right, int height) {
    return right - checkSize(height) - UiTheme.BTN_PAD - 12;
  }

  public StyledButton wrapText() {
    wrapText = true;
    return this;
  }

  public StyledButton stackCounter(boolean stack) {
    stackedCounter = stack;
    return this;
  }
  /** Moment the row appears in the cascade and moment of the feedback flash. */
  private long appearAt = -1L, flashAt = 0L;
  /** Borderless brightness feedback: 0..1, set by the row owner every frame. */
  public float embedFlash = 0f;

  /** The row fades in at its final geometry starting from this moment. */
  public StyledButton appear(long at) {
    appearAt = at;
    return this;
  }

  /** Short feedback on a change of this button's value. */
  public void flash() {
    flashAt = UiTheme.nowMs();
  }

  /** Row checkbox size: the layout outside computes it the same way as drawing does. */
  public static int checkSize(int h) {
    return Math.max(10, Math.min(UiTheme.CHECK, h - 12));
  }
  /** Share of enabled nested settings: draws the "partly on" state. */
  public float mixed = 0f;

  public StyledButton embedStep(int bx, int by, int bs, int valW) {
    embedX = bx;
    embedY = by;
    embedBtn = bs;
    embedValW = valW;
    guardL = bx - UiTheme.GAP;
    guardR = bx + bs * 2 + valW + UiTheme.GAP;
    return this;
  }

  /** Extend the protected inline control area to include a selector next to the counter. */
  public StyledButton extendGuardLeft(int left) {
    guardL = Math.min(guardL, left);
    return this;
  }

  /** The cursor is in the counter strip: the button itself does not react to it. */
  public boolean inGuard(int mx) {
    return UiMotion.inGuard(mx, guardL, guardR);
  }

  public boolean inGuard(int mx, int my) {
    return inGuard(mx) && (!stackedCounter || my >= embedY && my < embedY + embedBtn);
  }

  public StyledButton(int id, int x, int y, int w, int h, String label) {
    super(id, x, y, w, h, label);
  }

  public StyledButton accent(int c) {
    accent = c;
    return this;
  }

  public StyledButton primary() {
    primary = true;
    return this;
  }

  public StyledButton asToggle(boolean state) {
    toggle = true;
    on = state;
    knob = state ? 1f : 0f;
    knobMotion.snap(knob);
    return this;
  }

  public StyledButton tip(String... t) {
    tooltip = t;
    return this;
  }

  public StyledButton sub(String text) {
    subtitle = text;
    return this;
  }

  public StyledButton icon(String glyph) {
    icon = glyph;
    return this;
  }

  /** Scale only the glyph of an icon-only button, keeping its hit rectangle unchanged. */
  public StyledButton iconScale(float scale) {
    iconOnlyScale = Math.max(0.1f, scale);
    return this;
  }

  public StyledButton value(String text) {
    value = text;
    return this;
  }

  public StyledButton compact() {
    compact = true;
    return this;
  }
  /** No own background or border: part of a single block. */
  public StyledButton flat() {
    flat = true;
    return this;
  }

  public void setOn(boolean state) {
    on = state;
  }

  public boolean isHover(int mx, int my) {
    return visible && UiMotion.contains(mx, my, x, y, width, height) && !inGuard(mx, my);
  }

  @Override
  public boolean mousePressed(Minecraft mc, int mx, int my) {
    boolean accepted = enabled && isHover(mx, my);
    if (accepted) {
      UiBubbles.burst(mx, my, accent);
    }
    return accepted;
  }

  @Override
  public void drawButton(Minecraft mc, int mx, int my, float pt) {
    if (!visible) return;
    float ap = Appearance.progress(appearAt, UiTheme.nowMs());
    if (ap <= 0f) return;
    if (ap >= 1f) {
      drawBody(mc, mx, my, pt);
      return;
    }
    try {
      UiTheme.opacity(ap);
      drawBody(mc, mx, my, pt);
    } finally {
      UiTheme.opacityReset();
    }
  }

  private void drawBody(Minecraft mc, int mx, int my, float pt) {
    hovered = isHover(mx, my);
    updateAnimations();
    float h = hover;
    int acc = toggle ? UiTheme.lerp(UiTheme.GRAY, UiTheme.GREEN, knob) : accent;
    if (flat) {
      drawFlat(mc, h, press);
      return;
    }
    UiTheme.buttonBg(x, y, x + width, y + height, acc, hover, enabled, press, primary && !toggle);
    drawFlash(acc);
    // Press feedback is color-only: label, glyph and counter coordinates stay aligned.
    int shift = 0;
    if (icon == null && UiTheme.isControlSymbol(displayString)) {
      UiTheme.controlSymbol(
          displayString,
          x + width / 2f,
          y + height / 2f + shift,
          Math.max(0.85f, Math.min(1.2f, Math.min(width, height) / 14f)),
          enabled ? UiTheme.lerp(UiTheme.TEXT_DIM, UiTheme.TEXT, h) : UiTheme.TEXT_MUTE);
      return;
    }
    int left = drawIcon(mc, shift, acc);
    if (selectedMark) {
      float mid = y + height / 2f;
      UiFinish.selectionTick(left, mid, left + 3f, mid + 3f, 1.7f, UiTheme.TEXT);
      left += 14;
    }
    int right = x + width - UiTheme.BTN_PAD;
    if (toggle) right = drawToggle(mc, shift);
    else if (value != null) right = drawValue(mc, shift, h);
    drawLabel(mc, left, right, shift, h);
  }

  /** Hover, press and toggle transitions towards their current targets. */
  private void updateAnimations() {
    long now = UiTheme.nowMs();
    boolean animated = UiTheme.animationsEnabled();
    boolean hTarget = hovered && enabled, pTarget = hTarget && Mouse.isButtonDown(0);
    if (knob < 0f) knobMotion.snap(on ? 1f : 0f);
    hover = hoverMotion.update(hTarget ? 1f : 0f, now, UiTheme.HOVER_MS, animated);
    press = pressMotion.update(pTarget ? 1f : 0f, now, UiTheme.PRESS_MS, animated);
    knob = knobMotion.update(on ? 1f : 0f, now, UiTheme.KNOB_MS, animated);
  }

  /** Flat button: no background or border, only a hover highlight and the label. */
  private void drawFlat(Minecraft mc, float h, float p) {
    int col =
        !enabled
            ? UiTheme.alpha(UiTheme.TEXT_MUTE, 0.7f)
            : UiTheme.lerp(UiTheme.TEXT_DIM, UiTheme.TEXT, h);
    if (boxedSpeedReset) UiTheme.inset(x, y, x + width, y + height);
    if (enabled && h > 0.01f)
      UiTheme.round(
          x + 1,
          y + 1,
          x + width - 1,
          y + height - 1,
          UiTheme.alpha(UiTheme.ACCENT, 0.10f * h + 0.10f * p));
    if (boxedSpeedReset) FolderIcons.drawSpeedReset(x + width / 2f, y + height / 2f, 1.25f, col);
    else if (icon != null && displayString.isEmpty())
      UiTheme.controlSymbol(
          icon,
          x + width / 2f,
          y + height / 2f,
          "reset".equals(icon)
              ? Math.min(1.8f, Math.min(width, height) / 10f)
              : Math.max(0.85f, Math.min(1.2f, Math.min(width, height) / 14f)),
          col);
    else if (UiTheme.isControlSymbol(displayString))
      UiTheme.controlSymbol(
          displayString,
          x + width / 2f,
          y + height / 2f,
          Math.max(0.85f, Math.min(1.2f, Math.min(width, height) / 14f)),
          col);
    else
      UiTheme.drawCenteredFitted(
          UiFonts.get(mc),
          displayString,
          x + width / 2f,
          y + (height - UiFonts.get(mc).FONT_HEIGHT) / 2,
          Math.max(0, width - 8),
          col,
          false);
  }

  /** Short feedback flash after a value change. */
  private void drawFlash(int acc) {
    if (!UiTheme.animationsEnabled()) {
      flashAt = 0L;
      return;
    }
    if (flashAt > 0L) {
      long fe = UiTheme.nowMs() - flashAt;
      if (fe >= UiTheme.FLASH_MS) flashAt = 0L;
      else UiTheme.flashRing(x, y, x + width, y + height, acc, 1f - fe / (float) UiTheme.FLASH_MS);
    }
  }

  /** Icon badge on the left; returns the x where the label starts. */
  private int drawIcon(Minecraft mc, int shift, int acc) {
    int left = x + UiTheme.BTN_PAD;
    if (icon != null && displayString.isEmpty() && width >= 8 && height >= 8) {
      // Icon-only controls (collapse/search) must remain visible at compact widths.
      UiTheme.controlSymbol(
          icon,
          x + width / 2f,
          y + height / 2f,
          iconOnlyScale,
          enabled ? UiTheme.TEXT : UiTheme.TEXT_MUTE);
      return x + width;
    }
    boolean smallMenuIcon = compact && ("settings".equals(icon) || "exit".equals(icon));
    if (icon != null && width >= 44 && (height >= 16 || (smallMenuIcon && height >= 12))) {
      int size = Math.min(UiTheme.ICON, height - 2), pad = UiTheme.BTN_PAD;
      UiTheme.badge(
          UiFonts.get(mc),
          icon,
          x + pad,
          y + (height - size) / 2 + shift,
          size,
          acc,
          enabled && (on || !toggle));
      left = x + pad + size + UiTheme.ICON_GAP;
    }
    return left;
  }

  /** Checkbox at the right edge and the inline counter; returns the right edge for the label. */
  private int drawToggle(Minecraft mc, int shift) {
    int sw = checkSize(height),
        sx = x + width - sw - UiTheme.BTN_PAD,
        sy = y + (height - sw) / 2 + shift;
    UiTheme.checkAt(sx, sy, sw, knob, enabled, UiTheme.GREEN, mixed);
    int right = sx - UiTheme.GAP;
    if (hasExpander) {
      UiTheme.chevron(
          expanderX(x + width, height),
          y + height / 2f,
          7f,
          expanderOpen ? 1f : 0f,
          UiTheme.TEXT_DIM);
      right = Math.min(right, sx - 20);
    }
    if (embedValue != null && embedBtn > 0) {
      // The counter is left of the toggle: the label must not overlap it.
      if (!stackedCounter) right = Math.min(right, guardL);
      drawEmbeddedCounter(mc);
    }
    return right;
  }

  /** Inline "− N +" counter: frame, flash and number; the "−"/"+" are separate buttons. */
  private void drawEmbeddedCounter(Minecraft mc) {
    int bw = embedBtn * 2 + embedValW;
    UiTheme.counterFrame(embedX, embedY, embedBtn, embedValW);
    if (embedFlash > 0.02f && UiTheme.animationsEnabled())
      UiTheme.counterFeedback(embedX, embedY, embedX + bw, embedY + embedBtn, embedFlash);
    UiTheme.drawCenteredFitted(
        UiFonts.get(mc),
        embedValue,
        embedX + embedBtn + embedValW / 2f,
        embedY + (embedBtn - UiFonts.get(mc).FONT_HEIGHT) / 2,
        Math.max(8, embedValW - 4),
        embedDim ? UiTheme.TEXT_MUTE : UiTheme.TEXT,
        false);
  }

  /** Value text at the right edge; returns the right edge for the label. */
  private int drawValue(Minecraft mc, int shift, float h) {
    String val = value;
    int maxVal = Math.max(16, width / 2 - 8),
        vw = UiTheme.fittedWidth(UiFonts.get(mc), val, maxVal);
    UiTheme.drawFitted(
        UiFonts.get(mc),
        val,
        x + width - UiTheme.BTN_PAD - 2 - vw,
        y + (height - UiFonts.get(mc).FONT_HEIGHT) / 2 + shift,
        maxVal,
        enabled ? UiTheme.lerp(UiTheme.TEXT_DIM, UiTheme.TEXT, h) : UiTheme.TEXT_MUTE);
    return x + width - UiTheme.BTN_PAD - 2 - vw - UiTheme.GAP;
  }

  /** Label, optionally with a subtitle line, between left and right. */
  private void drawLabel(Minecraft mc, int left, int right, int shift, float h) {
    int base = enabled ? UiTheme.TEXT_DIM : UiTheme.TEXT_MUTE,
        live = enabled ? UiTheme.TEXT : UiTheme.TEXT_MUTE;
    int textColor = primary && !toggle && enabled ? UiTheme.TEXT : UiTheme.lerp(base, live, h);
    int room = Math.max(0, right - left);
    boolean twoLine = subtitle != null && height >= 26 && room >= 30;
    if (!toggle && icon == null && value == null && !twoLine && !compact) {
      UiTheme.drawCenteredFitted(
          UiFonts.get(mc),
          displayString,
          x + width / 2f,
          y + (height - UiFonts.get(mc).FONT_HEIGHT) / 2 + shift,
          Math.max(0, width - 12),
          textColor,
          false);
      return;
    }
    String label = displayString;
    if (wrapText) {
      int textH = stackedCounter ? embedY - y - 5 : height - 8;
      UiRowText.draw(
          UiFonts.get(mc),
          label,
          subtitle,
          left,
          y + 4,
          room,
          textH,
          textColor,
          enabled ? UiTheme.TEXT_MUTE : UiTheme.alpha(UiTheme.TEXT_MUTE, 0.6f));
      return;
    }
    if (twoLine) {
      int blockY = y + Math.max(0, (height - (UiFonts.get(mc).FONT_HEIGHT * 2 + 1)) / 2) + shift;
      UiTheme.drawFitted(UiFonts.get(mc), label, left, blockY, room, textColor);
      UiTheme.drawFitted(
          UiFonts.get(mc),
          subtitle,
          left,
          blockY + UiFonts.get(mc).FONT_HEIGHT + 1,
          room,
          enabled ? UiTheme.TEXT_MUTE : UiTheme.alpha(UiTheme.TEXT_MUTE, 0.6f));
    } else {
      UiTheme.drawFitted(
          UiFonts.get(mc),
          label,
          left,
          y + (height - UiFonts.get(mc).FONT_HEIGHT) / 2 + shift,
          room,
          textColor);
    }
  }
}
