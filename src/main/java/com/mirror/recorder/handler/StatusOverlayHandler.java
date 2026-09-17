package com.mirror.recorder.handler;
import com.mirror.recorder.config.RecorderConfig;
import com.mirror.recorder.gui.UiTheme;
import com.mirror.recorder.manager.RecorderManager;
import com.mirror.recorder.storage.StorageManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;

@SideOnly(Side.CLIENT)
public class StatusOverlayHandler{
    private final Minecraft mc=Minecraft.getMinecraft();private final RecorderManager manager;private final RecorderConfig config;private final BaritoneReturnController returner;
    private String title="",detail="";private float progress=-1f;private int color=UiTheme.ACCENT,drawX=8,drawY=8,drawW=0,dragDX=0,dragDY=0,dragW=0;private static final int HUD_H=42,HUD_PAD_X=16,HUD_PAD_Y=10,HUD_MARGIN=12;private boolean dragging=false,hudVisible=false;
    public StatusOverlayHandler(RecorderManager m,RecorderConfig c,BaritoneReturnController r){manager=m;config=c;returner=r;}
    @SubscribeEvent public void onTick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;if(dragging&&!Mouse.isButtonDown(0)){dragging=false;dragW=0;config.save();}
        title="";detail="";progress=-1f;if(mc.player==null||mc.world==null)return;int slot=manager.getActiveOperationSlot();
        if(manager.isRecording()){long ticks=manager.getRecordingTickCounter(),left=Math.max(0L,(long)StorageManager.MAX_FRAMES-ticks);title=s("Запись","Recording","Запис","Aufnahme","Nagrywanie");detail=s("Слот ","Slot ","Слот ","Slot ","Slot ")+slot+"  ·  "+s("Идёт ","Elapsed ","Йде ","Vergangen ","Minęło ")+time(ticks)+"  ·  "+s("Осталось ","Left ","Залишилось ","Übrig ","Pozostało ")+time(left);progress=Math.min(1f,ticks/(float)StorageManager.MAX_FRAMES);color=UiTheme.RED;}
        else if(manager.isPlaying()){int frames=Math.max(1,manager.getSlotFrameCount(slot)),current=Math.min(frames,manager.getCurrentFrameIndex()+1);if(returner!=null&&returner.isBusy()&&returner.getLoopLimit()>1){int limit=returner.getLoopLimit();title=s("Цикл ","Loop ","Цикл ","Schleife ","Pętla ")+returner.getCycle()+"/"+(limit==0?s("без лимита","unlimited","без ліміту","unbegrenzt","bez limitu"):String.valueOf(limit));color=UiTheme.PURPLE;}else{title=s("Воспроизведение","Playback","Відтворення","Wiedergabe","Odtwarzanie");color=UiTheme.BLUE;}detail=s("Слот ","Slot ","Слот ","Slot ","Slot ")+slot+"  ·  "+s("Кадр ","Frame ","Кадр ","Frame ","Klatka ")+current+"/"+frames;progress=current/(float)frames;if(manager.isPaused()){title=s("Пауза","Paused","Пауза","Pause","Pauza")+" · "+title;color=UiTheme.AMBER;}}
        else if(returner!=null&&returner.isRecovering()){title=s("Нужна начальная точка","Start point required","Потрібна початкова точка","Startpunkt benötigt","Potrzebny punkt startu");detail=s("Встаньте на сохранённую позицию","Stand on the saved position","Встаньте на збережену позицію","Stelle dich auf die gespeicherte Position","Stań na zapisanej pozycji");color=UiTheme.AMBER;}
        else if(returner!=null&&returner.isBusy()){title=s("Автовозврат","Auto return","Автоповернення","Auto-Rückkehr","Auto-powrót");detail=s("Возврат к началу  ·  Слот ","Returning to start  ·  Slot ","Повернення на початок  ·  Слот ","Rückkehr zum Start  ·  Slot ","Powrót na start  ·  Slot ")+returner.getSlot();color=UiTheme.GREEN;}
        else if(manager.hasPendingAction()){int sec=manager.getDelaySecondsRemaining();title=s("Запуск","Starting","Запуск","Start","Start");detail=(sec>0?s("Осталось ","Left ","Залишилось ","Übrig ","Pozostało ")+sec+s(" с  ·  "," s  ·  "," с  ·  "," s  ·  "," s  ·  "):"")+s("Слот ","Slot ","Слот ","Slot ","Slot ")+manager.getPendingSlot();color=UiTheme.AMBER;}
    }
    @SubscribeEvent public void onRender(RenderGameOverlayEvent.Post e){
        if(e.getType()!=RenderGameOverlayEvent.ElementType.ALL||mc.fontRenderer==null)return;String t=title,d=detail;boolean preview=mc.currentScreen!=null&&mc.currentScreen.getClass().getName().startsWith("com.mirror.recorder.gui.");if(t.isEmpty()&&preview){t=s("Индикатор состояния","Status indicator","Індикатор стану","Statusanzeige","Wskaźnik stanu");d=s("Shift + перетаскивание — переместить HUD","Shift + drag to move the HUD","Shift + перетягування — перемістити HUD","Shift + Ziehen verschiebt das HUD","Shift + przeciąganie przenosi HUD");color=UiTheme.ACCENT;}
        hudVisible=!t.isEmpty();if(!hudVisible)return;ScaledResolution sr=new ScaledResolution(mc);int screenW=sr.getScaledWidth(),screenH=sr.getScaledHeight(),maxW=Math.max(40,screenW-HUD_MARGIN*2);int textW=Math.max(mc.fontRenderer.getStringWidth(t),mc.fontRenderer.getStringWidth(d));int wanted=Math.min(maxW,textW+HUD_PAD_X*2+2);drawW=(dragging&&dragW>0)?Math.min(maxW,Math.max(40,dragW)):wanted;drawX=MathHelper.clamp(config.getHudX(),HUD_MARGIN,Math.max(HUD_MARGIN,screenW-drawW-HUD_MARGIN));drawY=MathHelper.clamp(config.getHudY(),HUD_MARGIN,Math.max(HUD_MARGIN,screenH-HUD_H-HUD_MARGIN));
        UiTheme.round(drawX,drawY,drawX+drawW,drawY+HUD_H,UiTheme.HUD_GLASS);UiTheme.border(drawX,drawY,drawX+drawW,drawY+HUD_H,UiTheme.alpha(color,0.58f));
        int textRoom=Math.max(8,drawW-HUD_PAD_X*2-2),textX=drawX+HUD_PAD_X;UiTheme.drawFittedShadow(mc.fontRenderer,t,textX,drawY+HUD_PAD_Y,textRoom,UiTheme.TEXT);UiTheme.drawFitted(mc.fontRenderer,d,textX,drawY+HUD_PAD_Y+15,textRoom,UiTheme.TEXT_DIM);
        if(progress>=0f){int l=drawX+HUD_PAD_X,r=drawX+drawW-HUD_PAD_X,w=Math.max(0,(int)((r-l)*Math.max(0f,Math.min(1f,progress))));Gui.drawRect(l,drawY+HUD_H-6,l+w,drawY+HUD_H-4,UiTheme.alpha(color,0.85f));}
    }
    private static String s(String ru,String en,String uk,String de,String pl){return com.mirror.recorder.gui.Lang.s(ru,en,uk,de,pl);}
    private String time(long ticks){long sec=Math.max(0L,ticks/20L);return String.format(java.util.Locale.ROOT,"%02d:%02d",sec/60L,sec%60L);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void onGuiMouse(GuiScreenEvent.MouseInputEvent.Pre e){if(!hudVisible||mc.currentScreen==null||!mc.currentScreen.getClass().getName().startsWith("com.mirror.recorder.gui.")){if(dragging){dragging=false;dragW=0;config.save();}return;}ScaledResolution sr=new ScaledResolution(mc);int mx=Mouse.getEventX()*sr.getScaledWidth()/Math.max(1,mc.displayWidth),my=sr.getScaledHeight()-Mouse.getEventY()*sr.getScaledHeight()/Math.max(1,mc.displayHeight)-1,button=Mouse.getEventButton();if(button==0&&Mouse.getEventButtonState()&&GuiScreen.isShiftKeyDown()&&mx>=drawX&&mx<drawX+drawW&&my>=drawY&&my<drawY+HUD_H){dragging=true;dragW=drawW;dragDX=mx-drawX;dragDY=my-drawY;e.setCanceled(true);}if(dragging){int nx=MathHelper.clamp(mx-dragDX,HUD_MARGIN,Math.max(HUD_MARGIN,sr.getScaledWidth()-drawW-HUD_MARGIN)),ny=MathHelper.clamp(my-dragDY,HUD_MARGIN,Math.max(HUD_MARGIN,sr.getScaledHeight()-HUD_H-HUD_MARGIN));config.setHudPosition(nx,ny);e.setCanceled(true);if(button==0&&!Mouse.getEventButtonState()){dragging=false;dragW=0;config.save();}}}
}
