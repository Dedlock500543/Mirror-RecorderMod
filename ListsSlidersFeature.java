package com.mirror.recorder.handler;

import com.mirror.recorder.debug.MirrorDebug;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiOptionSlider;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Lists and sliders in windows: row clicks, scrolling and slider dragging. */
@SideOnly(Side.CLIENT)
final class ListsSlidersFeature implements ReplayState.Feature {
  private final RecordingHandler handler;
  private final ReplayState state;
  private final Minecraft mc = Minecraft.getMinecraft();

  ListsSlidersFeature(RecordingHandler handler, ReplayState state) {
    this.handler = handler;
    this.state = state;
  }

  private Field guiButtonListField;
  private boolean guiButtonListResolved = false;
  private GuiButton replaySlider = null;
  private GuiScreen replaySliderScreen = null;
  /** Left button press: remember the slider under the cursor (or forget the previous one). */
  void grabSlider(GuiScreen screen, int x, int y) {
    replaySlider = findReplaySlider(screen, x, y);
    replaySliderScreen = replaySlider == null ? null : screen;
    sliderHeldNs = replaySlider == null ? 0L : System.nanoTime();
  }
  /**
   * A press on a slider is released immediately: a held sound slider means a settings packet every
   * frame and a kick for spam. Returns true if the press landed on this window's grabbed slider and
   * it was released.
   */
  boolean releaseGrabbedSlider(GuiScreen screen, int x, int y, int button) {
    if (button != 0 || replaySlider == null || replaySliderScreen != screen) return false;
    try {
      replaySlider.mouseReleased(x, y);
    } catch (Exception e) {
    }
    if (state.reflect.mouseReleased() != null)
      try {
        state
            .reflect
            .mouseReleased()
            .invoke(screen, Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(button));
      } catch (Exception e) {
        state.warnClickFailure("Recorded slider release failed", e);
      }
    sliderAppliedNs = System.nanoTime();
    sliderHeldNs = sliderAppliedNs;
    return true;
  }

  private final Map<Class<?>, Field[]> slotListCache = new HashMap<Class<?>, Field[]>();
  /** Button fields of a list row: the row class is constant, the hierarchy walk is needed once. */
  private final Map<Class<?>, Field[]> buttonFieldCache = new HashMap<Class<?>, Field[]>();

  private Field[] buttonFieldsOf(Class<?> c) {
    Field[] cached = buttonFieldCache.get(c);
    if (cached != null) return cached;
    List<Field> out = new ArrayList<Field>();
    for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
      Field[] fs;
      try {
        fs = k.getDeclaredFields();
      } catch (Exception e) {
        continue;
      }
      for (Field f : fs) {
        if (!GuiButton.class.isAssignableFrom(f.getType())) continue;
        try {
          f.setAccessible(true);
          out.add(f);
        } catch (Exception ignored) {
        }
      }
    }
    Field[] arr = out.toArray(new Field[out.size()]);
    buttonFieldCache.put(c, arr);
    return arr;
  }

  private void resolveGuiButtonList(GuiScreen screen) {
    if (guiButtonListResolved) return;
    guiButtonListResolved = true;
    String[] names = {"buttonList", "field_146292_n"};
    for (String n : names)
      try {
        Field f = GuiScreen.class.getDeclaredField(n);
        f.setAccessible(true);
        guiButtonListField = f;
        return;
      } catch (Exception e) {
      }
    try {
      for (Field f : GuiScreen.class.getDeclaredFields())
        if (List.class.isAssignableFrom(f.getType())) {
          f.setAccessible(true);
          Object v = f.get(screen);
          if (v instanceof List) {
            for (Object o : (List<?>) v)
              if (o instanceof GuiButton) {
                guiButtonListField = f;
                return;
              }
          }
        }
    } catch (Exception e) {
    }
  }
  /**
   * A button class's own mouseDragged marks a slider. The answer depends only on the class, so it
   * is cached.
   */
  private final Map<Class<?>, Boolean> sliderClassCache = new HashMap<Class<?>, Boolean>();

  private boolean sliderButton(GuiButton b) {
    if (b == null || b.getClass() == GuiButton.class) return false;
    if (b instanceof GuiOptionSlider) return true;
    Class<?> c = b.getClass();
    Boolean known = sliderClassCache.get(c);
    if (known != null) return known.booleanValue();
    boolean slider = false;
    try {
      for (Method m : c.getDeclaredMethods()) {
        Class<?>[] a = m.getParameterTypes();
        String n = m.getName();
        if (a.length == 3
            && a[0] == Minecraft.class
            && a[1] == Integer.TYPE
            && a[2] == Integer.TYPE
            && (n.equals("mouseDragged") || n.equals("func_146119_b"))) {
          slider = true;
          break;
        }
      }
    } catch (Exception e) {
      slider = false;
    }
    sliderClassCache.put(c, Boolean.valueOf(slider));
    return slider;
  }

  GuiButton findReplaySlider(GuiScreen screen, int x, int y) {
    resolveGuiButtonList(screen);
    if (guiButtonListField != null)
      try {
        Object v = guiButtonListField.get(screen);
        if (v instanceof List) {
          for (Object o : (List<?>) v)
            if (o instanceof GuiButton) {
              GuiButton b = (GuiButton) o;
              if (b.visible
                  && b.enabled
                  && x >= b.x
                  && x < b.x + b.width
                  && y >= b.y
                  && y < b.y + b.height
                  && sliderButton(b)) return b;
            }
        }
      } catch (Exception e) {
        state.warnClickFailure("Cannot inspect GUI sliders", e);
      }
    return findRowSlider(screen, x, y);
  }
  /**
   * Graphics sliders live in list rows (GuiOptionsRowList), not in buttonList. Only the row under
   * the cursor is taken: off-screen rows have stale button coordinates that may overlap visible
   * ones.
   */
  private GuiButton findRowSlider(GuiScreen screen, int x, int y) {
    for (Field f : slotListsOf(screen.getClass())) {
      Object list;
      try {
        list = f.get(screen);
      } catch (Exception e) {
        continue;
      }
      if (!(list instanceof GuiListExtended)) continue;
      GuiListExtended le = (GuiListExtended) list;
      Object entry;
      try {
        int idx = le.getSlotIndexFromScreenCoords(x, y);
        if (idx < 0) continue;
        entry = le.getListEntry(idx);
      } catch (Throwable t) {
        continue;
      }
      if (entry == null) continue;
      for (Field bf : buttonFieldsOf(entry.getClass())) {
        try {
          Object o = bf.get(entry);
          if (!(o instanceof GuiButton)) continue;
          GuiButton b = (GuiButton) o;
          if (b.visible
              && b.enabled
              && x >= b.x
              && x < b.x + b.width
              && y >= b.y
              && y < b.y + b.height
              && sliderButton(b)) return b;
        } catch (Exception e) {
        }
      }
    }
    return null;
  }
  /**
   * Accessible instance GuiSlot fields of the screen and up to two superclasses in a stable order:
   * the list index is written into the scroll record.
   */
  Field[] slotListsOf(Class<?> c) {
    Field[] cached = slotListCache.get(c);
    if (cached != null) return cached;
    List<Field> out = new ArrayList<Field>();
    int depth = 0;
    for (Class<?> k = c;
        k != null && k != Object.class && depth < 3;
        k = k.getSuperclass(), depth++) {
      Field[] fs = k.getDeclaredFields();
      Arrays.sort(
          fs,
          new Comparator<Field>() {
            public int compare(Field a, Field b) {
              return a.getName().compareTo(b.getName());
            }
          });
      for (Field f : fs)
        if (GuiSlot.class.isAssignableFrom(f.getType()) && !Modifier.isStatic(f.getModifiers())) {
          try {
            f.setAccessible(true);
            out.add(f);
          } catch (Exception e) {
          }
        }
    }
    Field[] arr = out.toArray(new Field[out.size()]);
    slotListCache.put(c, arr);
    return arr;
  }

  private static Method slotAmountMethod = null, slotScrollByMethod = null;
  private static boolean slotScrollResolved = false;

  private void resolveSlotScroll() {
    if (slotScrollResolved) return;
    slotScrollResolved = true;
    slotAmountMethod =
        findMethod(
            GuiSlot.class, new String[] {"getAmountScrolled", "func_148148_g"}, new Class<?>[0]);
    slotScrollByMethod =
        findMethod(
            GuiSlot.class, new String[] {"scrollBy", "func_148145_f"}, new Class<?>[] {int.class});
    MirrorDebug.probe("GuiSlot.getAmountScrolled", slotAmountMethod != null, "list scroll record");
    MirrorDebug.probe("GuiSlot.scrollBy", slotScrollByMethod != null, "list scroll replay");
  }

  int slotAmount(Object list) {
    resolveSlotScroll();
    if (slotAmountMethod == null || list == null) return Integer.MIN_VALUE;
    try {
      Object r = slotAmountMethod.invoke(list);
      return r instanceof Number ? ((Number) r).intValue() : Integer.MIN_VALUE;
    } catch (Exception e) {
      return Integer.MIN_VALUE;
    }
  }
  /**
   * Replaying list scrolling: set the absolute value from the recording, vanilla clamps it to the
   * list bounds itself.
   */
  void deliverListScroll(GuiScreen screen, int index, int amount) {
    Field[] lists = slotListsOf(screen.getClass());
    if (index < 0 || index >= lists.length) return;
    try {
      Object list = lists[index].get(screen);
      int cur = slotAmount(list);
      if (cur == Integer.MIN_VALUE || slotScrollByMethod == null) return;
      if (amount != cur) slotScrollByMethod.invoke(list, Integer.valueOf(amount - cur));
    } catch (Exception e) {
      state.warnClickFailure("Recorded list scroll failed", e);
    }
  }

  void deliverSliderDrag(GuiScreen screen, int cx, int cy, boolean end) {
    int x = MathHelper.clamp(screen.width / 2 + cx, 0, Math.max(0, screen.width - 1)),
        y = MathHelper.clamp(screen.height / 2 + cy, 0, Math.max(0, screen.height - 1));
    // The middle of the gesture moves smoothly, the end is set exactly to the recorded point.
    if (end) handler.pointer.pointerSnap(x, y);
    else handler.pointer.pointerGlide(x, y);
    sliderHeldNs = end ? 0L : System.nanoTime();
    // The slider is taken only from a press in this same window, not from the release point: the
    // release of the "Music & Sounds" button arrives in the new window and would "press" whatever
    // slider is under the cursor (misclick).
    GuiButton b = replaySliderScreen == screen ? replaySlider : null;
    // The slider is NOT held down: vanilla would then overwrite the setting every frame; sound
    // sliders also send a
    // client settings packet, risking a kick for spam. Instead, intermediate drag presses are
    // limited
    // to 10 per second; initial presses and releases are not throttled: a press sets the value by
    // coordinate, the release closes the gesture
    // immediately.
    if (b != null) {
      long nowNs = System.nanoTime();
      if (end || sliderAppliedNs <= 0L || nowNs - sliderAppliedNs >= SLIDER_APPLY_INTERVAL_NS) {
        try {
          int px = MathHelper.clamp(x, b.x, Math.max(b.x, b.x + b.width - 1)),
              py = MathHelper.clamp(y, b.y, Math.max(b.y, b.y + b.height - 1));
          b.mousePressed(mc, px, py);
          b.mouseReleased(px, py);
          sliderAppliedNs = nowNs;
        } catch (Exception e) {
          state.warnClickFailure("Recorded slider drag failed", e);
        }
      }
    }
    if (end) {
      replaySlider = null;
      replaySliderScreen = null;
      sliderHeldNs = 0L;
      sliderAppliedNs = 0L;
    }
  }

  private long sliderHeldNs = 0L, sliderAppliedNs = 0L;
  /**
   * At most 10 intermediate slider drag presses per second; initial presses and releases are
   * exempt. Held sound sliders send settings packets every frame and risk a kick for spam.
   */
  private static final long SLIDER_APPLY_INTERVAL_NS = 100_000_000L;
  /** A slider with no events for longer than 0.4 s is considered stuck and released. */
  private static final long SLIDER_STUCK_NS = 400_000_000L;
  /**
   * Guard: if gesture points stopped coming or the window changed, release the slider ourselves. A
   * held slider makes vanilla overwrite the setting every frame.
   */
  void releaseStuckSlider() {
    GuiButton b = replaySlider;
    GuiScreen owner = replaySliderScreen;
    if (b == null || owner == null) return;
    boolean gone = mc.currentScreen != owner;
    if (!gone && (sliderHeldNs <= 0L || System.nanoTime() - sliderHeldNs < SLIDER_STUCK_NS)) return;
    int x = b.x + Math.max(0, b.width / 2), y = b.y + Math.max(0, b.height / 2);
    replaySlider = null;
    replaySliderScreen = null;
    sliderHeldNs = 0L;
    sliderAppliedNs = 0L;
    if (gone) {
      handler.containers.cancelDeferredPress();
      return;
    }
    try {
      b.mouseReleased(x, y);
    } catch (Exception e) {
    }
    state.reflect.ensureResolved();
    if (state.reflect.mouseReleased() != null)
      try {
        state
            .reflect
            .mouseReleased()
            .invoke(owner, Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(0));
      } catch (Exception e) {
        state.warnClickFailure("Recorded slider release failed", e);
      }
    handler.containers.cancelDeferredPress();
    MirrorDebug.log("GUI", "slider drag released by watchdog");
  }
  // Plain GuiSlot lists in windows (e.g. language) read the LWJGL mouse themselves in
  // handleMouseInput instead of getting the click from GuiScreen.mouseClicked. Without this a click
  // on a list row was lost.
  void deliverListClick(GuiScreen screen, int x, int y, int button) {
    if (screen == null || button != 0) return;
    for (Field f : listFields(screen.getClass())) {
      Object o;
      try {
        o = f.get(screen);
      } catch (Exception e) {
        continue;
      }
      if (!(o instanceof GuiSlot)) continue;
      // GuiListExtended lists (graphics, controls, resource packs) get the click from the window's
      // own mouseClicked. A second call would double-press: the graphics toggle would click twice
      // and go back.
      if (o instanceof GuiListExtended) continue;
      if (listClickExtended(o, x, y, button)) return;
      if (listClickSlot(o, x, y)) return;
    }
  }
  // Screen fields and list methods are looked up once per class, not on every replayed click.
  private static final Field[] NO_FIELDS = new Field[0];
  private final Map<Class<?>, Field[]> listFieldCache = new HashMap<Class<?>, Field[]>();
  private final Map<String, Method> methodCache = new HashMap<String, Method>();
  /**
   * Fields of the screen and its two parents, in getDeclaredFields order from the screen class up.
   * The order must stay fixed: it decides which list a replayed click reaches first. Fields whose
   * type cannot hold a GuiSlot are dropped up front.
   */
  private Field[] listFields(Class<?> screenType) {
    if (screenType == null) return NO_FIELDS;
    Field[] cached = listFieldCache.get(screenType);
    if (cached != null) return cached;
    List<Field> out = new ArrayList<Field>();
    Class<?> c = screenType;
    for (int depth = 0; c != null && depth < 3; c = c.getSuperclass(), depth++) {
      Field[] fs;
      try {
        fs = c.getDeclaredFields();
      } catch (Exception e) {
        continue;
      }
      for (Field f : fs) {
        Class<?> t = f.getType();
        if (!t.isAssignableFrom(GuiSlot.class) && !GuiSlot.class.isAssignableFrom(t)) continue;
        try {
          f.setAccessible(true);
          out.add(f);
        } catch (Exception ignored) {
        }
      }
    }
    cached = out.toArray(new Field[out.size()]);
    listFieldCache.put(screenType, cached);
    return cached;
  }

  private boolean listClickExtended(Object list, int x, int y, int button) {
    try {
      Method m =
          findMethod(
              list.getClass(),
              new String[] {"mouseClicked", "func_148179_a"},
              new Class<?>[] {int.class, int.class, int.class});
      if (m == null) return false;
      Object r = m.invoke(list, Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(button));
      return (r instanceof Boolean) && ((Boolean) r).booleanValue();
    } catch (Exception e) {
      return false;
    }
  }

  private boolean listClickSlot(Object list, int x, int y) {
    try {
      Method idx =
          findMethod(
              list.getClass(),
              new String[] {"getSlotIndexFromScreenCoords", "func_148124_c"},
              new Class<?>[] {int.class, int.class});
      if (idx == null) return false;
      Object r = idx.invoke(list, Integer.valueOf(x), Integer.valueOf(y));
      if (!(r instanceof Integer)) return false;
      int i = ((Integer) r).intValue();
      if (i < 0) return false;
      Method el =
          findMethod(
              list.getClass(),
              new String[] {"elementClicked", "func_148144_a"},
              new Class<?>[] {int.class, boolean.class, int.class, int.class});
      if (el == null) return false;
      el.invoke(list, Integer.valueOf(i), Boolean.FALSE, Integer.valueOf(x), Integer.valueOf(y));
      return true;
    } catch (Exception e) {
      return false;
    }
  }
  /**
   * The cache key includes argument types: otherwise an overload with the same name and parameter
   * count would return a different method.
   */
  private Method findMethod(Class<?> type, String[] names, Class<?>[] args) {
    StringBuilder k = new StringBuilder(type.getName()).append('#').append(names[0]).append('(');
    for (int i = 0; i < args.length; i++) {
      if (i > 0) k.append(',');
      k.append(args[i].getName());
    }
    String key = k.append(')').toString();
    if (methodCache.containsKey(key)) return methodCache.get(key);
    Method found = findMethod0(type, names, args);
    methodCache.put(key, found);
    return found;
  }

  private Method findMethod0(Class<?> type, String[] names, Class<?>[] args) {
    for (Class<?> c = type; c != null; c = c.getSuperclass()) {
      Method[] ms;
      try {
        ms = c.getDeclaredMethods();
      } catch (Exception e) {
        continue;
      }
      for (Method m : ms) {
        Class<?>[] p = m.getParameterTypes();
        if (p.length != args.length) continue;
        boolean same = true;
        for (int i = 0; i < p.length; i++)
          if (p[i] != args[i]) {
            same = false;
            break;
          }
        if (!same) continue;
        for (String n : names)
          if (m.getName().equals(n)) {
            try {
              m.setAccessible(true);
            } catch (Exception e) {
              return null;
            }
            return m;
          }
      }
    }
    return null;
  }

  @Override
  public void reset() {
    replaySlider = null;
    replaySliderScreen = null;
    sliderHeldNs = 0L;
    sliderAppliedNs = 0L;
  }

  private static final int WHEEL_CHAT = -1, WHEEL_CREATIVE = -2;

  void recordWheel(GuiScreen screen, GuiEventRecorder recorder, int offset) {
    try {
      if (screen instanceof GuiChat) {
        Object chat = mc.ingameGUI.getChatGUI();
        int position = TextEditors.member(chat, "scrollPos", "field_146250_j").getInt(chat);
        addWheel(recorder, screen, offset, WHEEL_CHAT, position, 0);
      } else if (screen instanceof GuiContainerCreative) {
        int position =
            Float.floatToIntBits(
                TextEditors.member(screen, "currentScroll", "field_147067_x").getFloat(screen));
        addWheel(
            recorder,
            screen,
            offset,
            WHEEL_CREATIVE,
            position,
            ((GuiContainerCreative) screen).getSelectedTabIndex());
      } else {
        Field[] lists = slotListsOf(screen.getClass());
        for (int i = 0; i < lists.length; i++) {
          int amount = slotAmount(lists[i].get(screen));
          if (amount != Integer.MIN_VALUE) addWheel(recorder, screen, offset, i, amount, 0);
        }
      }
    } catch (ReflectiveOperationException e) {
      recorder.abortSemanticCapture("Wheel capture failed: " + e);
    }
  }

  private void addWheel(
      GuiEventRecorder recorder,
      GuiScreen screen,
      int offset,
      int kind,
      int position,
      int context) {
    if (recorder.hasRoom())
      recorder.addGuiEvent(
          screen.getClass().getName(),
          new int[] {
            GuiEventQueue.GA_WHEEL, offset, kind, GuiEventQueue.GUI_FLAG_TIMED, position, context
          });
    else recorder.abortSemanticCapture("Wheel event exceeds frame capacity");
  }

  void deliverWheel(GuiScreen screen, int kind, int position, int context) {
    try {
      if (kind == WHEEL_CHAT && screen instanceof GuiChat) {
        Object chat = mc.ingameGUI.getChatGUI();
        int current = TextEditors.member(chat, "scrollPos", "field_146250_j").getInt(chat);
        mc.ingameGUI.getChatGUI().scroll(position - current);
      } else if (kind == WHEEL_CREATIVE && screen instanceof GuiContainerCreative) {
        deliverCreativeWheel((GuiContainerCreative) screen, position, context);
      } else if (kind >= 0) deliverListScroll(screen, kind, position);
    } catch (ReflectiveOperationException e) {
      state.warnClickFailure("Recorded wheel failed", e);
    }
  }

  private void deliverCreativeWheel(GuiContainerCreative screen, int position, int tab)
      throws ReflectiveOperationException {
    float scroll = Float.intBitsToFloat(position);
    if (tab < 0
        || tab >= CreativeTabs.CREATIVE_TAB_ARRAY.length
        || Float.isNaN(scroll)
        || scroll < 0f
        || scroll > 1f) return;
    if (screen.getSelectedTabIndex() != tab)
      TextEditors.method(
              screen.getClass(),
              new Class<?>[] {CreativeTabs.class},
              "setCurrentCreativeTab",
              "func_147050_b")
          .invoke(screen, CreativeTabs.CREATIVE_TAB_ARRAY[tab]);
    TextEditors.member(screen, "currentScroll", "field_147067_x").setFloat(screen, scroll);
    TextEditors.method(
            screen.inventorySlots.getClass(),
            new Class<?>[] {float.class},
            "scrollTo",
            "func_148329_a")
        .invoke(screen.inventorySlots, scroll);
  }
}
