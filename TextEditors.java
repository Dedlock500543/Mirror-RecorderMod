package com.mirror.recorder.handler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiRepair;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiEditSign;
import net.minecraft.client.gui.recipebook.GuiRecipeBook;
import net.minecraft.client.gui.recipebook.IRecipeShownListener;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.text.TextComponentString;

/** Explicit editor owners; nested widgets are traversed only through known vanilla contracts. */
final class TextEditors {
  private TextEditors() {}

  static final class Editor {
    final GuiScreen screen;
    final Object owner;
    final GuiTextField field;
    final String key;

    Editor(GuiScreen screen, Object owner, GuiTextField field, String key) {
      this.screen = screen;
      this.owner = owner;
      this.field = field;
      this.key = key;
    }

    String text() throws ReflectiveOperationException {
      if (field != null) return field.getText();
      if (owner instanceof TileEntitySign)
        return ((TileEntitySign) owner).signText[line()].getUnformattedText();
      if (key.equals("book:title"))
        return (String) member(owner, "bookTitle", "field_146482_z").get(owner);
      return (String)
          method(owner.getClass(), new Class<?>[0], "pageGetCurrent", "func_146456_p")
              .invoke(owner);
    }

    int cursor() throws ReflectiveOperationException {
      return field == null ? text().length() : field.getCursorPosition();
    }

    int selection() throws ReflectiveOperationException {
      return field == null ? cursor() : field.getSelectionEnd();
    }

    void cursor(int cursor, int selection) {
      if (field == null) return;
      field.setFocused(true);
      field.setCursorPosition(cursor);
      field.setSelectionPos(selection);
    }

    private int line() {
      return Integer.parseInt(key.substring("sign:line:".length()));
    }

    void text(String value) throws ReflectiveOperationException {
      if (owner instanceof TileEntitySign) {
        ((TileEntitySign) owner).signText[line()] = new TextComponentString(value);
        ((TileEntitySign) owner).markDirty();
        return;
      }
      if (field != null) field.setText(value);
      else if (key.equals("book:title"))
        member(owner, "bookTitle", "field_146482_z").set(owner, value);
      else
        method(owner.getClass(), new Class<?>[] {String.class}, "pageSetCurrent", "func_146457_a")
            .invoke(owner, value);
      changed();
    }

    private void changed() throws ReflectiveOperationException {
      if (owner instanceof GuiRecipeBook) {
        member(owner, "lastSearch", "field_193963_r")
            .set(owner, field.getText().toLowerCase(Locale.ROOT));
        method(owner.getClass(), new Class<?>[] {String.class}, "pirateRecipe", "func_193716_a")
            .invoke(owner, field.getText());
        method(
                owner.getClass(),
                new Class<?>[] {boolean.class},
                "updateCollections",
                "func_193003_g")
            .invoke(owner, false);
      } else if (screen instanceof GuiRepair) {
        method(screen.getClass(), new Class<?>[0], "renameItem", "func_147090_g").invoke(screen);
      } else if (screen instanceof GuiContainerCreative) {
        method(screen.getClass(), new Class<?>[0], "updateCreativeSearch", "func_147053_i")
            .invoke(screen);
      } else if (screen instanceof GuiScreenBook) {
        member(owner, "bookIsModified", "field_146481_r").setBoolean(owner, true);
        if (key.equals("book:title"))
          method(owner.getClass(), new Class<?>[0], "updateButtons", "func_146464_h").invoke(owner);
      }
    }
  }

  static Editor focused(GuiScreen screen) throws ReflectiveOperationException {
    if (screen == null) return null;
    for (Editor editor : editors(screen)) {
      if (editor.field == null || editor.field.isFocused()) return editor;
    }
    return null;
  }

  static Editor find(GuiScreen screen, String key) throws ReflectiveOperationException {
    for (Editor editor : editors(screen)) if (editor.key.equals(key)) return editor;
    return null;
  }

  private static List<Editor> editors(GuiScreen screen) throws ReflectiveOperationException {
    List<Editor> out = new ArrayList<Editor>();
    if (screen instanceof IRecipeShownListener) addRecipe(screen, out);
    int fieldIndex = 0;
    for (Class<?> type = screen.getClass();
        type != null && type != Object.class;
        type = type.getSuperclass()) {
      for (Field f : type.getDeclaredFields()) {
        if (!GuiTextField.class.isAssignableFrom(f.getType())) continue;
        int index = fieldIndex++;
        f.setAccessible(true);
        GuiTextField field = (GuiTextField) f.get(screen);
        if (field != null
            && field.getVisible()
            && member(field, "isEnabled", "field_146226_p").getBoolean(field))
          out.add(new Editor(screen, screen, field, fieldKey(screen, field, index)));
      }
    }
    if (screen instanceof GuiScreenBook) addBook(screen, out);
    if (screen instanceof GuiEditSign) {
      TileEntitySign sign =
          (TileEntitySign) member(screen, "tileSign", "field_146848_f").get(screen);
      int line = member(screen, "editLine", "field_146851_h").getInt(screen);
      out.add(new Editor(screen, sign, null, "sign:line:" + line));
    }
    return out;
  }

  private static String fieldKey(GuiScreen screen, GuiTextField field, int index) {
    if (screen instanceof GuiChat) return "chat:input";
    if (screen instanceof GuiRepair) return "anvil:name";
    if (screen instanceof GuiContainerCreative) return "creative:search";
    // An ordinal distinguishes widgets that deliberately share the same vanilla numeric id.
    return "field:" + field.getId() + ":" + index;
  }

  private static void addRecipe(GuiScreen screen, List<Editor> out)
      throws ReflectiveOperationException {
    Object book =
        method(screen.getClass(), new Class<?>[0], "getRecipeGui", "func_194310_f").invoke(screen);
    if (!(book instanceof GuiRecipeBook) || !((GuiRecipeBook) book).isVisible()) return;
    GuiTextField field = (GuiTextField) member(book, "searchBar", "field_193962_q").get(book);
    if (field != null
        && field.getVisible()
        && member(field, "isEnabled", "field_146226_p").getBoolean(field))
      out.add(new Editor(screen, book, field, "recipe:search"));
  }

  private static void addBook(GuiScreen screen, List<Editor> out)
      throws ReflectiveOperationException {
    if (!member(screen, "bookIsUnsigned", "field_146475_i").getBoolean(screen)) return;
    boolean title = member(screen, "bookGettingSigned", "field_146480_s").getBoolean(screen);
    String key =
        title
            ? "book:title"
            : "book:page:" + member(screen, "currPage", "field_146484_x").getInt(screen);
    out.add(new Editor(screen, screen, null, key));
  }

  static Field member(Object owner, String... names) throws NoSuchFieldException {
    for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
      for (String name : names) {
        try {
          Field f = type.getDeclaredField(name);
          f.setAccessible(true);
          return f;
        } catch (NoSuchFieldException absent) {
          /* Both runtime namespaces must be tried. */
        }
      }
    }
    throw new NoSuchFieldException(names[0]);
  }

  static Method method(Class<?> owner, Class<?>[] args, String... names)
      throws NoSuchMethodException {
    for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
      for (String name : names) {
        try {
          Method m = type.getDeclaredMethod(name, args);
          m.setAccessible(true);
          return m;
        } catch (NoSuchMethodException absent) {
          /* Both runtime namespaces must be tried. */
        }
      }
    }
    throw new NoSuchMethodException(names[0]);
  }
}
