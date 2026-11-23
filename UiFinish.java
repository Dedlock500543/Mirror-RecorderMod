package com.mirror.recorder.gui;
/** Visual-only helpers. Text, layout, input and playback state are unchanged. */
final class UiFinish {
  static void selectionTick(float x, float y, float ignoredX, float ignoredY, float ignoredWidth, int color) {
    UiTheme.line(x + 0.4f, y + 0.15f, x + 2.9f, y + 2.55f, 1.45f, color);
    UiTheme.line(x + 2.9f, y + 2.55f, x + 8f, y - 3.15f, 1.45f, color);
  }
  static void skipTickArm(float a, float b, float c, float d, float width, int color) {}
  static void hudShadow(int l, int t, int r, int b, int ignored) {
    UiTheme.round(l - 1, t + 1, r + 1, b + 2, 0x24000000);
    UiTheme.round(l, t, r, b, 0x48000000);
  }
  static void hudSurface(int l, int t, int r, int b, int ignored) {
    UiTheme.roundGradient(l, t, r, b, 0xF219273D, 0xEE0B1320);
    if (r - l > 20)
      UiTheme.line(l + 7f, t + 1.1f, r - 7f, t + 1.1f, 0.85f, 0x26FFFFFF);
  }
  static void hudHeader(int l, int t, int r, int b, int color) {
    int rgb = color & 0x00FFFFFF;
    UiTheme.roundGradient(l, t, r, b, 0x28000000 | rgb, 0x05000000 | rgb);
    if (b - t > 16) UiTheme.line(l + 4f, t + 7f, l + 4f, b - 7f, 1.8f, 0xB8000000 | rgb);
    if (r - l > 40) UiTheme.line(l + 10f, b - 0.8f, r - 10f, b - 0.8f, 0.65f, 0x36000000 | rgb);
  }
  static void hudIconBadge(int l, int t, int r, int b, int color) {
    int rgb = color & 0x00FFFFFF;
    UiTheme.border(l - 1, t - 1, r + 1, b + 1, 0x22000000 | rgb);
    UiTheme.roundGradient(l, t, r, b, 0x40000000 | rgb, 0x1C000000 | rgb);
    UiTheme.border(l, t, r, b, 0x72000000 | rgb);
    if (r - l > 8) UiTheme.line(l + 3f, t + 1f, r - 3f, t + 1f, 0.7f, 0x28FFFFFF);
  }
  static void hudTrack(int l, int t, int r, int b, int ignored) {
    UiTheme.roundGradient(l, t, r, b, 0xFF283951, 0xFF172436);
    UiTheme.border(l, t, r, b, 0x24FFFFFF);
  }
  static void hudTrackPulse(int l, int t, int r, int b, int color) {
    UiTheme.border(l - 1, t - 1, r + 1, b + 1, UiTheme.alpha(color, 0.22f));
    UiTheme.roundGradient(l, t, r, b, UiTheme.lerp(color, 0xFFFFFFFF, 0.22f), color);
  }
  static void hudTrackFill(int l, int t, int r, int b, int color) {
    UiTheme.roundGradient(l, t, r, b, UiTheme.lerp(color, 0xFFFFFFFF, 0.18f), color);
  }
}
