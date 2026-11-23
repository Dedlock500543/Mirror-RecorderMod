package com.mirror.recorder;

import com.mirror.recorder.command.CommandMirror;
import com.mirror.recorder.config.RecorderConfig;
import com.mirror.recorder.debug.MirrorDebug;
import com.mirror.recorder.gui.UiBubbles;
import com.mirror.recorder.gui.UiFonts;
import com.mirror.recorder.gui.UiScreenTransitions;
import com.mirror.recorder.handler.BaritoneReturnController;
import com.mirror.recorder.handler.KeybindLocalizer;
import com.mirror.recorder.handler.PathLineRenderer;
import com.mirror.recorder.handler.PlaybackTrajectory;
import com.mirror.recorder.handler.RecordingHandler;
import com.mirror.recorder.handler.StartMarkerRenderer;
import com.mirror.recorder.handler.StatusOverlayHandler;
import com.mirror.recorder.handler.TransientChat;
import com.mirror.recorder.handler.WorldLifecycleHandler;
import com.mirror.recorder.manager.RecorderManager;
import com.mirror.recorder.storage.StorageManager;
import java.io.File;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.lwjgl.input.Keyboard;

/** Main mod class: registers event handlers, hotkeys and the command. */
@Mod(
    modid = MirrorRecorder.MOD_ID,
    name = MirrorRecorder.MOD_NAME,
    version = MirrorRecorder.VERSION,
    clientSideOnly = true,
    acceptedMinecraftVersions = "[1.12.2]")
public class MirrorRecorder {
  public static final String MOD_ID = "mirror_recorder",
      MOD_NAME = "Mirror Recorder",
      VERSION = "1.0.0";

  @Mod.Instance(MOD_ID)
  public static MirrorRecorder instance;

  private RecorderConfig config;
  private StorageManager storage;
  private RecorderManager manager;
  private RecordingHandler recordingHandler;

  public RecordingHandler getRecordingHandler() {
    return recordingHandler;
  }

  private BaritoneReturnController returnController;
  private KeyBinding keyRecord, keyPlay, keyLoop, keyStop, keyGui;

  public static MirrorRecorder getInstance() {
    return instance;
  }

  @Mod.EventHandler
  public void preInit(FMLPreInitializationEvent e) {
    instance = this;
    File gameDir = e.getModConfigurationDirectory().getParentFile();
    MirrorDebug.init(gameDir);
    config = new RecorderConfig(e.getSuggestedConfigurationFile());
    storage = new StorageManager(gameDir);
    manager = new RecorderManager(config, storage);
    storage.warmUpSummaries();
    MirrorDebug.setEnabled(config.isDebug());
    MirrorDebug.setCsvEnabled(config.isDebugCsv());
    MirrorDebug.log(
        "BOOT",
        MOD_NAME
            + " "
            + VERSION
            + " preInit, config="
            + e.getSuggestedConfigurationFile().getAbsolutePath());
    // Quitting the game or Alt+F4 mid-recording: the unfinished recording is flushed to disk
    // instead of being lost.
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                new Runnable() {
                  public void run() {
                    try {
                      if (manager != null) manager.checkpointRecording();
                    } catch (Throwable t) {
                      MirrorDebug.log("STORAGE", "shutdown checkpoint failed: " + t);
                    }
                    try {
                      if (storage != null) storage.shutdownFlush();
                    } catch (Throwable t) {
                      MirrorDebug.log("STORAGE", "shutdown flush failed: " + t);
                    }
                  }
                },
                "MirrorRecorder-save"));
    // Key names are translation keys: the game translates them via
    // assets/mirror_recorder/lang/*.lang, and without lang files KeybindLocalizer does. The
    // category is the literal mod name: it is the same in every language and does not depend on
    // lang files (otherwise the raw key would show in "Controls" without them).
    String cat = "Mirror Recorder";
    // Use a universal key context; screen handling still filters typing, rebinding and mod UI,
    // and handles only stop/menu hotkeys in foreign non-text screens.
    keyRecord =
        new KeyBinding(
            "key.mirror_recorder.record", KeyConflictContext.UNIVERSAL, Keyboard.KEY_NONE, cat);
    keyPlay =
        new KeyBinding(
            "key.mirror_recorder.play", KeyConflictContext.UNIVERSAL, Keyboard.KEY_NONE, cat);
    keyLoop =
        new KeyBinding(
            "key.mirror_recorder.loop", KeyConflictContext.UNIVERSAL, Keyboard.KEY_NONE, cat);
    keyStop =
        new KeyBinding(
            "key.mirror_recorder.stop", KeyConflictContext.UNIVERSAL, Keyboard.KEY_NONE, cat);
    keyGui =
        new KeyBinding(
            "key.mirror_recorder.gui", KeyConflictContext.UNIVERSAL, Keyboard.KEY_NONE, cat);
    ClientRegistry.registerKeyBinding(keyRecord);
    ClientRegistry.registerKeyBinding(keyPlay);
    ClientRegistry.registerKeyBinding(keyLoop);
    ClientRegistry.registerKeyBinding(keyStop);
    ClientRegistry.registerKeyBinding(keyGui);
  }

  @Mod.EventHandler
  public void init(FMLInitializationEvent e) {
    PlaybackTrajectory trajectory = new PlaybackTrajectory(manager);
    returnController = new BaritoneReturnController(manager, config);
    MinecraftForge.EVENT_BUS.register(new UiFonts());
    MinecraftForge.EVENT_BUS.register(returnController);
    recordingHandler = new RecordingHandler(manager, config, trajectory);
    MinecraftForge.EVENT_BUS.register(recordingHandler);
    MinecraftForge.EVENT_BUS.register(new WorldLifecycleHandler(manager));
    MinecraftForge.EVENT_BUS.register(new PathLineRenderer(manager, config, trajectory));
    MinecraftForge.EVENT_BUS.register(
        new StartMarkerRenderer(manager, config, returnController, trajectory));
    MinecraftForge.EVENT_BUS.register(new StatusOverlayHandler(manager, config, returnController));
    MinecraftForge.EVENT_BUS.register(new TransientChat());
    UiScreenTransitions transitions = new UiScreenTransitions();
    MinecraftForge.EVENT_BUS.register(transitions);
    MinecraftForge.EVENT_BUS.register(new UiBubbles());
    net.minecraft.client.resources.IResourceManager resources =
        net.minecraft.client.Minecraft.getMinecraft().getResourceManager();
    if (resources instanceof net.minecraft.client.resources.IReloadableResourceManager)
      ((net.minecraft.client.resources.IReloadableResourceManager) resources)
          .registerReloadListener(transitions);
    MinecraftForge.EVENT_BUS.register(new KeybindLocalizer());
    CommandMirror command = new CommandMirror(manager, config, returnController);
    ClientCommandHandler.instance.registerCommand(command);
    MinecraftForge.EVENT_BUS.register(command);
    MirrorDebug.log(
        "BOOT",
        "handlers registered: BaritoneReturnController, RecordingHandler, WorldLifecycleHandler, PathLineRenderer, StartMarkerRenderer, StatusOverlayHandler, TransientChat, KeybindLocalizer, CommandMirror");
    MirrorDebug.probe(
        "baritone.api.BaritoneAPI",
        MirrorDebug.classPresent("baritone.api.BaritoneAPI"),
        "auto return before playback");
  }

  public KeyBinding getKeyRecord() {
    return keyRecord;
  }

  public KeyBinding getKeyPlay() {
    return keyPlay;
  }

  public KeyBinding getKeyLoop() {
    return keyLoop;
  }

  public KeyBinding getKeyStop() {
    return keyStop;
  }

  public KeyBinding getKeyGui() {
    return keyGui;
  }

  public RecorderManager getManager() {
    return manager;
  }

  public RecorderConfig getConfig() {
    return config;
  }

  public StorageManager getStorage() {
    return storage;
  }

  public BaritoneReturnController getReturnController() {
    return returnController;
  }
}
