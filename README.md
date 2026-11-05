# Mirror Recorder

**Mirror Recorder** is a client-side mod for **Minecraft 1.12.2 / Forge** that records player input and plays it back later.

> 🎥 The mod replays **actual player input** instead of directly simulating movement or physics. This keeps playback tied to Minecraft's normal game mechanics.

**Русская версия:** [`README_RU.md`](README_RU.md)
--

## ✨ Features

* 🎬 Record and replay player input
* 🕹️ Movement, camera rotation, clicks, jumping and hotbar slot switching
* 💬 Chat and command recording/playback
* 🔁 Single and looped playback
* 🔢 Configurable loop limit
* ⚡ Playback speed from **0.25× to 4×**
* 🧭 Limited route stabilization using input; elytra flight uses velocity
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

The JVM flag `-Dmirror.debug=true` forces the text log on; `-Dmirror.debug.csv=true` forces CSV on. Each flag prevents its corresponding output from being switched off in the settings.

The CSV dump (`logs/mirror-playback-<timestamp>-run<number>.csv`) is written inside the game directory and is capped at 200,000 rows per file.

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
| `/mirror list [page]`          | List the ten slots on a page         |
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

On the ground it works **through normal player input**; elytra flight restores and corrects velocity. It does not use teleportation or direct coordinate manipulation:

* input correction is switched on by «Route stabilization» in **Settings → Replay**; elytra velocity restoration and drift checks do not depend on this setting;
* drift correction only works at **1.0×** speed — at any other speed the mod reports that stabilization is off; recorded elytra velocity is also restored at other playback speeds;
* if the deviation exceeds **3 blocks** playback stops with a message (in elytra flight the limit is softer —
  **16 blocks** with up to 2 seconds of patience, because flight physics catches up with the route on its own);
* elytra velocity correction is capped at 0.35 blocks per tick on each axis.

### Right-click timer

Vanilla keeps a right-click delay (4 ticks by default) and can swallow the next recorded click. With exact block
placement the mod aligns that timer (3 → 4 ticks) so playback places blocks at the same pace as a live player's hand.
The mod also resets the vanilla left-click delay counter during playback.

---

## 🏠 Automatic Return

Before playback starts and between loop runs, the mod can automatically return the player to the starting point.

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

The system clipboard contents are not saved separately, but a sent chat message is saved even when its text was pasted from the clipboard.

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
| `/mirror limit <slot> <0\|2–1000>` | how many rounds before stopping |
| `/mirror pause` | pause and resume playback |
| `/mirror stop` | stop recording or playback |
| `/mirror selftest` | engine self-test: events and timestamps |
| `/mirror list [page]` | show slots 1–100 |
| `/mirror delete <slot>` | delete a recording (to the trash first) |
| `/mirror name <slot> [text]` | show or set the slot name |
| `/mirror marker <slot> [text]` | show or set the slot description |
| `/mirror gui` | open the mod window |

### Chat Recording

Chat **is saved during recording regardless of the chat playback setting**.

Recordings may contain:

* private messages;
* passwords;
* other sensitive information.

Sending saved messages through the chat queue can be disabled in:

**Settings → Replay**

### Imported Recordings

Chat and commands from **imported `.mrr` recordings** are replayed the same way as those from your own recordings. There is no origin-based chat block or warning about that block.

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

**Settings → Stop**

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

**Settings → General → Language**

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

* corrupted frames before the first valid frame are skipped; later ones are replaced by pauses;
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
./gradlew runClient
```

The working directory is:

```text
run/
```

### Build Environment Information

```bash
./gradlew modInfo
```

---

## 🔢 Version

The version lives in one place — `MirrorRecorder.java → VERSION`. The build reads it from there
for the jar name and the manifest and fills it into `mcmod.info` (which keeps `"${version}"`).

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
