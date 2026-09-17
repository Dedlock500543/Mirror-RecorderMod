package com.mirror.recorder;
import com.mirror.recorder.command.CommandMirror;
import com.mirror.recorder.config.RecorderConfig;
import com.mirror.recorder.debug.MirrorDebug;
import com.mirror.recorder.handler.*;
import com.mirror.recorder.manager.RecorderManager;
import com.mirror.recorder.storage.StorageManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.*;
import org.lwjgl.input.Keyboard;

@Mod(modid=MirrorRecorder.MOD_ID,name=MirrorRecorder.MOD_NAME,version=MirrorRecorder.VERSION,clientSideOnly=true,acceptedMinecraftVersions="[1.12.2]")
public class MirrorRecorder {
    public static final String MOD_ID="mirror_recorder",MOD_NAME="Mirror Recorder",VERSION="1.0.0";
    @Mod.Instance(MOD_ID) public static MirrorRecorder instance;
    private RecorderConfig config; private StorageManager storage; private RecorderManager manager; private BaritoneReturnController returnController;
    private KeyBinding keyRecord,keyPlay,keyLoop,keyStop,keyGui;
    public static MirrorRecorder getInstance(){return instance;}
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent e){
        instance=this;
        java.io.File gameDir=e.getModConfigurationDirectory().getParentFile();
        MirrorDebug.init(gameDir);
        config=new RecorderConfig(e.getSuggestedConfigurationFile());
        storage=new StorageManager(gameDir);
        manager=new RecorderManager(config,storage);
        MirrorDebug.setEnabled(config.isDebug());MirrorDebug.setCsvEnabled(config.isDebugCsv());
        MirrorDebug.log("BOOT",MOD_NAME+" "+VERSION+" preInit, config="+e.getSuggestedConfigurationFile().getAbsolutePath());
        // Выход из игры или Alt+F4 посреди записи: незакрытая запись сбрасывается на диск, а не пропадает.
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable(){public void run(){
            try{if(manager!=null)manager.checkpointRecording();}catch(Throwable t){MirrorDebug.log("STORAGE","shutdown checkpoint failed: "+t);}
            try{if(storage!=null)storage.shutdownFlush();}catch(Throwable t){MirrorDebug.log("STORAGE","shutdown flush failed: "+t);}}},"MirrorRecorder-save"));
        // Названия клавиш — translation keys: их переводит игра через
        // assets/mirror_recorder/lang/*.lang, а без lang-файлов — KeybindLocalizer.
        // Категория — литеральное название мода: одинакова во всех языках и не
        // зависит от lang-файлов (иначе без них в «Управлении» виден сырой ключ).
        String cat="Mirror Recorder";
        // Горячие клавиши должны обрабатываться и в чате/GUI; сам обработчик по-прежнему проверяет допустимость каждой операции.
        keyRecord=new KeyBinding("key.mirror_recorder.record",KeyConflictContext.UNIVERSAL,Keyboard.KEY_NONE,cat);
        keyPlay=new KeyBinding("key.mirror_recorder.play",KeyConflictContext.UNIVERSAL,Keyboard.KEY_NONE,cat);
        keyLoop=new KeyBinding("key.mirror_recorder.loop",KeyConflictContext.UNIVERSAL,Keyboard.KEY_NONE,cat);
        keyStop=new KeyBinding("key.mirror_recorder.stop",KeyConflictContext.UNIVERSAL,Keyboard.KEY_NONE,cat);
        keyGui=new KeyBinding("key.mirror_recorder.gui",KeyConflictContext.UNIVERSAL,Keyboard.KEY_NONE,cat);
        ClientRegistry.registerKeyBinding(keyRecord);ClientRegistry.registerKeyBinding(keyPlay);ClientRegistry.registerKeyBinding(keyLoop);
        ClientRegistry.registerKeyBinding(keyStop);ClientRegistry.registerKeyBinding(keyGui);
    }
    @Mod.EventHandler public void init(FMLInitializationEvent e){
        PlaybackTrajectory trajectory=new PlaybackTrajectory(manager);
        returnController=new BaritoneReturnController(manager,config);
        MinecraftForge.EVENT_BUS.register(returnController);
        MinecraftForge.EVENT_BUS.register(new RecordingHandler(manager,config,trajectory));
        MinecraftForge.EVENT_BUS.register(new WorldLifecycleHandler(manager));
        MinecraftForge.EVENT_BUS.register(new PathLineRenderer(manager,config,trajectory));
        MinecraftForge.EVENT_BUS.register(new StartMarkerRenderer(manager,config,returnController));
        MinecraftForge.EVENT_BUS.register(new StatusOverlayHandler(manager,config,returnController));
        MinecraftForge.EVENT_BUS.register(new TransientChat());
        MinecraftForge.EVENT_BUS.register(new KeybindLocalizer());
        CommandMirror command=new CommandMirror(manager,config,returnController);ClientCommandHandler.instance.registerCommand(command);MinecraftForge.EVENT_BUS.register(command);
        MirrorDebug.log("BOOT","handlers registered: RecordingHandler, WorldLifecycleHandler, PathLineRenderer, StartMarkerRenderer, StatusOverlayHandler, TransientChat, BaritoneReturnController, CommandMirror");
        MirrorDebug.probe("baritone.api.BaritoneAPI",MirrorDebug.classPresent("baritone.api.BaritoneAPI"),"auto return before playback");
    }
    public KeyBinding getKeyRecord(){return keyRecord;} public KeyBinding getKeyPlay(){return keyPlay;} public KeyBinding getKeyLoop(){return keyLoop;}
    public KeyBinding getKeyStop(){return keyStop;} public KeyBinding getKeyGui(){return keyGui;}
    public RecorderManager getManager(){return manager;} public RecorderConfig getConfig(){return config;}
    public StorageManager getStorage(){return storage;} public BaritoneReturnController getReturnController(){return returnController;}
}
