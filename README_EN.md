# Mirror Recorder

**Mirror Recorder** is a client-side mod for **Minecraft 1.12.2 / Forge** that records player input and plays it back later.

> 🎥 The mod replays **actual player input** instead of directly simulating movement or physics. This keeps playback tied to Minecraft's normal game mechanics.

**GitHub:** https://github.com/Dedlock500543/Mirror-RecorderMod
**Download project (ZIP):** https://github.com/Dedlock500543/Mirror-RecorderMod/archive/refs/heads/main.zip

GitHub saves the archive as `Mirror-RecorderMod-main.zip`. After extraction, the project folder is named `Mirror-RecorderMod-main`.

**Русская версия:** [`README.md`](README.md)

---

## ✨ Features

* 🎬 Record and replay player input
* 🕹️ Movement, camera rotation, clicks, jumping and hotbar slot switching
* 💬 Chat and command recording/playback
* 🔁 Single and looped playback
* 🔢 Configurable loop limit
* ⚡ Playback speed from **0.25× to 4×**
* 🧭 Limited route stabilization using input only
* 🏠 Automatic return to the starting point
* 🤖 Optional **Baritone** integration
* 📍 Route and start-point visualization
* 🖥️ Playback status HUD
* 💾 `.mrr` import/export
* 📑 Slot duplication
* 🗑️ Trash for deleted recordings
* 🌍 Five interface languages
* 🛑 Stop on damage
* ⌨️ Stop on manual movement input
* 🌎 Recordings can continue across world changes
* 🧰 Playback of actions inside Minecraft GUIs
* 💾 Partial recovery of damaged recordings

---

## 🧩 Requirements

| Component         | Requirement                            |
| ----------------- | -------------------------------------- |
| Minecraft         | **1.12.2**                             |
| Minecraft Forge   | **14.23.5.2864+** in the 1.12.2 branch |
| Java for the game | **Java 8**                             |
| Java for building | **JDK 8**                              |
| Baritone          | Optional                               |

> ⚠️ **ForgeGradle 3 requires JDK 8.** Building with JDK 9+ is not supported and will fail with a clear error message.

---

## 📦 Installation

1. Install **Minecraft Forge 1.12.2**.
2. Put `Mirror-Recorder-1.12.2-1.0.0.jar` into your `mods` folder.
3. Launch Minecraft.

Mirror Recorder is **client-side only**.

> **Do not install Mirror Recorder on a server.** No server installation is required or supported.

---

## 📁 File Locations

All paths below are relative to the game directory:

* `.minecraft`
* a MultiMC / Prism Launcher instance directory
* CurseForge or another launcher instance directory

The mod obtains the game directory through Forge and does not write to unrelated locations.

| Purpose         | Path                             |
| --------------- | -------------------------------- |
| Configuration   | `config/mirror_recorder.cfg`     |
| Recordings      | `mirror_recorder/slot_N.nbt`     |
| Backups         | `mirror_recorder/slot_N.bak` |
| Temporary files | `mirror_recorder/slot_N.tmp` |
| Exports         | `mirror_recorder/exports/*.mrr`  |
| Trash           | `mirror_recorder/trash/`         |
| Diagnostic log  | `logs/mirror-debug.log`          |

The trash keeps up to **100 recently deleted recordings**.

### Diagnostics

The `logs/mirror-debug.log` file is switched on by the `debug` setting in `config/mirror_recorder.cfg`.

The JVM flags `-Dmirror.debug=true` and `-Dmirror.debug.csv=true` force diagnostics **on**: while a flag is set the log
cannot be switched off in the settings — only restarting the game without the flag helps.

The CSV dump (`mirror-debug.csv`) is written next to the game directory and is capped at 200,000 rows.

---

## 🎮 Controls

Mirror Recorder key bindings are **unassigned by default** to avoid conflicts with other mods.

Configure them in:

**Options → Controls → Mirror Recorder**

The main functions are also available through commands.

| Command                        | Action                      |
| ------------------------------ | --------------------------- |
| `/mirror gui`                  | Open the main GUI           |
| `/mirror record <slot>`        | Start recording             |
| `/mirror play <slot>`          | Play a recording once       |
| `/mirror loop <slot>`          | Start looped playback       |
| `/mirror limit <slot> <N>`     | Set the loop count          |
| `/mirror stop`                 | Stop recording/playback     |
| `/mirror list`                 | List occupied slots         |
| `/mirror name <slot> <name>`   | Rename a recording          |
| `/mirror delete <slot>`        | Delete a recording          |
| `/mirror marker <slot> [text]` | Set a recording description |

### Slots

* **100 slots**
* Up to **72,000 frames per slot**
* At 20 TPS, this is approximately **1 hour of recording per slot**

---

## 🎥 Recording & Playback

Mirror Recorder records player actions and replays them in the same order.

Recorded input may include:

* movement;
* camera rotation;
* clicks;
* jumping;
* hotbar slot switching;
* chat;
* commands;
* actions inside Minecraft GUIs.

The mod **does not teleport the player** and does not replace Minecraft's physics with its own simulation.

### Playback Speed

Supported speeds:

**0.25× → 4×**

When frames are skipped:

* input actions are replayed in their original order;
* camera rotation uses the latest frame for the corresponding tick.

> 🧭 Route stabilization is available only at **1.0×** playback speed.

---

## 🧭 Route Stabilization

Mirror Recorder provides limited route stabilization.

It works **through normal player input only** and does not use teleportation or direct coordinate manipulation:

* switched on by «Route stabilization» in **Settings → Replay**;
* only works at **1.0×** speed — at any other speed the mod reports that stabilization is off;
* if the deviation exceeds **3 blocks** playback stops with a message (in elytra flight the limit is softer —
  **16 blocks** with up to 2 seconds of patience, because flight physics catches up with the route on its own);
* elytra correction is capped at 0.35 blocks per tick.

### Right-click timer

Vanilla keeps a right-click delay (3 ticks by default) and can swallow the next recorded click. With exact block
placement the mod aligns that timer (3 → 4 ticks) so playback places blocks at the same pace as a live player's hand.
This is the only place where the mod touches a vanilla timer.

---

## 🏠 Automatic Return

After playback finishes, the mod can automatically return the player to the starting point.

Two modes are available:

* local alignment;
* **Baritone**, when installed.

Baritone is **not a required dependency** and is accessed through reflection.

---

## 📍 Route & Start Point Visualization

The mod can visualize the recorded route and starting point.

The start point can be displayed as:

* a point;
* a ring;
* a beam.

The visualization color can be configured.

---

## 🖥️ HUD

The HUD displays the current recording or playback status.

Inside Mirror Recorder GUIs, it can be moved with:

**Shift + mouse drag**

Without `Shift`, clicks are passed to the interface buttons underneath the HUD.

---

## 🌎 World Changes

Recordings can continue across world changes.

For example:

**lobby → portal → game world**

The entire sequence is stored in a single recording.

During playback, the world transition must happen **normally**, just as it did during recording:

* through a portal;
* through a game interface;
* through a command;
* or through another normal in-game mechanism.

Once the new world has finished loading, playback continues.

---

## 🧰 Minecraft GUIs

Mirror Recorder records actions performed inside Minecraft interfaces.

Examples include:

* dropping items;
* moving items with number keys;
* closing windows with keys;
* entering text into anvils;
* entering text into signs;
* interacting with chests;
* interacting with crafting tables;
* other GUI clicks.

### Important

Container clicks are replayed using the **recorded cursor position** and only inside a GUI of the same type.

The mod does not inspect the container contents.

Therefore, if items are located in different slots than during the original recording, the same click may interact with a different item.

### Clipboard

`Ctrl` shortcuts inside text fields are not reproduced.

For example, pasting text from the system clipboard is not stored as part of the recording.

---

## 💬 Chat & Commands

Chat playback sends **real messages and commands** to the server.

> ⚠️ On public servers, automated input may be prohibited by server rules.

### Commands

Everything is available from chat with `/mirror`:

| Command | What it does |
| --- | --- |
| `/mirror record <slot>` | start recording into a slot |
| `/mirror play <slot>` | play the recording once |
| `/mirror loop <slot>` | play the recording in a loop |
| `/mirror limit <1–5>` | how many rounds before stopping |
| `/mirror pause` | pause and resume playback |
| `/mirror stop` | stop recording or playback |
| `/mirror selftest` | engine self-test: events and timestamps |
| `/mirror list [page]` | show slots 1–100 |
| `/mirror delete <slot>` | delete a recording (to the trash first) |
| `/mirror name <slot>` | set the slot name |
| `/mirror marker <slot>` | set the slot marker |
| `/mirror gui` | open the mod window |

### Chat Recording

Chat recording is **disabled by default**.

This prevents recordings from accidentally storing:

* private messages;
* passwords;
* other sensitive information.

It can be enabled in:

**Settings → Replay**

### Imported Recordings

Chat messages and commands from **imported `.mrr` recordings are never sent**.

This prevents an imported recording from:

* sending messages in chat;
* executing commands on your behalf.

When such a frame is encountered for the first time, the mod displays a warning in chat.

> Your own recordings continue to work normally.

---

## 🛑 Stop Conditions

Playback can be configured to stop automatically when:

* the player takes damage;
* `W` is pressed manually;
* `A` is pressed;
* `S` is pressed;
* `D` is pressed;
* `Space` is pressed.

These settings are available under:

**Settings → Recording**

---

## 🌐 Languages

The interface supports:

* 🇷🇺 Russian
* 🇬🇧 English
* 🇺🇦 Ukrainian
* 🇩🇪 German
* 🇵🇱 Polish

By default, **Auto** follows the Minecraft language setting.

The language can also be selected manually in:

**Settings → Interface → Language**

### Control Names

Key names in the **Controls** menu are translated by Minecraft itself and therefore depend on the Minecraft language.

The section name **Mirror Recorder** always remains unchanged.

---

## 💾 Import & Export

Recordings can be:

* exported to `.mrr`;
* imported from `.mrr`;
* duplicated between slots;
* moved to the trash when deleted.

---

## 🩹 Damaged Recordings

A damaged recording does not necessarily become completely unusable.

Mirror Recorder attempts to load it partially:

* corrupted frames are skipped;
* valid frames continue to play;
* the diagnostic log records how many frames were lost.

This allows as much of the recording as possible to be recovered.

---

## 🔨 Building from Source

Building requires **JDK 8**.

### Windows

```bat
set "JAVA_HOME=<path-to-your-jdk8>"
gradlew.bat --no-daemon build
```

### Linux / macOS

```bash
export JAVA_HOME=<path-to-your-jdk8>
./gradlew --no-daemon build
```

The resulting JAR is:

```text
build/libs/Mirror-Recorder-1.12.2-1.0.0.jar
```

The JAR is already reobfuscated:

```gradle
jar.finalizedBy('reobfJar')
```

### Launch the Client

To test the project:

```bash
gradlew runClient
```

The working directory is:

```text
run/
```

### Build Environment Information

```bash
gradlew modInfo
```

---

## 🔢 Version

When changing the mod version, update it in all three locations:

```text
build.gradle
src/main/resources/mcmod.info
src/main/java/com/mirror/recorder/MirrorRecorder.java
```

Specifically:

```text
build.gradle → version
mcmod.info → "version"
MirrorRecorder.java → VERSION
```

---

## ⚠️ Important Limitations

Mirror Recorder replays **input**, not a predetermined result.

Playback may therefore behave differently when the game state differs from the state during the original recording.

For example:

* container contents have changed;
* objects are in different positions;
* the world state is different;
* loading or timing conditions changed;
* the GUI is different from the original state.

This is especially important for container interactions: the mod replays the recorded cursor position but does not verify which item is currently located there.

---

## 📜 License

See the project repository and license file included with the source code.

---

**Mirror Recorder** — record, save, and replay real player input in Minecraft 1.12.2.
