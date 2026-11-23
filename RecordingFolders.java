package com.mirror.recorder.gui;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** Folder labels and slot assignments only; recordings themselves remain untouched. */
final class RecordingFolders {
  static final int MAX_SLOTS = 100, MAX_FOLDERS = 100, MAX_NAME = 40;
  private final File file;
  private final LinkedHashMap<String, String> names = new LinkedHashMap<String, String>();
  private final Map<Integer, String> assigned = new LinkedHashMap<Integer, String>();
  private boolean readable = true;

  RecordingFolders(File file) {
    this.file = file;
    load();
  }

  private void load() {
    if (file == null || !file.exists()) return;
    Properties p = new Properties();
    try (InputStreamReader reader =
        new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8)) {
      p.load(reader);
      int count = Integer.parseInt(p.getProperty("count", "0"));
      if (count < 0 || count > MAX_FOLDERS) throw new IOException("Invalid folder count");
      for (int i = 0; i < count; i++) {
        String id = p.getProperty("folder." + i + ".id"),
            name = p.getProperty("folder." + i + ".name");
        if (id == null
            || !id.matches("[a-f0-9-]{36}")
            || name == null
            || name.trim().isEmpty()
            || name.length() > MAX_NAME
            || names.containsKey(id)) throw new IOException("Invalid folder");
        names.put(id, name);
      }
      for (int slot = 1; slot <= MAX_SLOTS; slot++) {
        String id = p.getProperty("slot." + slot);
        if (id != null && !id.isEmpty()) {
          if (!names.containsKey(id)) throw new IOException("Unknown folder assignment");
          assigned.put(Integer.valueOf(slot), id);
        }
      }
    } catch (Exception e) {
      names.clear();
      assigned.clear();
      readable = false;
    }
  }

  boolean isReadable() {
    return readable;
  }

  List<String> ids() {
    return Collections.unmodifiableList(new ArrayList<String>(names.keySet()));
  }

  String name(String id) {
    return names.get(id);
  }

  String folderOf(int slot) {
    return assigned.get(Integer.valueOf(slot));
  }

  boolean validName(String value, String except) {
    if (value == null || value.trim().isEmpty() || value.trim().length() > MAX_NAME) return false;
    for (Map.Entry<String, String> e : names.entrySet())
      if (!e.getKey().equals(except) && e.getValue().equalsIgnoreCase(value.trim())) return false;
    return true;
  }

  boolean create(String name) {
    if (!readable || names.size() >= MAX_FOLDERS || !validName(name, null)) return false;
    LinkedHashMap<String, String> next = new LinkedHashMap<String, String>(names);
    next.put(UUID.randomUUID().toString(), name.trim());
    return commit(next, new LinkedHashMap<Integer, String>(assigned));
  }

  boolean rename(String id, String name) {
    if (!readable || !names.containsKey(id) || !validName(name, id)) return false;
    LinkedHashMap<String, String> next = new LinkedHashMap<String, String>(names);
    next.put(id, name.trim());
    return commit(next, new LinkedHashMap<Integer, String>(assigned));
  }

  boolean delete(String id) {
    if (!readable || !names.containsKey(id)) return false;
    LinkedHashMap<String, String> next = new LinkedHashMap<String, String>(names);
    next.remove(id);
    Map<Integer, String> slots = new LinkedHashMap<Integer, String>(assigned);
    for (Integer slot : new ArrayList<Integer>(slots.keySet()))
      if (id.equals(slots.get(slot))) slots.remove(slot);
    return commit(next, slots);
  }

  boolean move(int slot, String id) {
    if (!readable || slot < 1 || slot > MAX_SLOTS || (id != null && !names.containsKey(id)))
      return false;
    Map<Integer, String> next = new LinkedHashMap<Integer, String>(assigned);
    if (id == null) next.remove(Integer.valueOf(slot));
    else next.put(Integer.valueOf(slot), id);
    return commit(new LinkedHashMap<String, String>(names), next);
  }

  private boolean commit(LinkedHashMap<String, String> next, Map<Integer, String> slots) {
    if (file == null) return false;
    try {
      Files.createDirectories(file.toPath().getParent());
      Properties p = new Properties();
      p.setProperty("count", String.valueOf(next.size()));
      int i = 0;
      for (Map.Entry<String, String> e : next.entrySet()) {
        p.setProperty("folder." + i + ".id", e.getKey());
        p.setProperty("folder." + i + ".name", e.getValue());
        i++;
      }
      for (Map.Entry<Integer, String> e : slots.entrySet())
        p.setProperty("slot." + e.getKey(), e.getValue());
      java.nio.file.Path tmp = Files.createTempFile(file.toPath().getParent(), "folders-", ".tmp");
      try {
        try (OutputStreamWriter out =
            new OutputStreamWriter(Files.newOutputStream(tmp), StandardCharsets.UTF_8)) {
          p.store(out, "Mirror Recorder folder assignments");
        }
        try {
          Files.move(
              tmp,
              file.toPath(),
              StandardCopyOption.ATOMIC_MOVE,
              StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
          Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
      } finally {
        Files.deleteIfExists(tmp);
      }
      names.clear();
      names.putAll(next);
      assigned.clear();
      assigned.putAll(slots);
      return true;
    } catch (IOException | RuntimeException e) {
      return false;
    }
  }
}
