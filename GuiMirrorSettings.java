package com.mirror.recorder.gui;

import com.mirror.recorder.config.RecorderConfig;
import com.mirror.recorder.manager.RecorderManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Mod settings: the category list on the left, only the selected category's options on the right.
 * The hierarchy comes from the category header, indents and row cards, not separators.
 */
public class GuiMirrorSettings extends ModScreen {

  private static final int SAVE = 212,
      CANCEL = 213,
      FOLD_ALL = 214,
      HELP_PREV = 220,
      HELP_NEXT = 221,
      VIS_PATH = 230,
      VIS_MARK = 231,
      TAB_BASE = 300,
      TAB_COUNT = 5,
      DEC_BASE = 1000,
      INC_BASE = 2000,
      ACT_BASE = 3000;
  private static final int ROW_PLAY_MOVE = 7,
      ROW_PLAY_JUMP = 8,
      ROW_PLAY_SNEAK = 9,
      ROW_PLAY_SPRINT = 10,
      ROW_PLAY_INTERACTION = 11,
      ROW_PLAY_CHAT = 12,
      ROW_APPLY_ROTATION = 13,
      ROW_STOP_ON_MOVE = 14,
      ROW_AUTO_RETURN = 15,
      ROW_ROUTE_STABILIZATION = 16,
      ROW_CURSOR_STABILIZATION = 17,
      ROW_RETURN_TOLERANCE = 18,
      ROW_RETURN_ALIGN_SPEED = 19,
      ROW_RETURN_TIMEOUT = 20,
      // 21 was the separate stabilization screen; keep the retired id unused.
      ROW_START_DELAY = 23,
      ROW_SKIP_LOOP_DELAY = 24,
      ROW_MARKER_MODE = 26,
      ROW_MARKER_RADIUS = 27,
      ROW_MARKER_BEACON_HEIGHT = 28,
      ROW_MARKER_PULSE = 29,
      ROW_MARKER_LABEL = 30,
      ROW_MARKER_LABEL_DISTANCE = 31,
      ROW_MARKER_LABEL_UNLIMITED = 32,
      ROW_MARKER_THROUGH_WALLS = 33,
      ROW_MARKER_CUSTOM_COLOR = 34,
      ROW_TOOLTIPS = 35,
      ROW_LANGUAGE = 36,
      ROW_FACTORY_RESET = 37,
      ROW_PATH_LINE = 44,
      ROW_PATH_STYLE = 45,
      ROW_PATH_WIDTH = 46,
      ROW_PATH_LENGTH = 47,
      ROW_PATH_POINT_STEP = 48,
      ROW_PATH_POINT_SIZE = 49,
      ROW_PATH_HEIGHT = 50,
      ROW_PATH_FADE = 51,
      ROW_PATH_THROUGH_WALLS = 52,
      ROW_RESET_VISUALS = 53,
      ROW_COLOR_TARGET = 54,
      ROW_PLAYBACK_SPEED = 55,
      ROW_SOUNDS = 56,
      ROW_GUARD_ON_DAMAGE = 57,
      ROW_TRASH = 58,
      ROW_PLAYBACK_SPEED_RESET = 59,
      ROW_LOOP_LIMIT = 60,
      ROW_AUTO_LEAVE = 61,
      ROW_LEAVE_CYCLES = 62,
      ROW_VISIBLE_CHAT = 63,
      // 64 was the removed exact block placement row: keep it unused so ids stay stable.
      ROW_STOP_ON_MOUSE = 65,
      ROW_STOP_ON_MOUSE_PX = 66,
      ROW_MOD_MESSAGES = 67,
      ROW_AUTO_DELETE_MESSAGES = 68,
      ROW_MESSAGE_LIFETIME = 69,
      ROW_UI_ANIMATIONS = 70,
      ROW_STOP_ON_ROUTE = 71,
      ROW_PLAY_MOD_COMMANDS = 72,
      ROW_LEAVE_MODE = 73;
  private static final int T_TOGGLE = 0,
      T_STEP = 1,
      T_CYCLE = 2,
      T_ACTION = 3,
      T_HEAD = 5,
      HEAD_KEY_BASE = -2000,
      GROUP_HEAD_H = 26,
      GROUP_HEAD_STEP = 32,
      LEAVE_CONTROL_GAP = 6,
      COLOR_PANEL_GAP = 4;

  private static final class Row {
    final int type, key, color;
    final String label, note, icon;
    final String[] tip;

    Row(int type, int key, String label, String note, String icon, int color, String[] tip) {
      this.type = type;
      this.key = key;
      this.label = label;
      this.note = note;
      this.icon = icon;
      this.color = color;
      this.tip = tip;
    }
  }

  private final RecorderManager manager;
  private final RecorderConfig config;
  private final GuiScreen parent;
  private final List<Row> rows = new ArrayList<Row>();
  private GuiColorWheel wheel;
  private StyledButton save, cancel;
  private static int colorTarget = 0, visElement = 0;
  /**
   * While true, the full search catalog is built: rows hidden by current values and the selected
   * object are included too.
   */
  private boolean fullCatalog = false;

  private int tab = 1, scroll = 0;
  private boolean colorPanelOpen = false, revealColorRow = false;
  private boolean returnDetailsOpen = false, revealReturnDetails = false;
  private int[] rowY = new int[0];
  private int[] snapI;
  private float[] snapF;
  private boolean[] snapB;
  private String snapLang = "ru";
  private boolean saved = false, openingChild = false, wheelTouched = false;
  /** Save outcome: everything written. */
  private static final int SAVE_OK = 0;

  /** Save outcome: the save failed, nothing written. */
  private static final int SAVE_NONE = 1;

  /** Save outcome: the save failed after only the shared file was written. */
  private static final int SAVE_PARTIAL = 2;

  private int saveError = SAVE_OK;
  private int winX,
      winY,
      winW,
      winH,
      headH,
      sideX,
      sideW,
      sideTop,
      sideRowH,
      contentX,
      contentRight,
      contentTop,
      contentBottom,
      listBottom,
      footerY,
      rowH,
      visibleRows,
      maxScroll,
      wheelSize,
      colorPanelY = -1;
  private float sbHover = 0f, sbHoverFrom = 0f;
  private boolean sbDrag = false, sbHoverTarget = false;
  private long sbHoverAt = 0L;
  private int sbGrab = 0;
  private int helpSection = 0,
      helpScroll = 0,
      helpMaxScroll = 0,
      helpListX,
      helpListRight,
      helpListTop,
      helpRowH = 14,
      helpTextX,
      helpTextRight,
      helpTextTop,
      helpVisible = 1;
  private final List<UiTextBlock.Line> helpLines = new ArrayList<UiTextBlock.Line>();
  // static: collapsed sections survive closing/reopening the screen (including via /mirror gui)
  // within a game session.
  private static final boolean[][] collapsed = new boolean[TAB_COUNT][24];
  /** Start of the row appearance cascade. */
  private long listAnimAt = 0L;

  private long[] rowAppearAt = new long[0];
  /**
   * Search box across all sections: handles typos, transliteration and the wrong keyboard layout.
   */
  private CenteredTextField search;

  private TimeTextField leaveEditor;
  private int leaveEditX = -1, leaveEditY, leaveEditW, leaveEditH;

  private String searchQuery = "";

  private boolean searchFocused = false;

  private boolean searchActive() {
    return searchQuery.length() > 0;
  }
  /** For a header row: bottom edge of the module card with all nested settings. */
  private int[] cardBottom = new int[0];
  /** What was enabled in the module before it was switched off by the master checkbox. */
  private static final HashMap<Integer, List<Integer>> groupMemory =
      new HashMap<Integer, List<Integer>>();
  /** Last changed value: counters briefly brighten without an outline. */
  private int flashKey = -1;

  private long flashAt = 0L;

  private float flashAmt(int key) {
    if (!config.isUiAnimationsEnabled() || key != flashKey || flashAt <= 0L) return 0f;
    long e = UiTheme.nowMs() - flashAt;
    if (e >= UiTheme.FLASH_MS) return 0f;
    return 1f - e / (float) UiTheme.FLASH_MS;
  }

  public GuiMirrorSettings(RecorderManager m, RecorderConfig c, GuiScreen p) {
    this(m, c, p, 0);
  }

  public GuiMirrorSettings(RecorderManager m, RecorderConfig c, GuiScreen p, int startTab) {
    manager = m;
    config = c;
    parent = p;
    tab = Math.max(TABS[0], Math.min(TAB_COUNT - 1, startTab));
    listAnimAt = UiTheme.nowMs();
    snapshot();
  }

  // ------------------------------------------------ value snapshot
  private int[] ints() {
    return new int[] {
      config.getStartDelay(),
      config.getPathLineR(),
      config.getPathLineG(),
      config.getPathLineB(),
      config.getPathLineAlpha(),
      config.getStartMarkerMode(),
      config.getReturnTimeoutSeconds(),
      config.getPathLineStyle(),
      config.getPathLineLength(),
      config.getPathPointStep(),
      config.getMarkerOwnR(),
      config.getMarkerOwnG(),
      config.getMarkerOwnB(),
      config.getMarkerOwnAlpha(),
      config.getMarkerLabelDistance(),
      config.getLoopLimit(),
      config.getAutoLeaveCycles(),
      config.getStopOnMouseThreshold(),
      config.getModMessageLifetimeSeconds(),
      config.getAutoLeaveMinutes()
    };
  }

  private void applyInts(int[] v) {
    config.setStartDelay(v[0]);
    config.setPathLineR(v[1]);
    config.setPathLineG(v[2]);
    config.setPathLineB(v[3]);
    config.setPathLineAlpha(v[4]);
    config.setStartMarkerMode(v[5]);
    config.setReturnTimeoutSeconds(v[6]);
    config.setPathLineStyle(v[7]);
    config.setPathLineLength(v[8]);
    config.setPathPointStep(v[9]);
    config.setMarkerR(v[10]);
    config.setMarkerG(v[11]);
    config.setMarkerB(v[12]);
    config.setMarkerAlpha(v[13]);
    config.setMarkerLabelDistance(v[14]);
    if (v.length > 15) config.setLoopLimit(v[15]);
    if (v.length > 16) config.setAutoLeaveCycles(v[16]);
    if (v.length > 17) config.setStopOnMouseThreshold(v[17]);
    if (v.length > 18) config.setModMessageLifetimeSeconds(v[18]);
    if (v.length > 19) config.setAutoLeaveMinutes(v[19]);
  }

  private float[] floats() {
    return new float[] {
      config.getPathLineWidth(),
      config.getReturnPositionTolerance(),
      config.getReturnAlignSpeed(),
      config.getPathPointSize(),
      config.getPathLineHeight(),
      config.getMarkerRadius(),
      config.getMarkerBeaconHeight(),
      config.getPlaybackSpeed()
    };
  }

  private void applyFloats(float[] v) {
    config.setPathLineWidth(v[0]);
    config.setReturnPositionTolerance(v[1]);
    config.setReturnAlignSpeed(v[2]);
    config.setPathPointSize(v[3]);
    config.setPathLineHeight(v[4]);
    config.setMarkerRadius(v[5]);
    config.setMarkerBeaconHeight(v[6]);
    config.setPlaybackSpeed(v[7]);
  }

  private boolean[] bools() {
    return new boolean[] {
      config.isStopOnMove(),
      config.isApplyRotation(),
      config.isRouteStabilization(),
      config.isPathLineEnabled(),
      config.isShowTooltips(),
      config.isAutoReturnEnabled(),
      config.isCursorStabilization(),
      config.isSkipLoopStartDelay(),
      config.isPlaybackMovement(),
      config.isPlaybackJump(),
      config.isPlaybackSneak(),
      config.isPlaybackSprint(),
      config.isPlaybackInteraction(),
      config.isPlaybackChat(),
      config.isPathLineFade(),
      config.isPathLineThroughWalls(),
      config.isMarkerCustomColor(),
      config.isMarkerPulse(),
      config.isMarkerLabel(),
      config.isMarkerLabelUnlimited(),
      config.isMarkerThroughWalls(),
      config.isSoundsEnabled(),
      config.isGuardOnDamage(),
      config.isAutoLeaveEnabled(),
      config.isVisibleChat(),
      config.isStopOnMouseMove(),
      config.isSendModMessages(),
      config.isAutoDeleteModMessages(),
      config.isUiAnimationsEnabled(),
      config.isStopOnRouteDeviation(),
      config.isPlaybackModCommands(),
      config.isAutoLeaveTimed()
    };
  }

  private void applyBools(boolean[] v) {
    config.setStopOnMove(v[0]);
    config.setApplyRotation(v[1]);
    config.setRouteStabilization(v[2]);
    config.setPathLineEnabled(v[3]);
    config.setShowTooltips(v[4]);
    config.setAutoReturnEnabled(v[5]);
    config.setCursorStabilization(v[6]);
    config.setSkipLoopStartDelay(v[7]);
    config.setPlaybackMovement(v[8]);
    config.setPlaybackJump(v[9]);
    config.setPlaybackSneak(v[10]);
    config.setPlaybackSprint(v[11]);
    config.setPlaybackInteraction(v[12]);
    config.setPlaybackChat(v[13]);
    config.setPathLineFade(v[14]);
    config.setPathLineThroughWalls(v[15]);
    config.setMarkerCustomColor(v[16]);
    config.setMarkerPulse(v[17]);
    config.setMarkerLabel(v[18]);
    config.setMarkerLabelUnlimited(v[19]);
    config.setMarkerThroughWalls(v[20]);
    config.setSoundsEnabled(v[21]);
    config.setGuardOnDamage(v[22]);
    if (v.length > 23) config.setAutoLeaveEnabled(v[23]);
    if (v.length > 24) config.setVisibleChat(v[24]);
    if (v.length > 25) config.setStopOnMouseMove(v[25]);
    if (v.length > 26) config.setSendModMessages(v[26]);
    if (v.length > 27) config.setAutoDeleteModMessages(v[27]);
    if (v.length > 28) config.setUiAnimationsEnabled(v[28]);
    if (v.length > 29) config.setStopOnRouteDeviation(v[29]);
    if (v.length > 30) config.setPlaybackModCommands(v[30]);
    if (v.length > 31) config.setAutoLeaveTimed(v[31]);
  }
  /**
   * New baseline: the badge cache is reset immediately, otherwise "CHANGED" would linger for up to
   * 120 ms after a factory reset.
   */
  private void snapshot() {
    snapI = ints();
    snapF = floats();
    snapB = bools();
    snapLang = config.getLanguage();
    changedCache = false;
    changedAt = 0L;
  }

  private void restore() {
    applyInts(snapI);
    applyFloats(snapF);
    applyBools(snapB);
    config.setLanguage(snapLang);
  }

  private boolean changed() {
    return !Arrays.equals(snapI, ints())
        || !Arrays.equals(snapF, floats())
        || !Arrays.equals(snapB, bools())
        || !snapLang.equals(config.getLanguage());
  }

  private boolean changedCache = false;
  private long changedAt = 0L;
  /**
   * The "CHANGED" badge is refreshed a few times per second: a full snapshot comparison reads every
   * setting, and it is not needed every frame.
   */
  private boolean changedForChip() {
    long now = UiTheme.nowMs();
    if (changedAt == 0L || now - changedAt >= 120L) {
      changedCache = changed();
      changedAt = now;
    }
    return changedCache;
  }

  // ------------------------------------------------ categories
  private String tabName(int i) {
    switch (i) {
      case 0:
        return Lang.t("settings.stop");
      case 1:
        return Lang.t("settings.replay");
      case 2:
        return Lang.t("settings.visualization");
      case 3:
        return Lang.t("settings.general");
      default:
        return Lang.t("settings.help");
    }
  }

  private String tabHint(int i) {
    switch (i) {
      case 0:
        return Lang.t("settings.when_to_abort_playback");
      case 1:
        return Lang.t("settings.record_replay_loop");
      case 2:
        return Lang.t("settings.what_is_drawn_in_the");
      case 3:
        return Lang.t("settings.tooltips_language_reset");
      default:
        return Lang.t("settings.how_the_mod_works");
    }
  }

  private String tabIcon(int i) {
    switch (i) {
      case 0:
        return "tab_stop";
      case 1:
        return "\u25b6";
      case 2:
        return "tab_visuals";
      case 3:
        return "\u2699";
      default:
        return "?";
    }
  }

  private int tabColor(int i) {
    switch (i) {
      case 0:
        return UiTheme.RED;
      case 1:
        return UiTheme.BLUE;
      case 2:
        return UiTheme.PURPLE;
      case 3:
        return UiTheme.ACCENT;
      default:
        return UiTheme.GRAY;
    }
  }

  private boolean hasColorPanel() {
    return tab == 2 && !searchActive() && colorPanelOpen;
  }
  /** The row is unavailable until the setting it depends on is enabled. */
  private boolean rowLocked(int key) {
    return (key == ROW_LEAVE_CYCLES && !config.isAutoLeaveEnabled())
        || (key == ROW_STOP_ON_MOUSE_PX && !config.isStopOnMouseMove())
        || (key == ROW_MESSAGE_LIFETIME && !config.isAutoDeleteModMessages());
  }

  /** Appearance time of the row the layout is currently building: picked up in add(). */
  private long rowAppear = 0L;

  private StyledButton add(boolean locked, StyledButton b) {
    b.enabled = !locked;
    return add(b);
  }

  private void head(String label) {
    rows.add(new Row(T_HEAD, -1000 - rows.size(), label, null, null, UiTheme.TEXT_MUTE, null));
  }

  private void add(
      int type, int key, String label, String note, String icon, int color, String[] tip) {
    rows.add(new Row(type, key, label, note, icon, color, tip));
  }

  private static final int GROUP_GAP = 12;
  /** Indent of a nested setting from the module card edge, and the card's own padding. */
  private static final int SUB_INDENT = 14, CARD_PAD = 8;

  private static final int TOPBAR_H = 18;

  private int listTop() {
    return tab == 4 ? contentTop : contentTop + TOPBAR_H + 6;
  }

  private int cardLeft() {
    return contentX + 6;
  }

  private int cardRight() {
    return contentRight - 6;
  }

  private int subLeft() {
    return cardLeft() + (searchActive() ? 0 : SUB_INDENT);
  }

  private int subRight() {
    return cardRight() - CARD_PAD;
  }
  /** Visible categories. Indices match tabName/tabColor. */
  private static final int[] TABS = {0, 1, 2, 3, 4};

  private static int tabIndex(int t) {
    for (int i = 0; i < TABS.length; i++) if (TABS[i] == t) return i;
    return 0;
  }

  private int groupCount() {
    int n = 0;
    for (Row r : rows) if (r.type == T_HEAD) n++;
    return n;
  }

  private boolean anyGroupOpen() {
    for (int g = 0; g < groupCount(); g++) {
      int slot = collapseSlot(g);
      if (slot >= 0 && slot < collapsed[tab].length && !collapsed[tab][slot]) return true;
    }
    return false;
  }

  /**
   * Module master checkbox: turns its toggle settings off and restores them when switched back on.
   */
  private void toggleGroupMaster(int group, int slot) {
    if (group < 0) return;
    List<Integer> keys = new ArrayList<Integer>();
    int g = -1;
    for (Row r : rawRows) {
      if (r.type == T_HEAD) {
        g++;
        continue;
      }
      if (g == group && r.type == T_TOGGLE) keys.add(Integer.valueOf(r.key));
    }
    if (keys.isEmpty()) return;
    boolean anyOn = false;
    for (Integer k : keys) if (stateOf(k.intValue())) anyOn = true;
    Integer memo = Integer.valueOf(tab * 100 + slot);
    if (anyOn) {
      List<Integer> was = new ArrayList<Integer>();
      for (Integer k : keys)
        if (stateOf(k.intValue())) {
          was.add(k);
          setToggle(k.intValue(), false);
        }
      groupMemory.put(memo, was);
    } else {
      List<Integer> was = groupMemory.remove(memo);
      for (Integer k : keys) if (was == null || was.contains(k)) setToggle(k.intValue(), true);
    }
    flashKey = -1;
    initGui();
  }

  private int collapseSlot(int group) {
    return group + (tab == 2 && visElement == 1 ? 12 : 0);
  }

  private String archiveTitle(String label) {
    if (label == null || label.length() == 0) return "";
    String low = label.toLowerCase(Locale.ROOT);
    return low.substring(0, 1).toUpperCase(Locale.ROOT) + low.substring(1);
  }

  /** How many rows the category has, starting from the row after its header. */
  private int groupSize(List<Row> src, int headIndex) {
    int n = 0;
    for (int i = headIndex + 1; i < src.size() && src.get(i).type != T_HEAD; i++) n++;
    return n;
  }

  private String groupNote(int count) {
    return Lang.plural("settings.settings_count", count);
  }

  private List<Row> rawRows = new ArrayList<Row>();

  private void applyArchiveGroups() {
    List<Row> src = new ArrayList<Row>(rows);
    rawRows = src;
    List<Row> out = new ArrayList<Row>();
    int group = -1;
    for (int i = 0; i < src.size(); i++) {
      Row r = src.get(i);
      if (r.type == T_HEAD) {
        group++;
        int slot = collapseSlot(group);
        out.add(
            new Row(
                T_HEAD,
                HEAD_KEY_BASE - slot,
                archiveTitle(r.label),
                groupNote(groupSize(src, i)),
                null,
                r.color,
                null));
        continue;
      }
      if (group < 0 || !collapsed[tab][collapseSlot(group)]) out.add(r);
    }
    rows.clear();
    rows.addAll(out);
  }

  private boolean toggleArchiveAt(int mx, int my) {
    for (int i = 0; i < rows.size(); i++) {
      Row r = rows.get(i);
      if (r.type != T_HEAD || rowY[i] < 0) continue;
      int y = rowY[i];
      int[] box = masterBox(r, y);
      if (box != null
          && mx >= box[0] - 2
          && mx < box[0] + box[2] + 2
          && my >= y
          && my < y + GROUP_HEAD_H) {
        toggleGroupMaster(groupIndexOfHead(r), HEAD_KEY_BASE - r.key);
        return true;
      }
      if (mx >= cardLeft() && mx < cardRight() && my >= y && my < y + GROUP_HEAD_H) {
        int slot = HEAD_KEY_BASE - r.key;
        if (slot >= 0 && slot < collapsed[tab].length) {
          collapsed[tab][slot] = !collapsed[tab][slot];
          listAnimAt = UiTheme.nowMs();
          initGui();
          return true;
        }
      }
    }
    return false;
  }

  /** Appearance moment of the n-th visible row: a short top-down cascade. */
  private long appearAt(int index) {
    return !config.isUiAnimationsEnabled() || listAnimAt <= 0L
        ? 0L
        : listAnimAt + Math.min(8, index) * UiTheme.STAGGER_MS;
  }

  /** Top bar of the list: search box and the "collapse / expand all" button. */
  private void buildTopBar() {
    search = null;
    if (tab == 4) return;
    int y = contentTop, h = TOPBAR_H, right = contentRight - 6;
    boolean foldable = !searchActive() && groupCount() > 0;
    if (foldable) {
      boolean anyOpen = anyGroupOpen();
      int fw = Math.max(64, Math.min(104, (contentRight - contentX) / 3));
      add(
          new StyledButton(
                  FOLD_ALL,
                  right - fw,
                  y,
                  fw,
                  h,
                  anyOpen ? Lang.t("settings.collapse_all") : Lang.t("settings.expand_all"))
              .accent(UiTheme.GRAY)
              .icon(anyOpen ? "\u25b4" : "\u25be")
              .compact());
      right -= fw + UiTheme.GAP;
    }
    int sx = contentX + 6, sw = Math.max(60, right - sx);
    int reserve = 0;
    if (searchActive())
      reserve = fontRenderer.getStringWidth(rows.size() + Lang.t("settings.found")) + 8;
    search =
        new CenteredTextField(
            9001, fontRenderer, sx + 16, y + 1, Math.max(24, sw - 22 - reserve), h - 2);
    search.setFocusRail(false);
    search.setMaxStringLength(48);
    search.setText(searchQuery);
    search.setFocused(searchFocused);
    search.setCanLoseFocus(true);
    search.setTextColor(UiTheme.TEXT);
    searchX = sx;
    searchW = sw;
    searchY = y;
  }

  private int searchX, searchY, searchW;

  private void buildRows() {
    rows.clear();
    if (searchActive()) {
      buildSearchRows();
      return;
    }
    buildTabRows();
    applyArchiveGroups();
  }

  private void buildTabRows() {
    int c = tabColor(tab);
    switch (tab) {
      case 0:
        buildStopTab(c);
        break;
      case 1:
        buildReplayTab(c);
        break;
      case 2:
        buildVisualTab(c);
        break;
      case 3:
        buildGeneralTab(c);
        break;
      case 4:
        break;
    }
  }

  /** "Stop" tab: stopping playback by movement, mouse and damage. */
  private void buildStopTab(int c) {
    head(Lang.t("settings.stop_on_movement"));
    add(
        T_TOGGLE,
        ROW_STOP_ON_ROUTE,
        Lang.t("settings.stop_route"),
        Lang.t("settings.stop_route_note"),
        "route_drift",
        c,
        Lang.tip("settings.stop_route_tip"));
    add(
        T_TOGGLE,
        ROW_STOP_ON_MOVE,
        Lang.t("settings.stop_on_my_movement"),
        Lang.t("settings.w_a_s_d_or"),
        "keyboard",
        c,
        Lang.tip("settings.stop_when_you_move_on"));
    add(
        T_TOGGLE,
        ROW_STOP_ON_MOUSE,
        Lang.t("settings.stop_on_mouse_movement"),
        Lang.t("settings.moving_the_mouse_aborts_the"),
        "mouse",
        c,
        Lang.tip("settings.stop_on_cursor_movement_on"));
    add(
        T_TOGGLE,
        ROW_GUARD_ON_DAMAGE,
        Lang.t("settings.stop_when_hurt"),
        Lang.t("settings.any_hit_stops_the_run"),
        "heart_hurt",
        c,
        Lang.tip("settings.stop_when_hurt_the_moment"));
  }

  /** "Playback" tab: what to replay, start, loop and return to start. */
  private void buildReplayTab(int c) {
    addReplayMovementRows(c);
    addReplayActionRows(c);
    addReplayStartRows(c);
    addReplayReturnRows(c);
  }

  /** "Visuals" tab: route line and start marker. */
  private void buildVisualTab(int c) {
    if (visElement == 0) addRouteRows(c);
    else addMarkerRows(c);
  }

  /** "What to replay": movement keys. */
  private void addReplayMovementRows(int c) {
    head(Lang.t("settings.what_to_replay"));
    add(
        T_TOGGLE,
        ROW_PLAY_MOVE,
        Lang.t("settings.replay_movement"),
        Lang.t("settings.steps_from_the_recording"),
        "steps",
        c,
        Lang.tip("settings.replay_movement_on_the_player"));
    add(
        T_TOGGLE,
        ROW_PLAY_JUMP,
        Lang.t("settings.replay_jumps"),
        Lang.t("settings.space_from_the_recording"),
        "jump",
        c,
        Lang.tip("settings.replay_jumps_on_space_is"));
    add(
        T_TOGGLE,
        ROW_PLAY_SNEAK,
        Lang.t("settings.replay_sneaking"),
        Lang.t("settings.shift_from_the_recording"),
        "sneak",
        c,
        Lang.tip("settings.replay_sneaking_on_shift_is"));
    add(
        T_TOGGLE,
        ROW_PLAY_SPRINT,
        Lang.t("settings.replay_sprinting"),
        Lang.t("settings.sprint_from_the_recording"),
        "\u00bb",
        c,
        Lang.tip("settings.replay_sprinting_on_it_sprints"));
  }

  /** "What to replay" continued: clicks, chat and camera. */
  private void addReplayActionRows(int c) {
    add(
        T_TOGGLE,
        ROW_PLAY_MOD_COMMANDS,
        Lang.t("settings.mod_commands"),
        Lang.t("settings.mod_commands_note"),
        "command",
        c,
        Lang.tip("settings.mod_commands_tip"));
    add(
        T_TOGGLE,
        ROW_PLAY_INTERACTION,
        Lang.t("settings.replay_clicks_and_items"),
        Lang.t("settings.attacks_food_blocks_hotbar"),
        "\u2694",
        c,
        Lang.tip("settings.replay_clicks_and_items_that"));
    add(
        T_TOGGLE,
        ROW_PLAY_CHAT,
        Lang.t("settings.replay_chat"),
        Lang.t("settings.messages_are_sent_again"),
        "chat",
        c,
        Lang.tip("settings.replay_chat_on_messages_and"));
    add(
        T_TOGGLE,
        ROW_VISIBLE_CHAT,
        Lang.t("settings.live_chat"),
        Lang.t("settings.chat_opens_and_types_letter"),
        "chat_typing",
        c,
        Lang.tip("settings.live_chat_on_the_chat"));
    add(
        T_TOGGLE,
        ROW_APPLY_ROTATION,
        Lang.t("settings.replay_mouse_rotation"),
        Lang.t("settings.recorded_look_direction"),
        "look_rotate",
        c,
        Lang.tip("settings.replay_mouse_look_on_the"));
  }

  /** "Start" and "Loop": delay, skip, speed, loop limit and auto-leave. */
  private void addReplayStartRows(int c) {
    head(Lang.t("settings.start_and_loop_values"));
    add(
        T_STEP,
        ROW_START_DELAY,
        Lang.t("edit.start_delay"),
        Lang.t("settings.precise_pause_before_recording_or"),
        "\u23f1",
        c,
        Lang.tip("settings.pause_before_start_how_many"));
    add(
        T_STEP,
        ROW_PLAYBACK_SPEED,
        Lang.t("settings.replay_speed"),
        Lang.t("settings.faster_or_slower_than_the"),
        "speed",
        c,
        Lang.tip("settings.replay_speed_1_00x_plays"));
    add(
        T_STEP,
        ROW_LOOP_LIMIT,
        Lang.t("settings.loop_rounds"),
        Lang.t("settings.0_means_unlimited"),
        "loop",
        c,
        Lang.tip("settings.loop_rounds_the_loop_stops"));
    head(Lang.t("settings.loop"));
    add(
        T_TOGGLE,
        ROW_SKIP_LOOP_DELAY,
        Lang.t("settings.delay_before_first_cycle_only"),
        Lang.t("settings.later_repeats_begin_immediately"),
        "skip_delay",
        c,
        Lang.tip("settings.countdown_before_the_first_round"));
    add(
        T_TOGGLE,
        ROW_AUTO_LEAVE,
        Lang.t("settings.auto_leave"),
        Lang.t(
            config.isAutoLeaveTimed()
                ? "settings.auto_leave_note_time"
                : "settings.auto_leave_note_cycles"),
        "disconnect",
        c,
        Lang.tip("settings.auto_leave_tip"));
  }

  /** "Return to start": Baritone return, route, gaze and inline approach parameters. */
  private void addReplayReturnRows(int c) {
    head(Lang.t("settings.return_to_start"));
    add(
        T_TOGGLE,
        ROW_AUTO_RETURN,
        Lang.t("stabilizationSettings.baritone_auto_return"),
        Lang.t("settings.before_playback_and_between_cycles")
            + (fullCatalog ? "" : " · " + Lang.t("settings.return_settings_hint")),
        "return_start",
        c,
        Lang.tip("settings.return_to_the_start_point_2"));
    if (returnDetailsOpen || fullCatalog) {
      add(
          T_STEP,
          ROW_RETURN_TOLERANCE,
          Lang.t("stabilizationSettings.return_accuracy"),
          Lang.t("stabilizationSettings.return_accuracy_note"),
          "target",
          c,
          Lang.tip("stabilizationSettings.how_close_to_the_start"));
      add(
          T_STEP,
          ROW_RETURN_ALIGN_SPEED,
          Lang.t("stabilizationSettings.approach_speed"),
          Lang.t("stabilizationSettings.approach_speed_note"),
          "speed",
          c,
          Lang.tip("stabilizationSettings.how_fast_to_walk_on"));
      add(
          T_STEP,
          ROW_RETURN_TIMEOUT,
          Lang.t("stabilizationSettings.timeout"),
          Lang.t("stabilizationSettings.timeout_note"),
          "hourglass",
          c,
          Lang.tip("stabilizationSettings.if_it_cannot_get_there"));
    }
    add(
        T_TOGGLE,
        ROW_ROUTE_STABILIZATION,
        Lang.t("settings.route_stabilization"),
        Lang.t("settings.gentle_input_correction_on_drift"),
        "stabilize",
        c,
        Lang.tip("settings.stay_on_the_route_if"));
    add(
        T_TOGGLE,
        ROW_CURSOR_STABILIZATION,
        Lang.t("stabilizationSettings.restore_the_view_to_the"),
        Lang.t("settings.before_playback_starts"),
        "eye",
        c,
        Lang.tip("settings.restore_the_look_direction_before"));
  }

  /** Visuals, route: the line itself and its color. */
  private void addRouteRows(int c) {
    head(Lang.t("settings.route"));
    add(
        T_TOGGLE,
        ROW_PATH_LINE,
        Lang.t("settings.show_the_route"),
        Lang.t("settings.the_path_line_appears_in"),
        "route",
        c,
        Lang.tip("settings.route_a_line_in_the"));
    add(
        T_CYCLE,
        ROW_PATH_STYLE,
        Lang.t("settings.how_the_route_is_drawn"),
        Lang.t("settings.line_line_with_dots_or"),
        "route_style",
        c,
        Lang.tip("settings.route_style_line_is_a"));
    add(
        T_ACTION,
        ROW_COLOR_TARGET,
        Lang.t("settings.route_color"),
        (colorPanelOpen
            ? Lang.t("settings.palette_is_open_use_the")
            : Lang.t("settings.click_to_open_the_palette")),
        "palette",
        c,
        Lang.tip("settings.route_color_changes_the_color"));
    addRouteGeometryRows(c);
    addRouteVisibilityRows(c);
  }

  /** Route geometry: height, length, width and dots (dots only for dotted styles). */
  private void addRouteGeometryRows(int c) {
    head(Lang.t("settings.geometry"));
    add(
        T_STEP,
        ROW_PATH_HEIGHT,
        Lang.t("settings.height_above_ground"),
        Lang.t("settings.the_line_lifts_above_the"),
        "height",
        c,
        Lang.tip("settings.route_height_how_high_the"));
    add(
        T_STEP,
        ROW_PATH_LENGTH,
        Lang.t("settings.how_much_of_the_path"),
        Lang.t("settings.how_many_upcoming_frames_are"),
        "path_length",
        c,
        Lang.tip("settings.route_length_a_higher_number"));
    add(
        T_STEP,
        ROW_PATH_WIDTH,
        Lang.t("settings.route_line_thickness"),
        Lang.t("settings.the_line_gets_thicker_or"),
        "line_width",
        c,
        Lang.tip("settings.line_width_thicker_makes_the"));
    if (fullCatalog || config.getPathLineStyle() != 0) {
      add(
          T_STEP,
          ROW_PATH_POINT_SIZE,
          Lang.t("settings.size_of_the_route_dots"),
          Lang.t("settings.the_dots_on_the_path"),
          "point_size",
          c,
          Lang.tip("settings.dot_size_bigger_dots_are"));
      add(
          T_STEP,
          ROW_PATH_POINT_STEP,
          Lang.t("settings.how_often_to_place_dots"),
          Lang.t("settings.dots_along_the_path_appear"),
          "point_step",
          c,
          Lang.tip("settings.dot_spacing_a_smaller_step"));
    }
  }

  /** Route visibility and the appearance reset. */
  private void addRouteVisibilityRows(int c) {
    head(Lang.t("settings.visibility"));
    add(
        T_TOGGLE,
        ROW_PATH_FADE,
        Lang.t("settings.the_line_fades_toward_the"),
        Lang.t("settings.the_far_end_of_the"),
        "opacity",
        c,
        Lang.tip("settings.fade_the_near_part_stays"));
    add(
        T_TOGGLE,
        ROW_PATH_THROUGH_WALLS,
        Lang.t("settings.route_stays_visible_through_walls"),
        Lang.t("settings.the_line_will_not_hide"),
        "through_walls",
        c,
        Lang.tip("settings.through_walls_the_route_is"));
    add(
        T_ACTION,
        ROW_RESET_VISUALS,
        Lang.t("settings.reset_the_look"),
        Lang.t("settings.bring_back_the_normal_line"),
        "reset",
        UiTheme.AMBER,
        Lang.tip("settings.reset_look_normal_colors_and"));
  }

  /**
   * Visuals, start marker: style and reset; dependent options when the marker is on or fullCatalog
   * is true.
   */
  private void addMarkerRows(int c) {
    head(Lang.t("settings.start_marker"));
    add(
        T_CYCLE,
        ROW_MARKER_MODE,
        Lang.t("settings.start_marker_style"),
        Lang.t("settings.off_a_dot_a_ring"),
        "\u2691",
        c,
        Lang.tip("settings.start_marker_a_mark_in"));
    if (fullCatalog || config.getStartMarkerMode() > 0) {
      addMarkerShapeRows(c);
      addMarkerLabelRows(c);
      head(Lang.t("settings.visibility"));
      add(
          T_TOGGLE,
          ROW_MARKER_THROUGH_WALLS,
          Lang.t("settings.marker_stays_visible_through_walls"),
          Lang.t("settings.the_mark_will_not_hide"),
          "through_walls",
          c,
          Lang.tip("settings.through_walls_the_mark_stays"));
    }
    add(
        T_ACTION,
        ROW_RESET_VISUALS,
        Lang.t("settings.reset_the_look"),
        Lang.t("settings.bring_back_the_normal_line"),
        "reset",
        UiTheme.AMBER,
        Lang.tip("settings.reset_look_normal_colors_and"));
  }

  /** Marker color, ring size (ring modes) and beam height (beam mode). */
  private void addMarkerShapeRows(int c) {
    add(
        T_TOGGLE,
        ROW_MARKER_CUSTOM_COLOR,
        Lang.t("settings.own_color_for_the_marker"),
        Lang.t("settings.the_marker_stops_copying_the"),
        "color_custom",
        c,
        Lang.tip("settings.separate_color_on_the_marker"));
    add(
        T_ACTION,
        ROW_COLOR_TARGET,
        Lang.t("settings.marker_color"),
        (colorPanelOpen
            ? Lang.t("settings.palette_is_open_use_the")
            : Lang.t("settings.click_to_open_the_palette")),
        "palette",
        c,
        Lang.tip("settings.marker_color_changes_the_mark"));
    if (fullCatalog || config.getStartMarkerMode() >= 2 || config.isMarkerLabel())
      head(Lang.t("settings.geometry"));
    if (fullCatalog || config.getStartMarkerMode() >= 2) {
      add(
          T_STEP,
          ROW_MARKER_RADIUS,
          Lang.t("settings.size_of_the_marker_ring"),
          Lang.t("settings.the_ring_around_the_start"),
          "circle",
          c,
          Lang.tip("settings.ring_size_a_wider_ring"));
    }
    if (fullCatalog || config.getStartMarkerMode() == 3) {
      add(
          T_STEP,
          ROW_MARKER_BEACON_HEIGHT,
          Lang.t("settings.height_of_the_marker_beam"),
          Lang.t("settings.the_vertical_beam_grows_taller"),
          "beacon",
          c,
          Lang.tip("settings.beam_height_a_taller_beam"));
    }
    if (fullCatalog || config.isMarkerLabel()) {
      add(
          T_STEP,
          ROW_MARKER_LABEL_DISTANCE,
          Lang.t("settings.how_far_the_label_stays"),
          Lang.t("settings.the_label_hides_if_you"),
          "distance",
          c,
          Lang.tip("settings.label_distance_a_higher_number"));
    }
  }

  /** Marker label and effects: pulse, label and its visibility distance. */
  private void addMarkerLabelRows(int c) {
    head(Lang.t("settings.label_and_effects"));
    add(
        T_TOGGLE,
        ROW_MARKER_PULSE,
        Lang.t("settings.the_marker_pulses"),
        Lang.t("settings.the_mark_softly_blinks"),
        "pulse",
        c,
        Lang.tip("settings.pulse_the_mark_brightens_and"));
    add(
        T_TOGGLE,
        ROW_MARKER_LABEL,
        Lang.t("settings.label_on_the_marker"),
        Lang.t("settings.a_start_label_appears_above"),
        "label_tag",
        c,
        Lang.tip("settings.label_text_above_the_mark"));
    if (fullCatalog || config.isMarkerLabel()) {
      add(
          T_TOGGLE,
          ROW_MARKER_LABEL_UNLIMITED,
          Lang.t("settings.show_the_label_at_any"),
          Lang.t("settings.the_marker_text_stays_visible"),
          "\u221e",
          c,
          Lang.tip("settings.no_limit_the_label_never"));
    }
  }

  /** "General" tab: interface and data. */
  private void buildGeneralTab(int c) {
    head(Lang.t("settings.interface"));
    add(
        T_TOGGLE,
        ROW_MOD_MESSAGES,
        Lang.t("settings.mod_messages"),
        Lang.t("settings.mod_messages_note"),
        "mod_message",
        c,
        Lang.tip("settings.mod_messages_tip"));
    add(
        T_TOGGLE,
        ROW_TOOLTIPS,
        Lang.t("settings.hover_tooltips"),
        Lang.t("settings.short_explanation_near_the_cursor"),
        "?",
        c,
        Lang.tip("settings.tooltips_small_explanations_next_to"));
    add(
        T_TOGGLE,
        ROW_SOUNDS,
        Lang.t("settings.mod_sounds"),
        Lang.t("settings.countdown_start_finish"),
        "sound",
        c,
        Lang.tip("settings.sounds_quiet_cues_the_countdown"));
    add(
        T_CYCLE,
        ROW_LANGUAGE,
        Lang.t("settings.interface_language"),
        Lang.t("settings.auto_or_one_of_five"),
        "language",
        c,
        Lang.tip("settings.language_switches_all_text_auto"));
    add(
        T_TOGGLE,
        ROW_AUTO_DELETE_MESSAGES,
        Lang.t("settings.auto_delete_messages"),
        Lang.t("settings.auto_delete_messages_note"),
        "auto_clear",
        c,
        (Lang.t("settings.auto_delete_messages_tip")
                + "\n"
                + Lang.t("settings.message_lifetime_tip"))
            .split("\n"));
    add(
        T_TOGGLE,
        ROW_UI_ANIMATIONS,
        Lang.t("settings.ui_animations"),
        Lang.t("settings.ui_animations_note"),
        "spark",
        c,
        Lang.tip("settings.ui_animations_tip"));
    head(Lang.t("settings.data"));
    add(
        T_ACTION,
        ROW_TRASH,
        Lang.t("settings.recording_trash"),
        Lang.t("settings.bring_a_deleted_recording_back"),
        "trash_restore",
        UiTheme.GREEN,
        Lang.tip("settings.trash_every_deleted_recording_lands"));
    add(
        T_ACTION,
        ROW_FACTORY_RESET,
        Lang.t("settings.reset_to_factory_settings"),
        Lang.t("settings.recordings_remain_untouched"),
        "reset",
        UiTheme.RED,
        Lang.tip("settings.reset_settings_puts_every_setting"));
  }
  /**
   * Flat list of matches across all sections: no category headers here. The search runs over the
   * full catalog — both visual tabs and rows currently hidden by dependent values. The palette
   * button is not searched: it belongs to the selected object of the "Visuals" tab.
   */
  private void buildSearchRows() {
    String[] q = SearchMatch.variants(searchQuery);
    List<Row> out = new ArrayList<Row>();
    Set<Integer> seen = new HashSet<Integer>();
    int savedTab = tab, savedElement = visElement;
    fullCatalog = true;
    for (int n = 0; n < TABS.length; n++) {
      tab = TABS[n];
      for (int element = 0; element < (tab == 2 ? 2 : 1); element++) {
        visElement = tab == 2 ? element : savedElement;
        rows.clear();
        buildTabRows();
        String group = "";
        for (Row r : rows) {
          if (r.type == T_HEAD) {
            group = r.label;
            continue;
          }
          if (r.key == ROW_COLOR_TARGET || seen.contains(Integer.valueOf(r.key))) continue;
          String searchable =
              r.label + " " + (r.note == null ? "" : r.note) + " " + group + " " + tabName(TABS[n]);
          if (r.key == ROW_AUTO_DELETE_MESSAGES)
            searchable +=
                " "
                    + Lang.t("settings.message_lifetime")
                    + " "
                    + Lang.t("settings.message_lifetime_note");
          if (SearchMatch.hit(q, searchable)) {
            seen.add(Integer.valueOf(r.key));
            String gt = archiveTitle(group);
            String where = gt.isEmpty() ? tabName(TABS[n]) : tabName(TABS[n]) + " \u203a " + gt;
            out.add(
                new Row(
                    r.type,
                    r.key,
                    r.label,
                    r.note == null || r.note.isEmpty() ? where : where + " \u00b7 " + r.note,
                    r.icon,
                    r.color,
                    r.tip));
          }
        }
      }
    }
    fullCatalog = false;
    tab = savedTab;
    visElement = savedElement;
    rows.clear();
    rows.addAll(out);
    rawRows = new ArrayList<Row>(out);
  }

  // ------------------------------------------------ values

  private String markerName() {
    switch (config.getStartMarkerMode()) {
      case 0:
        return Lang.t("settings.off");
      case 1:
        return Lang.t("settings.point");
      case 2:
        return Lang.t("settings.ring");
      default:
        return Lang.t("settings.beacon");
    }
  }

  private String styleName() {
    switch (config.getPathLineStyle()) {
      case 0:
        return Lang.t("settings.line");
      case 1:
        return Lang.t("settings.line_points");
      default:
        return Lang.t("settings.points");
    }
  }

  private String valueOf(int key) {
    switch (key) {
      case ROW_MESSAGE_LIFETIME:
        return config.getModMessageLifetimeSeconds() + Lang.t("edit.s");
      case ROW_START_DELAY:
        return config.getStartDelay() + Lang.t("edit.s");
      case ROW_LOOP_LIMIT:
        return config.getLoopLimit() == 0
            ? Lang.t("settings.unlimited")
            : String.valueOf(config.getLoopLimit());
      case ROW_PLAYBACK_SPEED:
        return String.format(Locale.ROOT, "%.2f", config.getPlaybackSpeed()) + "x";
      case ROW_LEAVE_CYCLES:
        if (!config.isAutoLeaveTimed()) return String.valueOf(config.getAutoLeaveCycles());
        int minutes = config.getAutoLeaveMinutes();
        int hours = minutes / 60, remainder = minutes % 60;
        if (hours == 0) return remainder + " " + Lang.t("settings.auto_leave_min_short");
        if (remainder == 0) return hours + " " + Lang.t("settings.auto_leave_hour_short");
        return hours
            + " "
            + Lang.t("settings.auto_leave_hour_short")
            + " "
            + remainder
            + " "
            + Lang.t("settings.auto_leave_min_short");
      case ROW_STOP_ON_MOUSE_PX:
        return config.getStopOnMouseThreshold() + " px";
      case ROW_RETURN_TOLERANCE:
        return String.format(Locale.ROOT, "%.2f", config.getReturnPositionTolerance());
      case ROW_RETURN_ALIGN_SPEED:
        return String.format(Locale.ROOT, "%.1f", config.getReturnAlignSpeed());
      case ROW_RETURN_TIMEOUT:
        return config.getReturnTimeoutSeconds() + Lang.t("edit.s");
      case ROW_MARKER_RADIUS:
        return String.format(Locale.ROOT, "%.1f", config.getMarkerRadius());
      case ROW_MARKER_BEACON_HEIGHT:
        return String.format(Locale.ROOT, "%.0f", config.getMarkerBeaconHeight());
      case ROW_MARKER_LABEL_DISTANCE:
        return config.isMarkerLabelUnlimited()
            ? Lang.t("settings.unlimited_2")
            : config.getMarkerLabelDistance() + Lang.t("settings.bl");
      case ROW_PATH_WIDTH:
        return String.format(Locale.ROOT, "%.1f", config.getPathLineWidth());
      case ROW_PATH_LENGTH:
        return String.valueOf(config.getPathLineLength());
      case ROW_PATH_POINT_STEP:
        return String.valueOf(config.getPathPointStep());
      case ROW_PATH_POINT_SIZE:
        return String.format(Locale.ROOT, "%.2f", config.getPathPointSize());
      case ROW_PATH_HEIGHT:
        return String.format(Locale.ROOT, "%.1f", config.getPathLineHeight());
      case ROW_MARKER_MODE:
        return markerName();
      case ROW_PATH_STYLE:
        return styleName();
      case ROW_LANGUAGE:
        return Lang.name();
      case ROW_COLOR_TARGET:
        return colorTarget == 0 ? Lang.t("settings.path") : Lang.t("settings.marker");
      default:
        return "";
    }
  }

  private boolean stateOf(int key) {
    switch (key) {
      case ROW_PLAY_MOVE:
        return config.isPlaybackMovement();
      case ROW_PLAY_JUMP:
        return config.isPlaybackJump();
      case ROW_PLAY_SNEAK:
        return config.isPlaybackSneak();
      case ROW_PLAY_SPRINT:
        return config.isPlaybackSprint();
      case ROW_PLAY_INTERACTION:
        return config.isPlaybackInteraction();
      case ROW_PLAY_MOD_COMMANDS:
        return config.isPlaybackModCommands();
      case ROW_STOP_ON_ROUTE:
        return config.isStopOnRouteDeviation();
      case ROW_PLAY_CHAT:
        return config.isPlaybackChat();
      case ROW_VISIBLE_CHAT:
        return config.isVisibleChat();
      case ROW_APPLY_ROTATION:
        return config.isApplyRotation();
      case ROW_STOP_ON_MOVE:
        return config.isStopOnMove();
      case ROW_STOP_ON_MOUSE:
        return config.isStopOnMouseMove();
      case ROW_AUTO_DELETE_MESSAGES:
        return config.isAutoDeleteModMessages();
      case ROW_UI_ANIMATIONS:
        return config.isUiAnimationsEnabled();
      case ROW_MOD_MESSAGES:
        return config.isSendModMessages();
      case ROW_AUTO_RETURN:
        return config.isAutoReturnEnabled();
      case ROW_ROUTE_STABILIZATION:
        return config.isRouteStabilization();
      case ROW_CURSOR_STABILIZATION:
        return config.isCursorStabilization();
      case ROW_SKIP_LOOP_DELAY:
        return config.isSkipLoopStartDelay();
      case ROW_AUTO_LEAVE:
        return config.isAutoLeaveEnabled();
      case ROW_MARKER_PULSE:
        return config.isMarkerPulse();
      case ROW_MARKER_LABEL:
        return config.isMarkerLabel();
      case ROW_MARKER_LABEL_UNLIMITED:
        return config.isMarkerLabelUnlimited();
      case ROW_MARKER_THROUGH_WALLS:
        return config.isMarkerThroughWalls();
      case ROW_MARKER_CUSTOM_COLOR:
        return config.isMarkerCustomColor();
      case ROW_TOOLTIPS:
        return config.isShowTooltips();
      case ROW_SOUNDS:
        return config.isSoundsEnabled();
      case ROW_GUARD_ON_DAMAGE:
        return config.isGuardOnDamage();
      case ROW_PATH_LINE:
        return config.isPathLineEnabled();
      case ROW_PATH_FADE:
        return config.isPathLineFade();
      case ROW_PATH_THROUGH_WALLS:
        return config.isPathLineThroughWalls();
      default:
        return false;
    }
  }

  /** Set the toggle to the required state: used by the module master checkbox. */
  private void setToggle(int key, boolean value) {
    if (stateOf(key) != value) toggleKey(key);
  }

  private void toggleKey(int key) {
    boolean v = !stateOf(key);
    switch (key) {
      case ROW_PLAY_MOVE:
        config.setPlaybackMovement(v);
        break;
      case ROW_PLAY_JUMP:
        config.setPlaybackJump(v);
        break;
      case ROW_PLAY_SNEAK:
        config.setPlaybackSneak(v);
        break;
      case ROW_PLAY_SPRINT:
        config.setPlaybackSprint(v);
        break;
      case ROW_PLAY_INTERACTION:
        config.setPlaybackInteraction(v);
        break;
      case ROW_PLAY_MOD_COMMANDS:
        config.setPlaybackModCommands(!config.isPlaybackModCommands());
        break;
      case ROW_STOP_ON_ROUTE:
        config.setStopOnRouteDeviation(!config.isStopOnRouteDeviation());
        break;
      case ROW_PLAY_CHAT:
        config.setPlaybackChat(v);
        break;
      case ROW_VISIBLE_CHAT:
        config.setVisibleChat(v);
        break;
      case ROW_APPLY_ROTATION:
        config.setApplyRotation(v);
        break;
      case ROW_STOP_ON_MOVE:
        config.setStopOnMove(v);
        break;
      case ROW_STOP_ON_MOUSE:
        config.setStopOnMouseMove(v);
        break;
      case ROW_AUTO_DELETE_MESSAGES:
        config.setAutoDeleteModMessages(v);
        break;
      case ROW_UI_ANIMATIONS:
        config.setUiAnimationsEnabled(v);
        break;
      case ROW_MOD_MESSAGES:
        config.setSendModMessages(v);
        break;
      case ROW_AUTO_RETURN:
        config.setAutoReturnEnabled(v);
        break;
      case ROW_ROUTE_STABILIZATION:
        config.setRouteStabilization(v);
        break;
      case ROW_CURSOR_STABILIZATION:
        config.setCursorStabilization(v);
        break;
      case ROW_SKIP_LOOP_DELAY:
        config.setSkipLoopStartDelay(v);
        break;
      case ROW_AUTO_LEAVE:
        config.setAutoLeaveEnabled(v);
        break;
      case ROW_MARKER_PULSE:
        config.setMarkerPulse(v);
        break;
      case ROW_MARKER_LABEL:
        config.setMarkerLabel(v);
        break;
      case ROW_MARKER_LABEL_UNLIMITED:
        config.setMarkerLabelUnlimited(v);
        break;
      case ROW_MARKER_THROUGH_WALLS:
        config.setMarkerThroughWalls(v);
        break;
      case ROW_MARKER_CUSTOM_COLOR:
        config.setMarkerCustomColor(v);
        break;
      case ROW_TOOLTIPS:
        config.setShowTooltips(v);
        break;
      case ROW_SOUNDS:
        config.setSoundsEnabled(v);
        break;
      case ROW_GUARD_ON_DAMAGE:
        config.setGuardOnDamage(v);
        break;
      case ROW_PATH_LINE:
        config.setPathLineEnabled(v);
        break;
      case ROW_PATH_FADE:
        config.setPathLineFade(v);
        break;
      case ROW_PATH_THROUGH_WALLS:
        config.setPathLineThroughWalls(v);
        break;
      default:
        break;
    }
  }

  private void step(int key, int dir) {
    flashKey = key;
    flashAt = UiTheme.nowMs();
    boolean
        fine = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT),
        coarse =
            Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    switch (key) {
      case ROW_MESSAGE_LIFETIME:
        config.setModMessageLifetimeSeconds(
            Math.max(
                1, Math.min(600, config.getModMessageLifetimeSeconds() + dir * (coarse ? 10 : 1))));
        break;
      case ROW_START_DELAY:
        config.setStartDelay(config.getStartDelay() + dir * (coarse ? 5 : 1));
        break;
      case ROW_LEAVE_CYCLES:
        if (config.isAutoLeaveTimed())
          config.setAutoLeaveMinutes(config.getAutoLeaveMinutes() + dir);
        else config.setAutoLeaveCycles(config.getAutoLeaveCycles() + dir * (coarse ? 10 : 1));
        break;
      case ROW_STOP_ON_MOUSE_PX:
        config.setStopOnMouseThreshold(config.getStopOnMouseThreshold() + dir * (coarse ? 5 : 1));
        break;
      case ROW_LOOP_LIMIT:
        {
          int cur = config.getLoopLimit(), st = coarse ? 10 : fine ? 1 : 2;
          config.setLoopLimit(
              dir > 0 ? (cur == 0 ? 2 : cur + st) : (cur == 0 ? 0 : cur - st < 2 ? 0 : cur - st));
          break;
        }
      case ROW_PLAYBACK_SPEED:
        config.setPlaybackSpeed(
            config.getPlaybackSpeed() + dir * (fine ? 0.05f : coarse ? 0.5f : 0.25f));
        break;
      case ROW_RETURN_TOLERANCE:
        config.setReturnPositionTolerance(
            config.getReturnPositionTolerance() + dir * (coarse ? 0.05f : 0.01f));
        break;
      case ROW_RETURN_ALIGN_SPEED:
        config.setReturnAlignSpeed(
            config.getReturnAlignSpeed() + dir * (fine ? 0.05f : coarse ? 0.2f : 0.1f));
        break;
      case ROW_RETURN_TIMEOUT:
        config.setReturnTimeoutSeconds(
            config.getReturnTimeoutSeconds() + dir * (fine ? 1 : coarse ? 15 : 5));
        break;
      case ROW_MARKER_RADIUS:
        config.setMarkerRadius(
            config.getMarkerRadius() + dir * (fine ? 0.05f : coarse ? 0.25f : 0.1f));
        break;
      case ROW_MARKER_BEACON_HEIGHT:
        config.setMarkerBeaconHeight(
            config.getMarkerBeaconHeight() + dir * (fine ? 1f : coarse ? 5f : 2f));
        break;
      case ROW_MARKER_LABEL_DISTANCE:
        config.setMarkerLabelDistance(
            config.getMarkerLabelDistance() + dir * (fine ? 1 : coarse ? 32 : 8));
        break;
      case ROW_PATH_WIDTH:
        config.setPathLineWidth(
            config.getPathLineWidth() + dir * (fine ? 0.1f : coarse ? 1f : 0.5f));
        break;
      case ROW_PATH_LENGTH:
        config.setPathLineLength(config.getPathLineLength() + dir * (fine ? 5 : coarse ? 50 : 20));
        break;
      case ROW_PATH_POINT_STEP:
        config.setPathPointStep(config.getPathPointStep() + dir * (coarse ? 5 : 1));
        break;
      case ROW_PATH_POINT_SIZE:
        config.setPathPointSize(
            config.getPathPointSize() + dir * (fine ? 0.01f : coarse ? 0.1f : 0.05f));
        break;
      case ROW_PATH_HEIGHT:
        config.setPathLineHeight(
            config.getPathLineHeight() + dir * (fine ? 0.05f : coarse ? 0.25f : 0.1f));
        break;
      default:
        break;
    }
  }

  // ------------------------------------------------ layout
  private StyledButton add(StyledButton b) {
    if (rowAppear > 0L) b.appear(rowAppear);
    buttonList.add(b);
    return b;
  }

  private int[] rowHeights;

  private int heightAt(int i) {
    return rowHeights != null && i >= 0 && i < rowHeights.length ? rowHeights[i] : rowH;
  }

  private boolean stackedSteps() {
    return subRight() - subLeft() < 310;
  }

  private boolean isReturnMini(Row r) {
    return !searchActive()
        && (r.key == ROW_RETURN_TOLERANCE
            || r.key == ROW_RETURN_ALIGN_SPEED
            || r.key == ROW_RETURN_TIMEOUT);
  }

  private boolean hasCounter(Row r) {
    return r.type == T_STEP
        || r.type == T_TOGGLE
            && (r.key == ROW_AUTO_LEAVE
                || r.key == ROW_STOP_ON_MOUSE
                || r.key == ROW_AUTO_DELETE_MESSAGES);
  }

  private int measuredRowHeight(Row r) {
    if (r.type == T_HEAD) return GROUP_HEAD_H;
    if (isReturnMini(r)) return stackedSteps() ? 38 : 22;
    int w = subRight() - subLeft(), textW = w - 44;
    if (hasCounter(r)) {
      textW = stackedSteps() ? w - 62 : stepBlockX() - subLeft() - 44;
      if (r.key == ROW_AUTO_LEAVE && !stackedSteps()) textW = leaveModeX() - subLeft() - 44;
    } else if (r.type == T_TOGGLE) {
      textW -= 22;
      if (r.key == ROW_AUTO_RETURN && !searchActive()) textW -= 16;
    } else if (r.type == T_CYCLE)
      textW -= Math.min(w / 2, fontRenderer.getStringWidth(valueOf(r.key)) + 10);
    int h =
        UiRowText.height(
                fontRenderer, r.label, expandedColorRow(r) ? null : r.note, Math.max(16, textW))
            + 8;
    if (hasCounter(r) && stackedSteps()) h += stepBtn() + 5;
    int base = Math.max(UiTheme.ROW_H, h);
    return base + (expandedColorRow(r) ? COLOR_PANEL_GAP + colorPanelHeight() : 0);
  }

  private boolean expandedColorRow(Row r) {
    return hasColorPanel() && r.key == ROW_COLOR_TARGET;
  }

  private int colorPanelHeight() {
    int room = listBottom - (listTop() + 32) - 4 - UiTheme.ROW_H - COLOR_PANEL_GAP;
    return Math.max(44, Math.min(92, room));
  }

  private int colorPanelPadding() {
    return colorPanelHeight() < 72 ? 5 : 8;
  }

  private int counterY(int y) {
    return stackedSteps() ? y + rowH - stepBtn() - 4 : y + (rowH - stepBtn()) / 2;
  }

  private int stepBtn() {
    return Math.min(rowH - 8, subRight() - subLeft() < 240 ? 12 : 18);
  }

  /** Shared counter width, independent of the row label, tab and toggle state. */
  private int stepValW() {
    int need = 48;
    if (fontRenderer != null)
      need = Math.max(need, fontRenderer.getStringWidth(Lang.t("settings.unlimited")) + 8);
    int room = subRight() - subLeft() - counterRightReserve() - stepBtn() * 2 - 70;
    return Math.max(36, Math.min(need, room));
  }

  /** The time value fits beside the mode icon without creating a separate feature card. */
  private int leaveValW() {
    String sample =
        "8759 "
            + Lang.t("settings.auto_leave_hour_short")
            + " 59 "
            + Lang.t("settings.auto_leave_min_short");
    int need = Math.max(60, fontRenderer.getStringWidth(sample) + 8);
    int room =
        subRight()
            - subLeft()
            - leaveRightReserve()
            - stepBtn() * 3
            - LEAVE_CONTROL_GAP
            - UiTheme.BTN_PAD;
    return Math.max(36, Math.min(need, room));
  }

  /** A lower control strip can use the trailing slot only when it clears the checkbox. */
  private int leaveRightReserve() {
    int controlsTop = rowH - stepBtn() - 4;
    int checkBottom = (rowH + StyledButton.checkSize(rowH)) / 2;
    return stackedSteps() && controlsTop >= checkBottom + 2
        ? UiTheme.BTN_PAD
        : counterRightReserve();
  }

  private int leaveModeX() {
    return leaveBlockX() - stepBtn() - LEAVE_CONTROL_GAP;
  }

  private int leaveBlockX() {
    return subRight() - leaveRightReserve() - stepBtn() * 2 - leaveValW();
  }

  private String counterValue(int key) {
    String value = valueOf(key);
    // On a narrow GUI, keep the unlimited value away from both hit targets. The infinity symbol
    // has the same meaning; ordinary widths retain the full localized label.
    if (key == ROW_LOOP_LIMIT
        && config.getLoopLimit() == 0
        && fontRenderer.getStringWidth(value) > stepValW() - 8) return "∞";
    return value;
  }

  private int counterRightReserve() {
    return UiTheme.BTN_PAD + Math.max(stepBtn(), StyledButton.checkSize(rowH)) + UiTheme.STEP_GAP;
  }

  private int stepBlockW() {
    return stepBtn() * 2 + stepValW();
  }

  /** Reserve the same trailing slot even when the row has no checkbox. */
  private int stepBlockX() {
    return subRight() - counterRightReserve() - stepBlockW();
  }
  // ---- feature list scrollbar
  /**
   * How many rows fit when starting from the given row: returns the index of the last visible one.
   */
  private int lastVisibleRow(int from, int startY) {
    int cursor = startY, count = 0, last = from - 1;
    for (int i = from; i < rows.size(); i++) {
      Row r = rows.get(i);
      if (r.type == T_HEAD) {
        if (count > 0) cursor += GROUP_GAP - UiTheme.ROW_GAP;
        if (!headerFits(i, cursor)) break;
        count++;
        last = i;
        cursor += GROUP_HEAD_STEP;
        continue;
      }
      if (cursor + heightAt(i) > listBottom - 4) break;
      count++;
      last = i;
      cursor += heightAt(i) + UiTheme.ROW_GAP;
    }
    return last;
  }
  /** Scrolling reaches exactly the last row: no unreachable items remain. */
  private int computeMaxScroll(int startY) {
    int total = rows == null ? 0 : rows.size();
    if (total <= 0) return 0;
    for (int s = 0; s < total; s++) if (lastVisibleRow(s, startY) >= total - 1) return s;
    return total - 1;
  }

  // Keep an expanded header with its first child so it never looks empty at the viewport edge.
  private boolean headerFits(int index, int y) {
    int required = GROUP_HEAD_H;
    if (index + 1 < rows.size() && rows.get(index + 1).type != T_HEAD)
      required = GROUP_HEAD_STEP + heightAt(index + 1);
    return y + required <= listBottom - 4;
  }

  private boolean sbActive() {
    return tab != 4 && maxScroll > 0 && sbBottom() > sbTop();
  }

  private int sbTop() {
    return listTop() + 4;
  }

  private int sbBottom() {
    return listBottom - 4;
  }

  private int sbThumbH() {
    int track = sbBottom() - sbTop();
    return Math.min(
        track,
        Math.max(16, track * Math.max(1, rows.size() - maxScroll) / Math.max(1, rows.size())));
  }

  private int sbThumbY() {
    int track = sbBottom() - sbTop(), max = Math.max(1, maxScroll);
    return sbTop() + (track - sbThumbH()) * Math.max(0, Math.min(max, scroll)) / max;
  }

  private void sbSetFromMouse(int my) {
    int span = Math.max(1, sbBottom() - sbTop() - sbThumbH()), max = maxScroll;
    int ns = Math.max(0, Math.min(max, Math.round((my - sbGrab - sbTop()) / (float) span * max)));
    if (ns != scroll) {
      scroll = ns;
      initGui();
    }
  }

  @Override
  public void initGui() {
    finishLeaveEdit(true);
    leaveEditX = -1;
    buttonList.clear();
    stepToggleBtns.clear();
    stepToggleKeys.clear();
    wheel = null;
    colorPanelY = -1;
    layoutWindow();
    addTabButtons();
    openingChild = false;
    buildRows();
    rowY = new int[rows.size()];
    Arrays.fill(rowY, -1);
    rowAppearAt = new long[rows.size()];
    cardBottom = new int[rows.size()];
    listBottom = contentBottom;
    int avail = listBottom - listTop() - 4;
    rowH = UiTheme.ROW_H;
    rowHeights = new int[rows.size()];
    for (int i = 0; i < rows.size(); i++) rowHeights[i] = measuredRowHeight(rows.get(i));
    visibleRows = Math.max(1, (avail + 5) / (rowH + 5));
    rowAppear = 0L;
    buildTopBar();
    int cursorY = listTop() + 4, placed = 0, head = -1;
    if (tab == 2 && !searchActive()) cursorY = addVisualChips();
    maxScroll = computeMaxScroll(cursorY);
    scroll = Math.max(0, Math.min(maxScroll, scroll));
    revealPaletteRow(cursorY);
    revealReturnRows(cursorY);
    for (int i = scroll; i < rows.size(); i++) {
      Row r = rows.get(i);
      if (r.type == T_HEAD) {
        if (placed > 0) cursorY += GROUP_GAP - UiTheme.ROW_GAP;
        if (!headerFits(i, cursorY)) break;
        rowY[i] = cursorY;
        rowAppearAt[i] = appearAt(placed);
        cardBottom[i] = cursorY + GROUP_HEAD_H;
        head = i;
        placed++;
        cursorY += GROUP_HEAD_STEP;
        continue;
      }
      rowH = rowHeights[i];
      if (cursorY + rowH > listBottom - 4) break;
      int y = cursorY;
      rowY[i] = y;
      rowAppearAt[i] = appearAt(placed);
      long ap = rowAppearAt[i];
      placed++;
      cursorY += rowH + UiTheme.ROW_GAP;
      if (head >= 0) cardBottom[head] = Math.min(y + rowH + CARD_PAD, listBottom - 4);
      int w = subRight() - subLeft();
      rowAppear = ap;
      addRowControls(r, y, w);
    }
    rowAppear = 0L;
    if (placed > 0) visibleRows = placed;
    addFooterButtons();
    if (tab == 4) layoutHelp();
  }

  /** Keep the parent in view when its compact parameters are expanded. */
  private void revealReturnRows(int startY) {
    if (!revealReturnDetails) return;
    revealReturnDetails = false;
    int parent = -1, last = -1;
    for (int i = 0; i < rows.size(); i++) {
      if (rows.get(i).key == ROW_AUTO_RETURN) parent = i;
      if (rows.get(i).key == ROW_RETURN_TIMEOUT) last = i;
    }
    if (parent < 0 || last < parent) return;
    scroll = Math.min(scroll, parent);
    while (scroll < parent && lastVisibleRow(scroll, startY) < last) scroll++;
  }

  /** Opening a tall row must reveal its controls, not leave the palette below the viewport. */
  private void revealPaletteRow(int startY) {
    if (!revealColorRow) return;
    revealColorRow = false;
    for (int i = 0; i < rows.size(); i++) {
      if (!expandedColorRow(rows.get(i))) continue;
      scroll = Math.min(scroll, i);
      while (scroll < i && lastVisibleRow(scroll, startY) < i) scroll++;
      return;
    }
  }

  private void layoutWindow() {
    winW = Math.min(width - 14, 596);
    winH = Math.min(height - 14, 412);
    winX = (width - winW) / 2;
    winY = (height - winH) / 2;
    headH = Math.min(38, Math.max(24, winH / 9));
    footerY = winY + winH - 26;
    sideX = winX + 12;
    sideW = Math.max(96, Math.min(140, winW / 4));
    sideTop = winY + headH + 26;
    contentX = sideX + sideW + 12;
    contentRight = winX + winW - 12;
    contentTop = winY + headH + 26;
    contentBottom = footerY - 10;
    sideRowH =
        Math.max(
            18,
            Math.min(
                26, (contentBottom - sideTop - (TABS.length - 1) * UiTheme.ROW_GAP) / TABS.length));
  }

  private boolean sideHintFits(int tabIndex) {
    return fontRenderer != null
        && sideRowH >= 26
        && UiRowText.height(
                fontRenderer,
                tabName(tabIndex),
                tabHint(tabIndex),
                sideW - UiTheme.BTN_PAD * 2 - UiTheme.ICON - UiTheme.ICON_GAP)
            <= sideRowH - 6;
  }

  private void addTabButtons() {
    for (int n = 0; n < TABS.length; n++) {
      int i = TABS[n];
      int y = sideTop + n * (sideRowH + UiTheme.ROW_GAP);
      StyledButton b =
          add(
              new StyledButton(TAB_BASE + i, sideX, y, sideW, sideRowH, tabName(i))
                  .accent(tabColor(i))
                  .icon(tabIcon(i))
                  // The section hint is shown right away if the row is tall enough: fewer hovers
                  // needed.
                  .sub(sideHintFits(i) ? tabHint(i) : null)
                  .wrapText()
                  .compact()
                  .tip(Lang.tip("settings.click_to_open_this_group", tabName(i), tabHint(i))));
      if (i == tab) b.primary();
    }
  }

  /** The "Route / Start marker" toggles, without the decorative preview. */
  private int addVisualChips() {
    int chipY = listTop() + 6,
        chipH = 20,
        gap = 6,
        chipW = Math.max(48, (contentRight - contentX - 12 - gap) / 2);
    StyledButton pathChip =
        add(
            new StyledButton(
                    VIS_PATH, contentX + 6, chipY, chipW, chipH, Lang.t("settings.route_2"))
                .accent(UiTheme.PURPLE)
                .icon("route")
                .compact()
                .tip(Lang.tip("settings.route_the_path_line_in")));
    StyledButton markChip =
        add(
            new StyledButton(
                    VIS_MARK,
                    contentX + 12 + chipW,
                    chipY,
                    chipW,
                    chipH,
                    Lang.t("settings.start_mark"))
                .accent(UiTheme.AMBER)
                .icon("\u2691")
                .compact()
                .tip(Lang.tip("settings.start_mark_the_mark_where")));
    if (visElement == 0) pathChip.primary();
    else markChip.primary();
    return chipY + chipH + 6;
  }

  /** Row button by its type: toggle, value cycle, action or −/+ step. */
  private void addRowControls(Row r, int y, int w) {
    if (r.type == T_TOGGLE && r.key == ROW_AUTO_LEAVE) addAutoLeaveRow(r, y, w);
    else if (r.type == T_TOGGLE && r.key == ROW_STOP_ON_MOUSE)
      addStepToggleRow(
          r,
          y,
          w,
          ROW_STOP_ON_MOUSE_PX,
          Lang.t("settings.mouse_movement_threshold"),
          Lang.t("settings.from_1_to_50_px"));
    else if (r.type == T_TOGGLE && r.key == ROW_AUTO_DELETE_MESSAGES)
      addStepToggleRow(
          r,
          y,
          w,
          ROW_MESSAGE_LIFETIME,
          Lang.t("settings.message_lifetime"),
          Lang.t("settings.message_lifetime_tip"));
    else if (r.type == T_TOGGLE) {
      StyledButton toggle =
          new StyledButton(ACT_BASE + r.key, subLeft(), y, w, rowH, r.label)
              .asToggle(stateOf(r.key))
              .icon(r.icon)
              .sub(r.note)
              .wrapText()
              .tip(r.tip);
      if (r.key == ROW_AUTO_RETURN && !searchActive()) toggle.expander(returnDetailsOpen);
      add(toggle);
    } else if (r.type == T_CYCLE) {
      StyledButton cb =
          add(
              new StyledButton(ACT_BASE + r.key, subLeft(), y, w, rowH, r.label)
                  .accent(r.color)
                  .icon(r.icon)
                  .sub(r.note)
                  .wrapText()
                  .value(valueOf(r.key))
                  .tip(r.tip));
      if (r.key == ROW_COLOR_TARGET && colorPanelOpen) cb.primary();
    } else if (r.type == T_ACTION) {
      boolean expanded = expandedColorRow(r);
      int buttonH = rowH - (expanded ? COLOR_PANEL_GAP + colorPanelHeight() : 0);
      StyledButton action =
          new StyledButton(ACT_BASE + r.key, subLeft(), y, w, buttonH, r.label)
              .accent(r.color)
              .icon(r.icon)
              .sub(expanded ? null : r.note)
              .wrapText()
              .tip(r.tip);
      if (expanded) {
        action.primary();
        addColorWheel(y + buttonH + COLOR_PANEL_GAP);
      }
      add(action);
    } else if (r.type == T_STEP) addStepControls(r, y);
  }

  /** −/+ buttons of a step row; playback speed also gets a "Default" button. */
  private void addStepControls(Row r, int y) {
    int bs = stepBtn(), by = counterY(y), bx = stepBlockX();
    boolean lock = rowLocked(r.key);
    add(
        lock,
        new StyledButton(DEC_BASE + r.key, bx, by, bs, bs, "\u2212")
            .accent(UiTheme.GRAY)
            .compact()
            .flat()
            .tip(Lang.tip("settings.less_decreases_shift_for_a", r.label)));
    add(
        lock,
        new StyledButton(INC_BASE + r.key, bx + bs + stepValW(), by, bs, bs, "+")
            .accent(UiTheme.GRAY)
            .compact()
            .flat()
            .tip(Lang.tip("settings.more_increases_shift_for_a", r.label)));
    if (r.key == ROW_PLAYBACK_SPEED) {
      int dx = bx - 3 - bs;
      add(
          new StyledButton(ACT_BASE + ROW_PLAYBACK_SPEED_RESET, dx, by, bs, bs, "")
              .accent(UiTheme.GRAY)
              .compact()
              .boxedSpeedReset()
              .flat()
              .tip(Lang.tip("settings.normal_speed_sets_1_00x")));
    }
  }

  /** The picker belongs to its color row and takes part in the same scrolling geometry. */
  private void addColorWheel(int top) {
    colorPanelY = top;
    int pad = colorPanelPadding();
    wheelSize =
        Math.max(
            18,
            Math.min(
                56,
                Math.min(
                    colorPanelHeight() - pad * 2 - 16, subRight() - subLeft() - pad * 2 - 34)));
    wheel = new GuiColorWheel(subLeft() + pad, top + pad, wheelSize);
    if (colorTarget == 1)
      wheel.setColorRGBA(
          config.getMarkerOwnR(),
          config.getMarkerOwnG(),
          config.getMarkerOwnB(),
          config.getMarkerOwnAlpha());
    else
      wheel.setColorRGBA(
          config.getPathLineR(),
          config.getPathLineG(),
          config.getPathLineB(),
          config.getPathLineAlpha());
  }

  private void addFooterButtons() {
    int bw = Math.min(112, (winW - 40) / 4), bh = UiTheme.BTN_H;
    cancel =
        add(
            new StyledButton(
                    CANCEL, winX + winW - 12 - bw * 2 - 6, footerY, bw, bh, Lang.t("common.cancel"))
                .accent(UiTheme.GRAY));
    save =
        add(
            new StyledButton(SAVE, winX + winW - 12 - bw, footerY, bw, bh, Lang.t("common.save"))
                .primary()
                .accent(UiTheme.GREEN));
  }

  private boolean helpCompact;

  // ------------------------------------------------ inline help
  private void layoutHelp() {
    String[][] d = HelpTexts.current();
    helpSection = Math.max(0, Math.min(d.length - 1, helpSection));
    layoutHelpColumns(d.length);
    helpLines.clear();
    String[] body = d[helpSection];
    int wrapW = helpBodyWidth();
    helpLines.addAll(
        UiTextBlock.wrap(
            fontRenderer,
            java.util.Arrays.copyOfRange(body, 1, body.length),
            wrapW,
            UiTheme.TEXT_DIM,
            UiTheme.TEXT_DIM));
    int bodyTop = helpTextTop + 24, bodyBottom = helpBodyBottom();
    helpVisible = UiTextBlock.visibleLines(fontRenderer, bodyBottom - bodyTop);
    helpMaxScroll = Math.max(0, helpLines.size() - helpVisible);
    helpScroll = Math.max(0, Math.min(helpMaxScroll, helpScroll));
    int navInset = helpListRight - helpListX < 96 ? 4 : 8;
    int nb = helpListRight - helpListX < 96 ? 18 : 22, navY = contentBottom - 26;
    add(
        new StyledButton(HELP_PREV, helpListX + navInset, navY, nb, 20, "\u2039")
            .accent(UiTheme.GRAY)
            .compact());
    add(
        new StyledButton(HELP_NEXT, helpListRight - navInset - nb, navY, nb, 20, "\u203a")
            .accent(UiTheme.GRAY)
            .compact());
  }

  /** Keep every chapter above the navigation, including at Minecraft's minimum GUI height. */
  private void layoutHelpColumns(int count) {
    int avail = contentRight - contentX - 12;
    helpCompact = avail < 260;
    helpListX = contentX + 6;
    // On narrow windows the chapter list gives its space to the text. The existing previous/next
    // buttons and chapter counter still expose all eleven chapters below the text.
    helpListRight =
        helpCompact
            ? contentRight - 6
            : helpListX + Math.max(80, Math.min(152, (int) (avail * 0.38f)));
    helpListTop = contentTop + 6;
    helpRowH = Math.max(9, Math.min(18, (contentBottom - helpListTop - 32) / Math.max(1, count)));
    helpTextX = helpCompact ? helpListX : helpListRight + 8;
    helpTextRight = contentRight - 6;
    helpTextTop = contentTop + 6;
  }

  private int helpBodyBottom() {
    return contentBottom - (helpCompact ? 36 : 8);
  }

  private int helpBodyWidth() {
    // The same inner width is used when wrapping, drawing and clipping.
    return Math.max(1, helpTextRight - helpTextX - 26);
  }

  private void drawHelpPanel(int mx, int my) {
    String[][] d = HelpTexts.current();
    if (!helpCompact) {
      UiTheme.card(
          helpListX, helpListTop - 4, helpListRight, contentBottom, UiTheme.DEEP, UiTheme.BORDER);
      for (int i = 0; i < d.length; i++) {
        int y = helpListTop + i * helpRowH;
        if (y + helpRowH > contentBottom - 32) break;
        boolean sel = i == helpSection,
            hov = mx >= helpListX && mx < helpListRight && my >= y && my < y + helpRowH;
        if (sel || hov)
          UiTheme.round(
              helpListX + 3,
              y,
              helpListRight - 3,
              y + helpRowH - 1,
              sel ? UiTheme.CARD_SEL : UiTheme.CARD_HOVER);
        UiTheme.dot(helpListX + 8, y + (helpRowH - 6) / 2, sel ? UiTheme.PURPLE : UiTheme.GRAY);
        UiTheme.drawFitted(
            fontRenderer,
            d[i][0],
            helpListX + 19,
            UiTheme.centerTextY(fontRenderer, y, y + helpRowH),
            helpListRight - helpListX - 26,
            sel ? UiTheme.TEXT : UiTheme.TEXT_DIM);
      }
    }
    UiTheme.card(
        helpTextX, helpTextTop - 4, helpTextRight, contentBottom, UiTheme.CARD, UiTheme.BORDER);
    UiTheme.drawFittedShadow(
        fontRenderer,
        "\u00a7l" + d[helpSection][0],
        helpTextX + 9,
        helpTextTop + 2,
        helpTextRight - helpTextX - 18,
        UiTheme.TEXT);
    int y = helpTextTop + 24;
    int lineHeight = UiTextBlock.lineHeight(fontRenderer);
    UiTheme.beginClip(mc, helpTextX + 3, y - 2, helpTextRight - 10, helpBodyBottom());
    try {
      for (int i = helpScroll; i < helpLines.size() && i < helpScroll + helpVisible; i++) {
        UiTextBlock.Line line = helpLines.get(i);
        UiTheme.drawTextLine(fontRenderer, line.text, helpTextX + 9, y, line.color);
        y += lineHeight;
      }
    } finally {
      UiTheme.endClip();
    }
    if (helpLines.size() > helpVisible) {
      int trackTop = helpTextTop + 24, trackBottom = helpBodyBottom();
      int trackH = Math.max(1, trackBottom - trackTop);
      int thumbH = Math.min(trackH, Math.max(12, trackH * helpVisible / helpLines.size()));
      int thumbY = trackTop + (trackH - thumbH) * helpScroll / Math.max(1, helpMaxScroll);
      UiTheme.round(helpTextRight - 7, trackTop, helpTextRight - 5, trackBottom, UiTheme.BORDER);
      UiTheme.round(helpTextRight - 7, thumbY, helpTextRight - 5, thumbY + thumbH, UiTheme.PURPLE);
    }
    int navWidth = helpListRight - helpListX;
    int navInset = navWidth < 96 ? 4 : 8, navButton = navWidth < 96 ? 18 : 22;
    UiTheme.drawCenteredFitted(
        fontRenderer,
        (helpSection + 1) + (navWidth < 96 ? "/" : " / ") + d.length,
        helpNavCenter(),
        contentBottom - 20,
        Math.max(1, navWidth - 2 * (navInset + navButton) - 2),
        UiTheme.TEXT_MUTE,
        false);
  }

  private int helpNavCenter() {
    return helpListX + (helpListRight - helpListX) / 2;
  }

  private void selectHelp(int index) {
    int total = HelpTexts.current().length;
    helpSection = (index % total + total) % total;
    helpScroll = 0;
    initGui();
  }

  private void applyWheel() {
    if (wheel == null) return;
    wheelTouched = true;
    if (visElement == 1 || colorTarget == 1) {
      config.setMarkerCustomColor(true);
      config.setMarkerR(wheel.getRed());
      config.setMarkerG(wheel.getGreen());
      config.setMarkerB(wheel.getBlue());
      config.setMarkerAlpha(wheel.getAlpha());
    } else {
      config.setPathLineR(wheel.getRed());
      config.setPathLineG(wheel.getGreen());
      config.setPathLineB(wheel.getBlue());
      config.setPathLineAlpha(wheel.getAlpha());
    }
  }

  /** Use the ordinary setting row; the compact mode icon shares the counter strip. */
  private void addAutoLeaveRow(Row r, int y, int w) {
    int by = counterY(y), modeX = leaveModeX();
    int bx = leaveBlockX(), bs = stepBtn(), valW = leaveValW();
    boolean lock = !config.isAutoLeaveEnabled();
    StyledButton main =
        add(
            new StyledButton(ACT_BASE + r.key, subLeft(), y, w, rowH, r.label)
                .asToggle(stateOf(r.key))
                .icon(r.icon)
                .sub(r.note)
                .wrapText()
                .tip(r.tip)
                .embedStep(bx, by, bs, valW)
                .stackCounter(stackedSteps())
                .extendGuardLeft(modeX - 2));
    main.embedValue = counterValue(ROW_LEAVE_CYCLES);
    main.embedDim = lock;
    stepToggleBtns.add(main);
    stepToggleKeys.add(Integer.valueOf(ROW_LEAVE_CYCLES));
    addLeaveMode(lock, modeX, by);
    addLeaveCounterControls(lock, bx, by, valW);
    if (config.isAutoLeaveTimed()) {
      leaveEditX = bx + bs;
      leaveEditY = by;
      leaveEditW = valW;
      leaveEditH = bs;
    }
  }

  private void addLeaveMode(boolean lock, int x, int y) {
    boolean timed = config.isAutoLeaveTimed();
    add(
        lock,
        new StyledButton(ACT_BASE + ROW_LEAVE_MODE, x, y, stepBtn(), stepBtn(), "")
            .icon(timed ? "timer" : "reload")
            .accent(UiTheme.BLUE)
            .compact()
            .tip(
                Lang.tip(
                    timed
                        ? "settings.auto_leave_mode_time_tip"
                        : "settings.auto_leave_mode_cycles_tip")));
  }

  private void addLeaveCounterControls(boolean lock, int bx, int by, int valW) {
    int bs = stepBtn();
    String label =
        Lang.t(
            config.isAutoLeaveTimed()
                ? "settings.auto_leave_time_value"
                : "settings.cycles_before_leaving");
    String hint =
        Lang.t(
            config.isAutoLeaveTimed()
                ? "settings.auto_leave_time_step_tip"
                : "settings.ctrl_steps_by_10");
    add(
        lock,
        new StyledButton(DEC_BASE + ROW_LEAVE_CYCLES, bx, by, bs, bs, "−")
            .accent(UiTheme.GRAY)
            .compact()
            .flat()
            .tip((Lang.t("settings.less_decreases") + label + "\n" + hint).split("\n")));
    add(
        lock,
        new StyledButton(INC_BASE + ROW_LEAVE_CYCLES, bx + bs + valW, by, bs, bs, "+")
            .accent(UiTheme.GRAY)
            .compact()
            .flat()
            .tip((Lang.t("settings.more_increases") + label + "\n" + hint).split("\n")));
  }

  /** Toggle buttons of rows built by {@link #addStepToggleRow}. */
  private final List<StyledButton> stepToggleBtns = new ArrayList<StyledButton>();

  /** Setting keys of the inline counters, in the same order as {@link #stepToggleBtns}. */
  private final List<Integer> stepToggleKeys = new ArrayList<Integer>();

  /**
   * A feature row with an inline counter: "− N +" lives inside it on the right, the toggle sits
   * right of the counter. All numeric rows share this counter column. While the feature itself is
   * off, the counter is grey and not clickable.
   */
  private void addStepToggleRow(
      Row r, int y, int w, int stepKey, String stepLabel, String stepHint) {
    int bs = stepBtn(), by = counterY(y);
    int valW = stepValW(), bx = stepBlockX();
    boolean lock = rowLocked(stepKey);
    StyledButton main =
        add(
            new StyledButton(ACT_BASE + r.key, subLeft(), y, w, rowH, r.label)
                .asToggle(stateOf(r.key))
                .icon(r.icon)
                .sub(r.note)
                .wrapText()
                .tip(r.tip)
                .embedStep(bx, by, bs, valW)
                .stackCounter(stackedSteps()));
    main.embedValue = counterValue(stepKey);
    main.embedDim = lock;
    stepToggleBtns.add(main);
    stepToggleKeys.add(Integer.valueOf(stepKey));
    String less = Lang.t("settings.less_decreases");
    String more = Lang.t("settings.more_increases");
    add(
        lock,
        new StyledButton(DEC_BASE + stepKey, bx, by, bs, bs, "−")
            .accent(UiTheme.GRAY)
            .compact()
            .flat()
            .tip((less + stepLabel + "\n" + stepHint).split("\n")));
    add(
        lock,
        new StyledButton(INC_BASE + stepKey, bx + bs + valW, by, bs, bs, "+")
            .accent(UiTheme.GRAY)
            .compact()
            .flat()
            .tip((more + stepLabel + "\n" + stepHint).split("\n")));
  }

  // ------------------------------------------------ drawing
  /** How far the row has appeared in the cascade: 0..1. */
  private float rowFade(int index) {
    if (index < 0 || index >= rowAppearAt.length) return 1f;
    long at = rowAppearAt[index];
    if (at <= 0L) return 1f;
    long e = UiTheme.nowMs() - at;
    if (e <= 0L) return 0f;
    if (e >= UiTheme.APPEAR_MS) return 1f;
    return UiTheme.easeOut(e / (float) UiTheme.APPEAR_MS);
  }

  private void beginRowAnim(float ap) {
    if (ap >= 0.999f) return;
    UiTheme.opacity(ap);
  }

  private void endRowAnim(float ap) {
    if (ap >= 0.999f) return;
    UiTheme.opacityReset();
  }
  /** Module backdrop: the header and all nested settings live in one card. */
  private void moduleCard(Row r, int index, int gy) {
    int bottom = index < cardBottom.length ? cardBottom[index] : gy + GROUP_HEAD_H;
    if (bottom <= gy + GROUP_HEAD_H + 2) return;
    int l = cardLeft(), rr = cardRight(), c = tabColor(tab);
    UiTheme.card(l, gy, rr, bottom, UiTheme.alpha(UiTheme.DEEP, 0.55f), UiTheme.BORDER);
    // Guide line in the section color: shows the settings belong to this module.
    UiTheme.round(l + 6, gy + GROUP_HEAD_H, l + 8, bottom - 6, UiTheme.alpha(c, 0.45f));
  }
  /** How many module settings can be enabled and how many are enabled now. */
  private int[] groupToggles(int group) {
    int g = -1, total = 0, on = 0;
    for (Row r : rawRows) {
      if (r.type == T_HEAD) {
        g++;
        continue;
      }
      if (g != group || r.type != T_TOGGLE) continue;
      total++;
      if (stateOf(r.key)) on++;
    }
    return new int[] {total, on};
  }

  private int groupIndexOfHead(Row head) {
    int g = -1;
    for (Row r : rows) {
      if (r.type != T_HEAD) continue;
      g++;
      if (r == head) return g;
    }
    return -1;
  }
  /** Square of the module master checkbox, or null if the module has nothing to enable. */
  private int[] masterBox(Row r, int gy) {
    int group = groupIndexOfHead(r);
    if (group < 0) return null;
    int[] st = groupToggles(group);
    if (st[0] == 0) return null;
    int cs = StyledButton.checkSize(GROUP_HEAD_H);
    int cx = cardRight() - UiTheme.BTN_PAD - cs, cy = gy + (GROUP_HEAD_H - cs) / 2;
    return new int[] {cx, cy, cs, st[0], st[1]};
  }
  /** Module header: arrow, name, counter and master checkbox on the right. */
  private static final int GROUP_LABEL_MIN_W = 70;

  private void headRow(Row r, int gy, int mx, int my) {
    int l = cardLeft(), rr = cardRight(), slot = HEAD_KEY_BASE - r.key;
    boolean shut = slot >= 0 && slot < collapsed[tab].length && collapsed[tab][slot];
    int[] box = masterBox(r, gy);
    boolean overBox = box != null && mx >= box[0] - 2 && mx < box[0] + box[2] + 2;
    boolean hov = mx >= l && mx < rr && my >= gy && my < gy + GROUP_HEAD_H;
    UiTheme.rowCard(l, gy, rr, gy + GROUP_HEAD_H, hov && !overBox ? 1f : 0f, false, tabColor(tab));
    UiTheme.chevron(
        l + 13f, gy + GROUP_HEAD_H / 2f, 7f, shut ? 0f : 1f, hov ? UiTheme.TEXT : UiTheme.TEXT_DIM);
    int ic = UiTheme.ICON, ix = l + 22;
    UiTheme.badge(
        fontRenderer, tabIcon(tab), ix, gy + (GROUP_HEAD_H - ic) / 2, ic, tabColor(tab), true);
    int tx = ix + ic + UiTheme.ICON_GAP;
    int noteW = r.note == null ? 0 : fontRenderer.getStringWidth(r.note);
    int noteRight = box == null ? rr - UiTheme.BTN_PAD - 4 : box[0] - 12;
    int noteX = noteRight - noteW;
    boolean below = r.note != null && noteX - 12 < tx + fontRenderer.getStringWidth(r.label) + 3;
    int textY = gy + (GROUP_HEAD_H - fontRenderer.FONT_HEIGHT) / 2;
    if (below) {
      UiTheme.drawFittedShadow(
          fontRenderer, r.label, tx, gy + 2, Math.max(16, noteRight - tx), UiTheme.TEXT);
      UiTheme.drawFitted(
          fontRenderer, r.note, tx, gy + 14, Math.max(16, noteRight - tx), UiTheme.TEXT_MUTE);
    } else {
      int labelRight = r.note == null ? noteRight : noteX - 12;
      UiTheme.drawFittedShadow(
          fontRenderer, r.label, tx, textY, Math.max(16, labelRight - tx), UiTheme.TEXT);
      if (r.note != null)
        UiTheme.drawTextLine(fontRenderer, r.note, noteX, textY, UiTheme.TEXT_MUTE);
    }
    if (box != null) {
      float on = box[3] > 0 && box[4] == box[3] ? 1f : 0f;
      float mixed = box[4] > 0 && box[4] < box[3] ? 1f : 0f;
      if (overBox && hov)
        UiTheme.flashRing(box[0], box[1], box[0] + box[2], box[1] + box[2], UiTheme.ACCENT, 0.5f);
      UiTheme.checkAt(box[0], box[1], box[2], on, true, UiTheme.GREEN, mixed);
    }
  }

  private void valueRow(Row r, int y, int mx, int my) {
    boolean mini = isReturnMini(r);
    int l = subLeft() + (mini ? 12 : 0), rr = subRight();
    boolean hov = mx >= l && mx < rr && my >= y && my < y + rowH;
    UiTheme.rowCard(l, y, rr, y + rowH, hov, false, r.color);
    int size = Math.min(rowH - 8, 14), tx = l + 8, bs = stepBtn(), vx = stepBlockX();
    boolean lock = rowLocked(r.key);
    if (!mini && r.icon != null && rowH >= 16) {
      UiTheme.badge(fontRenderer, r.icon, l + 6, y + (rowH - size) / 2, size, r.color, true);
      tx = l + 6 + size + 6;
    }
    int lim = Math.max(16, (r.type == T_STEP && !stackedSteps() ? vx - 10 : rr - 10) - tx);
    int textH = rowH - 8 - (r.type == T_STEP && stackedSteps() ? bs + 5 : 0);
    UiRowText.draw(
        fontRenderer,
        r.label,
        mini ? null : r.note,
        tx,
        y + 4,
        lim,
        textH,
        lock ? UiTheme.TEXT_MUTE : UiTheme.TEXT,
        UiTheme.TEXT_MUTE);
    if (r.type == T_STEP) {
      int by = counterY(y), bw = stepBlockW();
      UiTheme.counterFrame(vx, by, bs, stepValW());
      float fl = flashAmt(r.key);
      if (fl > 0.02f) UiTheme.counterFeedback(vx, by, vx + bw, by + bs, fl);
      UiTheme.drawCenteredFitted(
          fontRenderer,
          counterValue(r.key),
          vx + bs + stepValW() / 2f,
          by + (bs - fontRenderer.FONT_HEIGHT) / 2,
          stepValW() - 4,
          lock ? UiTheme.TEXT_MUTE : UiTheme.TEXT,
          false);
    }
  }

  @Override
  public void drawScreen(int mx, int my, float pt) {
    int bd = UiTheme.GLASS_BACKDROP;
    drawGradientRect(0, 0, width, height, bd, bd);
    UiTheme.window(winX, winY, winX + winW, winY + winH, headH);
    drawHeader();
    int sideBottom = sideTop + (TABS.length - 1) * (sideRowH + UiTheme.ROW_GAP) + sideRowH;
    UiTheme.card(
        sideX - 4,
        sideTop - 4,
        sideX + sideW + 4,
        sideBottom + 4,
        UiTheme.FUNCTION_LIST_GLASS,
        UiTheme.BORDER_HI);
    if (tab != 4)
      UiTheme.card(
          contentX,
          listTop(),
          contentRight,
          listBottom,
          UiTheme.FUNCTION_LIST_GLASS,
          UiTheme.BORDER_HI);
    drawSearchBox();
    if (searchActive() && rows.isEmpty())
      UiTheme.drawCenteredFitted(
          fontRenderer,
          Lang.t("settings.nothing_found_try_another_word"),
          (contentX + contentRight) / 2f,
          listTop() + 24,
          Math.max(40, contentRight - contentX - 24),
          UiTheme.TEXT_MUTE,
          false);
    if (tab == 4) drawHelpPanel(mx, my);
    drawRowDecor(mx, my);
    if (sbActive()) drawScrollBar(mx, my);
    if (wheel != null) drawColorPanel();
    if (saveError != SAVE_OK)
      UiTheme.drawFitted(
          fontRenderer,
          saveError == SAVE_PARTIAL
              ? Lang.t("settings.general_settings_saved_the_slot")
              : Lang.t("settings.could_not_save_the_settings"),
          winX + 16,
          footerY + 6,
          Math.max(20, winW - 260),
          UiTheme.RED);
    for (int i = 0; i < stepToggleBtns.size(); i++) {
      int k = stepToggleKeys.get(i).intValue();
      StyledButton b = stepToggleBtns.get(i);
      b.embedValue = k == ROW_LEAVE_CYCLES && leaveEditor != null ? "" : counterValue(k);
      b.embedDim = rowLocked(k);
      b.embedFlash = flashAmt(k);
    }
    super.drawScreen(mx, my, pt);
    if (leaveEditor != null) leaveEditor.drawTextBox();
    drawTips(mx, my);
  }

  /** Window header, the "Changed" badge and the current section name. */
  private void drawHeader() {
    UiTheme.header(
        fontRenderer,
        winX,
        winY,
        winX + winW,
        headH,
        Lang.t("main.settings"),
        Lang.t("settings.sections_on_the_left_hover"),
        tabColor(tab),
        80);
    if (changedForChip()) {
      String chip = Lang.t("settings.changed");
      int cw = UiTheme.pillWidth(fontRenderer, chip);
      UiTheme.chip(
          fontRenderer,
          chip,
          winX + winW - 12 - cw,
          winY + (headH - UiTheme.PILL_H) / 2,
          UiTheme.AMBER);
    }
    UiTheme.drawFittedShadow(
        fontRenderer,
        "\u00a7l" + tabName(tab),
        contentX + 6,
        contentTop - 15,
        Math.max(24, contentRight - contentX - 70),
        UiTheme.TEXT);
  }

  /** Search field: hint in an empty field and the number of matching rows. */
  private void drawSearchBox() {
    if (tab != 4 && search != null) {
      boolean focus = search.isFocused();
      UiTheme.card(
          searchX,
          searchY,
          searchX + searchW,
          searchY + TOPBAR_H,
          UiTheme.DEEP,
          focus ? UiTheme.CARD_SELB : UiTheme.BORDER);
      int gc = focus ? UiTheme.ACCENT : UiTheme.TEXT_MUTE;
      VectorIcons.draw("search", searchX + 10.5f, searchY + 9f, 1.15f, gc);
      if (searchQuery.length() == 0 && !focus)
        UiTheme.drawFitted(
            fontRenderer,
            Lang.t("settings.search_settings"),
            searchX + 18,
            searchY + (TOPBAR_H - fontRenderer.FONT_HEIGHT) / 2,
            searchW - 24,
            UiTheme.TEXT_MUTE);
      search.drawTextBox();
      if (searchActive()) {
        String found = rows.size() + Lang.t("settings.found");
        int fw = fontRenderer.getStringWidth(found);
        UiTheme.drawFitted(
            fontRenderer,
            found,
            searchX + searchW - fw - 7,
            searchY + (TOPBAR_H - fontRenderer.FONT_HEIGHT) / 2,
            fw,
            rows.isEmpty() ? UiTheme.AMBER : UiTheme.TEXT_MUTE);
      }
    }
  }

  /**
   * Group cards and step rows with the appearance animation (buttons are drawn by
   * super.drawScreen).
   */
  private void drawRowDecor(int mx, int my) {
    drawContinuedModuleDecor();
    for (int i = 0; i < rows.size(); i++) {
      if (rowY[i] < 0) continue;
      Row r = rows.get(i);
      rowH = rowHeights[i];
      if (r.type == T_HEAD) {
        float ap = rowFade(i);
        if (ap <= 0.01f) continue;
        beginRowAnim(ap);
        moduleCard(r, i, rowY[i]);
        headRow(r, rowY[i], mx, my);
        endRowAnim(ap);
        continue;
      }
      if (r.type == T_STEP) {
        float ap = rowFade(i);
        if (ap <= 0.01f) continue;
        beginRowAnim(ap);
        valueRow(r, rowY[i], mx, my);
        endRowAnim(ap);
      }
    }
  }

  /** Keep the module guide visible when its header has scrolled out of the row window. */
  private int[] continuedModuleBounds() {
    if (searchActive() || tab == 4) return null;
    int first = -1;
    for (int i = 0; i < rowY.length; i++) {
      if (rowY[i] >= 0) {
        first = i;
        break;
      }
    }
    if (first < 0 || rows.get(first).type == T_HEAD) return null;
    int head = first - 1;
    while (head >= 0 && rows.get(head).type != T_HEAD) head--;
    if (head < 0) return null;
    int bottom = rowY[first] + heightAt(first) + CARD_PAD;
    for (int i = first + 1; i < rows.size() && rows.get(i).type != T_HEAD; i++) {
      if (rowY[i] >= 0) bottom = rowY[i] + heightAt(i) + CARD_PAD;
    }
    return new int[] {rowY[first], Math.min(bottom, listBottom - 4)};
  }

  private void drawContinuedModuleDecor() {
    int[] bounds = continuedModuleBounds();
    if (bounds == null || bounds[1] <= bounds[0]) return;
    int l = cardLeft(), rr = cardRight();
    UiTheme.card(l, bounds[0], rr, bounds[1], UiTheme.alpha(UiTheme.DEEP, 0.55f), UiTheme.BORDER);
    UiTheme.round(l + 6, bounds[0], l + 8, bounds[1] - 6, UiTheme.alpha(tabColor(tab), 0.45f));
  }

  private void drawScrollBar(int mx, int my) {
    long now = UiTheme.nowMs();
    boolean target =
        sbDrag || (mx >= contentRight - 7 && mx < contentRight && my >= sbTop() && my < sbBottom());
    if (target != sbHoverTarget) {
      sbHoverFrom = sbHover;
      sbHoverTarget = target;
      sbHoverAt = now;
    }
    sbHover =
        UiTheme.transition(sbHoverFrom, sbHoverTarget ? 1f : 0f, sbHoverAt, now, UiTheme.HOVER_MS);
    // Draw with the same geometry used for hit testing and dragging; row heights can differ.
    UiTheme.round(contentRight - 6, sbTop(), contentRight - 4, sbBottom(), UiTheme.BORDER);
    UiTheme.round(
        contentRight - 6,
        sbThumbY(),
        contentRight - 4,
        sbThumbY() + sbThumbH(),
        UiTheme.lerp(tabColor(tab), UiTheme.TEXT, sbHover * 0.3f));
  }

  private void drawColorPanel() {
    UiTheme.card(
        subLeft(),
        colorPanelY,
        subRight(),
        colorPanelY + colorPanelHeight(),
        UiTheme.DEEP,
        UiTheme.alpha(UiTheme.PURPLE, 0.45f));
    wheel.draw();
    int px = subLeft() + colorPanelPadding() + wheel.getWidth() + 14;
    int right = subRight() - colorPanelPadding(), py = colorPanelY + colorPanelPadding();
    if (right - px < 64 || colorPanelHeight() < 60) return;
    int rgb = wheel.getRed() << 16 | wheel.getGreen() << 8 | wheel.getBlue();
    drawColorSwatch(px, py, rgb);
    UiTheme.drawFitted(
        fontRenderer,
        String.format(Locale.ROOT, "#%06X", rgb),
        px + 30,
        py + 7,
        right - px - 30,
        UiTheme.TEXT);
    UiTheme.drawFitted(
        fontRenderer,
        Lang.t("settings.color_opacity", Math.round(wheel.getAlpha() * 100f / 255f)),
        px,
        py + 31,
        right - px,
        UiTheme.TEXT_MUTE);
  }

  private void drawColorSwatch(int x, int y, int rgb) {
    UiTheme.card(x, y, x + 22, y + 22, UiTheme.DEEP, UiTheme.BORDER_HI);
    UiTheme.beginRects();
    try {
      for (int dx = 0; dx < 18; dx += 3)
        for (int dy = 0; dy < 18; dy += 3)
          UiTheme.rect(
              x + 2 + dx,
              y + 2 + dy,
              x + 5 + dx,
              y + 5 + dy,
              (dx / 3 + dy / 3) % 2 == 0 ? 0xFFCDD2DA : 0xFF858E9D);
      UiTheme.rect(x + 2, y + 2, x + 20, y + 20, wheel.getAlpha() << 24 | rgb);
    } finally {
      UiTheme.endRects();
    }
  }

  private void drawTips(int mx, int my) {
    if (!config.isShowTooltips()) return;
    if (leaveEditor == null
        && leaveEditX >= 0
        && mx >= leaveEditX
        && mx < leaveEditX + leaveEditW
        && my >= leaveEditY
        && my < leaveEditY + leaveEditH) {
      if (UiTheme.tipReady(this))
        UiTheme.tooltip(
            fontRenderer, Lang.tip("settings.auto_leave_value_tip"), mx, my, width, height);
      return;
    }
    for (GuiButton g : buttonList)
      if (g instanceof StyledButton) {
        StyledButton b = (StyledButton) g;
        if (b.visible
            && b.tooltip != null
            && mx >= b.x
            && my >= b.y
            && mx < b.x + b.width
            && my < b.y + b.height
            && !b.inGuard(mx, my)) {
          if (UiTheme.tipReady(b)) UiTheme.tooltip(fontRenderer, b.tooltip, mx, my, width, height);
          return;
        }
      }
    for (int i = 0; i < rows.size(); i++) {
      if (rowY[i] < 0) continue;
      Row r = rows.get(i);
      if (r.type == T_STEP
          && my >= listTop() + 4
          && my < listBottom - 4
          && r.tip != null
          && mx >= subLeft()
          && mx < subRight()
          && my >= rowY[i]
          && my < rowY[i] + heightAt(i)) {
        if (UiTheme.tipReady(r)) UiTheme.tooltip(fontRenderer, r.tip, mx, my, width, height);
        return;
      }
    }
  }

  // ------------------------------------------------ input
  @Override
  public void handleMouseInput() throws IOException {
    super.handleMouseInput();
    int d = Mouse.getEventDWheel();
    int mx = Mouse.getEventX() * width / mc.displayWidth;
    int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
    if (!wheelOverContent(mx, my)) return;
    if (d != 0 && tab == 4) {
      helpScroll = Math.max(0, Math.min(helpMaxScroll, helpScroll + (d > 0 ? -2 : 2)));
      return;
    }
    if (d != 0) {
      int max = maxScroll, old = scroll;
      scroll = Math.max(0, Math.min(max, scroll + (d > 0 ? -1 : 1)));
      if (scroll != old) initGui();
    }
  }

  private boolean wheelOverContent(int mx, int my) {
    if (tab == 4)
      return mx >= helpTextX
          && mx < helpTextRight
          && my >= helpTextTop + 24
          && my < helpBodyBottom();
    return mx >= contentX && mx < contentRight && my >= listTop() && my < listBottom;
  }

  /** Switching to another section resets the search: otherwise the list stays mixed. */
  private void clearSearch() {
    searchQuery = "";
    searchFocused = false;
    if (search != null) {
      search.setText("");
      search.setFocused(false);
    }
  }

  private void setSearch(String q) {
    searchQuery = q == null ? "" : q;
    searchFocused = true;
    scroll = 0;
    listAnimAt = UiTheme.nowMs();
    initGui();
  }

  @Override
  public void updateScreen() {
    super.updateScreen();
    if (search != null) search.updateCursorCounter();
    if (leaveEditor != null) leaveEditor.updateCursorCounter();
  }

  @Override
  protected void mouseClicked(int mx, int my, int btn) throws IOException {
    if (leaveEditor != null) {
      if (mx >= leaveEditX
          && mx < leaveEditX + leaveEditW
          && my >= leaveEditY
          && my < leaveEditY + leaveEditH) {
        leaveEditor.mouseClicked(mx, my, btn);
        return;
      }
      finishLeaveEdit(true);
      initGui();
    }
    if (btn == 0
        && config.isAutoLeaveEnabled()
        && leaveEditX >= 0
        && mx >= leaveEditX
        && mx < leaveEditX + leaveEditW
        && my >= leaveEditY
        && my < leaveEditY + leaveEditH) {
      leaveEditor =
          new TimeTextField(
              991,
              fontRenderer,
              leaveEditX + 3,
              leaveEditY,
              Math.max(1, leaveEditW - 6),
              leaveEditH);
      leaveEditor.setMaxStringLength(6);
      leaveEditor.setText(String.valueOf(config.getAutoLeaveMinutes()));
      leaveEditor.setFocused(true);
      leaveEditor.setCursorPositionEnd();
      leaveEditor.setSelectionPos(0);
      return;
    }
    if (search != null) {
      boolean inside =
          mx >= searchX && mx < searchX + searchW && my >= searchY && my < searchY + TOPBAR_H;
      if (inside) {
        if (btn == 0 || btn == 1) UiBubbles.burst(mx, my, tabColor(tab));
        if (btn == 1) {
          setSearch("");
          return;
        }
        searchFocused = true;
        search.setFocused(true);
        search.mouseClicked(mx, my, btn);
        return;
      }
      if (search.isFocused()) {
        searchFocused = false;
        search.setFocused(false);
      }
    }
    if ((btn == 0 || btn == 1) && !searchActive() && tab == 1) {
      for (int i = 0; i < rows.size(); i++) {
        int arrowX = StyledButton.expanderX(subRight(), heightAt(i));
        boolean arrowHit = mx >= arrowX - 8 && mx < arrowX + 8;
        if (rows.get(i).key == ROW_AUTO_RETURN
            && rowY[i] >= 0
            && mx >= subLeft()
            && mx < subRight()
            && my >= rowY[i]
            && my < rowY[i] + heightAt(i)
            && (btn == 1 || arrowHit)) {
          returnDetailsOpen = !returnDetailsOpen;
          revealReturnDetails = returnDetailsOpen;
          UiBubbles.burst(mx, my, tabColor(tab));
          initGui();
          return;
        }
      }
    }
    // The category header also collapses with the left button: the right one is kept as a familiar
    // alternative.
    if ((btn == 0 || btn == 1) && toggleArchiveAt(mx, my)) {
      UiBubbles.burst(mx, my, tabColor(tab));
      return;
    }
    if (btn == 0
        && sbActive()
        && mx >= contentRight - 7
        && mx < contentRight
        && my >= sbTop()
        && my < sbBottom()) {
      int ty = sbThumbY(), th = sbThumbH();
      sbGrab = my >= ty && my < ty + th ? my - ty : th / 2;
      sbDrag = true;
      UiBubbles.burst(mx, my, tabColor(tab));
      sbSetFromMouse(my);
      return;
    }
    super.mouseClicked(mx, my, btn);
    if (tab == 4 && !helpCompact && btn == 0 && mx >= helpListX && mx < helpListRight) {
      String[][] d = HelpTexts.current();
      for (int i = 0; i < d.length; i++) {
        int y = helpListTop + i * helpRowH;
        if (my >= y && my < y + helpRowH - 1) {
          UiBubbles.burst(mx, my, tabColor(tab));
          selectHelp(i);
          return;
        }
      }
    }
    if (wheel != null && btn == 0 && wheel.mouseClicked(mx, my)) {
      UiBubbles.burst(mx, my, tabColor(tab));
      applyWheel();
    }
  }

  @Override
  protected void mouseClickMove(int mx, int my, int btn, long time) {
    if (sbDrag) {
      sbSetFromMouse(my);
      return;
    }
    if (wheel != null && wheel.isDragging()) {
      wheel.mouseDragged(mx, my);
      applyWheel();
    }
  }

  @Override
  protected void mouseReleased(int mx, int my, int state) {
    sbDrag = false;
    super.mouseReleased(mx, my, state);
    if (wheel != null) wheel.mouseReleased();
  }

  @Override
  protected void keyTyped(char c, int key) throws IOException {
    if (leaveEditor != null) {
      if (key == Keyboard.KEY_RETURN
          || key == Keyboard.KEY_NUMPADENTER
          || key == Keyboard.KEY_TAB) {
        finishLeaveEdit(true);
        initGui();
      } else if (key == Keyboard.KEY_ESCAPE) {
        finishLeaveEdit(false);
        initGui();
      } else if (Character.isDigit(c)
          || Character.isISOControl(c)
          || key == Keyboard.KEY_LEFT
          || key == Keyboard.KEY_RIGHT
          || key == Keyboard.KEY_HOME
          || key == Keyboard.KEY_END
          || key == Keyboard.KEY_DELETE) {
        leaveEditor.textboxKeyTyped(c, key);
      }
      return;
    }
    if (key == Keyboard.KEY_F1) {
      handleNavButton(TAB_BASE + 4);
      return;
    }
    if (tab == 4 && scrollHelp(key)) return;
    if (search != null && search.isFocused()) {
      if (key == Keyboard.KEY_ESCAPE) {
        if (searchQuery.length() > 0) setSearch("");
        else {
          searchFocused = false;
          search.setFocused(false);
        }
        return;
      }
      if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_TAB) {
        searchFocused = false;
        search.setFocused(false);
        return;
      }
      String before = search.getText();
      search.textboxKeyTyped(c, key);
      if (!search.getText().equals(before)) setSearch(search.getText());
      return;
    }
    // Started typing: the search picks up the input by itself, without clicking the field.
    if (search != null && Character.isLetterOrDigit(c)) {
      searchFocused = true;
      search.setFocused(true);
      search.textboxKeyTyped(c, key);
      setSearch(search.getText());
      return;
    }
    if (key == Keyboard.KEY_ESCAPE && colorPanelOpen) {
      colorPanelOpen = false;
      initGui();
      return;
    }
    if (key == Keyboard.KEY_ESCAPE) {
      mc.displayGuiScreen(parent);
      return;
    }
    // Left/right arrows move between sections, up/down scroll the list: that is more familiar.
    if (key == Keyboard.KEY_LEFT || key == Keyboard.KEY_RIGHT) {
      int dir = key == Keyboard.KEY_LEFT ? TABS.length - 1 : 1;
      tab = TABS[(tabIndex(tab) + dir) % TABS.length];
      clearSearch();
      scroll = 0;
      listAnimAt = UiTheme.nowMs();
      initGui();
      return;
    }
    if (key == Keyboard.KEY_UP
        || key == Keyboard.KEY_DOWN
        || key == Keyboard.KEY_PRIOR
        || key == Keyboard.KEY_NEXT
        || key == Keyboard.KEY_HOME
        || key == Keyboard.KEY_END) {
      int page = Math.max(1, visibleRows - 1), old = scroll;
      if (key == Keyboard.KEY_UP) scroll -= 1;
      else if (key == Keyboard.KEY_DOWN) scroll += 1;
      else if (key == Keyboard.KEY_PRIOR) scroll -= page;
      else if (key == Keyboard.KEY_NEXT) scroll += page;
      else if (key == Keyboard.KEY_HOME) scroll = 0;
      else scroll = maxScroll;
      scroll = Math.max(0, Math.min(maxScroll, scroll));
      if (scroll != old) initGui();
      return;
    }
    super.keyTyped(c, key);
  }

  private boolean scrollHelp(int key) {
    int page = Math.max(1, helpVisible - 1);
    switch (key) {
      case Keyboard.KEY_UP:
        helpScroll--;
        break;
      case Keyboard.KEY_DOWN:
        helpScroll++;
        break;
      case Keyboard.KEY_PRIOR:
        helpScroll -= page;
        break;
      case Keyboard.KEY_NEXT:
        helpScroll += page;
        break;
      case Keyboard.KEY_HOME:
        helpScroll = 0;
        break;
      case Keyboard.KEY_END:
        helpScroll = helpMaxScroll;
        break;
      default:
        return false;
    }
    helpScroll = Math.max(0, Math.min(helpMaxScroll, helpScroll));
    return true;
  }

  @Override
  protected void actionPerformed(GuiButton b) throws IOException {
    int id = b.id;
    if (handleNavButton(id)) return;
    if (id == CANCEL) {
      mc.displayGuiScreen(parent);
      return;
    }
    if (id == SAVE) {
      onSave();
      return;
    }
    if (id >= ACT_BASE) {
      onRowAction(b, id - ACT_BASE);
      return;
    }
    if (id >= INC_BASE) {
      step(id - INC_BASE, 1);
      if (id - INC_BASE == ROW_LEAVE_CYCLES && config.isAutoLeaveTimed()) initGui();
      return;
    }
    if (id >= DEC_BASE) {
      step(id - DEC_BASE, -1);
      if (id - DEC_BASE == ROW_LEAVE_CYCLES && config.isAutoLeaveTimed()) initGui();
      return;
    }
  }

  /**
   * Sections, the "Route / Marker" toggle, help and "Collapse all"; true means the button was
   * handled.
   */
  private boolean handleNavButton(int id) {
    if (id == VIS_PATH) {
      visElement = 0;
      colorTarget = 0;
      colorPanelOpen = false;
      scroll = 0;
      initGui();
      return true;
    }
    if (id == VIS_MARK) {
      visElement = 1;
      colorTarget = 1;
      colorPanelOpen = false;
      scroll = 0;
      initGui();
      return true;
    }
    if (id >= TAB_BASE && id < TAB_BASE + TAB_COUNT) {
      if (tab == id - TAB_BASE) return true;
      tab = id - TAB_BASE;
      clearSearch();
      listAnimAt = UiTheme.nowMs();
      scroll = 0;
      wheelTouched = false;
      colorPanelOpen = false;
      initGui();
      return true;
    }
    if (id == HELP_PREV) {
      selectHelp(helpSection - 1);
      return true;
    }
    if (id == HELP_NEXT) {
      selectHelp(helpSection + 1);
      return true;
    }
    if (id == FOLD_ALL) {
      int groups = groupCount();
      boolean shut = anyGroupOpen();
      for (int g = 0; g < groups; g++) {
        int slot = collapseSlot(g);
        if (slot >= 0 && slot < collapsed[tab].length) collapsed[tab][slot] = shut;
      }
      if (!shut) scroll = 0;
      listAnimAt = UiTheme.nowMs();
      initGui();
      return true;
    }
    return false;
  }

  /** "Save": on success the draft becomes the initial state and the window closes. */
  private void onSave() {
    saveError = commit();
    if (saveError == SAVE_NONE) return;
    // The shared file is already written: the draft becomes the initial state, "Cancel" does not
    // roll it back.
    snapshot();
    if (saveError == SAVE_OK) {
      saved = true;
      mc.displayGuiScreen(parent);
    }
  }

  private void onRowAction(GuiButton b, int key) {
    if (key == ROW_LEAVE_MODE) {
      config.setAutoLeaveTimed(!config.isAutoLeaveTimed());
      initGui();
      return;
    }
    if (key == ROW_TRASH) {
      openingChild = true;
      mc.displayGuiScreen(new GuiMirrorTrash(this, manager));
      return;
    }
    if (key == ROW_FACTORY_RESET) {
      openingChild = true;
      mc.displayGuiScreen(new GuiResetConfirm(this));
      return;
    }
    if (key == ROW_RESET_VISUALS) {
      config.resetAppearance();
      initGui();
      return;
    }
    if (key == ROW_MARKER_MODE) {
      config.setStartMarkerMode((config.getStartMarkerMode() + 1) % 4);
      initGui();
      return;
    }
    if (key == ROW_PATH_STYLE) {
      config.setPathLineStyle((config.getPathLineStyle() + 1) % 3);
      initGui();
      return;
    }
    if (key == ROW_LANGUAGE) {
      config.setLanguage(Lang.next());
      initGui();
      return;
    }
    if (key == ROW_PLAYBACK_SPEED_RESET) {
      config.setPlaybackSpeed(1f);
      initGui();
      return;
    }
    if (key == ROW_COLOR_TARGET) {
      colorTarget = visElement;
      colorPanelOpen = !colorPanelOpen;
      revealColorRow = colorPanelOpen;
      if (visElement == 1) config.setMarkerCustomColor(true);
      initGui();
      return;
    }
    toggleKey(key);
    flashKey = key;
    flashAt = UiTheme.nowMs();
    if (b instanceof StyledButton) {
      ((StyledButton) b).setOn(stateOf(key));
      ((StyledButton) b).flash();
    }
    // Toggles that the row set depends on rebuild the list immediately.
    if (key == ROW_AUTO_LEAVE
        || key == ROW_STOP_ON_MOUSE
        || key == ROW_MARKER_LABEL
        || key == ROW_AUTO_DELETE_MESSAGES
        || key == ROW_UI_ANIMATIONS) initGui();
  }

  private void finishLeaveEdit(boolean commit) {
    if (leaveEditor == null) return;
    if (commit) {
      try {
        String text = leaveEditor.getText();
        if (text.matches("[0-9]{1,6}")) config.setAutoLeaveMinutes(Integer.parseInt(text));
      } catch (NumberFormatException ignored) {
        // Keep the previous valid setting.
      }
    }
    leaveEditor = null;
  }

  /** Return from the trash screen (GuiMirrorTrash). */
  void finishTrash() {
    openingChild = false;
    mc.displayGuiScreen(this);
  }

  /**
   * Saves the shared config, then the active slot's settings if the slot has frames. If the slot
   * save fails, attempts to restore the shared file to the baseline: success returns SAVE_NONE;
   * failure returns SAVE_PARTIAL, so "Cancel" must not undo the draft already written.
   */
  private int commit() {
    int slot = config.getActiveSlot();
    if (!config.save()) return SAVE_NONE;
    if (manager.getSlotFrameCount(slot) <= 0 || manager.saveSlotSettings(slot)) return SAVE_OK;
    int[] draftI = ints();
    float[] draftF = floats();
    boolean[] draftB = bools();
    String draftLang = config.getLanguage();
    restore();
    boolean rolledBack = config.save();
    applyInts(draftI);
    applyFloats(draftF);
    applyBools(draftB);
    config.setLanguage(draftLang);
    return rolledBack ? SAVE_NONE : SAVE_PARTIAL;
  }

  void finishFactoryReset(boolean confirmed) {
    openingChild = false;
    if (confirmed) {
      int activeSlot = config.getActiveSlot();
      config.resetAll();
      if (!config.save()) saveError = SAVE_NONE;
      else if (manager.getSlotFrameCount(activeSlot) > 0 && !manager.saveSlotSettings(activeSlot))
        saveError = SAVE_PARTIAL;
      else saveError = SAVE_OK;
      // A reset that did not reach disk must not become the Cancel baseline.
      if (saveError == SAVE_NONE) restore();
      else snapshot();
      scroll = 0;
      colorPanelOpen = false;
      // Factory reset also restores the window layout: all collapsed categories are expanded again.
      for (boolean[] t : collapsed) Arrays.fill(t, false);
    }
    mc.displayGuiScreen(this);
  }

  @Override
  public void onGuiClosed() {
    if (!saved && !openingChild) restore();
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }
}
