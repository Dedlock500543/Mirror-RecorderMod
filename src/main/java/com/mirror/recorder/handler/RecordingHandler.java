package com.mirror.recorder.handler;
import com.mirror.recorder.MirrorRecorder;
import com.mirror.recorder.model.Frame;
import com.mirror.recorder.manager.RecorderManager;
import com.mirror.recorder.config.RecorderConfig;
import com.mirror.recorder.debug.MirrorDebug;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.*;
import net.minecraftforge.fml.relauncher.*;
import net.minecraftforge.event.world.WorldEvent;
import org.lwjgl.input.Keyboard;import org.lwjgl.input.Mouse;
import org.apache.logging.log4j.*;import java.lang.reflect.*;
@SideOnly(Side.CLIENT)
public class RecordingHandler {
    private static final Logger LOG=LogManager.getLogger("MirrorRecorder/Input");
    private boolean sndRec=false,sndPlay=false;private int sndCycle=0;
    private final SafetyGuard guard=new SafetyGuard();
    private final Minecraft mc=Minecraft.getMinecraft();
    private final RecorderManager manager; private final RecorderConfig config;
    private volatile String pendingChatMessage=null;
    /** Защита от Win: эти три клавиши сворачивают игру, поэтому не записываются и не повторяются. */
    private static final int WIN_LEFT=219,WIN_RIGHT=220,WIN_MENU=221;
    /** Сырые клавиши текущего тика и клавиши, которые зажал сам повтор. */
    private final java.util.ArrayDeque<int[]> keyLog=new java.util.ArrayDeque<int[]>();
    private final java.util.HashSet<Integer> replayedKeys=new java.util.HashSet<Integer>();
    /** Нарисованный курсор повтора: системная мышь остаётся у пользователя. */
    private boolean pointerOn=false;private float pointerX=0f,pointerY=0f;private float pointerFromX=0f,pointerFromY=0f,pointerToX=0f,pointerToY=0f;private long pointerStartNs=0L,pointerDurNs=50000000L,pointerLastNs=0L;private int typeWait=0;private boolean chatWarned=false;private boolean chatKeyReplay=false;private boolean prevChatOpen=false;private int pendingKeyMask=0;private static final int CHAT_OPEN_WAIT=20;
    private static final int POINTER_EDGE=0xFF0B1120,POINTER_BODY=0xFF7AA2FF;
    /** Живой чат: текст печатается в настоящем окне чата по одной букве. */
    private String typeText=null;private int typeAt=0;private long typeNextNs=0L;
    private static final long CHAT_FALLBACK_CHAR_NS=85000000L;
    private java.lang.reflect.Field chatField=null;
    private static final String[] CHAT_FIELDS={"field_146415_a","inputField"};
    /** Окно потеряло фокус: LWJGL оставляет клавиши вечно «нажатыми». */
    private boolean lostFocus=false;
    private boolean keysNeedReset=false;
    /** Одна очередь на клики И клавиши окон: события доставляются строго в том порядке, в каком были записаны. */
    private static final int CHAT_QUEUE_LIMIT=256,GUI_EVENT_TTL=40,GUI_QUEUE_LIMIT=128;
    /** Сколько тиков повтор может стоять на месте без применённого ввода, прежде чем остановиться самому. */
    private static final int STALL_LIMIT=10;
    private int stallTicks=0;
    private long rotRun=-1L;private int rotFrame=-1;private float lookDYaw=0f,lookDPitch=0f,savedLookYaw=0f,savedLookPitch=0f,savedPrevLookYaw=0f,savedPrevLookPitch=0f;private boolean lookRenderPushed=false;
    private long appliedRun=-1L,blendRun=-1L;
    /** Опора вращения на прогон: при старте повтора камера ставится на записанный стартовый угол, дальше повтор идёт по дельтам с плавным доворотом к записанному углу (не более MAX_ROT_CORRECTION за тик). */
    private long rotAnchorRun=-1L;private boolean rotAnchorSet=false;private static final float MAX_ROT_CORRECTION=10.0f;
    private final java.util.ArrayDeque<String> chatQueue=new java.util.ArrayDeque<String>();
    /** Записанные сообщения отправляются по одному: залп в один тик ведёт к кику за флуд. */
    private long lastChatSentAt=0L;
    private int appliedFrame=-1,interactionFrame=-1;private boolean heldAttack=false,heldUse=false,recRotInit=false;private int lastMask=0;private float lastRecYaw=0f,lastRecPitch=0f;private long interactionRun=-1L;private float adjustedForward,adjustedStrafe;private final PlaybackTrajectory trajectory;private boolean clickMethodsResolved=false,guiClickMethodResolved=false,clickMethodWarning=false;private Method clickMouseMethod,rightClickMouseMethod,guiMouseClickedMethod;private Field leftClickCounterField;private Method sendClickBlockMethod;private Field rightClickDelayField;private volatile boolean pendingGuiClick=false,pendingGuiCenter=false;private volatile int pendingGuiCX=0,pendingGuiCY=0;private boolean importedChatWarned=false;private volatile float pendingGuiX=0f,pendingGuiY=0f;private volatile String pendingGuiScreen=null;private String lastNotice="";private long lastNoticeAt=0L;private Method guiMouseReleasedMethod,guiKeyTypedMethod;private Field guiButtonListField;private boolean guiButtonListResolved=false;private net.minecraft.client.gui.GuiButton replaySlider=null;private GuiScreen replaySliderScreen=null;private int lastHotbar=-1;private final java.util.ArrayDeque<Object> guiQueue=new java.util.ArrayDeque<Object>();private boolean noticeGuiDrop=false;private int autoCheckpointTicks=0;private boolean sprintRefused=false;private boolean speedNoticeShown=false;private boolean backgroundPolicyHeld=false,savedPauseOnLostFocus=true;private int useHoldFallbackDelay=0;private boolean wantSprint=false;private boolean sprintAssist=false;private boolean useTimerAlign=false;private long maskRun=-1L;private volatile boolean pendingGuiShift=false;private int screenMismatchTicks=0;private static final int SCREEN_CLOSE_DELAY=10;private boolean quickMoveResolved=false;private Method handleMouseClickMethod,slotAtPositionMethod;private final Frame[] lookAhead=new Frame[100];private static final int GA_TYPE=0,GA_DROP=1,GA_PICK=2,GA_CLOSE=3,GA_SWAP=4,GA_DRAG=5,GA_DRAG_END=6,GA_CMOVE=7,GA_CRELEASE=8,GUI_FLAG_TIMED=4,GUI_KEY_LIMIT=64;private final java.util.List<int[]> pendingGuiKeys=new java.util.ArrayList<int[]>();private volatile String pendingGuiKeyScreen=null;private volatile int pendingGuiButton=0;private long recordTickStartNs=0L;private final java.util.Map<String,Field[]> textFieldCache=new java.util.HashMap<String,Field[]>();private int worldSuspendTicks=0;private static final int WORLD_SUSPEND_LIMIT=600;private volatile boolean worldResetPending=false;
    /** Отложенное нажатие в контейнере: отпуск приходит отдельным событием (GA_CRELEASE) или следующим нажатием. */
    private GuiScreen defPressScreen=null;private int defPressX=0,defPressY=0,defPressButton=0;private boolean defPressActive=false;private long defPressStartNs=0L;
    private Method guiMouseClickMoveMethod=null;
    /** Записываемый драг в контейнере: кнопка, экран, троттлинг движений (события мыши и тики идут в одном потоке). */
    private int recDragButton=-1,recDragLastX=0,recDragLastY=0;private String recDragScreen=null;private long recDragLastMoveNs=0L;
    private boolean pendingDropAll=false;
    public RecordingHandler(RecorderManager manager,RecorderConfig config,PlaybackTrajectory trajectory){this.manager=manager;this.config=config;this.trajectory=trajectory;}
    /** Аварийная остановка: гасит запись, повтор, возврат и все удерживаемые клавиши. */
    private void emergencyStop(String reason){
        MirrorRecorder mod=MirrorRecorder.getInstance();
        if(mod!=null&&mod.getReturnController()!=null)mod.getReturnController().cancel();
        resetPlayback();manager.stopAll();forceResetKeys();guard.standby(mc.player);
        SoundFx.error();com.mirror.recorder.debug.MirrorDebug.log("GUARD",reason);sendMsg("\u00a7c[!] "+reason);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void onInputUpdate(InputUpdateEvent event){
        if(!manager.isPlaying())return;
        EntityPlayerSP player=mc.player;if(player==null)return;
        // Пауза: записанный ввод не навязывается вообще — кадр стоит, игрок свободен.
        if(manager.isPaused()){event.getMovementInput().moveForward=0f;event.getMovementInput().moveStrafe=0f;event.getMovementInput().jump=false;event.getMovementInput().sneak=false;wantSprint=false;sprintAssist=false;return;}
        Frame frame=manager.getCurrentPlaybackFrame();if(frame==null)return;
        boolean replaySneak=config.isPlaybackSneak()&&(frame.hasKeyMask?frame.key(Frame.K_SNEAK):frame.sneak);
        trajectory.updateCalibration(player);event.getMovementInput().sneak=replaySneak;
        if(config.isPlaybackMovement()){
            float rawForward=frame.hasKeyMask?((frame.key(Frame.K_FORWARD)?1f:0f)-(frame.key(Frame.K_BACK)?1f:0f)):frame.moveForward,rawStrafe=frame.hasKeyMask?((frame.key(Frame.K_LEFT)?1f:0f)-(frame.key(Frame.K_RIGHT)?1f:0f)):frame.moveStrafe;
            float forward=MathHelper.clamp(rawForward,-1.0f,1.0f),strafe=MathHelper.clamp(rawStrafe,-1.0f,1.0f);if(!config.isApplyRotation()&&(forward!=0f||strafe!=0f)){double rec=Math.toRadians(frame.yaw),cur=Math.toRadians(player.rotationYaw),worldX=-forward*Math.sin(rec)+strafe*Math.cos(rec),worldZ=forward*Math.cos(rec)+strafe*Math.sin(rec),nf=-worldX*Math.sin(cur)+worldZ*Math.cos(cur),ns=worldX*Math.cos(cur)+worldZ*Math.sin(cur),peak=Math.max(Math.abs(nf),Math.abs(ns));if(peak>1d){nf/=peak;ns/=peak;}forward=(float)nf;strafe=(float)ns;}
            // Ваниль замедляет красться ×0.3 вн��три updatePlayerMoveState до нашего хука — масштабируем ДО
            // стабилизации: иначе поправка считается по полному вводу, а применяется к урезанному и теряет 70% авторитета.
            if(replaySneak){forward*=0.3f;strafe*=0.3f;}
            if(!stabilizeRoute(player,frame,forward,strafe)){event.getMovementInput().moveForward=0f;event.getMovementInput().moveStrafe=0f;event.getMovementInput().jump=false;event.getMovementInput().sneak=false;return;}
            event.getMovementInput().moveForward=adjustedForward;event.getMovementInput().moveStrafe=adjustedStrafe;
        }
        event.getMovementInput().jump=config.isPlaybackJump()&&(frame.hasKeyMask?frame.key(Frame.K_JUMP):frame.jump);
        // Replay the actual recorded sprint state: opening chat clears key states, and double-tap sprint never uses the sprint key bit.
        wantSprint=config.isPlaybackSprint()&&(frame.sprint||(frame.hasKeyMask&&frame.key(Frame.K_SPRINT)))&&!replaySneak;if(!config.isPlaybackMovement())sprintAssist=false;player.setSprinting(wantSprint||sprintAssist);
        applyPlaybackRotation();
        applyElytraFlight(player,frame);
        appliedRun=manager.getPlaybackRunId();appliedFrame=manager.getCurrentFrameIndex();keysNeedReset=true;
    }
    /** Новый угол ставится до кликов. prevRotation не трогаем: его перезапишет тик сущности, а плавность руки восстанавливается на кадре отрисовки. */
    private void applyPlaybackRotation(){
        EntityPlayerSP player=mc.player;if(player==null||!manager.isPlaying())return;
        Frame frame=manager.getCurrentPlaybackFrame();if(frame==null)return;
        long run=manager.getPlaybackRunId();int index=manager.getCurrentFrameIndex();
        if(rotRun==run&&rotFrame==index)return;
        rotRun=run;rotFrame=index;
        float carryYaw=manager.consumeCarryYaw(),carryPitch=manager.consumeCarryPitch();
        if(blendRun!=run){blendRun=run;guiQueue.clear();}
        if(rotAnchorRun!=run){rotAnchorRun=run;rotAnchorSet=false;if(frame.hasKeyMask&&config.isApplyRotation()){float baseYaw=frame.yaw-frame.dYaw,basePitch=MathHelper.clamp(frame.pitch-frame.dPitch,-90.0f,90.0f);player.rotationYaw=baseYaw;player.rotationPitch=basePitch;player.prevRotationYaw=baseYaw;player.prevRotationPitch=basePitch;player.rotationYawHead=baseYaw;rotAnchorSet=true;}}
        if(!config.isApplyRotation()){lookDYaw=0f;lookDPitch=0f;return;}
        float targetYaw,targetPitch;
        // На элитрах pitch и yaw — это руль и газ: ошибка в градус уводит с траектории на метры,
        // а доворот дельтами по 10° за тик её только накапливал. В кадрах полёта ставим записанный угол как есть.
        if(frame.hasKeyMask&&!frame.elytra){
            float stepYaw=player.rotationYaw+carryYaw+frame.dYaw,stepPitch=player.rotationPitch+carryPitch+frame.dPitch;
            // Доворот к записанному абсолютному углу, но не более MAX_ROT_CORRECTION градусов за тик: накопленная ошибка дельт гасится плавно, без рывков камеры.
            if(rotAnchorSet){
                stepYaw+=MathHelper.clamp(MathHelper.wrapDegrees(frame.yaw-stepYaw),-MAX_ROT_CORRECTION,MAX_ROT_CORRECTION);
                stepPitch+=MathHelper.clamp(frame.pitch-stepPitch,-MAX_ROT_CORRECTION,MAX_ROT_CORRECTION);
            }
            targetYaw=stepYaw;targetPitch=MathHelper.clamp(stepPitch,-90.0f,90.0f);
        }
        else{targetYaw=frame.yaw;targetPitch=MathHelper.clamp(frame.pitch,-90.0f,90.0f);}
        lookDYaw=MathHelper.wrapDegrees(targetYaw-player.rotationYaw);
        lookDPitch=MathHelper.clamp(targetPitch,-90.0f,90.0f)-player.rotationPitch;
        player.rotationYaw=player.rotationYaw+lookDYaw;
        player.rotationPitch=player.rotationPitch+lookDPitch;
        player.rotationYawHead=player.rotationYaw;
    }
    /** Ваниль рисует руку через partialTicks. После тика сущности prevRotation==rotation, поэтому на кадр отрисовки подставляем угол между тиками. */
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void onRenderTick(TickEvent.RenderTickEvent event){
        // Timed GUI input is delivered on render frames, not only at 20 TPS.
        if(event.phase==TickEvent.Phase.START&&manager.isPlaying()){processGuiQueue(false);tickChatTyping(false);}
        // Движение мыши считаем на кадрах отрисовки: ванильные дельты обновляются раз в кадр, а не раз в тик.
        if(event.phase==TickEvent.Phase.END)sampleMouseMovement();
        if(!manager.isPlaying()||!config.isApplyRotation()){popInterpolatedLook();return;}
        if(event.phase==TickEvent.Phase.START)pushInterpolatedLook(event.renderTickTime);else popInterpolatedLook();
    }
    private void pushInterpolatedLook(float partialTicks){
        EntityPlayerSP player=mc.player;if(player==null||lookRenderPushed)return;
        savedLookYaw=player.rotationYaw;savedLookPitch=player.rotationPitch;savedPrevLookYaw=player.prevRotationYaw;savedPrevLookPitch=player.prevRotationPitch;
        float pt=partialTicks<0f?0f:(partialTicks>1f?1f:partialTicks);
        float yaw=savedLookYaw-lookDYaw*(1f-pt),pitch=savedLookPitch-lookDPitch*(1f-pt);
        player.rotationYaw=yaw;player.rotationPitch=pitch;player.prevRotationYaw=yaw;player.prevRotationPitch=pitch;
        player.rotationYawHead=yaw;player.prevRotationYawHead=yaw;lookRenderPushed=true;
    }
    private void popInterpolatedLook(){
        if(!lookRenderPushed)return;EntityPlayerSP player=mc.player;lookRenderPushed=false;if(player==null)return;
        player.rotationYaw=savedLookYaw;player.rotationPitch=savedLookPitch;player.prevRotationYaw=savedPrevLookYaw;player.prevRotationPitch=savedPrevLookPitch;
        player.rotationYawHead=savedLookYaw;player.prevRotationYawHead=savedPrevLookYaw;
    }
    private boolean stabilizeRoute(EntityPlayerSP player,Frame frame,float forward,float strafe){
        adjustedForward=forward;adjustedStrafe=strafe;boolean prevAssist=sprintAssist;sprintAssist=false;
        // Игра отказала в спринте (голод ≤ 6 и т.п.): больше не долбимся всю оставшуюся часть прогона.
        if(prevAssist&&!player.isSprinting())sprintRefused=true;
        if(!trajectory.isReady())return true;if(Math.abs(config.getPlaybackSpeed()-1f)>0.001f){if(!speedNoticeShown){speedNoticeShown=true;MirrorDebug.log("ROUTE","stabilization disabled: playback speed "+config.getPlaybackSpeed()+" != 1.00");sendMsg(L("§eСкорость повтора не 1.00: маршрут будет пройден не полностью, стабилизация выключена. Для точного повтора ставьте 1.00.","§ePlayback speed is not 1.00: the route will not be followed completely and stabilization is off. Use 1.00 for an exact replay.","§eШвидкість відтворення не 1.00: маршрут буде пройдено не повністю, стабілізація вимкнена. Для точного повтору ставте 1.00.","§eWiedergabegeschwindigkeit ist nicht 1.00: Die Route wird nicht vollständig abgefahren, die Stabilisierung ist aus. Für exakte Wiedergabe 1.00 verwenden.","§ePrędkość odtwarzania nie wynosi 1.00: trasa nie zostanie przejechana w całości, stabilizacja jest wyłączona. Dla dokładnego powtórzenia ustaw 1.00."));}return true;}double errorX=trajectory.expectedPreX()-player.posX,errorZ=trajectory.expectedPreZ()-player.posZ,errorSq=errorX*errorX+errorZ*errorZ;
        MirrorDebug.route(manager.getPlaybackRunId(),manager.getCurrentFrameIndex(),trajectory.expectedPreX(),trajectory.expectedPreZ(),player.posX,player.posZ,Math.sqrt(errorSq),player.onGround);
        boolean elytraRoute=frame.elytra||player.isElytraFlying();
        double abortSq=elytraRoute?ELYTRA_ABORT_SQ:9d;int abortTicks=elytraRoute?ELYTRA_ABORT_TICKS:1;
        if(errorSq>abortSq){
            // На элитрах снос терпим дольше: физика полёта догоняет маршрут сама, а мгновенный обрыв на второй секунде был багом.
            if(++deviationTicks<abortTicks)return true;
            deviationTicks=0;MirrorDebug.log("ROUTE","ABORT: deviation "+String.format(java.util.Locale.ROOT,"%.2f",Double.valueOf(Math.sqrt(errorSq)))+" blocks over limit "+String.format(java.util.Locale.ROOT,"%.1f",Double.valueOf(Math.sqrt(abortSq)))+" at frame "+manager.getCurrentFrameIndex()+" (run "+manager.getPlaybackRunId()+", onGround="+player.onGround+", elytra="+elytraRoute+", water="+player.isInWater()+", riding="+player.isRiding()+")");MirrorDebug.csvClose();
            if(elytraRoute)sendMsg(L("§cВоспроизведение остановлено: в полёте маршрут ушёл больше чем на 16 блоков.","§cPlayback stopped: in-flight route deviation is over 16 blocks.","§cВідтворення зупинено: у польоті маршрут відійшов більше ніж на 16 блоків.","§cWiedergabe gestoppt: Routenabweichung im Flug über 16 Blöcke.","§cOdtwarzanie zatrzymane: w locie odchylenie trasy powyżej 16 bloków."));
            else sendMsg(L("\u00a7c\u0412\u043e\u0441\u043f\u0440\u043e\u0438\u0437\u0432\u0435\u0434\u0435\u043d\u0438\u0435 \u043e\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d\u043e: \u043e\u0442\u043a\u043b\u043e\u043d\u0435\u043d\u0438\u0435 \u043c\u0430\u0440\u0448\u0440\u0443\u0442\u0430 \u0431\u043e\u043b\u044c\u0448\u0435 3 \u0431\u043b\u043e\u043a\u043e\u0432.","§cPlayback stopped: route deviation is over 3 blocks.","§cВідтворення зупинено: відхилення маршруту більше 3 блоків.","§cWiedergabe gestoppt: Routenabweichung über 3 Blöcke.","§cOdtwarzanie zatrzymane: odchylenie trasy większe niż 3 bloki."));
            manager.stopPlayback();forceResetKeys();return false;
        }
        deviationTicks=0;
        // Порог сноса останавливает повтор всегда, даже когда коррекция маршрута выключена: 3 блока по земле, 16 блоков и 2 секунды выдержки на элитрах.
        if(!config.isRouteStabilization())return true;if(player.isRiding()||player.capabilities.isFlying||player.isElytraFlying()||frame.elytra||player.isInWater()||player.isInLava()||player.isOnLadder())return true;double velocityX=trajectory.expectedVelocityX()-player.motionX,velocityZ=trajectory.expectedVelocityZ()-player.motionZ,velocitySq=velocityX*velocityX+velocityZ*velocityZ;if(errorSq<1.0E-4d&&velocitySq<6.4E-5d)return true;
        float positionGain=player.onGround?0.45f:0.12f,velocityGain=player.onGround?0.18f:0.05f,limit=player.onGround?0.28f:0.08f;double worldX=errorX*positionGain+velocityX*velocityGain,worldZ=errorZ*positionGain+velocityZ*velocityGain,yaw=Math.toRadians(player.rotationYaw),localForward=-worldX*Math.sin(yaw)+worldZ*Math.cos(yaw),localStrafe=worldX*Math.cos(yaw)+worldZ*Math.sin(yaw);
        adjustedForward=MathHelper.clamp(forward+MathHelper.clamp((float)localForward,-limit,limit),-1f,1f);adjustedStrafe=MathHelper.clamp(strafe+MathHelper.clamp((float)localStrafe,-limit,limit),-1f,1f);
        // Ваниль срывает спринт при moveForward<0.8 — не даём коррекции опустить ввод ниже этого порога.
        boolean sneakNow=config.isPlaybackSneak()&&(frame.hasKeyMask?frame.key(Frame.K_SNEAK):frame.sneak);
        boolean recSprint=config.isPlaybackSprint()&&(frame.sprint||(frame.hasKeyMask&&frame.key(Frame.K_SPRINT)))&&!sneakNow;
        if(recSprint&&forward>=0.8f&&adjustedForward<0.8f)adjustedForward=0.8f;
        // Отставание на полном ходу вперёд нечем догнать: ввод уже 1.0. Кратковременный спринт — единственный запас скорости.
        double fwdError=-errorX*Math.sin(yaw)+errorZ*Math.cos(yaw);
        boolean canAssist=config.isPlaybackSprint()&&!recSprint&&!sneakNow&&player.onGround&&adjustedForward>=0.8f&&!(frame.hasKeyMask&&frame.key(Frame.K_ATTACK))&&!sprintRefused;
        sprintAssist=canAssist&&fwdError>(prevAssist?0.05d:0.15d);
        return true;
    }
    // Полёт на элитрах: свой порог сноса и своя опора. Жёсткие 3 блока рвали повтор через пару секунд полёта:
    // на глиссаде скорость до 2.5 блока за тик, и клавишами там маршрут не правится вообще.
    private static final double ELYTRA_ABORT_SQ=256d,ELYTRA_POS_GAIN=0.35d,ELYTRA_VEL_LIMIT=0.35d;
    private static final int ELYTRA_ABORT_TICKS=40,ELYTRA_WAIT_TICKS=20,MOUSE_GRACE_TICKS=20,MOUSE_REGRAB_GRACE=6;
    private static final double MOUSE_DECAY=0.75d;
    private int deviationTicks=0,elytraMismatchTicks=0,mouseMoveGrace=MOUSE_GRACE_TICKS;private boolean elytraNoticeShown=false;private double mouseMovePixels=0d;private long mouseRun=-1L;private boolean mouseFree=false;private int mouseFreeX=0,mouseFreeY=0;
    /** Полёт на элитрах повторяется скоростью, а не клавишами: ваниль считает глиссаду из motion и взгляда,
     *  а moveForward/moveStrafe в этом состоянии не участвуют. Ставим записанную скорость начала тика
     *  и добавляем ограниченную поправку на снос — маршрут держится и через минуту полёта. */
    private void applyElytraFlight(EntityPlayerSP player,Frame frame){
        if(frame==null||!frame.elytra){elytraMismatchTicks=0;return;}
        if(!config.isPlaybackMovement())return;
        if(!player.isElytraFlying()){
            // Полёт ещё не подтверждён сервером (START_FALL_FLYING идёт по сети) или элитр на игроке нет.
            if(++elytraMismatchTicks>=ELYTRA_WAIT_TICKS&&!elytraNoticeShown){
                elytraNoticeShown=true;MirrorDebug.log("PLAYBACK","recorded elytra flight without actual flight at frame "+manager.getCurrentFrameIndex());
                sendMsg(L("§eВ записи полёт на элитрах, а сейчас полёта нет: наденьте элитры для точного повтора.","§eThe recording flies with elytra but the player is not flying: equip elytra for an exact replay.","§eУ записі політ на елітрах, а зараз польоту немає: надіньте елітри для точного повтору.","§eDie Aufnahme fliegt mit Elytren, der Spieler aber nicht: Elytren anlegen für exakte Wiedergabe.","§eNagranie leci na elytrze, a gracz nie: załóż elytrę dla dokładnego powtórzenia."));
            }
            return;
        }
        elytraMismatchTicks=0;
        Frame previous=manager.getPreviousPlaybackFrame();
        double baseX=player.motionX,baseY=player.motionY,baseZ=player.motionZ;
        if(previous!=null&&previous.elytra){baseX=previous.motionX;baseY=previous.motionY;baseZ=previous.motionZ;}
        // Поправку на снос даём только на скорости 1.00: на ускоренном повторе кадры и физика идут в разном темпе.
        if(trajectory.isReady()&&Math.abs(config.getPlaybackSpeed()-1f)<0.001f){
            baseX+=MathHelper.clamp((trajectory.expectedPreX()-player.posX)*ELYTRA_POS_GAIN,-ELYTRA_VEL_LIMIT,ELYTRA_VEL_LIMIT);
            baseY+=MathHelper.clamp((trajectory.expectedPreY()-player.posY)*ELYTRA_POS_GAIN,-ELYTRA_VEL_LIMIT,ELYTRA_VEL_LIMIT);
            baseZ+=MathHelper.clamp((trajectory.expectedPreZ()-player.posZ)*ELYTRA_POS_GAIN,-ELYTRA_VEL_LIMIT,ELYTRA_VEL_LIMIT);
        }
        player.motionX=baseX;player.motionY=baseY;player.motionZ=baseZ;
    }
    /** Сколько мышь проехала за кадр, в пикселях. Два режима, потому что mouseHelper.deltaX ваниль обновляет
     *  только при inGameHasFocus: в игре читаем его дельты (Mouse.getDX() их бы съел, и камера встала бы),
     *  а при открытом экране — абсолютную позицию курсора, иначе в инвентаре и настройках дельта всегда нулевая. */
    private void sampleMouseMovement(){
        if(!config.isStopOnMouseMove()||!manager.isPlaying()||manager.isPaused()){mouseMovePixels=0d;mouseFree=false;return;}
        // Пока мод сам работает с окном из записи, движение считать нельзя: это не рука игрока.
        if(manager.getSimulateCooldown()>0){mouseMovePixels=0d;mouseFree=false;return;}
        if(mc.currentScreen==null&&mc.inGameHasFocus){
            mouseFree=false;
            if(mc.mouseHelper!=null)mouseMovePixels+=Math.abs(mc.mouseHelper.deltaX)+Math.abs(mc.mouseHelper.deltaY);
            return;
        }
        // Окно неактивно (Alt+Tab): позиция курсора не обновляется, а возврат в окно дал бы ложный прыжок.
        boolean active=true;try{active=org.lwjgl.opengl.Display.isActive();}catch(Exception e){active=true;}
        if(!active){mouseFree=false;return;}
        int x,y;try{x=Mouse.getX();y=Mouse.getY();}catch(Exception e){mouseFree=false;return;}
        // Первый кадр со свободным курсором — только база: прыжок при открытии экрана — не движение руки.
        if(!mouseFree){mouseFree=true;mouseFreeX=x;mouseFreeY=y;return;}
        mouseMovePixels+=Math.abs(x-mouseFreeX)+Math.abs(y-mouseFreeY);mouseFreeX=x;mouseFreeY=y;
    }
    /** Порог в пикселях: дрожание руки повтор не рвёт, осознанное движение — рвёт. Накопитель
     *  гасим на четверть за тик, а не обнуляем: при включённой стабилизации камера не едет, и игрок
     *  ведёт мышь медленно — порог за один тик так не набирался и стоп ждал рывка.
     *  Первые тики повтора и тики после возврата захвата мыши пропускаем: там дельта появляется сама. */
    private boolean mouseStopTriggered(){
        long run=manager.getPlaybackRunId();if(mouseRun!=run){mouseRun=run;mouseMoveGrace=MOUSE_GRACE_TICKS;mouseMovePixels=0d;return false;}
        if(!config.isStopOnMouseMove()){mouseMovePixels=0d;return false;}
        if(mouseMoveGrace>0){--mouseMoveGrace;mouseMovePixels=0d;return false;}
        if(mouseMovePixels>=config.getStopOnMouseThreshold()){mouseMovePixels=0d;return true;}
        mouseMovePixels*=MOUSE_DECAY;
        return false;
    }
    /** Стоп по мыши обязан работать и при открытом экране, и в замороженном мире (одиночная игра +
     *  любое ванильное окно = isGamePaused): иначе из настроек Minecraft повтор не остановить вообще,
     *  а ради этого настройка и нужна. */
    private boolean stopByMouseMove(){
        if(!manager.isPlaying()||manager.isPaused())return false;
        if(manager.getSimulateCooldown()>0){mouseMovePixels=0d;return false;}
        if(!mouseStopTriggered())return false;
        MirrorDebug.log("PLAYBACK","STOP: manual mouse movement at frame "+manager.getCurrentFrameIndex()+" (run "+manager.getPlaybackRunId()+", screen "+(mc.currentScreen==null?"none":mc.currentScreen.getClass().getSimpleName())+")");
        MirrorDebug.csvClose();resetPlayback();manager.stopPlayback();SoundFx.stop();sendMsg(L("§eВоспроизведение остановлено: обнаружено движение мыши.","§ePlayback stopped: mouse movement detected.","§eВідтворення зупинено: виявлено рух миші.","§eWiedergabe gestoppt: Mausbewegung erkannt.","§eOdtwarzanie zatrzymane: wykryto ruch myszy."));
        return true;
    }
    private void resetStabilizer(){trajectory.reset();speedNoticeShown=false;sprintAssist=false;sprintRefused=false;deviationTicks=0;elytraMismatchTicks=0;elytraNoticeShown=false;mouseMovePixels=0d;mouseMoveGrace=MOUSE_GRACE_TICKS;mouseFree=false;}
    /** Пока идёт операция мода, не даём ванилле открыть паузу через 500 мс после потери фокуса. Исходную настройку пользователя возвращаем без сохранения в options.txt. */
    private void updateBackgroundPolicy(){
        if(mc.gameSettings==null)return;MirrorRecorder mod=MirrorRecorder.getInstance();BaritoneReturnController r=mod==null?null:mod.getReturnController();boolean active=manager.isBusy()||(r!=null&&(r.isBusy()||r.isRecovering()));
        if(active){if(!backgroundPolicyHeld){savedPauseOnLostFocus=mc.gameSettings.pauseOnLostFocus;backgroundPolicyHeld=true;}mc.gameSettings.pauseOnLostFocus=false;}
        else releaseBackgroundPolicy();
    }
    private void releaseBackgroundPolicy(){if(backgroundPolicyHeld&&mc.gameSettings!=null)mc.gameSettings.pauseOnLostFocus=savedPauseOnLostFocus;backgroundPolicyHeld=false;}
    /** Ваниль возвращает захват мыши только по клику (runTickMouse), да и тот лишь при активном окне
     *  (setIngameFocus проверяет Display.isActive). Если в мире без открытого экрана захвата нет —
     *  возвращаем сами: эффект обязательного клика, но без клика. Чужие экраны, чат, пауза, смерть и
     *  неактивное окно не трогаем — там свободный курсор законен. */
    /** Захват мыши возвращаем только пока идёт операция мода (запись, повтор, возврат): в покое окно ведёт себя как ваниль. */
    private void ensureIngameGrab(){
        if(mc.player==null||mc.world==null||mc.currentScreen!=null)return;
        // Захват мыши теряется и при inGameHasFocus=true: тогда камера стоит, пока не кликнешь мышкой.
        boolean grabbed=true;try{grabbed=Mouse.isGrabbed();}catch(Exception e){grabbed=true;}
        if(mc.inGameHasFocus&&grabbed)return;
        // Окно свёрнуто — забирать курсор нельзя: пользователь работает в другой программе.
        try{if(!org.lwjgl.opengl.Display.isActive())return;}catch(Exception e){}
        MirrorRecorder mod=MirrorRecorder.getInstance();BaritoneReturnController r=mod==null?null:mod.getReturnController();
        if(!(manager.isBusy()||(r!=null&&(r.isBusy()||r.isRecovering()))))return;
        mc.setIngameFocus();mouseMoveGrace=MOUSE_REGRAB_GRACE;mouseMovePixels=0d;mouseFree=false;
        // setIngameFocus ставит leftClickCounter=10000 — ванильная защита от клика, которым возвращают курсор в игру.
        // Для повтора это тихая потеря всех записанных атак на 500 секунд после любого Alt+Tab, поэтому блокировку снимаем сразу.
        clearLeftClickBlock();
    }
    /** Периодический автосейв живой записи: краш или Alt+F4 не должны съедать часы работы. */
    private void tickAutoCheckpoint(){
        int seconds=config.getAutoCheckpointSeconds();
        if(seconds<=0){autoCheckpointTicks=0;return;}
        if(++autoCheckpointTicks<seconds*20)return;
        autoCheckpointTicks=0;manager.checkpointRecording();MirrorDebug.log("STORAGE","auto checkpoint after "+seconds+" s");
    }
    @SubscribeEvent public void onClientTick(TickEvent.ClientTickEvent event){
        updateBackgroundPolicy();focusGuard();if(event.phase==TickEvent.Phase.START){recordTickStartNs=manager.isRecording()?System.nanoTime():0L;tickChatTyping(true);}ensureIngameGrab();
        if(event.phase==TickEvent.Phase.START){if(manager.isPlaying()&&!mc.isGamePaused()){popInterpolatedLook();long run=manager.getPlaybackRunId();int index=manager.getCurrentFrameIndex();if(rotRun==run&&rotFrame==index){lookDYaw=0f;lookDPitch=0f;}applyPlaybackRotation();handlePlaybackInteractions();}return;}
        // Мира нет: либо переход между мирами (ждём), либо выход в меню/дисконнект (30 с без мира — стоп с сохранением).
        if(mc.world==null){if(manager.isBusy()&&++worldSuspendTicks>=WORLD_SUSPEND_LIMIT){worldSuspendTicks=0;boolean wasRec=manager.isRecording();manager.stopAll();forceResetKeys();sendMsg(wasRec?L("§eЗапись остановлена: выход из мира.","§eRecording stopped: left the world.","§eЗапис зупинено: вихід зі світу.","§eAufnahme gestoppt: Welt verlassen.","§eNagrywanie zatrzymane: opuszczenie świata."):L("§eПовтор остановлен: выход из мира.","§ePlayback stopped: left the world.","§eВідтворення зупинено: вихід зі світу.","§eWiedergabe gestoppt: Welt verlassen.","§eOdtwarzanie zatrzymane: opuszczenie świata."));}return;}
        if(worldSuspendTicks>0)worldSuspendTicks=0;
        EntityPlayerSP player=mc.player;if(player==null||mc.world==null)return;if(!manager.isRecording())pendingChatMessage=null;
        int failedSlot=manager.consumeUnsavedWarningSlot();if(failedSlot>0)SoundFx.error();
        if(failedSlot>0)sendMsg(L("\u00a7c\u041e\u0448\u0438\u0431\u043a\u0430 \u0441\u043e\u0445\u0440\u0430\u043d\u0435\u043d\u0438\u044f: \u0441\u043b\u043e\u0442 ","§cSave error: slot ","§cПомилка збереження: слот ","§cSpeicherfehler: Slot ","§cBłąd zapisu: slot ")+failedSlot+L(" \u043e\u0441\u0442\u0430\u043b\u0441\u044f \u0442\u043e\u043b\u044c\u043a\u043e \u0432 \u043f\u0430\u043c\u044f\u0442\u0438. \u041d\u0435 \u0437\u0430\u043a\u0440\u044b\u0432\u0430\u0439\u0442\u0435 \u0438\u0433\u0440\u0443."," is kept only in memory. Do not close the game."," залишився лише в пам'яті. Не закривайте гру."," ist nur noch im Speicher. Schließe das Spiel nicht."," pozostał tylko w pamięci. Nie zamykaj gry."));
        boolean nowRec=manager.isRecording(),nowPlay=manager.isPlaying();
        int limitSlot=manager.consumeLimitReachedSlot();
        if(limitSlot>0)sendMsg(L("§eЗапись остановлена: достигнут лимит ","§eRecording stopped: the frame limit of ","§eЗапис зупинено: досягнуто ліміт ","§eAufnahme gestoppt: das Limit von ","§eNagranie zatrzymane: osiągnięto limit ")+com.mirror.recorder.storage.StorageManager.MAX_FRAMES+L(" кадров (1 час). Слот сохраняется в фоне."," frames (1 hour) was reached. The slot is saving in the background."," кадрів (1 годину). Слот зберігається у фоні."," Frames (1 Stunde) wurde erreicht. Der Slot wird im Hintergrund gespeichert."," klatek (1 godzina) został osiągnięty. Slot zapisuje się w tle."));
        if(nowRec&&!sndRec)SoundFx.recordStart();
        if(!nowRec&&sndRec)SoundFx.recordStop();
        if(nowPlay&&!sndPlay){SoundFx.playStart();sndCycle=manager.getPlaybackCycle();}
        else if(nowPlay){int cyc=manager.getPlaybackCycle();if(cyc>sndCycle){sndCycle=cyc;SoundFx.cycle();}}
        sndRec=nowRec;sndPlay=nowPlay;
        if(nowRec||nowPlay||manager.hasPendingAction()){String threat=guard.check(player,config);if(threat!=null){emergencyStop(threat);return;}}else guard.standby(player);
        if((manager.isPlaying()||manager.isRecording()||manager.hasPendingAction())&&(player.isDead||player.getHealth()<=0f)){boolean wasRec=manager.isRecording();manager.stopAll();forceResetKeys();sendMsg(wasRec?L("§eЗапись остановлена: игрок погиб.","§eRecording stopped: the player died.","§eЗапис зупинено: гравець загинув.","§eAufnahme gestoppt: Der Spieler ist gestorben.","§eNagrywanie zatrzymane: gracz zginął."):L("§eПовтор остановлен: игрок погиб.","§ePlayback stopped: the player died.","§eВідтворення зупинено: гравець загинув.","§eWiedergabe gestoppt: Der Spieler ist gestorben.","§eOdtwarzanie zatrzymane: gracz zginął."));return;}
        if(manager.hasPendingAction()){
            int prev=manager.getDelaySecondsRemaining();int sec=manager.tickDelay();
            if(sec>0&&sec!=prev){sendMsg("\u00a7e"+sec+"...");SoundFx.countdown(sec);}else if(sec==0)sendMsg(L("\u00a7a\u0421\u0442\u0430\u0440\u0442!","§aStart!","§aСтарт!","§aStart!","§aStart!"));return;
        }
        // Удержание с открытым экраном гоняем в конце тика: Esc стирает состояния клавиш в середине тика (unPressAllKeys), а ванильные повторы при экране заглушены контекстом IN_GAME и проверкой currentScreen. END-фаза покрывает и тик, в котором экран открыли. Таймер/внутренние проверки не дают двойных срабатываний.
        if(manager.isPlaying()&&mc.currentScreen!=null){if(heldAttack)driveScreenAttackHold();if(heldUse)driveScreenUseHold();}
        if(useTimerAlign){useTimerAlign=false;if(rightClickDelayField!=null)try{if(rightClickDelayField.getInt(mc)==3)rightClickDelayField.setInt(mc,4);}catch(Exception e){warnClickFailure("Cannot align use timer",e);}}
        // Мир заморожен (одиночная игра + пауза): запись и повтор обязаны замереть вместе с ним, иначе кадры жрутся в замороженном мире и точность уходит в ноль. На сервере мир не замирает — там ничего не меняется.
        if(manager.isRecording()){if(!mc.isGamePaused()){handleRecord(player);tickAutoCheckpoint();}else clearPendingInput();}else if(manager.isPlaying()){if(!mc.isGamePaused())handlePlayback(player);else stopByMouseMove();}else if(keysNeedReset)forceResetKeys();
    }
    private void handlePlaybackInteractions(){
        if(mc.player==null||mc.world==null||mc.player.isDead||mc.player.getHealth()<=0f)return;
        // Пауза: удержания отпускаем и ничего нового не жмём — кадр стоит, очередь окон ждёт.
        if(manager.isPaused()){if(heldAttack||heldUse){heldAttack=false;heldUse=false;if(mc.gameSettings!=null){applyHold(mc.gameSettings.keyBindAttack,false);applyHold(mc.gameSettings.keyBindUseItem,false);}}return;}
        if(useHoldFallbackDelay>0)--useHoldFallbackDelay;
        boolean interact=config.isPlaybackInteraction();
        if(!interact&&(heldAttack||heldUse)){heldAttack=false;heldUse=false;lastMask=0;forceReleaseClicks();}
        long run=manager.getPlaybackRunId();int index=manager.getCurrentFrameIndex();
        boolean fresh=interactionRun!=run||interactionFrame!=index;
        boolean blocked=config.isStopOnMove()&&manager.getSimulateCooldown()<=0&&isRealMovement();
        Frame frame=manager.getCurrentPlaybackFrame();
        if(fresh&&frame!=null&&!blocked){
            interactionRun=run;interactionFrame=index;
            java.util.List<Frame> pending=manager.consumeSkippedFrames();
            // Первый кадр запуска: состояние кнопок берём из него самого. При lastMask=0 зажатая в записи
            // кнопка выглядит как новое нажатие и на нулевом кадре стреляет лишний клик. Настоящее нажатие
            // ровно на этом кадре приходит через frame.leftClick/attackClicks и не теряется.
            if(maskRun!=run){Frame seed=pending.isEmpty()?frame:pending.get(0);maskRun=run;lastMask=seed.hasKeyMask?seed.keyMask:0;}
            for(int i=0;i<pending.size();i++)replayFrameEvents(pending.get(i),false,interact);
            replayFrameEvents(frame,true,interact);
        }
        if(interact){processGuiQueue();closeUnexpectedScreen(frame);}
        if(defPressActive&&(mc.currentScreen==null||mc.currentScreen!=defPressScreen)){defPressActive=false;defPressScreen=null;}
    }
    /** Один кадр записи: сначала дискретные события, затем клики и чат. При ускорении пропущенные кадры проходят тем же путём, поэтому события не теряются. */
    private void replayFrameEvents(Frame frame,boolean current,boolean interact){
        if(frame==null)return;
        if(current){applyKeyEvents(frame);setPointer(frame);openRecordedMenu(frame);}
        // Граница мира: опоры поворота и стабилизации от старого мира невалидны, очереди окон мертвы.
        if(frame.worldReset){rotAnchorSet=false;resetStabilizer();guiQueue.clear();}
        if(config.isPlaybackChat()&&frame.chatMessage!=null&&!frame.chatMessage.isEmpty()){
            if(manager.isSlotImported(manager.getActiveOperationSlot()))warnImportedChat();
            else if(chatQueue.size()<CHAT_QUEUE_LIMIT)chatQueue.add(frame.chatMessage);
            else{LOG.warn("Chat queue overflow, recorded message dropped");MirrorDebug.log("CHAT","queue overflow at limit "+CHAT_QUEUE_LIMIT+", dropped a message of "+frame.chatMessage.length()+" chars");}
        }
        if(!interact||mc.gameSettings==null)return;
        KeyBinding attack=mc.gameSettings.keyBindAttack,use=mc.gameSettings.keyBindUseItem;
        if(!frame.hasKeyMask){
            heldAttack=false;heldUse=false;lastMask=0;
            KeyBinding.setKeyBindState(attack.getKeyCode(),false);KeyBinding.setKeyBindState(use.getKeyCode(),false);
            fireOrderedClicks(frame,attack,use,frame.attackClicks,frame.useClicks);
            ;
            return;
        }
        int mask=frame.keyMask;
        // A held-state bit cannot distinguish a hold from another press on the next tick. The explicit mouse events can.
        boolean attackPress=frame.leftClick||rising(mask,Frame.K_ATTACK),usePress=frame.rightClick||rising(mask,Frame.K_USE);
        int attackShots=frame.attackClicks>0?frame.attackClicks:(attackPress?1:0),useShots=frame.useClicks>0?frame.useClicks:(usePress?1:0);
        // Маска пропущенных кадров не нужна: их события доставляются по порядку через consumeSkippedFrames.
        applyHotbar(frame.hotbarSlot);
        // При открытом окне ваниль не читает хоткеи: накопленное нажатие выстреливало после закрытия окна (фантомный дроп/инвентарь). Действия окна повтор��ются через guiKeys, мировые клавиши — только когда окна нет ни в записи, ни сейчас.
        boolean guiKeyCtx=frame.guiKeys.length>0||(frame.hasScreenState&&frame.openScreen!=null),hasGuiClose=false;
        for(int i=0;i+6<=frame.guiKeys.length;i+=6)if(frame.guiKeys[i]==GA_CLOSE){hasGuiClose=true;break;}
        if(mc.currentScreen==null&&!guiKeyCtx){if((mask&Frame.K_DROP)!=0&&(lastMask&Frame.K_DROP)==0&&mc.player!=null&&!mc.player.isSpectator())mc.player.dropItem(frame.dropAll);pulse(mc.gameSettings.keyBindSwapHands,(mask&Frame.K_SWAP)!=0,(lastMask&Frame.K_SWAP)!=0);pulse(mc.gameSettings.keyBindPickBlock,(mask&Frame.K_PICK)!=0,(lastMask&Frame.K_PICK)!=0);}
        if(mc.currentScreen==null&&!hasGuiClose&&(mask&Frame.K_INVENTORY)!=0&&(lastMask&Frame.K_INVENTORY)==0&&mc.player!=null){
            // To zhe, chto vanilj po klavishe inventarja, no napryamuju: nakopitelj pressTime cherez onTick terjaetsja pri meljkajushchih oknah.
            boolean spNoLan=mc.isSingleplayer()&&(mc.getIntegratedServer()==null||!mc.getIntegratedServer().getPublic());
            if(!spNoLan){if(mc.playerController.isRidingHorse())mc.player.sendHorseInventory();else mc.displayGuiScreen(new net.minecraft.client.gui.inventory.GuiInventory(mc.player));}
        }
        boolean guiFrame=frame.guiClick&&frame.guiScreen!=null&&!frame.guiScreen.isEmpty();
        if(guiFrame){
            KeyBinding.setKeyBindState(attack.getKeyCode(),false);KeyBinding.setKeyBindState(use.getKeyCode(),false);
            if(frame.guiButton==2)queueGuiClick(frame,2);
            else queueOrderedGuiClicks(frame,attackPress,usePress);
            heldAttack=false;heldUse=false;
        }else if(current){
            boolean wantAttack=(mask&Frame.K_ATTACK)!=0,wantUse=(mask&Frame.K_USE)!=0;
            applyHold(attack,wantAttack);applyHold(use,wantUse);
            fireOrderedClicks(frame,attack,use,attackShots,useShots);
            ;
            heldAttack=wantAttack;heldUse=wantUse;
        }else{
            fireOrderedClicks(frame,attack,use,attackShots,useShots);
            ;
            heldAttack=(mask&Frame.K_ATTACK)!=0;heldUse=(mask&Frame.K_USE)!=0;
        }
        if(frame.guiKeys.length>0&&frame.openScreen!=null&&(config.isVisibleChat()||!frame.openScreen.endsWith(".GuiChat")))queueGuiKeys(frame);
        lastMask=mask;
    }
    private boolean rising(int mask,int bit){return (mask&bit)!=0&&(lastMask&bit)==0;}
    /** Клики тика воспроизводятся ровно в записанном порядке (ЛКМ/ПКМ не меняются местами).
     *  Старые записи порядка не содержат, для них поведение остаётся прежним. */
    private void fireOrderedClicks(Frame frame,KeyBinding attack,KeyBinding use,int attackShots,int useShots){
        int[] seq=frame.clickSeq;int left=attackShots,right=useShots;
        for(int i=0;i<seq.length;i++){
            if(seq[i]==0){if(left<=0)continue;left--;fireRecordedClick(frame,attack,false);}
            else if(seq[i]==1){if(right<=0)continue;right--;fireRecordedClick(frame,use,true);}
        }
        for(int i=0;i<left;i++)fireRecordedClick(frame,attack,false);
        for(int i=0;i<right;i++)fireRecordedClick(frame,use,true);
    }
    /** Клики окна ставятся в очередь тем же порядком, каким были записаны внутри тика. */
    private void queueOrderedGuiClicks(Frame frame,boolean attackPress,boolean usePress){
        int[] seq=frame.clickSeq;
        if(seq.length>0){
            boolean queued=false;
            for(int i=0;i<seq.length;i++){
                if(seq[i]==0&&attackPress){queueGuiClick(frame,0);queued=true;}
                else if(seq[i]==1&&usePress){queueGuiClick(frame,1);queued=true;}
            }
            if(queued)return;
        }
        if(attackPress)queueGuiClick(frame,0);
        if(usePress)queueGuiClick(frame,1);
    }
    /** Снимает ванильную блокировку удара (leftClickCounter): записанный клик обязан сработать. */
    private void clearLeftClickBlock(){
        if(!clickMethodsResolved)resolveClickMethods();
        if(leftClickCounterField==null)return;
        try{leftClickCounterField.setInt(mc,0);}catch(Exception e){warnClickFailure("Cannot clear the left click block",e);}
    }
    /** Смена предмета повторяется как нажатие клавиши слота, а не как запись состояния инвентаря. */
    private void applyHotbar(int slot){
        if(slot<0||slot>8)return;lastHotbar=slot;
        // Сверяемся с фактическим слотом игрока, а не с кэшем: если предмет сменили вручную или откатил сервер,
        // повтор вернёт записанное состояние, а не решит, что оно уже применено.
        if(mc.player!=null&&mc.player.inventory!=null&&mc.player.inventory.currentItem!=slot){
            mc.player.inventory.currentItem=slot;
            if(mc.player.connection!=null)mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketHeldItemChange(slot));
        }
    }
    private static final class GuiClickEvent{
        final String screen;final float x,y;final int button,cx,cy;final boolean hasCenter,shift;int waited=0;
        GuiClickEvent(String screen,float x,float y,int button,boolean hasCenter,int cx,int cy,boolean shift){this.screen=screen;this.x=x;this.y=y;this.button=button;this.hasCenter=hasCenter;this.cx=cx;this.cy=cy;this.shift=shift;}
    }
    /** Любой реально открытый экран, включая чат и интерфейс мода, может принять записанный GUI-клик. */
    /** Клавиша, нажатая в чужом окне: ждёт своего экрана так же, как клик. */
    private static final class GuiKeyEvent{
        final String screen;final int[] data;int waited=0,at=0;final long startedNs=System.nanoTime();
        GuiKeyEvent(String screen,int[] data){this.screen=screen;this.data=data;}
    }
    private static boolean replayableScreen(String className){return className!=null&&!className.isEmpty();}
    private void queueGuiClick(Frame frame,int button){
        if(frame==null||!replayableScreen(frame.guiScreen))return;
        if(guiQueue.size()>=GUI_QUEUE_LIMIT){MirrorDebug.log("GUI","event queue overflow, dropped click for "+frame.guiScreen);return;}
        guiQueue.add(new GuiClickEvent(frame.guiScreen,frame.guiX,frame.guiY,button,frame.hasGuiCenter,frame.guiCenterX,frame.guiCenterY,frame.guiShift));
    }
    /** Клики и клавиши окон идут одной очередью: событие ждёт свой экран и не может обогнать предыдущее. */
    private void processGuiQueue(){processGuiQueue(true);}
    /** ageWait=false is called from render frames: it may deliver due input but must not age the tick-based TTL. */
    private void processGuiQueue(boolean ageWait){
        int guard=0;
        while(!guiQueue.isEmpty()&&guard++<16){
            Object head=guiQueue.peek();GuiScreen screen=mc.currentScreen;
            String want=head instanceof GuiClickEvent?((GuiClickEvent)head).screen:((GuiKeyEvent)head).screen;
            if(screen!=null&&screen.getClass().getName().equals(want)){
                if(head instanceof GuiClickEvent){guiQueue.poll();if(replayableScreen(want))deliverGuiClick(screen,(GuiClickEvent)head);continue;}
                if(deliverGuiKeys(screen,(GuiKeyEvent)head)){guiQueue.poll();continue;}
                break;
            }
            if(!ageWait)break;
            int waited=head instanceof GuiClickEvent?++((GuiClickEvent)head).waited:++((GuiKeyEvent)head).waited;
            if(waited>GUI_EVENT_TTL){guiQueue.poll();MirrorDebug.log("GUI","dropped recorded GUI event: "+want+" did not open");warnGuiDrop();continue;}
            break;
        }
    }
    /** Потерянное событие окна — это расхождение с записью: говорим об этом один раз за прогон. */
    private void warnGuiDrop(){
        if(noticeGuiDrop)return;noticeGuiDrop=true;
        sendMsg(L("§eЧасть действий в окнах не повторена: нужное окно не открылось.","§eSome window actions were skipped: the expected window did not open.","§eЧастину дій у вікнах не повторено: потрібне вікно не відкрилося.","§eEinige Fensteraktionen wurden übersprungen: Das erwartete Fenster wurde nicht geöffnet.","§eCzęść akcji w oknach nie została powtórzona: potrzebne okno się nie otworzyło."));
    }
    private int eventOffsetMs(){long base=recordTickStartNs;if(base<=0L)return 0;long ms=(System.nanoTime()-base)/1000000L;return (int)(ms<0L?0L:(ms>49L?49L:ms));}
    private void resolveGuiButtonList(GuiScreen screen){
        if(guiButtonListResolved)return;guiButtonListResolved=true;
        String[] names={"buttonList","field_146292_n"};
        for(String n:names)try{Field f=GuiScreen.class.getDeclaredField(n);f.setAccessible(true);guiButtonListField=f;return;}catch(Exception e){}
        try{for(Field f:GuiScreen.class.getDeclaredFields())if(java.util.List.class.isAssignableFrom(f.getType())){f.setAccessible(true);Object v=f.get(screen);if(v instanceof java.util.List){for(Object o:(java.util.List<?>)v)if(o instanceof net.minecraft.client.gui.GuiButton){guiButtonListField=f;return;}}}}catch(Exception e){}
    }
    private boolean sliderButton(net.minecraft.client.gui.GuiButton b){
        if(b==null||b.getClass()==net.minecraft.client.gui.GuiButton.class)return false;
        if(b instanceof net.minecraft.client.gui.GuiOptionSlider)return true;
        for(Method m:b.getClass().getDeclaredMethods()){Class<?>[] a=m.getParameterTypes();String n=m.getName();if(a.length==3&&a[0]==Minecraft.class&&a[1]==Integer.TYPE&&a[2]==Integer.TYPE&&(n.equals("mouseDragged")||n.equals("func_146119_b")))return true;}
        return false;
    }
    private net.minecraft.client.gui.GuiButton findReplaySlider(GuiScreen screen,int x,int y){
        resolveGuiButtonList(screen);if(guiButtonListField==null)return null;
        try{Object v=guiButtonListField.get(screen);if(!(v instanceof java.util.List))return null;for(Object o:(java.util.List<?>)v)if(o instanceof net.minecraft.client.gui.GuiButton){net.minecraft.client.gui.GuiButton b=(net.minecraft.client.gui.GuiButton)o;if(b.visible&&b.enabled&&x>=b.x&&x<b.x+b.width&&y>=b.y&&y<b.y+b.height&&sliderButton(b))return b;}}catch(Exception e){warnClickFailure("Cannot inspect GUI sliders",e);}
        return null;
    }
    private void deliverSliderDrag(GuiScreen screen,int cx,int cy,boolean end){
        int x=MathHelper.clamp(screen.width/2+cx,0,Math.max(0,screen.width-1)),y=MathHelper.clamp(screen.height/2+cy,0,Math.max(0,screen.height-1));pointerSnap(x,y);
        if(replaySliderScreen!=screen||replaySlider==null){replaySlider=findReplaySlider(screen,x,y);replaySliderScreen=replaySlider==null?null:screen;}
        net.minecraft.client.gui.GuiButton b=replaySlider;
        if(b!=null)try{int px=MathHelper.clamp(x,b.x,Math.max(b.x,b.x+b.width-1)),py=MathHelper.clamp(y,b.y,Math.max(b.y,b.y+b.height-1));b.mousePressed(mc,px,py);b.mouseReleased(px,py);}catch(Exception e){warnClickFailure("Recorded slider drag failed",e);}
        if(end){replaySlider=null;replaySliderScreen=null;}
    }

    private void deliverGuiClick(GuiScreen screen,GuiClickEvent c){
        if(!guiClickMethodResolved){resolveGuiClickMethod();MirrorDebug.probe("GuiScreen.mouseClicked",guiMouseClickedMethod!=null,"GUI click replay");MirrorDebug.probe("GuiScreen.mouseReleased",guiMouseReleasedMethod!=null,"GUI release replay");}
        if(guiMouseClickedMethod==null)return;
        int x,y;
        if(c.hasCenter){x=MathHelper.clamp(screen.width/2+c.cx,0,Math.max(0,screen.width-1));y=MathHelper.clamp(screen.height/2+c.cy,0,Math.max(0,screen.height-1));}
        else{x=MathHelper.clamp((int)(c.x*Math.max(1,screen.width)),0,Math.max(0,screen.width-1));y=MathHelper.clamp((int)(c.y*Math.max(1,screen.height))-1,0,Math.max(0,screen.height-1));}
        pointerSnap(x,y);
        if(c.button==0){replaySlider=findReplaySlider(screen,x,y);replaySliderScreen=replaySlider==null?null:screen;}
        flushDeferredPress();
        if(c.button==2&&deliverClone(screen,x,y))return;
        if(c.shift&&deliverQuickMove(screen,x,y,c.button))return;
        try{guiMouseClickedMethod.invoke(screen,Integer.valueOf(x),Integer.valueOf(y),Integer.valueOf(c.button));}
        catch(Exception e){warnClickFailure("Recorded GUI click failed",e);return;}
        deliverListClick(screen,x,y,c.button);
        if(shouldDeferContainerRelease(screen,x,y,c)){armDeferredPress(screen,x,y,c.button);return;}
        if(guiMouseReleasedMethod==null)return;
        try{guiMouseReleasedMethod.invoke(screen,Integer.valueOf(x),Integer.valueOf(y),Integer.valueOf(c.button));}
        catch(Exception e){warnClickFailure("Recorded GUI release failed",e);}
        if(mc.currentScreen!=screen)clearLeftClickBlock();
    }
    /** Нажатие по слоту: отпуск откладываем — дальше может идти драг. Старые записи без GA_CRELEASE отпускаются следующим нажатием. */
    private boolean shouldDeferContainerRelease(GuiScreen screen,int x,int y,GuiClickEvent c){
        if(!(screen instanceof net.minecraft.client.gui.inventory.GuiContainer))return false;
        if(c==null||c.shift||(c.button!=0&&c.button!=1))return false;
        if(!quickMoveResolved)resolveQuickMove();
        if(slotAtPositionMethod==null)return false;
        try{Object slot=slotAtPositionMethod.invoke(screen,Integer.valueOf(x),Integer.valueOf(y));return slot instanceof net.minecraft.inventory.Slot;}
        catch(Exception e){return false;}
    }
    private void armDeferredPress(GuiScreen screen,int x,int y,int button){
        defPressScreen=screen;defPressX=x;defPressY=y;defPressButton=button;defPressActive=true;defPressStartNs=System.nanoTime();
    }
    /** Отпустить отложенное нажатие в его же окне; чужое окно не трогаем — нажатие просто отменяется. */
    private void flushDeferredPress(){
        GuiScreen s=defPressScreen;defPressActive=false;defPressScreen=null;
        if(s==null||mc.currentScreen!=s||guiMouseReleasedMethod==null)return;
        try{guiMouseReleasedMethod.invoke(s,Integer.valueOf(defPressX),Integer.valueOf(defPressY),Integer.valueOf(defPressButton));}
        catch(Exception e){warnClickFailure("Recorded GUI release failed",e);}
        if(mc.currentScreen!=s)clearLeftClickBlock();
    }
    private void deliverContainerMove(GuiScreen screen,int cx,int cy,int button){
        if(!(screen instanceof net.minecraft.client.gui.inventory.GuiContainer))return;
        if(!guiClickMethodResolved){resolveGuiClickMethod();MirrorDebug.probe("GuiScreen.mouseClickMove",guiMouseClickMoveMethod!=null,"container drag replay");}
        if(guiMouseClickMoveMethod==null)return;
        int x=MathHelper.clamp(screen.width/2+cx,0,Math.max(0,screen.width-1)),y=MathHelper.clamp(screen.height/2+cy,0,Math.max(0,screen.height-1));
        pointerSnap(x,y);
        long since=!defPressActive||defPressStartNs<=0L?0L:Math.max(0L,(System.nanoTime()-defPressStartNs)/1000000L);
        try{guiMouseClickMoveMethod.invoke(screen,Integer.valueOf(x),Integer.valueOf(y),Integer.valueOf(button),Long.valueOf(since));}
        catch(Exception e){warnClickFailure("Recorded container drag failed",e);}
    }
    private void deliverContainerRelease(GuiScreen screen,int cx,int cy,int button){
        // Сам отпуск и закрывает отложенное нажатие: двойного релиза не будет.
        defPressActive=false;defPressScreen=null;
        if(!(screen instanceof net.minecraft.client.gui.inventory.GuiContainer))return;
        if(!guiClickMethodResolved)resolveGuiClickMethod();
        if(guiMouseReleasedMethod==null)return;
        int x=MathHelper.clamp(screen.width/2+cx,0,Math.max(0,screen.width-1)),y=MathHelper.clamp(screen.height/2+cy,0,Math.max(0,screen.height-1));
        pointerSnap(x,y);
        try{guiMouseReleasedMethod.invoke(screen,Integer.valueOf(x),Integer.valueOf(y),Integer.valueOf(button));}
        catch(Exception e){warnClickFailure("Recorded GUI release failed",e);}
        if(mc.currentScreen!=screen)clearLeftClickBlock();
    }
    private void queueGuiKeys(Frame frame){
        // Общая очередь с кликами: при переполнении теряем новое событие, а не старейшее — порядок уже стоящих не ломается.
        if(guiQueue.size()>=GUI_QUEUE_LIMIT){MirrorDebug.log("GUI","event queue overflow, dropped key event for "+frame.openScreen);return;}
        guiQueue.add(new GuiKeyEvent(frame.openScreen,frame.guiKeys));
    }
    /** Повтор нажатий в окне: печать идёт через keyTyped, действия слотов — напрямую в handleMouseClick, потому что ваниль смотрит на физический курсор и физический Ctrl. */
    private boolean deliverGuiKeys(GuiScreen screen,GuiKeyEvent e){
        if(!guiClickMethodResolved){resolveGuiClickMethod();MirrorDebug.probe("GuiScreen.keyTyped",guiKeyTypedMethod!=null,"GUI key replay");}
        int[] d=e.data;
        while(e.at+6<=d.length){
            int i=e.at,action=d[i],key=d[i+1],aux=d[i+2],flags=d[i+3],cx=d[i+4],cy=d[i+5];
            if(!guiKeyDue(e,action,key,flags,cx))return false;
            if(action==GA_DRAG||action==GA_DRAG_END){
                // Непрерывное значение: из всех созревших точек драга подряд доставляем только последнюю.
                // Промежуточные применялись бы за микросекунды друг от друга: глаз и ухо их не видят,
                // а лесенку громкости (треск) и дёрганье чисел они дают. Порядок и финал сохраняются.
                int j=i;boolean end=(action==GA_DRAG_END);
                // Отпуск закрывает жест: дальше может идти уже следующий драг — его не глотаем.
                if(!end)while(j+12<=d.length){
                    int na=d[j+6];if(na!=GA_DRAG&&na!=GA_DRAG_END)break;
                    if(!guiKeyDue(e,na,d[j+7],d[j+9],d[j+10]))break;
                    j+=6;cx=d[j+4];cy=d[j+5];end=(d[j]==GA_DRAG_END);
                    if(end)break;
                }
                e.at=j+6;deliverSliderDrag(screen,cx,cy,end);
            }else{
                e.at+=6;
                if(action==GA_TYPE){if(guiKeyTypedMethod!=null)try{guiKeyTypedMethod.invoke(screen,Character.valueOf((char)aux),Integer.valueOf(key));}catch(Exception ex){warnClickFailure("Recorded key replay failed",ex);}}
                else if(action==GA_CLOSE){
                    if(screen instanceof net.minecraft.client.gui.inventory.GuiContainer)closeRecordedScreen(screen);
                    else if(guiKeyTypedMethod!=null){try{guiKeyTypedMethod.invoke(screen,Character.valueOf('\0'),Integer.valueOf(1));}catch(Exception ex){warnClickFailure("Recorded Esc replay failed",ex);}}
                    else closeRecordedScreen(screen);
                }
                else if(action==GA_CMOVE)deliverContainerMove(screen,cx,cy,aux);
                else if(action==GA_CRELEASE)deliverContainerRelease(screen,cx,cy,aux);
                else deliverContainerKey(screen,action,aux,(flags&2)!=0,cx,cy);
            }
            if(mc.currentScreen!=screen){clearLeftClickBlock();return true;}
        }
        return true;
    }
    /** Созрело ли событие по его записанной метке времени (с учётом скорости повтора). */
    private boolean guiKeyDue(GuiKeyEvent e,int action,int key,int flags,int cx){
        if((flags&GUI_FLAG_TIMED)==0)return true;
        int due=action==GA_TYPE?cx:key;
        float speed=Math.max(0.25f,config.getPlaybackSpeed());
        due=Math.max(0,Math.round(due/speed));
        long elapsed=(System.nanoTime()-e.startedNs)/1000000L;
        return elapsed>=due;
    }
    private void deliverContainerKey(GuiScreen screen,int action,int aux,boolean ctrl,int cx,int cy){
        if(!(screen instanceof net.minecraft.client.gui.inventory.GuiContainer))return;
        if(!quickMoveResolved)resolveQuickMove();
        if(handleMouseClickMethod==null||slotAtPositionMethod==null||mc.player==null)return;
        try{
            int x=screen.width/2+cx,y=screen.height/2+cy;
            Object slot=slotAtPositionMethod.invoke(screen,Integer.valueOf(x),Integer.valueOf(y));
            if(!(slot instanceof net.minecraft.inventory.Slot))return;
            net.minecraft.inventory.Slot s=(net.minecraft.inventory.Slot)slot;
            if(action==GA_DROP){if(s.getHasStack())handleMouseClickMethod.invoke(screen,s,Integer.valueOf(s.slotNumber),Integer.valueOf(ctrl?1:0),net.minecraft.inventory.ClickType.THROW);}
            else if(action==GA_PICK){if(s.getHasStack())handleMouseClickMethod.invoke(screen,s,Integer.valueOf(s.slotNumber),Integer.valueOf(0),net.minecraft.inventory.ClickType.CLONE);}
            else if(action==GA_SWAP){if(mc.player.inventory.getItemStack().isEmpty())handleMouseClickMethod.invoke(screen,s,Integer.valueOf(s.slotNumber),Integer.valueOf(aux),net.minecraft.inventory.ClickType.SWAP);}
        }catch(Exception e2){warnClickFailure("Recorded container key failed",e2);}
    }
    /** Средний клик в контейнере ваниль определяет через привязку pick-block, поэтому вызываем CLONE напрямую: привязка могла быть переназначена. */
    private boolean deliverClone(GuiScreen screen,int x,int y){
        if(!quickMoveResolved)resolveQuickMove();
        if(handleMouseClickMethod==null||slotAtPositionMethod==null)return false;
        try{
            Object slot=slotAtPositionMethod.invoke(screen,Integer.valueOf(x),Integer.valueOf(y));
            if(!(slot instanceof net.minecraft.inventory.Slot))return false;
            net.minecraft.inventory.Slot s=(net.minecraft.inventory.Slot)slot;
            if(s.getHasStack())handleMouseClickMethod.invoke(screen,s,Integer.valueOf(s.slotNumber),Integer.valueOf(0),net.minecraft.inventory.ClickType.CLONE);
            return true;
        }catch(Exception e){warnClickFailure("Recorded middle click failed",e);return false;}
    }
    /** Закрытие как в ванили: контейнер шлёт пакет на сервер, книга и табличка закрываются локально (табличка при этом отправляет текст в onGuiClosed). */
    private void closeRecordedScreen(GuiScreen screen){
        // Закрытие отменяет отложенный релиз: предмет остаётся на курсоре — как в записи.
        defPressActive=false;defPressScreen=null;
        if(screen instanceof net.minecraft.client.gui.inventory.GuiContainer){if(mc.player!=null)mc.player.closeScreen();}
        else mc.displayGuiScreen(null);
    }
    private int[] flattenGuiKeys(){
        int n=pendingGuiKeys.size();if(n==0)return null;
        int[] flat=new int[n*6];
        for(int i=0;i<n;i++)System.arraycopy(pendingGuiKeys.get(i),0,flat,i*6,6);
        return flat;
    }
    /** Shift-клик в контейнере ваниль определяет по физической клавише, а при повторе она не нажата: без этого предмет не перекладывается, а зависает на курсоре. */
    private boolean deliverQuickMove(GuiScreen screen,int x,int y,int button){
        if(!(screen instanceof net.minecraft.client.gui.inventory.GuiContainer))return false;
        if(!quickMoveResolved){resolveQuickMove();MirrorDebug.probe("GuiContainer.handleMouseClick",handleMouseClickMethod!=null,"shift-click replay");MirrorDebug.probe("GuiContainer.getSlotAtPosition",slotAtPositionMethod!=null,"shift-click slot lookup");}
        if(handleMouseClickMethod==null||slotAtPositionMethod==null)return false;
        try{
            Object slot=slotAtPositionMethod.invoke(screen,Integer.valueOf(x),Integer.valueOf(y));
            if(!(slot instanceof net.minecraft.inventory.Slot))return false;
            int id=((net.minecraft.inventory.Slot)slot).slotNumber;
            handleMouseClickMethod.invoke(screen,slot,Integer.valueOf(id),Integer.valueOf(button),net.minecraft.inventory.ClickType.QUICK_MOVE);
            return true;
        }catch(Exception e){warnClickFailure("Recorded shift-click failed",e);return false;}
    }
    private void resolveQuickMove(){
        quickMoveResolved=true;
        for(Method m:net.minecraft.client.gui.inventory.GuiContainer.class.getDeclaredMethods()){String n=m.getName();Class<?>[] a=m.getParameterTypes();
            if(a.length==4&&a[0]==net.minecraft.inventory.Slot.class&&a[1]==int.class&&a[2]==int.class&&a[3]==net.minecraft.inventory.ClickType.class&&(n.equals("handleMouseClick")||n.equals("func_184098_a")))handleMouseClickMethod=m;
            else if(a.length==2&&a[0]==int.class&&a[1]==int.class&&m.getReturnType()==net.minecraft.inventory.Slot.class&&(n.equals("getSlotAtPosition")||n.equals("func_146975_c")))slotAtPositionMethod=m;}
        try{if(handleMouseClickMethod!=null)handleMouseClickMethod.setAccessible(true);if(slotAtPositionMethod!=null)slotAtPositionMethod.setAccessible(true);}
        catch(Exception e){handleMouseClickMethod=null;slotAtPositionMethod=null;warnClickFailure("Cannot access container click methods",e);}
    }
    /** Чистый повтор состояния экрана: окно, которого в записи в этот момент не было, закрываем сами — иначе интерфейс жителя или сундука висит до конца повтора и глушит остальное. */
    /** Ekrany nastroek, kotorye povtor otkryvaet sam: esli zapisj ih uzhe zakryla, zavisshij ekran dogonjaem prinuditeljno. */
    private boolean settingsScreen(GuiScreen s){return s!=null&&settingsScreen(s.getClass().getName());}
    private boolean settingsScreen(String n){if(n==null||!n.startsWith("net.minecraft."))return false;for(String a:SETTINGS_SCREENS)if(n.endsWith("."+a))return true;return false;}

    private void closeUnexpectedScreen(Frame frame){
        GuiScreen screen=mc.currentScreen;
        boolean container=screen instanceof net.minecraft.client.gui.inventory.GuiContainer;
        boolean closable=container||screen instanceof net.minecraft.client.gui.GuiScreenBook||screen instanceof net.minecraft.client.gui.inventory.GuiEditSign||settingsScreen(screen);
        if(frame==null||screen==null||mc.player==null||!guiQueue.isEmpty()||!closable){screenMismatchTicks=0;return;}
        String name=screen.getClass().getName();
        // Закрываем только ванильные экраны: окна чужих модов (и свои собственные) мод не понимает и не трогает.
        if(!name.startsWith("net.minecraft.")){screenMismatchTicks=0;return;}
        // Zapis sama vnutri nastroek: perehod mezhdu ih ekranami — ne musor. Bez etogo menju pauzy otkryvalos i zakryvalos v cikle.
        if(settingsScreen(screen)&&frame.hasScreenState&&settingsScreen(frame.openScreen)){screenMismatchTicks=0;return;}
        if(frame.hasScreenState?name.equals(frame.openScreen):screenClickAhead(name)){screenMismatchTicks=0;return;}
        if(++screenMismatchTicks<SCREEN_CLOSE_DELAY)return;
        screenMismatchTicks=0;MirrorDebug.log("GUI","closing screen absent from the recording: "+name);
        defPressActive=false;defPressScreen=null;
        if(container)mc.player.closeScreen();else mc.displayGuiScreen(null);clearLeftClickBlock();
    }
    /** Записи формата 2 открытый экран не хранят: там ориентир — записанные клики этого же окна впереди. */
    private boolean screenClickAhead(String name){
        int count=manager.copyPlaybackFramesAhead(lookAhead);
        for(int i=0;i<count;i++){Frame f=lookAhead[i];if(f!=null&&f.guiClick&&name.equals(f.guiScreen))return true;}
        return false;
    }
    /** Взмах ровно как в ваниле: одно нажатие — один взмах; удержание обрабатывает сама игра (копание идёт со своим ритмом). */
    /** Свои пять клавиш мода в запись не идут: они управляют самим модом. */
    private boolean ownKey(int code){
        try{MirrorRecorder mod=MirrorRecorder.getInstance();if(mod==null)return false;
            return code==mod.getKeyRecord().getKeyCode()||code==mod.getKeyPlay().getKeyCode()||code==mod.getKeyLoop().getKeyCode()||code==mod.getKeyStop().getKeyCode()||code==mod.getKeyGui().getKeyCode();
        }catch(Exception e){return false;}
    }
    private static boolean systemKey(int code){return code==WIN_LEFT||code==WIN_RIGHT||code==WIN_MENU;}
    /** Все клавиши тика: код и состояние. Массив копится только если есть что копить. */
    private int[] collectKeyEvents(){
        if(!config.isRecordAllKeys()||keyLog.isEmpty())return null;
        int cap=Frame.MAX_KEY_EVENTS*2;int[] out=new int[cap];int n=0;
        while(!keyLog.isEmpty()&&n<cap-1){int[] e=keyLog.poll();if(e==null)continue;out[n++]=e[0];out[n++]=e[1];}
        if(!keyLog.isEmpty()){MirrorDebug.log("INPUT","key event overflow: "+keyLog.size()+" events dropped");keyLog.clear();}
        if(n==0)return null;
        int[] exact=new int[n];System.arraycopy(out,0,exact,0,n);return exact;
    }
    /** Клавиши, которыми повтор управляет сам: движение, клики, выброс, слоты, инвентарь, чат.
     *  В записи они остаются, но второй раз их нажимать нельзя: будет двойной выброс предмета и двойное переключение слота. */
    private boolean drivenKey(int code){
        net.minecraft.client.settings.GameSettings gs=mc.gameSettings;if(gs==null)return false;
        try{
            if(code==gs.keyBindAttack.getKeyCode()||code==gs.keyBindUseItem.getKeyCode()
             ||code==gs.keyBindForward.getKeyCode()||code==gs.keyBindBack.getKeyCode()
             ||code==gs.keyBindLeft.getKeyCode()||code==gs.keyBindRight.getKeyCode()
             ||code==gs.keyBindJump.getKeyCode()||code==gs.keyBindSneak.getKeyCode()||code==gs.keyBindSprint.getKeyCode()
             ||code==gs.keyBindDrop.getKeyCode()||code==gs.keyBindPickBlock.getKeyCode()||code==gs.keyBindSwapHands.getKeyCode()
             ||code==gs.keyBindInventory.getKeyCode()||code==gs.keyBindChat.getKeyCode()||code==gs.keyBindCommand.getKeyCode())return true;
            if(gs.keyBindsHotbar!=null)for(int i=0;i<gs.keyBindsHotbar.length;i++)if(gs.keyBindsHotbar[i]!=null&&code==gs.keyBindsHotbar[i].getKeyCode())return true;
        }catch(Exception e){return true;}
        return false;
    }
    /** Повтор клавиш: ставим состояние и помним, что зажали сами — чтобы отпустить при остановке. */
    private void applyKeyEvents(Frame frame){
        if(frame==null||frame.keyEvents==null||frame.keyEvents.length<2)return;
        for(int i=0;i+1<frame.keyEvents.length;i+=2){
            int code=frame.keyEvents[i];boolean down=frame.keyEvents[i+1]!=0;
            if(code<=0||systemKey(code)||ownKey(code)||drivenKey(code))continue;
            try{KeyBinding.setKeyBindState(code,down);if(down){KeyBinding.onTick(code);replayedKeys.add(Integer.valueOf(code));}else replayedKeys.remove(Integer.valueOf(code));}catch(Exception e){}
        }
    }
    private void releaseReplayedKeys(){
        if(replayedKeys.isEmpty())return;
        for(Integer code:replayedKeys){if(code!=null)try{KeyBinding.setKeyBindState(code.intValue(),false);}catch(Exception e){}}
        replayedKeys.clear();
    }
    /** Курсор записи в пикселях интерфейса: от размера окна не зависит. */
    private boolean cursorOn(){return mc.currentScreen!=null;}
    private float cursorPxX(){
        try{net.minecraft.client.gui.ScaledResolution r=new net.minecraft.client.gui.ScaledResolution(mc);
            return (float)(Mouse.getX()*r.getScaledWidth()/(double)Math.max(1,mc.displayWidth));}catch(Exception e){return 0f;}
    }
    private float cursorPxY(){
        try{net.minecraft.client.gui.ScaledResolution r=new net.minecraft.client.gui.ScaledResolution(mc);
            return (float)(r.getScaledHeight()-Mouse.getY()*r.getScaledHeight()/(double)Math.max(1,mc.displayHeight)-1);}catch(Exception e){return 0f;}
    }
    private void setPointer(Frame frame){if(frame==null||!frame.hasCursor){pointerOn=false;return;}long now=System.nanoTime();float px,py;if(!pointerOn){px=frame.curX;py=frame.curY;pointerLastNs=0L;}else{px=pointerLerpX();py=pointerLerpY();}long dt=pointerLastNs==0L?50000000L:now-pointerLastNs;if(dt<25000000L)dt=25000000L;if(dt>200000000L)dt=200000000L;pointerFromX=px;pointerFromY=py;pointerToX=frame.curX;pointerToY=frame.curY;pointerStartNs=now;pointerDurNs=dt;pointerLastNs=now;pointerX=frame.curX;pointerY=frame.curY;pointerOn=true;}
    private float pointerT(){if(pointerDurNs<=0L)return 1f;float t=(float)((System.nanoTime()-pointerStartNs)/(double)pointerDurNs);return t<0f?0f:(t>1f?1f:t);}
    private float pointerLerpX(){return pointerFromX+(pointerToX-pointerFromX)*pointerT();}
    private float pointerLerpY(){return pointerFromY+(pointerToY-pointerFromY)*pointerT();}
    /** Курсор рисуем сами и в цвете мода: видно, что это не основной курсор системы. */
    /** V moment dostavlenija klika risovannyj kursor stoit rovno v tochke klika. */
    private void pointerSnap(int x,int y){
        pointerFromX=x;pointerFromY=y;pointerToX=x;pointerToY=y;pointerX=x;pointerY=y;
        pointerStartNs=System.nanoTime();pointerDurNs=50000000L;pointerOn=true;
    }

    private void drawPointer(){
        if(!pointerOn||!config.isShowPointer())return;
        int x=(int)(pointerLerpX()+0.5f),y=(int)(pointerLerpY()+0.5f);boolean depthWas=false;
        try{depthWas=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);net.minecraft.client.renderer.GlStateManager.disableDepth();
            net.minecraft.client.gui.Gui.drawRect(x-5,y-1,x+6,y+2,POINTER_EDGE);net.minecraft.client.gui.Gui.drawRect(x-1,y-5,x+2,y+6,POINTER_EDGE);
            net.minecraft.client.gui.Gui.drawRect(x-4,y,x+5,y+1,POINTER_BODY);net.minecraft.client.gui.Gui.drawRect(x,y-4,x+1,y+5,POINTER_BODY);
        }catch(Exception e){}finally{try{if(depthWas)net.minecraft.client.renderer.GlStateManager.enableDepth();else net.minecraft.client.renderer.GlStateManager.disableDepth();}catch(Exception e){}}
    }
    @SubscribeEvent public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event){if(manager.isPlaying())drawPointer();}
    /** Печать по одной букве в настоящем окне чата. Не вышло — сообщение уйдёт как раньше, без потери. */
    private void startChatTyping(String msg){
        if(msg==null||msg.isEmpty())return;
        typeText=msg;typeAt=0;typeNextNs=0L;typeWait=CHAT_OPEN_WAIT;
        MirrorDebug.log("CHAT","visible typing started: "+msg);
        openChatScreen();
    }
    private void failTyping(String msg){typeText=null;if(mc.player!=null&&msg!=null&&!msg.isEmpty())mc.player.sendChatMessage(msg);}
    private void tickChatTyping(boolean tickWait){
        if(typeText==null)return;
        if(!manager.isPlaying()){typeText=null;typeNextNs=0L;return;}
        if(!(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat)){
            if(typeWait>0){if(tickWait)typeWait--;openChatScreen();return;}
            MirrorDebug.log("CHAT","chat screen did not open in "+CHAT_OPEN_WAIT+" ticks");warnVisibleChat();failTyping(typeText);return;
        }
        long now=System.nanoTime();if(typeNextNs>0L&&now<typeNextNs)return;
        if(typeAt<typeText.length()){
            typeWait=CHAT_OPEN_WAIT;char tc=typeText.charAt(typeAt);typeAt++;
            if(!typeChar(tc)&&!setChatText(typeText.substring(0,typeAt))){MirrorDebug.log("CHAT","chat input field is not reachable");warnVisibleChat();failTyping(typeText);return;}
            typeNextNs=now+CHAT_FALLBACK_CHAR_NS;return;
        }
        String done=typeText;typeText=null;typeNextNs=0L;
        if(!sendChatKey()&&mc.player!=null)mc.player.sendChatMessage(done);
        if(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat)mc.displayGuiScreen(null);clearLeftClickBlock();
    }
    /** Поле ввода чата: имя поля разное в obf и dev, поэтому пробуем оба имени. */
    // Печать идёт тем же путём, что и у игрока: GuiChat.keyTyped -> поле ввода.
    // Так работают курсор в строке, история и автодополнение.
    private boolean typeChar(char c){
        try{GuiScreen sc=mc.currentScreen;if(!(sc instanceof net.minecraft.client.gui.GuiChat))return false;
            if(!guiClickMethodResolved)resolveGuiClickMethod();
            if(guiKeyTypedMethod==null)return false;
            guiKeyTypedMethod.invoke(sc,Character.valueOf(c),Integer.valueOf(0));return true;
        }catch(Exception e){return false;}
    }
    // Enter в чате: ваниль сама отправит строку, добавит её в историю и закроет окно.
    private boolean sendChatKey(){
        try{GuiScreen sc=mc.currentScreen;if(!(sc instanceof net.minecraft.client.gui.GuiChat))return false;
            if(!guiClickMethodResolved)resolveGuiClickMethod();
            if(guiKeyTypedMethod==null)return false;
            guiKeyTypedMethod.invoke(sc,Character.valueOf('\r'),Integer.valueOf(28));
            return !(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat);
        }catch(Exception e){return false;}
    }
    // Списки в окнах (управление, язык, ресурспаки) сами читают LWJGL-мышь в handleMouseInput,
    // а не получают клик из GuiScreen.mouseClicked. Без этого клик по строке списка терялся.
    private void deliverListClick(GuiScreen screen,int x,int y,int button){
        if(screen==null||button!=0)return;
        Class<?> slotType;try{slotType=Class.forName("net.minecraft.client.gui.GuiSlot");}catch(Exception e){return;}
        Class<?> c=screen.getClass();
        for(int depth=0;c!=null&&depth<3;c=c.getSuperclass(),depth++){
            java.lang.reflect.Field[] fs;try{fs=c.getDeclaredFields();}catch(Exception e){continue;}
            for(java.lang.reflect.Field f:fs){
                Object o;try{f.setAccessible(true);o=f.get(screen);}catch(Exception e){continue;}
                if(o==null||!slotType.isInstance(o))continue;
                if(listClickExtended(o,x,y,button))return;
                if(listClickSlot(o,x,y))return;
            }
        }
    }
    private boolean listClickExtended(Object list,int x,int y,int button){
        try{java.lang.reflect.Method m=findMethod(list.getClass(),new String[]{"mouseClicked","func_148179_a"},
                new Class<?>[]{int.class,int.class,int.class});
            if(m==null)return false;
            Object r=m.invoke(list,Integer.valueOf(x),Integer.valueOf(y),Integer.valueOf(button));
            return (r instanceof Boolean)&&((Boolean)r).booleanValue();
        }catch(Exception e){return false;}
    }
    private boolean listClickSlot(Object list,int x,int y){
        try{java.lang.reflect.Method idx=findMethod(list.getClass(),
                new String[]{"getSlotIndexFromScreenCoords","func_148124_c"},new Class<?>[]{int.class,int.class});
            if(idx==null)return false;
            Object r=idx.invoke(list,Integer.valueOf(x),Integer.valueOf(y));
            if(!(r instanceof Integer))return false;
            int i=((Integer)r).intValue();if(i<0)return false;
            java.lang.reflect.Method el=findMethod(list.getClass(),new String[]{"elementClicked","func_148144_a"},
                new Class<?>[]{int.class,boolean.class,int.class,int.class});
            if(el==null)return false;
            el.invoke(list,Integer.valueOf(i),Boolean.FALSE,Integer.valueOf(x),Integer.valueOf(y));return true;
        }catch(Exception e){return false;}
    }
    private java.lang.reflect.Method findMethod(Class<?> type,String[] names,Class<?>[] args){
        for(Class<?> c=type;c!=null;c=c.getSuperclass()){
            java.lang.reflect.Method[] ms;try{ms=c.getDeclaredMethods();}catch(Exception e){continue;}
            for(java.lang.reflect.Method m:ms){
                Class<?>[] p=m.getParameterTypes();if(p.length!=args.length)continue;
                boolean same=true;for(int i=0;i<p.length;i++)if(p[i]!=args[i]){same=false;break;}
                if(!same)continue;
                for(String n:names)if(m.getName().equals(n)){try{m.setAccessible(true);}catch(Exception e){return null;}return m;}
            }
        }
        return null;
    }
    private void openChatScreen(){
        try{if(!(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat))mc.displayGuiScreen(new net.minecraft.client.gui.GuiChat());}catch(Exception e){}
    }

    /** Zhivoj chat ne poluchilsya: govorim ob etom odin raz za zapusk, chtoby otkaz ne byl nezametnym. */
    private void warnVisibleChat(){
        if(chatWarned)return;chatWarned=true;
        sendMsg(L("§eЖивой чат недоступен: сообщение отправлено обычным способом.","§eVisible chat is unavailable: the message was sent the plain way.","§eЖивий чат недоступний: повідомлення надіслано звичайним способом.","§eSichtbarer Chat ist nicht verfügbar: Die Nachricht wurde normal gesendet.","§eWidoczny czat jest niedostępny: wiadomość wysłano zwykłą metodą."));
    }

    /** Nachaljnyj tekst polja chata (slesh ot klavishi «/») zapisivaem kak obychnye bukvy. */
    private void injectChatPrefix(){
        boolean chatNow=mc.currentScreen instanceof net.minecraft.client.gui.GuiChat;
        if(chatNow&&!prevChatOpen){
            String init="";
            try{
                if(chatField==null){for(String nm:CHAT_FIELDS){try{java.lang.reflect.Field f=net.minecraft.client.gui.GuiChat.class.getDeclaredField(nm);f.setAccessible(true);chatField=f;break;}catch(Exception e){}}}
                if(chatField!=null){Object o=chatField.get(mc.currentScreen);if(o instanceof net.minecraft.client.gui.GuiTextField)init=((net.minecraft.client.gui.GuiTextField)o).getText();}
            }catch(Exception e){init="";}
            // Marker «chat otkryt»: povtor otkryvaet okno rovno na etom kadre, dazhe esli pervuju bukvu nazhali pozzhe.
            pendingGuiKeyScreen=net.minecraft.client.gui.GuiChat.class.getName();if(pendingGuiKeys.size()<GUI_KEY_LIMIT)pendingGuiKeys.add(new int[]{GA_TYPE,0,0,GUI_FLAG_TIMED,0,0});
            if(init!=null)for(int i=0;i<init.length()&&pendingGuiKeys.size()<GUI_KEY_LIMIT;i++)pendingGuiKeys.add(new int[]{GA_TYPE,0,init.charAt(i),GUI_FLAG_TIMED,0,0});
        }
        prevChatOpen=chatNow;
    }

    /** Zhivoj chat ne startuet poverh chuzhogo okna, no uzhe otkrytyj chat — ne pomeha. */
    private boolean chatBlockingScreen(){GuiScreen s=mc.currentScreen;return s!=null&&!(s instanceof net.minecraft.client.gui.GuiChat);}

    private boolean setChatText(String text){
        try{
            GuiScreen sc=mc.currentScreen;if(!(sc instanceof net.minecraft.client.gui.GuiChat))return false;
            if(chatField==null){for(String nm:CHAT_FIELDS){try{java.lang.reflect.Field f=net.minecraft.client.gui.GuiChat.class.getDeclaredField(nm);f.setAccessible(true);chatField=f;break;}catch(Exception e){}}}
            if(chatField==null)return false;
            Object o=chatField.get(sc);
            if(!(o instanceof net.minecraft.client.gui.GuiTextField))return false;
            ((net.minecraft.client.gui.GuiTextField)o).setText(text);return true;
        }catch(Exception e){return false;}
    }
    /** Потеря фокуса окна (Alt+Tab, Win): отжимаем клавиши, но только когда мод не работает. */
    private void focusGuard(){
        boolean active=true;try{active=org.lwjgl.opengl.Display.isActive();}catch(Exception e){active=true;}
        if(active){lostFocus=false;return;}
        if(lostFocus)return;
        lostFocus=true;
        if(!manager.isPlaying()&&!manager.isRecording())try{KeyBinding.unPressAllKeys();}catch(Exception e){}
    }
    /** Vec3d в 1.12.2 именует поля разно в obf и dev, поэтому берём по имени с перебором вариантов. */
    private static java.lang.reflect.Field[] VEC_FIELDS=null;
    private static final String[][] VEC_NAMES={{"x","field_72450_a","xCoord"},{"y","field_72448_b","yCoord"},{"z","field_72449_c","zCoord"}};
    private static double vecPart(net.minecraft.util.math.Vec3d v,int i){
        if(v==null||i<0||i>2)return 0d;
        try{
            if(VEC_FIELDS==null){
                java.lang.reflect.Field[] f=new java.lang.reflect.Field[3];
                for(int k=0;k<3;k++)for(String nm:VEC_NAMES[k]){try{java.lang.reflect.Field ff=net.minecraft.util.math.Vec3d.class.getDeclaredField(nm);ff.setAccessible(true);f[k]=ff;break;}catch(Exception e){}}
                VEC_FIELDS=f;
            }
            if(VEC_FIELDS[i]==null)return 0d;
            Object o=VEC_FIELDS[i].get(v);
            return o instanceof Number?((Number)o).doubleValue():0d;
        }catch(Exception e){return 0d;}
    }
    /** Цель тика: какой блок и какую его сторону видел игрок. */
    private net.minecraft.util.math.RayTraceResult aim(){
        try{net.minecraft.util.math.RayTraceResult r=mc.objectMouseOver;
            if(r==null||r.typeOfHit!=net.minecraft.util.math.RayTraceResult.Type.BLOCK||r.getBlockPos()==null)return null;
            return r;}catch(Exception e){return null;}
    }
    private boolean tgt(){return aim()!=null;}
    private int tgtX(){net.minecraft.util.math.RayTraceResult r=aim();return r==null?0:r.getBlockPos().getX();}
    private int tgtY(){net.minecraft.util.math.RayTraceResult r=aim();return r==null?0:r.getBlockPos().getY();}
    private int tgtZ(){net.minecraft.util.math.RayTraceResult r=aim();return r==null?0:r.getBlockPos().getZ();}
    private int tgtFace(){net.minecraft.util.math.RayTraceResult r=aim();return r==null||r.sideHit==null?-1:r.sideHit.getIndex();}
    private float tgtHitX(){net.minecraft.util.math.RayTraceResult r=aim();return r==null?0f:(float)vecPart(r.hitVec,0);}
    private float tgtHitY(){net.minecraft.util.math.RayTraceResult r=aim();return r==null?0f:(float)vecPart(r.hitVec,1);}
    private float tgtHitZ(){net.minecraft.util.math.RayTraceResult r=aim();return r==null?0f:(float)vecPart(r.hitVec,2);}
    /** Точная кладка: блок встаёт в записанную точку, а не туда, куда смотрит камера.
     *  Далеко, нет цели или сервер отказал — возвращаем false, и клик идёт обычным путём. */
    private boolean placeExact(Frame frame){
        if(!config.isExactPlacement()||frame==null||!frame.hasTarget)return false;
        if(frame.tgtFace<0||frame.tgtFace>5)return false;
        if(mc.player==null||mc.world==null||mc.playerController==null)return false;
        try{
            net.minecraft.util.math.BlockPos pos=new net.minecraft.util.math.BlockPos(frame.tgtX,frame.tgtY,frame.tgtZ);
            if(mc.player.getDistanceSq(pos)>36.0D)return false;
            net.minecraft.util.EnumFacing face=net.minecraft.util.EnumFacing.VALUES[frame.tgtFace];
            net.minecraft.util.math.Vec3d hit=new net.minecraft.util.math.Vec3d(frame.hitX,frame.hitY,frame.hitZ);
            net.minecraft.util.EnumActionResult res=mc.playerController.processRightClickBlock(mc.player,mc.world,pos,face,hit,net.minecraft.util.EnumHand.MAIN_HAND);
            if(res!=net.minecraft.util.EnumActionResult.SUCCESS)return false;
            mc.player.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
            return true;
        }catch(Exception e){return false;}
    }
    /** Экран настроек из записи открываем сами: иначе курсору негде нажимать.
     *  В одиночной игре пауза останавливает мир и повтор встал бы насовсем, поэтому там не открываем. */
    private void openRecordedMenu(Frame frame){
        if(frame==null||frame.openScreen==null||frame.openScreen.isEmpty())return;
        if(mc.currentScreen!=null||mc.player==null)return;
        // Zhivoj chat iz zapisi: okno otkryvaetsja po kadram, bukvy i strelki prihodjat zapisannymi klavishami.
        if(frame.openScreen.endsWith(".GuiChat")){
            if(config.isVisibleChat()&&typeText==null&&frame.guiKeys.length>0){try{mc.displayGuiScreen(new net.minecraft.client.gui.GuiChat());chatKeyReplay=true;}catch(Exception e){}}
            return;
        }
        boolean allowed=false;
        for(String a:SETTINGS_SCREENS)if(frame.openScreen.endsWith("."+a)){allowed=true;break;}
        if(!allowed)return;
        try{
            if(mc.isSingleplayer()&&(mc.getIntegratedServer()==null||!mc.getIntegratedServer().getPublic()))return;
            mc.displayInGameMenu();
        }catch(Exception e){}
    }
    private void applyHold(KeyBinding key,boolean down){if(key!=null)KeyBinding.setKeyBindState(key.getKeyCode(),down);}
    private void forceReleaseClicks(){if(mc.gameSettings==null)return;restorePhysical(mc.gameSettings.keyBindAttack);restorePhysical(mc.gameSettings.keyBindUseItem);}
    private void pulse(KeyBinding key,boolean down,boolean was){if(key==null||!down||was)return;KeyBinding.onTick(key.getKeyCode());}
    /** Дискретный клик вызываем нап��ямую: очередь нажатий KeyBinding стирается unPressAllKeys при открытии паузы/инвентаря, а на сервере мир в этот момент продолжает тикать. Прицел обновляем перед к��иком, чтобы попадание соответствовало текущему кадру. */
    private void fireRecordedClick(Frame frame,KeyBinding key,boolean right){
        drainPressQueue(key);if(frame.guiClick&&replayableScreen(frame.guiScreen)){queueGuiClick(frame,right?1:0);return;}
        // Свою кладку притормаживаем так же, как игра: иначе блоки уйдут быстрее ванильного темпа.
        if(right&&placeExact(frame)){
            if(!clickMethodsResolved)resolveClickMethods();
            if(rightClickDelayField!=null)try{rightClickDelayField.setInt(mc,4);}catch(Exception e){}
            useTimerAlign=true;return;
        }
        if(mc.player==null||mc.world==null)return;
        if(!clickMethodsResolved){resolveClickMethods();MirrorDebug.probe("Minecraft.clickMouse",clickMouseMethod!=null,"world click replay");MirrorDebug.probe("Minecraft.rightClickMouse",rightClickMouseMethod!=null,"world use replay");MirrorDebug.probe("Minecraft.leftClickCounter",leftClickCounterField!=null,"left click unblock");MirrorDebug.probe("Minecraft.sendClickBlockToController",sendClickBlockMethod!=null,"held attack with open screen");}
        if(mc.entityRenderer!=null)mc.entityRenderer.getMouseOver(1.0F);
        if(!right&&leftClickCounterField!=null)try{leftClickCounterField.setInt(mc,0);}catch(Exception e){warnClickFailure("Cannot unblock recorded left click",e);}
        Method method=right?rightClickMouseMethod:clickMouseMethod;
        if(method!=null)try{method.invoke(mc);if(right)useTimerAlign=true;}catch(Exception e){warnClickFailure("Recorded world click failed",e);}
    }

    private void resolveClickMethods(){
        clickMethodsResolved=true;
        for(Method m:Minecraft.class.getDeclaredMethods()){String n=m.getName();Class<?>[] a=m.getParameterTypes();
            if(a.length==0&&(n.equals("clickMouse")||n.equals("func_147116_af")))clickMouseMethod=m;
            else if(a.length==0&&(n.equals("rightClickMouse")||n.equals("func_147121_ag")))rightClickMouseMethod=m;
            else if(a.length==1&&a[0]==boolean.class&&(n.equals("sendClickBlockToController")||n.equals("func_147115_a")))sendClickBlockMethod=m;}
        for(Field f:Minecraft.class.getDeclaredFields()){String n=f.getName();if(f.getType()!=int.class)continue;
            if(n.equals("leftClickCounter")||n.equals("field_71429_W"))leftClickCounterField=f;
            else if(n.equals("rightClickDelayTimer")||n.equals("field_71467_ac"))rightClickDelayField=f;}
        try{if(clickMouseMethod!=null)clickMouseMethod.setAccessible(true);if(rightClickMouseMethod!=null)rightClickMouseMethod.setAccessible(true);if(sendClickBlockMethod!=null)sendClickBlockMethod.setAccessible(true);if(leftClickCounterField!=null)leftClickCounterField.setAccessible(true);if(rightClickDelayField!=null)rightClickDelayField.setAccessible(true);}catch(Exception e){clickMouseMethod=null;rightClickMouseMethod=null;sendClickBlockMethod=null;leftClickCounterField=null;rightClickDelayField=null;warnClickFailure("Cannot access Minecraft click methods",e);}
    }
    /** Открытый экран глушит ванильное ломание блоков (проверка currentScreen). На сервере мир тикает, поэтому повторяем вызов напрямую. */
    private void driveScreenAttackHold(){
        if(mc.isGamePaused()||mc.player==null||mc.world==null)return;
        if(!clickMethodsResolved)resolveClickMethods();
        if(leftClickCounterField!=null)try{leftClickCounterField.setInt(mc,0);}catch(Exception e){warnClickFailure("Cannot unblock held left click",e);}
        if(mc.entityRenderer!=null)mc.entityRenderer.getMouseOver(1.0F);
        if(sendClickBlockMethod!=null)try{sendClickBlockMethod.invoke(mc,Boolean.TRUE);}catch(Exception e){warnClickFailure("Held attack with open screen failed",e);}
    }
    /** Открытый экран глушит ванильный повтор ПКМ через контекст клавиши. Повторяем тот же ритм вручную: вызов, когда таймер задержки дошёл до ��уля и рука свободна. */
    private void driveScreenUseHold(){
        EntityPlayerSP player=mc.player;if(mc.isGamePaused()||player==null||mc.world==null||player.isHandActive())return;
        if(!clickMethodsResolved)resolveClickMethods();
        int delay;
        if(rightClickDelayField!=null){try{delay=rightClickDelayField.getInt(mc);}catch(Exception e){delay=useHoldFallbackDelay;}}
        else delay=useHoldFallbackDelay;
        if(delay>0)return;
        if(mc.entityRenderer!=null)mc.entityRenderer.getMouseOver(1.0F);
        if(rightClickMouseMethod!=null){try{rightClickMouseMethod.invoke(mc);useHoldFallbackDelay=4;}catch(Exception e){warnClickFailure("Held use with open screen failed",e);}}
    }
    private void resolveGuiClickMethod(){guiClickMethodResolved=true;
        for(Method m:GuiScreen.class.getDeclaredMethods()){String n=m.getName();Class<?>[] a=m.getParameterTypes();
            if(a.length!=3||a[0]!=int.class||a[1]!=int.class||a[2]!=int.class)continue;
            if(n.equals("mouseClicked")||n.equals("func_73864_a"))guiMouseClickedMethod=m;
            else if(n.equals("mouseReleased")||n.equals("func_146286_i"))guiMouseReleasedMethod=m;
        }
        for(Method m:GuiScreen.class.getDeclaredMethods()){String n=m.getName();Class<?>[] a=m.getParameterTypes();
            if(a.length==2&&a[0]==char.class&&a[1]==int.class&&(n.equals("keyTyped")||n.equals("func_73869_a")))guiKeyTypedMethod=m;}
        for(Method m:GuiScreen.class.getDeclaredMethods()){String n=m.getName();Class<?>[] a=m.getParameterTypes();
            if(a.length==4&&a[0]==int.class&&a[1]==int.class&&a[2]==int.class&&a[3]==long.class){if(n.equals("mouseClickMove")||n.equals("func_146273_a"))guiMouseClickMoveMethod=m;else if(guiMouseClickMoveMethod==null)guiMouseClickMoveMethod=m;}}
        try{if(guiMouseClickedMethod!=null)guiMouseClickedMethod.setAccessible(true);if(guiMouseReleasedMethod!=null)guiMouseReleasedMethod.setAccessible(true);if(guiKeyTypedMethod!=null)guiKeyTypedMethod.setAccessible(true);if(guiMouseClickMoveMethod!=null)guiMouseClickMoveMethod.setAccessible(true);}
        catch(Exception e){guiMouseClickedMethod=null;guiMouseReleasedMethod=null;guiKeyTypedMethod=null;guiMouseClickMoveMethod=null;warnClickFailure("Cannot access GuiScreen click methods",e);}
    }
    private void warnClickFailure(String text,Exception e){MirrorDebug.log("CLICK","failure: "+text+" ("+(e==null?"unknown":e.toString())+")");if(!clickMethodWarning){clickMethodWarning=true;LOG.error(text,e);}}
    private void handleRecord(EntityPlayerSP player){
        long idx=manager.getRecordingTickCounter();boolean jump=false;float fwd=0,str=0;boolean sneak=false;
        if(player.movementInput!=null&&mc.gameSettings!=null){
            jump=player.movementInput.jump;sneak=player.movementInput.sneak;
            if(true){
                fwd=(mc.gameSettings.keyBindForward.isKeyDown()?1f:0f)-(mc.gameSettings.keyBindBack.isKeyDown()?1f:0f);
                str=(mc.gameSettings.keyBindLeft.isKeyDown()?1f:0f)-(mc.gameSettings.keyBindRight.isKeyDown()?1f:0f);
            }
        }
        String chat=pendingChatMessage;pendingChatMessage=null;
        boolean sprint=player.isSprinting();
        boolean lmb=manager.getLeftClickState();int lmbN=lmb?Math.max(1,manager.getLeftClickCount()):0;
        boolean rmb=manager.getRightClickState();int rmbN=rmb?Math.max(1,manager.getRightClickCount()):0;boolean guiClick=(lmb||rmb||pendingGuiButton==2)&&pendingGuiClick;int guiBtn=guiClick?pendingGuiButton:0;injectChatPrefix();int[] guiKeys=flattenGuiKeys();float guiX=pendingGuiX,guiY=pendingGuiY;String guiScreen=pendingGuiScreen;boolean guiCenter=pendingGuiCenter;int guiCX=pendingGuiCX,guiCY=pendingGuiCY;boolean guiShift=guiClick&&pendingGuiShift;String openScreen=openScreenName();if((openScreen==null||openScreen.isEmpty())&&guiKeys!=null&&guiKeys.length>0)openScreen=pendingGuiKeyScreen;
        // Порядок кликов забираем всегда, даже когда запись кликов выключена: иначе он утечёт в следующие кадры.
        int[] clickOrder=manager.consumeClickSequence();
        if(idx<=0){keyLog.clear();recRotInit=false;prevChatOpen=mc.currentScreen instanceof net.minecraft.client.gui.GuiChat;pendingKeyMask=0;pendingGuiClick=false;pendingGuiCenter=false;pendingGuiShift=false;pendingGuiScreen=null;pendingGuiKeyScreen=null;pendingGuiButton=0;pendingGuiKeys.clear();recDragButton=-1;recDragScreen=null;pendingDropAll=false;}
        // Окно драга тихо закрылось (сервер, хоткей): неснятый драг дальше не пишется.
        if(recDragScreen!=null){String cur=openScreenName();if(cur==null||!cur.equals(recDragScreen)){recDragButton=-1;recDragScreen=null;}}
        int mask=0;net.minecraft.client.settings.GameSettings gs=mc.gameSettings;
        if(gs!=null){if(gs.keyBindAttack.isKeyDown()||lmb)mask|=Frame.K_ATTACK;if(gs.keyBindUseItem.isKeyDown()||rmb)mask|=Frame.K_USE;if(gs.keyBindForward.isKeyDown())mask|=Frame.K_FORWARD;if(gs.keyBindBack.isKeyDown())mask|=Frame.K_BACK;if(gs.keyBindLeft.isKeyDown())mask|=Frame.K_LEFT;if(gs.keyBindRight.isKeyDown())mask|=Frame.K_RIGHT;if(gs.keyBindJump.isKeyDown())mask|=Frame.K_JUMP;if(gs.keyBindSneak.isKeyDown())mask|=Frame.K_SNEAK;if(gs.keyBindSprint.isKeyDown())mask|=Frame.K_SPRINT;if(gs.keyBindDrop.isKeyDown())mask|=Frame.K_DROP;if(gs.keyBindSwapHands.isKeyDown())mask|=Frame.K_SWAP;if(gs.keyBindInventory.isKeyDown())mask|=Frame.K_INVENTORY;if(gs.keyBindPickBlock.isKeyDown())mask|=Frame.K_PICK;}mask|=pendingKeyMask;pendingKeyMask=0;
        boolean dropAll=(mask&Frame.K_DROP)!=0&&(pendingDropAll||GuiScreen.isCtrlKeyDown());pendingDropAll=false;
        int hotbar=player.inventory!=null?MathHelper.clamp(player.inventory.currentItem,0,8):-1;
        float dy=0f,dp=0f;if(recRotInit){dy=MathHelper.wrapDegrees(player.rotationYaw-lastRecYaw);dp=player.rotationPitch-lastRecPitch;}lastRecYaw=player.rotationYaw;lastRecPitch=player.rotationPitch;recRotInit=true;
        boolean worldReset=worldResetPending;worldResetPending=false;
        Frame frame=Frame.builder().x(player.posX).y(player.posY).z(player.posZ).yaw(player.rotationYaw).pitch(player.rotationPitch)
            .motionX(player.motionX).motionY(player.motionY).motionZ(player.motionZ)
            .onGround(player.onGround).elytra(player.isElytraFlying()).sneak(sneak).sprint(sprint).leftClick(lmb).rightClick(rmb).attackClicks(lmbN).useClicks(rmbN).clickSeq(clickOrder).jump(jump).keyEvents(collectKeyEvents()).cursor(cursorOn(),cursorPxX(),cursorPxY()).target(tgt(),tgtX(),tgtY(),tgtZ(),tgtFace(),tgtHitX(),tgtHitY(),tgtHitZ())
            .moveForward(fwd).moveStrafe(str).keyMask(mask).hotbarSlot(hotbar).dYaw(dy).dPitch(dp).guiClick(guiClick).guiX(guiX).guiY(guiY).guiScreen(guiScreen).guiCenter(guiClick&&guiCenter,guiCX,guiCY).guiShift(guiShift).guiButton(guiBtn).guiKeys(guiKeys).openScreen(openScreen).screenState(true).dropAll(dropAll).worldReset(worldReset).chatMessage(chat).tickIndex(idx).build();
        manager.setLeftClickState(false);manager.setRightClickState(false);pendingGuiClick=false;pendingGuiCenter=false;pendingGuiShift=false;pendingGuiScreen=null;pendingGuiKeyScreen=null;pendingGuiButton=0;pendingGuiKeys.clear();manager.recordTick(frame);
    }
    /** Экран этого тика для записи: собственный интерфейс мода в запись не попадает. */
/** Ванильные меню (пауза, настройки, выбор мира, «вы погибли») — это управление игрой, а не игровое действие.
     *  Их клики и клавиши в запись не идут: иначе повтор ждёт экран, который никогда не откроется, и держит очередь событий.
     *  Сам факт открытого экрана (openScreenName) при этом сохраняется: горячие клавиши мира в таких кадрах остаются заглушены. */
    /** Экраны настроек: их ввод записывается и повторяется — пользователь меняет их курсором, как обычно.
     *  Главное меню, списка серверов и выбора мира здесь нет: там повтор ждал бы экран, который не откроется. */
    private static final String[] SETTINGS_SCREENS={"GuiIngameMenu","GuiOptions","GuiScreenOptions","GuiScreenOptionsSounds","GuiVideoSettings","ScreenChatOptions","GuiControls","GuiLanguage","GuiCustomizeSkin","GuiKeyBindingList","GuiScreenResourcePacks"};
    private static final String[] MENU_SCREENS={"GuiIngameMenu","GuiOptions","GuiScreenOptions","GuiScreenOptionsSounds","GuiVideoSettings","GuiControls","GuiLanguage","GuiCustomizeSkin","GuiShareToLan","GuiScreenResourcePacks","GuiMainMenu","GuiMultiplayer","GuiSelectWorld","GuiWorldSelection","GuiCreateWorld","GuiCustomizeWorldScreen","GuiScreenCustomizePresets","GuiDisconnected","GuiDownloadTerrain","GuiScreenWorking","GuiYesNo","GuiGameOver","GuiWinGame","GuiStats","GuiAchievements","GuiScreenAddServer","GuiScreenServerList","GuiScreenDemo","GuiScreenBackup","GuiKeyBindingList"};
    private static boolean menuScreen(String className){
        if(className==null)return false;
        if(className.startsWith("net.minecraftforge."))return true;
        if(!className.startsWith("net.minecraft."))return false;
        // Экраны настроек — такое же действие игрока, как любое другое: их ввод записываем.
        for(String allow:SETTINGS_SCREENS)if(className.endsWith("."+allow))return false;
        String simple=className.substring(className.lastIndexOf('.')+1);
        int inner=simple.indexOf('$');if(inner>0)simple=simple.substring(0,inner);
        for(int i=0;i<MENU_SCREENS.length;i++)if(MENU_SCREENS[i].equals(simple))return true;
        return false;
    }
    /** Мир заморожен (одиночная игра и меню паузы): кадры не пишутся, значит и ввод этих тиков
     *  нельзя приклеивать к следующему кадру — иначе в записи появляется событие, которого в игровом ��ире не было. */
    private void clearPendingInput(){
        pendingGuiClick=false;pendingGuiCenter=false;pendingGuiShift=false;pendingGuiScreen=null;pendingGuiKeyScreen=null;pendingGuiButton=0;pendingGuiKeys.clear();
        manager.setLeftClickState(false);manager.setRightClickState(false);manager.clearClickSequence();pendingKeyMask=0;pendingDropAll=false;
    }
    private String openScreenName(){GuiScreen s=mc.currentScreen;if(s==null)return null;String n=s.getClass().getName();return n.startsWith("com.mirror.recorder.gui.")?null:n;}
    private void handlePlayback(EntityPlayerSP player){
        // Пауза: кадр стоит, антизависание молчит, ручное движение не считается срывом повтора.
        if(manager.isPaused()){stallTicks=0;appliedRun=-1L;appliedFrame=-1;return;}
        // Zhivoj chat idet v realnom vremeni: povtor zhdet konec pechati, inache sobytiya posle soobshcheniya uhodyat v otkrytoe okno chata.
        if(typeText!=null){stallTicks=0;appliedRun=-1L;appliedFrame=-1;return;}
        // Стоп при движении мыши — отдельная настройка: мышь дёргают чаще, чем WASD, и ломать старое поведение нельзя.
        if(stopByMouseMove())return;
        if(config.isStopOnMove()&&manager.getSimulateCooldown()<=0&&isRealMovement()){MirrorDebug.log("PLAYBACK","STOP: manual movement detected at frame "+manager.getCurrentFrameIndex()+" (run "+manager.getPlaybackRunId()+")");MirrorDebug.csvClose();resetPlayback();manager.stopPlayback();SoundFx.stop();sendMsg(L("\u00a7e\u0412\u043e\u0441\u043f\u0440\u043e\u0438\u0437\u0432\u0435\u0434\u0435\u043d\u0438\u0435 \u043e\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d\u043e: \u043e\u0431\u043d\u0430\u0440\u0443\u0436\u0435\u043d\u043e \u0440\u0443\u0447\u043d\u043e\u0435 \u0434\u0432\u0438\u0436\u0435\u043d\u0438\u0435.","§ePlayback stopped: manual movement detected.","§eВідтворення зупинено: виявлено ручний рух.","§eWiedergabe gestoppt: manuelle Bewegung erkannt.","§eOdtwarzanie zatrzymane: wykryto ręczny ruch."));return;}
        long run=manager.getPlaybackRunId();int frameIndex=manager.getCurrentFrameIndex();
        if(appliedRun!=run||appliedFrame!=frameIndex){
            // Ввод не применяется к игроку (например, спектатор за другой сущностью: InputUpdateEvent не приходит).
            // Короткие расхождения терпимы, но вечно висящий повтор — баг: через полсекунды честно останавливаем.
            if(++stallTicks>=STALL_LIMIT){stallTicks=0;MirrorDebug.log("PLAYBACK","STOP: player input not applied for "+STALL_LIMIT+" ticks at frame "+frameIndex);resetPlayback();manager.stopPlayback();SoundFx.stop();sendMsg(L("§eПовтор остановлен: управление не применяется к игроку (например, наблюдение за другой сущностью).","§ePlayback stopped: input is not reaching the player (e.g. spectating another entity).","§eВідтворення зупинено: керування не застосовується до гравця (наприклад, спостереження за іншою сутністю).","§eWiedergabe gestoppt: Die Eingabe erreicht den Spieler nicht (z. B. Beobachten einer anderen Entität).","§eOdtwarzanie zatrzymane: sterowanie nie dociera do gracza (np. obserwacja innej istoty)."));}
            return;
        }
        stallTicks=0;
        appliedRun=-1L;appliedFrame=-1;
        if(!chatQueue.isEmpty()){long nowMs=System.currentTimeMillis();if(nowMs<lastChatSentAt)lastChatSentAt=0L;if(nowMs-lastChatSentAt>=config.getChatSendIntervalMs()){String msg=(config.isVisibleChat()&&(typeText!=null||chatBlockingScreen()||(chatKeyReplay&&mc.currentScreen instanceof net.minecraft.client.gui.GuiChat)))?null:chatQueue.poll();if(msg!=null&&!msg.isEmpty()){if(chatKeyReplay){chatKeyReplay=false;}else if(config.isVisibleChat())startChatTyping(msg);else player.sendChatMessage(msg);lastChatSentAt=nowMs;}}}
        if(!manager.advancePlayback()){SoundFx.finish();resetPlayback();}
    }
    /** Чужая запись не говорит от вашего имени: чат и команды из импортированного слота не отправляются. */
    private void warnImportedChat(){
        if(importedChatWarned)return;importedChatWarned=true;
        MirrorDebug.log("CHAT","imported recording: chat and commands are not sent");
        sendMsg(L("§eСообщения и команды из импортированной записи не отправляются.","§eChat and commands from an imported recording are not sent.","§eПовідомлення та команди з імпортованого запису не надсилаються.","§eChat und Befehle aus einer importierten Aufnahme werden nicht gesendet.","§eCzat i komendy z zaimportowanego nagrania nie są wysyłane."));
    }
    private void resetPlayback(){forceResetKeys();}
    private void forceResetKeys(){
        flushDeferredPress();
        stallTicks=0;
        releaseReplayedKeys();pointerOn=false;typeText=null;typeNextNs=0L;chatKeyReplay=false;keyLog.clear();
        if(mc.gameSettings!=null){restorePhysical(mc.gameSettings.keyBindAttack);restorePhysical(mc.gameSettings.keyBindUseItem);if(mc.gameSettings.keyBindsHotbar!=null)for(KeyBinding h:mc.gameSettings.keyBindsHotbar)drainPressQueue(h);drainPressQueue(mc.gameSettings.keyBindDrop);drainPressQueue(mc.gameSettings.keyBindSwapHands);drainPressQueue(mc.gameSettings.keyBindPickBlock);drainPressQueue(mc.gameSettings.keyBindInventory);}if(mc.player!=null)mc.player.setSprinting(false);wantSprint=false;sprintAssist=false;chatQueue.clear();lastChatSentAt=0L;appliedRun=-1L;appliedFrame=-1;interactionRun=-1L;interactionFrame=-1;guiQueue.clear();lastHotbar=-1;heldAttack=false;heldUse=false;lastMask=0;rotRun=-1L;rotFrame=-1;lookDYaw=0f;lookDPitch=0f;popInterpolatedLook();recRotInit=false;pendingChatMessage=null;pendingGuiClick=false;pendingGuiCenter=false;pendingGuiScreen=null;importedChatWarned=false;resetStabilizer();keysNeedReset=false;useHoldFallbackDelay=0;wantSprint=false;useTimerAlign=false;maskRun=-1L;screenMismatchTicks=0;pendingGuiShift=false;pendingGuiKeyScreen=null;recordTickStartNs=0L;pendingGuiKeys.clear();replaySlider=null;replaySliderScreen=null;noticeGuiDrop=false;autoCheckpointTicks=0;pendingGuiButton=0;worldResetPending=false;worldSuspendTicks=0;
    }
    /** Clears presses buffered inside a KeyBinding: every isPressed() call consumes one. The guard prevents an endless loop if the counter never drains. */
    private void drainPressQueue(KeyBinding key){if(key==null)return;for(int guard=0;guard<64&&key.isPressed();guard++){}}
    private void restorePhysical(KeyBinding key){if(key==null)return;drainPressQueue(key);KeyBinding.setKeyBindState(key.getKeyCode(),physicalDown(key.getKeyCode()));}
    private boolean physicalDown(int code){try{if(code>=0)return Keyboard.isKeyDown(code);int button=code+100;return button>=0&&button<Mouse.getButtonCount()&&Mouse.isButtonDown(button);}catch(Exception e){return false;}}
    @SubscribeEvent public void onWorldUnload(WorldEvent.Unload event){if(event.getWorld().isRemote){releaseBackgroundPolicy();pendingChatMessage=null;forceResetKeys();}}
    @SubscribeEvent public void onWorldLoad(WorldEvent.Load event){if(event.getWorld().isRemote&&manager.isBusy()){worldResetPending=true;if(manager.isRecording())sendMsg(L("§aНовый мир: запись продолжается.","§aNew world: recording continues.","§aНовий світ: запис триває.","§aNeue Welt: Aufnahme läuft weiter.","§aNowy świat: nagrywanie trwa."));else if(manager.isPlaying())sendMsg(L("§aНовый мир: повтор продо��жается.","§aNew world: playback continues.","§aНовий світ: відтворення триває.","§aNeue Welt: Wiedergabe läuft weiter.","§aNowy świat: odtwarzanie trwa."));}}
    @SubscribeEvent public void onClientChat(ClientChatEvent event){if(manager.isRecording()&&mc.currentScreen instanceof net.minecraft.client.gui.GuiChat){String msg=event.getMessage();if(msg!=null&&!msg.isEmpty())pendingChatMessage=msg;}}
    @SubscribeEvent public void onMouseInput(InputEvent.MouseInputEvent event){
        // Сюда доходят только события без экрана: любой вооружённый драг окна уже протух (окно тихо закрылось).
        if(!manager.isRecording())return;recDragButton=-1;recDragScreen=null;if(mc.currentScreen!=null||!Mouse.getEventButtonState())return;int btn=Mouse.getEventButton();pendingGuiClick=false;pendingGuiCenter=false;pendingGuiShift=false;pendingGuiScreen=null;pendingGuiButton=0;if(btn==0)manager.addLeftClick();else if(btn==1)manager.addRightClick();
    }
    @SubscribeEvent public void onGuiMouseInput(GuiScreenEvent.MouseInputEvent.Pre event){
        if(!manager.isRecording())return;GuiScreen screen=event.getGui();if(screen==null)return;String name=screen.getClass().getName();
        if(!replayableScreen(name)||name.startsWith("com.mirror.recorder.gui.")||menuScreen(name)||screen instanceof net.minecraft.client.gui.GuiChat)return;
        int btn=Mouse.getEventButton();boolean down=Mouse.getEventButtonState();
        int gw=Math.max(1,screen.width),gh=Math.max(1,screen.height),px=MathHelper.clamp(Mouse.getEventX()*gw/Math.max(1,mc.displayWidth),0,gw-1),py=MathHelper.clamp(gh-Mouse.getEventY()*gh/Math.max(1,mc.displayHeight)-1,0,gh-1);
        float fx=px/(float)gw,fy=py/(float)gh;int cx=px-gw/2,cy=py-gh/2;
        if(down&&btn>=0&&btn<=2){
            pendingGuiButton=btn;pendingGuiClick=true;pendingGuiX=fx;pendingGuiY=fy;pendingGuiCX=cx;pendingGuiCY=cy;pendingGuiCenter=true;pendingGuiShift=GuiScreen.isShiftKeyDown();pendingGuiScreen=name;
            if(btn==0){manager.setLeftClickState(true);manager.addClickOrder(0);}else if(btn==1){manager.setRightClickState(true);manager.addClickOrder(1);}
        }
        if(down&&btn>=0&&btn<=2&&recDragButton<0&&screen instanceof net.minecraft.client.gui.inventory.GuiContainer&&(btn==0||btn==1)&&!GuiScreen.isShiftKeyDown()){recDragButton=btn;recDragScreen=name;recDragLastMoveNs=0L;recDragLastX=cx;recDragLastY=cy;}
        // Смена окна роняет незавершённый драг: отпуск чужого окна не пишется.
        if(recDragScreen!=null&&!recDragScreen.equals(name)){recDragButton=-1;recDragScreen=null;}
        if(screen instanceof net.minecraft.client.gui.inventory.GuiContainer){
            if(!down&&btn>=0&&btn<=2){
                if(btn==recDragButton&&name.equals(recDragScreen)){
                    if(pendingGuiKeys.size()<GUI_KEY_LIMIT){pendingGuiKeyScreen=name;pendingGuiKeys.add(new int[]{GA_CRELEASE,eventOffsetMs(),btn,GUI_FLAG_TIMED,cx,cy});}
                    else MirrorDebug.log("GUI","key event cap "+GUI_KEY_LIMIT+" reached in one tick, container release dropped");
                    recDragButton=-1;recDragScreen=null;
                }
            }else if(btn==-1&&recDragButton>=0&&name.equals(recDragScreen)){
                boolean held=false;try{held=Mouse.isButtonDown(recDragButton);}catch(Exception ignored){}
                if(!held){recDragButton=-1;recDragScreen=null;}
                else if(cx!=recDragLastX||cy!=recDragLastY){
                    long now=System.nanoTime();
                    // ~12 мс: путь для сплита слотов сохраняется, мусор от 1000 Гц мыши — нет.
                    if(now-recDragLastMoveNs>=12000000L){
                        if(pendingGuiKeys.size()<GUI_KEY_LIMIT){pendingGuiKeyScreen=name;pendingGuiKeys.add(new int[]{GA_CMOVE,eventOffsetMs(),recDragButton,GUI_FLAG_TIMED,cx,cy});recDragLastMoveNs=now;recDragLastX=cx;recDragLastY=cy;}
                        else MirrorDebug.log("GUI","key event cap "+GUI_KEY_LIMIT+" reached in one tick, container move dropped");
                    }
                }
            }
        }
        boolean dragMove=btn==-1&&Mouse.isButtonDown(0),dragEnd=btn==0&&!down;
        if(settingsScreen(screen)&&((btn==0&&down)||dragMove||dragEnd)){
            if(pendingGuiKeys.size()>=GUI_KEY_LIMIT){MirrorDebug.log("GUI","mouse event cap "+GUI_KEY_LIMIT+" reached in one tick, drag event dropped");return;}
            pendingGuiKeyScreen=name;pendingGuiKeys.add(new int[]{dragEnd?GA_DRAG_END:GA_DRAG,eventOffsetMs(),0,GUI_FLAG_TIMED,cx,cy});
        }
    }

    /** Клавиши в чужих окнах: классифицируем сразу (действие слота, закрытие, печать), чтобы повтор не зависел от текущих привязок ��лавиш. Чат и окна мода не пишем. */
    @SubscribeEvent public void onGuiKeyInput(GuiScreenEvent.KeyboardInputEvent.Pre event){
        if(!manager.isRecording()||!Keyboard.getEventKeyState())return;
        GuiScreen screen=event.getGui();if(screen==null||mc.gameSettings==null)return;
        String name=screen.getClass().getName();
        if(!replayableScreen(name)||name.startsWith("com.mirror.recorder.gui.")||menuScreen(name))return;
        int key=Keyboard.getEventKey();char chr=Keyboard.getEventCharacter();
        net.minecraft.client.settings.GameSettings gs=mc.gameSettings;
        boolean containerKey=screen instanceof net.minecraft.client.gui.inventory.GuiContainer;
        boolean textFocus=containerKey&&hasFocusedTextField(screen);
        boolean printable=chr>=32&&chr!=127,nav=key==14||key==199||key==203||key==205||key==207||key==211||key==200||key==208;
        boolean textWins=textFocus&&(printable||nav);
        int action=-1,aux=chr;
        if(key==1)action=GA_CLOSE;
        else if(!containerKey)action=GA_TYPE;
        else if(gs.keyBindInventory.isActiveAndMatches(key)){if(!textWins)action=GA_CLOSE;}
        else if(gs.keyBindDrop.isActiveAndMatches(key)){if(!textWins)action=GA_DROP;}
        else if(gs.keyBindPickBlock.isActiveAndMatches(key)){if(!textWins)action=GA_PICK;}
        else{int hb=hotbarKeyIndex(gs,key);if(hb>=0&&!textWins){action=GA_SWAP;aux=hb;}}
        if(action<0){if(printable||nav)action=GA_TYPE;else return;}
        if(pendingGuiKeys.size()>=GUI_KEY_LIMIT){MirrorDebug.log("GUI","key event cap "+GUI_KEY_LIMIT+" reached in one tick, event dropped");return;}
        int cx=0,cy=0;
        if(action==GA_DROP||action==GA_PICK||action==GA_SWAP){
            int gw=Math.max(1,screen.width),gh=Math.max(1,screen.height);
            float fx=MathHelper.clamp((float)Mouse.getX()/Math.max(1,mc.displayWidth),0f,1f),fy=MathHelper.clamp(1f-(float)Mouse.getY()/Math.max(1,mc.displayHeight),0f,1f);
            cx=Math.round(fx*gw)-gw/2;cy=Math.round(fy*gh)-gh/2;
        }
        int flags=(GuiScreen.isShiftKeyDown()?1:0)|(GuiScreen.isCtrlKeyDown()?2:0);if(screen instanceof net.minecraft.client.gui.GuiChat){flags|=GUI_FLAG_TIMED;cx=eventOffsetMs();}pendingGuiKeyScreen=name;pendingGuiKeys.add(new int[]{action,key,aux,flags,cx,cy});
    }
    /** Горячие клавиши мода при открытом экране. Forge шлёт KeyInputEvent только когда currentScreen==null,
     *  поэтому из инвентаря, настроек и любого чужого окна повтор и запись было не остановить. Ловим те же
     *  клавиши в событии экрана: печать текста и переназначение клавиш не задеваем, а свою клавишу гасим,
     *  чтобы окно на неё не реагировало и она не попала в запись. */
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void onGuiHotkey(GuiScreenEvent.KeyboardInputEvent.Pre event){
        if(!Keyboard.getEventKeyState())return;
        GuiScreen screen=event.getGui();if(screen==null)return;
        String name=screen.getClass().getName();if(name.startsWith("com.mirror.recorder.gui."))return;
        if(typingScreen(screen))return;
        MirrorRecorder mod=MirrorRecorder.getInstance();if(mod==null)return;
        int code=Keyboard.getEventKey();if(code<=0)return;
        BaritoneReturnController returner=mod.getReturnController();
        if(boundTo(mod.getKeyStop(),code)){
            if(!(manager.isBusy()||(returner!=null&&(returner.isBusy()||returner.isRecovering()))))return;
            MirrorDebug.log("INPUT","stop hotkey with open screen "+name);
            SoundFx.stop();resetPlayback();if(returner!=null)returner.cancel();manager.stopAll();
            sendMsg(L("§e[=] Остановлено","§e[=] Stopped","§e[=] Зупинено","§e[=] Gestoppt","§e[=] Zatrzymano"));
            event.setCanceled(true);return;
        }
        if(boundTo(mod.getKeyGui(),code)){mc.displayGuiScreen(new com.mirror.recorder.gui.GuiMirrorMain(manager,config));event.setCanceled(true);}
    }
    private static boolean boundTo(KeyBinding key,int code){return key!=null&&key.getKeyCode()!=0&&key.getKeyCode()==code;}
    /** Экраны, где клавиша принадлежит тексту или переназначению, а не моду. */
    private boolean typingScreen(GuiScreen screen){
        if(screen instanceof net.minecraft.client.gui.GuiChat||screen instanceof net.minecraft.client.gui.GuiScreenBook)return true;
        if(screen instanceof net.minecraft.client.gui.inventory.GuiEditSign||screen instanceof net.minecraft.client.gui.GuiControls)return true;
        return hasFocusedTextField(screen);
    }
    private int hotbarKeyIndex(net.minecraft.client.settings.GameSettings gs,int key){
        if(gs.keyBindsHotbar==null)return -1;
        for(int i=0;i<gs.keyBindsHotbar.length&&i<9;i++)if(gs.keyBindsHotbar[i].isActiveAndMatches(key))return i;
        return -1;
    }
    /** Фокус текстового поля решает, что клавиша — это печать (как ванильная наковальня: textboxKeyTyped идёт первым). */
    private boolean hasFocusedTextField(GuiScreen screen){
        try{for(Field f:textFieldsOf(screen.getClass())){f.setAccessible(true);Object o=f.get(screen);if(o instanceof net.minecraft.client.gui.GuiTextField&&((net.minecraft.client.gui.GuiTextField)o).isFocused())return true;}}catch(Throwable ignored){}
        return false;
    }
    private Field[] textFieldsOf(Class<?> c){
        Field[] cached=textFieldCache.get(c.getName());if(cached!=null)return cached;
        java.util.List<Field> out=new java.util.ArrayList<Field>();
        for(Class<?> k=c;k!=null&&k!=Object.class;k=k.getSuperclass())for(Field f:k.getDeclaredFields())if(net.minecraft.client.gui.GuiTextField.class.isAssignableFrom(f.getType()))out.add(f);
        Field[] arr=out.toArray(new Field[out.size()]);textFieldCache.put(c.getName(),arr);return arr;
    }
    @SubscribeEvent public void onKeyInput(InputEvent.KeyInputEvent event){
        // Всё, что нажал игрок, пишем как есть: любая чужая клавиша может быть частью действия.
        if(manager.isRecording()&&config.isRecordAllKeys()){
            try{int code=Keyboard.getEventKey();boolean down=Keyboard.getEventKeyState();
                if(code>0&&!systemKey(code)&&!ownKey(code)&&keyLog.size()<Frame.MAX_KEY_EVENTS)keyLog.add(new int[]{code,down?1:0});
            }catch(Exception e){}
        }
        // Bystryj tap klavishi okna ne viden v maske konca tika (klavisha uzhe otpushchena): lovim nazhatie po sobytiju.
        if(manager.isRecording()&&Keyboard.getEventKeyState()&&mc.currentScreen==null&&mc.gameSettings!=null){
            net.minecraft.client.settings.GameSettings g=mc.gameSettings;int pk=Keyboard.getEventKey();
            if(g.keyBindInventory.isActiveAndMatches(pk))pendingKeyMask|=Frame.K_INVENTORY;
            else if(g.keyBindDrop.isActiveAndMatches(pk)){pendingKeyMask|=Frame.K_DROP;pendingDropAll=pendingDropAll||GuiScreen.isCtrlKeyDown();}
            else if(g.keyBindSwapHands.isActiveAndMatches(pk))pendingKeyMask|=Frame.K_SWAP;
            else if(g.keyBindPickBlock.isActiveAndMatches(pk))pendingKeyMask|=Frame.K_PICK;
        }
        MirrorRecorder mod=MirrorRecorder.getInstance();if(mod==null)return;
        if(mod.getKeyRecord().isPressed()){if(mod.getReturnController()!=null&&mod.getReturnController().isBusy())sendMsg(L("\u00a7e\u0421\u043d\u0430\u0447\u0430\u043b\u0430 \u043e\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0435 \u0432\u043e\u0437\u0432\u0440\u0430\u0442 Baritone.","§eStop the Baritone return first.","§eСпочатку зупиніть повернення Baritone.","§eStoppe zuerst die Baritone-Rückkehr.","§eNajpierw zatrzymaj powrót Baritone."));else{int slot=config.getActiveSlot();if(manager.scheduleRecording(slot))sendMsg(L("\u00a7a[+] \u0417\u0430\u043f\u0438\u0441\u044c \u0447\u0435\u0440\u0435\u0437 ","§a[+] Recording in ","§a[+] Запис через ","§a[+] Aufnahme in ","§a[+] Nagranie za ")+config.getStartDelay()+L(" \u0441\u0435\u043a..."," sec..."," сек..."," Sek..."," s..."));else sendMsg(L("\u00a7c\u0421\u043b\u043e\u0442 ","§cSlot ","§cСлот ","§cSlot ","§cSlot ")+slot+L(" \u0437\u0430\u043d\u044f\u0442 \u0438\u043b\u0438 \u0438\u0434\u0451\u0442 \u0434\u0440\u0443\u0433\u043e\u0435 \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u0435"," is occupied or another action is running"," зайнятий або йде інша дія"," ist belegt oder eine andere Aktion läuft"," zajęty lub trwa inna akcja"));}}
        if(mod.getKeyPlay().isPressed()){int slot=config.getActiveSlot();BaritoneReturnController r=mod.getReturnController();if(r!=null){manager.setPreferredLoopMode(slot,false);r.request(slot,false);}}
        if(mod.getKeyLoop().isPressed()){int slot=config.getActiveSlot();BaritoneReturnController r=mod.getReturnController();if(r!=null){manager.setPreferredLoopMode(slot,true);r.request(slot,true);}}
        if(mod.getKeyStop().isPressed()){SoundFx.stop();resetPlayback();if(mod.getReturnController()!=null)mod.getReturnController().cancel();manager.stopAll();sendMsg(L("\u00a7e[=] \u041e\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d\u043e","§e[=] Stopped","§e[=] Зупинено","§e[=] Gestoppt","§e[=] Zatrzymano"));}
        if(mod.getKeyGui().isPressed())mc.displayGuiScreen(new com.mirror.recorder.gui.GuiMirrorMain(manager,config));
    }
    private boolean isRealMovement(){return mc.player!=null&&mc.gameSettings!=null&&(mc.gameSettings.keyBindForward.isKeyDown()||mc.gameSettings.keyBindBack.isKeyDown()||mc.gameSettings.keyBindLeft.isKeyDown()||mc.gameSettings.keyBindRight.isKeyDown()||mc.gameSettings.keyBindJump.isKeyDown());}
    private static String L(String ru,String en,String uk,String de,String pl){return com.mirror.recorder.gui.Lang.s(ru,en,uk,de,pl);}
    private void sendMsg(String msg){long now=System.currentTimeMillis();if(now<lastNoticeAt)lastNoticeAt=0L;if(msg!=null&&msg.equals(lastNotice)&&now-lastNoticeAt<2000L)return;lastNotice=msg==null?"":msg;lastNoticeAt=now;TransientChat.show(msg);}
}
