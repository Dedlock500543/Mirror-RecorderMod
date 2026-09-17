package com.mirror.recorder.gui;
import com.mirror.recorder.config.RecorderConfig;
import com.mirror.recorder.manager.RecorderManager;
import net.minecraft.client.gui.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
/**
 * Главный экран: слева список записей, справа карточка выбранного слота и действия,
 * справа расположен вертикальный блок действий. Логика записи и повтора не изменена.
 */
public class GuiMirrorMain extends GuiScreen{

    private static final int BTN_RECORD=101,BTN_PLAY=102,BTN_LOOP=103,BTN_STOP=104,BTN_SETTINGS=106,BTN_CLOSE=107;
    private static final int MAX_SLOTS=100,VISIBLE_SLOTS=10,TPS=20;private static final String PREFIX="\u00a78[\u00a7bMirror\u00a78]\u00a7r ";
    private final RecorderManager manager;private final RecorderConfig config;private int selectedSlot=1,scrollOffset=0;
    private float visScroll=0f,scrollFrom=0f;private long scrollAt=0L;
    private final float[] hovA=new float[MAX_SLOTS+2],hovFrom=new float[MAX_SLOTS+2];
    private final long[] hovAt=new long[MAX_SLOTS+2];private final boolean[] hovT=new boolean[MAX_SLOTS+2];
    private int winX,winY,winW,winH,headH,listX,listRight,listTop,listBottom,rowH,infoX,infoRight,infoY,infoH,actionsY;
    private StyledButton bRecord,bPlay,bLoop,bStop,bSettings,bClose;
    public GuiMirrorMain(RecorderManager m,RecorderConfig c){manager=m;config=c;selectedSlot=Math.max(1,Math.min(MAX_SLOTS,c.getActiveSlot()));scrollOffset=((selectedSlot-1)/VISIBLE_SLOTS)*VISIBLE_SLOTS;}

    private static String s(String ru,String en,String uk,String de,String pl){return Lang.s(ru,en,uk,de,pl);}
    private static String[] tt(String ru,String en,String uk,String de,String pl){return s(ru,en,uk,de,pl).split("\n");}

    private void layout(){
        winW=Math.min(width-14,564);winH=Math.min(height-14,362);winX=(width-winW)/2;winY=(height-winH)/2;headH=Math.min(38,Math.max(24,winH/9));
        int pad=12,gap=13;
        listX=winX+pad;int listW=Math.max(150,(int)((winW-pad*2-gap)*0.44f));listRight=listX+listW;
        listTop=winY+headH+26;listBottom=winY+winH-13;
        rowH=Math.min(27,Math.max(11,(listBottom-listTop)/VISIBLE_SLOTS));
        infoX=listRight+gap;infoRight=winX+winW-pad;infoY=listTop;
    }
    private StyledButton add(StyledButton b){buttonList.add(b);return b;}
    @Override public void initGui(){
        usedSlotsCache=-1;buttonList.clear();layout();
        int colW=infoRight-infoX,colRoom=Math.max(0,listBottom-infoY);
        int mainGap=colRoom<178?2:4,secH=colRoom<178?13:17,secGap=colRoom<178?3:4;
        int secBlock=secH*2+secGap;
        int mainH=Math.max(10,Math.min(26,(colRoom-46-mainGap*3-6-secBlock)/4));
        int blockH=mainH*4+mainGap*3+6+secBlock;
        // Колонка ниже минимума: карточкой жертвуем, но кнопки обязаны остаться внутри окна.
        if(blockH>colRoom){mainH=Math.max(9,(colRoom-mainGap*3-6-secBlock)/4);blockH=mainH*4+mainGap*3+6+secBlock;}
        actionsY=Math.max(infoY,listBottom-blockH);
        infoH=Math.max(0,actionsY-16-infoY);
        int by=actionsY;
        bRecord=add(new StyledButton(BTN_RECORD,infoX,by,colW,mainH,Lang.t("main.record")).primary().accent(UiTheme.GREEN).icon("\u25cf")
            .sub(s("Создать новую запись в выбранном слоте","Create a new recording in the selected slot","Створити новий запис у вибраному слоті","Neue Aufnahme im gewählten Slot erstellen","Utwórz nowe nagranie w wybranym slocie")).tip(Lang.tip("tip.record")));
        by+=mainH+mainGap;
        bPlay=add(new StyledButton(BTN_PLAY,infoX,by,colW,mainH,Lang.t("main.play")).primary().accent(UiTheme.BLUE).icon("\u25b6")
            .sub(s("Воспроизвести выбранную запись один раз","Play the selected recording once","Відтворити вибраний запис один раз","Gewählte Aufnahme einmal abspielen","Odtwórz wybrane nagranie raz")).tip(Lang.tip("tip.play")));
        by+=mainH+mainGap;
        bLoop=add(new StyledButton(BTN_LOOP,infoX,by,colW,mainH,Lang.t("main.loop")).primary().accent(UiTheme.PURPLE).icon("\u21bb")
            .sub(s("Повторять выбранную запись по кругу","Repeat the selected recording in a loop","Повторювати вибраний запис по колу","Gewählte Aufnahme in Schleife wiederholen","Powtarzaj wybrane nagranie w pętli")).tip(Lang.tip("tip.loop")));
        by+=mainH+mainGap;
        bStop=add(new StyledButton(BTN_STOP,infoX,by,colW,mainH,Lang.t("main.stop")).primary().accent(UiTheme.darken(UiTheme.RED,0.78f)).icon("\u25a0")
            .sub(s("Остановить текущую запись или повтор","Stop the current recording or playback","Зупинити поточний запис або повтор","Aktuelle Aufnahme oder Wiedergabe stoppen","Zatrzymaj bieżące nagrywanie lub odtwarzanie")).tip(Lang.tip("tip.stop")));
        by+=mainH+6;
        // Настройки занимают всю строку; справка доступна по F1 и внутри настроек.
        bSettings=add(new StyledButton(BTN_SETTINGS,infoX,by,colW,secH,Lang.t("main.settings")).accent(UiTheme.ACCENT).compact().tip(Lang.tip("tip.settings")));
        by+=secH+secGap;
        bClose=add(new StyledButton(BTN_CLOSE,infoX,by,colW,secH,Lang.t("common.close")).accent(UiTheme.GRAY).compact().tip(Lang.tip("tip.close")));
    }
    private com.mirror.recorder.handler.BaritoneReturnController returner(){com.mirror.recorder.MirrorRecorder mod=com.mirror.recorder.MirrorRecorder.getInstance();return mod==null?null:mod.getReturnController();}
    private int usedSlotsCache=-1;private long usedSlotsAt=0L;
    private int usedSlots(){long now=System.currentTimeMillis();if(usedSlotsCache>=0&&now-usedSlotsAt<500L)return usedSlotsCache;int used=0;for(int i=1;i<=MAX_SLOTS;i++)if(manager.getSlotFrameCount(i)>0)used++;usedSlotsCache=used;usedSlotsAt=now;return used;}
    private void updateButtonStates(){
        com.mirror.recorder.handler.BaritoneReturnController r=returner();
        boolean autoBusy=r!=null&&r.isBusy(),recovering=r!=null&&r.isRecovering(),ready=!manager.isBusy()&&!autoBusy;
        int frames=manager.getSlotFrameCount(selectedSlot);
        bRecord.enabled=ready&&frames==0;bPlay.enabled=ready&&frames>0;bLoop.enabled=ready&&frames>0;
        bStop.enabled=manager.isBusy()||autoBusy||recovering;bSettings.enabled=ready;
    }
    @Override public void drawScreen(int mx,int my,float pt){
        int bd=UiTheme.GLASS_BACKDROP;
        drawGradientRect(0,0,width,height,bd,bd);
        UiTheme.window(winX,winY,winX+winW,winY+winH,headH);
        UiTheme.header(fontRenderer,winX,winY,winX+winW,headH,"Mirror Recorder",
            s("1. выберите слот   2. действие справа   ·   ПКМ по слоту — ещё действия   ·   F1 — справка","1. pick a slot   2. press an action   ·   right-click a slot for more   ·   F1 for help","1. виберіть слот   2. дія справа   ·   ПКМ за слотом — більше дій   ·   F1 — довідка","1. Slot wählen   2. Aktion rechts   ·   Rechtsklick auf einen Slot für mehr   ·   F1 für Hilfe","1. wybierz slot   2. akcja po prawej   ·   PPM na slocie — więcej akcji   ·   F1 — pomoc"),UiTheme.ACCENT,56);
        String ver="v"+com.mirror.recorder.MirrorRecorder.VERSION;
        int vw=fontRenderer.getStringWidth(ver)+8;UiTheme.chip(fontRenderer,ver,winX+winW-12-vw,winY+13,UiTheme.ACCENT);
        UiTheme.sectionLabel(fontRenderer,s("ЗАПИСИ  ","RECORDINGS  ","ЗАПИСИ  ","AUFNAHMEN  ","NAGRANIA  ")+usedSlots()+"/100",listX,listTop-13,listRight-listX);
        UiTheme.sectionLabel(fontRenderer,s("ВЫБРАННАЯ ЗАПИСЬ","SELECTED RECORDING","ВИБРАНИЙ ЗАПИС","GEWÄHLTE AUFNAHME","WYBRANE NAGRANIE"),infoX,listTop-13,infoRight-infoX);
        drawSlotList(mx,my);if(infoH>0)drawInfo();if(actionsY-11>=infoY)UiTheme.sectionLabel(fontRenderer,s("ДЕЙСТВИЯ","ACTIONS","ДІЇ","AKTIONEN","AKCJE"),infoX,actionsY-11,infoRight-infoX);updateButtonStates();
        super.drawScreen(mx,my,pt);
        drawTooltips(mx,my);
    }
    private String fmtDur(int frames){int sec=Math.max(0,frames/TPS);return sec>=60?(sec/60)+s("м ","m ","хв ","m ","min ")+(sec%60)+s("с","s","с","s","s"):sec+s("с","s","с","s","s");}
    private String decimal(double value){String out=String.format(java.util.Locale.US,"%.1f",value);return Lang.dec(out);}
    private String fmtDurTable(int frames){return decimal(Math.max(0,frames)/(double)TPS)+s(" сек"," sec"," сек"," s"," s");}
    private String fmtSize(long b){if(b<1024)return b+" B";if(b<1048576)return decimal(b/1024d)+" KB";return decimal(b/1048576d)+" MB";}
    private static final java.text.SimpleDateFormat DATE_FMT=new java.text.SimpleDateFormat("dd.MM.yyyy, HH:mm",java.util.Locale.ROOT);
    private String fmtDate(long t){if(t<=0)return "--";synchronized(DATE_FMT){return DATE_FMT.format(new java.util.Date(t));}}
    private void drawInfo(){
        int slot=selectedSlot,frames=manager.getSlotFrameCount(slot);
        boolean active=manager.isSlotBusy(slot)||(returner()!=null&&returner().isSlotBusy(slot));
        String name=manager.getSlotName(slot),desc=manager.getSlotDescription(slot);
        if(name==null||name.isEmpty())name=frames>0?s("Запись "+slot,"Recording "+slot,"Запис "+slot,"Aufnahme "+slot,"Nagranie "+slot):s("Пустой слот","Empty slot","Порожній слот","Leerer Slot","Pusty slot");
        if(desc==null||desc.isEmpty())desc=frames>0?s("Описание не задано","No description","Опис не задано","Keine Beschreibung","Brak opisu"):s("Можно начать новую запись","Ready for a new recording","Можна почати новий запис","Bereit für eine neue Aufnahme","Można zacząć nowe nagranie");
        int accent=active?UiTheme.AMBER:frames>0?UiTheme.GREEN:UiTheme.GRAY;
        UiTheme.slotCard(infoX,infoY,infoRight,infoY+infoH,false,true,accent);
        boolean showStats=infoH>=46,table=frames>0&&infoH>=94;
        int barY=infoY+infoH-12;
        int badgeRoom=(showStats?barY-8:infoY+infoH-6)-(infoY+8);
        int badge=table?18:Math.max(10,Math.min(24,badgeRoom));
        UiTheme.badge(fontRenderer,String.valueOf(slot),infoX+10,infoY+8,badge,accent,frames>0);
        int tx=infoX+10+badge+8;
        String tag=active?s("ИСПОЛЬЗУЕТСЯ","IN USE","ВИКОРИСТОВУЄТЬСЯ","IN BENUTZUNG","W UŻYCIU"):frames>0?"":s("ПУСТО","EMPTY","ПОРОЖНЬО","LEER","PUSTY");
        int tw=tag.isEmpty()?0:fontRenderer.getStringWidth(tag)+8;
        if(!tag.isEmpty())UiTheme.chip(fontRenderer,tag,infoRight-10-tw,infoY+9,accent);
        UiTheme.beginClip(mc,infoX+6,infoY+4,infoRight-6,infoY+infoH-4);
        UiTheme.drawFitted(fontRenderer,name,tx,infoY+9,Math.max(24,infoRight-10-tw-tx-8),UiTheme.TEXT);
        int descRoom=Math.max(24,infoRight-tx-14);
        int descLimit=(showStats?barY-6:infoY+infoH-4)-8;
        if(table){
            String[] labels=Lang.pick(new String[]{"Тип","Описание","Кадры","Длительность","Размер","Создано"},new String[]{"Type","Description","Frames","Duration","Size","Created"},new String[]{"Тип","Опис","Кадри","Тривалість","Розмір","Створено"},new String[]{"Typ","Beschreibung","Frames","Dauer","Größe","Erstellt"},new String[]{"Typ","Opis","Klatki","Czas","Rozmiar","Utworzono"});
            String[] values=new String[]{config.isPreferredLoopMode()?s("Цикл","Loop","Цикл","Schleife","Pętla"):s("Один раз","Once","Один раз","Einmal","Raz"),desc,String.valueOf(frames),fmtDurTable(frames),fmtSize(manager.getSlotFileSize(slot)),fmtDate(manager.getSlotModified(slot))};
            UiTheme.infoTable(fontRenderer,labels,values,infoX+10,infoY+26,infoRight-10,infoY+infoH-6);
            UiTheme.endClip();
        }else{
            if(infoY+21<=descLimit){
                List<String> lines=fontRenderer.listFormattedStringToWidth(desc,descRoom);
                if(!lines.isEmpty())UiTheme.drawFitted(fontRenderer,lines.get(0),tx,infoY+21,descRoom,UiTheme.TEXT_DIM);
            }
            if(showStats){
                String stats=frames>0?((config.isPreferredLoopMode()?s("Цикл","Loop","Цикл","Schleife","Pętla"):"1×")+"  \u00b7  "+frames+s(" кадр."," fr."," кадр."," fr."," kl.")+"  \u00b7  "+fmtDur(frames)+"  \u00b7  "+fmtSize(manager.getSlotFileSize(slot))+"  \u00b7  "+fmtDate(manager.getSlotModified(slot)))
                    :s("Нажмите «Записать», чтобы начать","Press Record to start","Натисніть «Записати», щоб почати","Drücke «Aufnehmen», um zu starten","Naciśnij «Nagraj», aby zacząć");
                UiTheme.drawFitted(fontRenderer,stats,infoX+9,barY,Math.max(24,infoRight-infoX-18),frames>0?UiTheme.TEXT_DIM:UiTheme.TEXT_MUTE);
            }
            UiTheme.endClip();
        }
    }
    private void setScroll(int target){
        int max=MAX_SLOTS-VISIBLE_SLOTS,t=Math.max(0,Math.min(max,target));
        if(t==scrollOffset)return;
        scrollFrom=visScroll;scrollAt=System.currentTimeMillis();scrollOffset=t;
    }
    private void snapScroll(int target){
        int max=MAX_SLOTS-VISIBLE_SLOTS;scrollOffset=Math.max(0,Math.min(max,target));
        scrollFrom=scrollOffset;visScroll=scrollOffset;scrollAt=0L;
    }
    private float rowHover(int slot,boolean hovered,long now){
        if(hovered!=hovT[slot]){hovFrom[slot]=hovA[slot];hovT[slot]=hovered;hovAt[slot]=now;}
        hovA[slot]=UiTheme.transition(hovFrom[slot],hovered?1f:0f,hovAt[slot],now,UiTheme.HOVER_MS);
        return hovA[slot];
    }
    /** Слот под курсором с учётом текущей прокрутки: клик всегда попадает в ту строку, которая видна. */
    private int slotAt(int mx,int my){
        if(mx<listX||mx>listRight||my<listTop||my>=listBottom||rowH<=0)return -1;
        int idx=(int)Math.floor((my-listTop)/(double)rowH+visScroll)+1;
        if(idx<1||idx>MAX_SLOTS)return -1;
        int top=Math.round(listTop+(idx-1-visScroll)*rowH);
        return my>=top&&my<top+rowH-2?idx:-1;
    }
    private void drawSlotList(int mx,int my){
        long now=System.currentTimeMillis();
        visScroll=UiTheme.transition(scrollFrom,scrollOffset,scrollAt,now,UiTheme.SCROLL_MS);
        UiTheme.inset(listX-3,listTop-4,listRight+1,listBottom+4);
        int hovered=slotAt(mx,my);
        int first=Math.max(1,(int)Math.floor(visScroll)),last=Math.min(MAX_SLOTS,first+VISIBLE_SLOTS+1);
        UiTheme.beginClip(mc,listX-3,listTop-3,listRight+1,listBottom+3);
        for(int i=first;i<=last;i++){
            int top=Math.round(listTop+(i-1-visScroll)*rowH),bottom=top+rowH-2,frames=manager.getSlotFrameCount(i);
            if(bottom<listTop-2||top>listBottom+2)continue;
            boolean sel=i==selectedSlot;
            boolean active=manager.isSlotBusy(i)||(returner()!=null&&returner().isSlotBusy(i));
            float hv=rowHover(i,i==hovered,now);
            int color=active?UiTheme.lerp(UiTheme.AMBER,UiTheme.lerp(UiTheme.AMBER,0xFFFFFFFF,0.42f),UiTheme.pulse(1200L)):frames>0?UiTheme.GREEN:UiTheme.GRAY;
            UiTheme.slotCard(listX,top,listRight,bottom,hv,sel,color);
            int ty=top+(rowH-fontRenderer.FONT_HEIGHT)/2,slide=Math.round(UiTheme.ease(hv)*1.5f);
            UiTheme.dot(listX+8,top+(rowH-8)/2,color);
            int numberH=Math.max(10,Math.min(16,bottom-top-4)),numberY=top+(bottom-top-numberH)/2;
            UiTheme.slotNumber(fontRenderer,String.valueOf(i),listX+18,numberY,listX+44,numberY+numberH,color,sel);
            int nameX=listX+50+slide;
            if(frames==0)fontRenderer.drawString("\u00a7o"+s("пусто","empty","порожньо","leer","pusto"),nameX,ty,UiTheme.TEXT_MUTE);
            else{
                String meta=fmtDur(frames),name=manager.getSlotName(i);
                if(name==null||name.isEmpty())name=s("Запись "+i,"Recording "+i,"Запис "+i,"Aufnahme "+i,"Nagranie "+i);
                int metaW=fontRenderer.getStringWidth(meta),metaX=listRight-8-metaW;
                UiTheme.drawFitted(fontRenderer,name,nameX,ty,Math.max(8,metaX-nameX-6),sel?UiTheme.TEXT:UiTheme.lerp(UiTheme.TEXT_DIM,UiTheme.TEXT,UiTheme.ease(hv)));
                fontRenderer.drawString(meta,metaX,ty,sel?UiTheme.TEXT_DIM:UiTheme.TEXT_MUTE);
            }
        }
        UiTheme.endClip();
    }
    private void drawTooltips(int mx,int my){
        if(!config.isShowTooltips())return;
        for(GuiButton g:buttonList)if(g instanceof StyledButton){StyledButton b=(StyledButton)g;
            if(b.visible&&b.tooltip!=null&&mx>=b.x&&my>=b.y&&mx<b.x+b.width&&my<b.y+b.height){if(UiTheme.tipReady(b))UiTheme.tooltip(fontRenderer,b.tooltip,mx,my,width,height);return;}}
        {
            int i=slotAt(mx,my);
            if(i>0&&UiTheme.tipReady("slot"+i)){
                {
                    int frames=manager.getSlotFrameCount(i);
                    List<String> lines=new ArrayList<String>();
                    if(frames==0){
                        lines.add("\u00a7f"+s("Пустой слот "+i,"Empty slot "+i,"Порожній слот "+i,"Leerer Slot "+i,"Pusty slot "+i));
                        lines.add("\u00a77"+s("Сюда ещё ничего не записано.","Nothing is saved here yet.","Сюди ще нічого не записано.","Hier ist noch nichts gespeichert.","Nic tu jeszcze nie nagrano."));
                        lines.add("\u00a77"+s("Левая кнопка мыши — выбрать этот слот.","Left click selects this slot.","Ліва кнопка миші — вибрати цей слот.","Linksklick wählt diesen Slot.","Lewy przycisk myszy — wybiera ten slot."));
                        lines.add("\u00a78"+s("Правая кнопка — меню: загрузить файл, копия, удаление.","Right click opens the menu: load a file, copy, delete.","Права кнопка — меню: завантажити файл, копія, видалення.","Rechtsklick öffnet das Menü: Datei laden, Kopie, Löschen.","Prawy przycisk — menu: wczytaj plik, kopia, usunięcie."));
                    }else{
                        String name=manager.getSlotName(i);if(name==null||name.isEmpty())name=s("Запись "+i,"Recording "+i,"Запис "+i,"Aufnahme "+i,"Nagranie "+i);
                        String desc=manager.getSlotDescription(i);
                        lines.add("\u00a7f"+name+"  \u00a78#"+i);
                        if(desc!=null&&!desc.isEmpty())lines.add("\u00a77"+desc);
                        lines.add("\u00a77"+s("Длится: ","Lasts: ","Триває: ","Dauer: ","Czas: ")+fmtDur(frames)+"  \u00a78("+frames+s(" кадр.)"," fr.)"," кадр.)"," fr.)"," kl.)"));
                        lines.add("\u00a78"+fmtSize(manager.getSlotFileSize(i))+"  \u00b7  "+fmtDate(manager.getSlotModified(i)));
                        lines.add("\u00a77"+s("Левая кнопка мыши — выбрать эту запись.","Left click selects this recording.","Ліва кнопка миші — вибрати цей запис.","Linksklick wählt diese Aufnahme.","Lewy przycisk myszy — wybiera to nagranie."));
                        lines.add("\u00a78"+s("Правая кнопка — меню: имя, копия, файл, удаление.","Right click opens the menu: name, copy, file, delete.","Права кнопка — меню: ім'я, копія, файл, видалення.","Rechtsklick öffnet das Menü: Name, Kopie, Datei, Löschen.","Prawy przycisk — menu: nazwa, kopia, plik, usunięcie."));
                    }
                    if(UiTheme.tipReady(Integer.valueOf(i)))UiTheme.tooltip(fontRenderer,lines.toArray(new String[0]),mx,my,width,height);return;
                }
            }
        }
    }
    private void selectSlot(int slot){
        int target=Math.max(1,Math.min(MAX_SLOTS,slot));
        if(manager.selectSlot(target)){
            selectedSlot=target;
            if(target<=scrollOffset)setScroll(target-1);else if(target>scrollOffset+VISIBLE_SLOTS)setScroll(target-VISIBLE_SLOTS);
            try{config.save();}catch(Exception ignored){}
            initGui();
        }else if(manager.isBusy()||(returner()!=null&&returner().isBusy()))msg("\u00a7e"+s("Слот нельзя менять во время записи или повтора","The slot cannot be changed during recording or playback","Слот не можна змінювати під час запису або повтору","Der Slot kann während Aufnahme oder Wiedergabe nicht geändert werden","Slotu nie można zmieniać podczas nagrywania lub odtwarzania"));
    }
    @Override protected void mouseClicked(int mx,int my,int btn)throws IOException{
        super.mouseClicked(mx,my,btn);
        int slot=slotAt(mx,my);
        if(slot>0){if(btn==1)openSlotContext(slot,mx,my);else selectSlot(slot);}
    }
    private void openSlotContext(int slot,int mx,int my){
        if(manager.isBusy()||(returner()!=null&&returner().isBusy())){msg("\u00a7e"+s("Меню слота недоступно во время операции","Slot menu is unavailable during an operation","Меню слота недоступне під час операції","Slot-Menü während eines Vorgangs nicht verfügbar","Menu slotu niedostępne podczas operacji"));return;}
        if(slot!=selectedSlot)selectSlot(slot);if(selectedSlot==slot)mc.displayGuiScreen(new GuiMirrorSlotContext(this,manager,config,slot,mx,my));
    }
    private void openSlotTools(int slot){
        if(manager.isBusy()||(returner()!=null&&returner().isBusy())){msg("\u00a7e"+s("Данные слота недоступны во время записи или повтора","Slot data is unavailable during recording or playback","Дані слота недоступні під час запису або повтору","Slot-Daten während Aufnahme oder Wiedergabe nicht verfügbar","Dane slotu niedostępne podczas nagrywania lub odtwarzania"));return;}
        if(slot!=selectedSlot)selectSlot(slot);
        if(selectedSlot==slot)mc.displayGuiScreen(new GuiMirrorSlotTools(this,manager,config,slot));
    }
    @Override public void handleMouseInput()throws IOException{
        super.handleMouseInput();
        int wheel=Mouse.getEventDWheel();if(wheel==0)return;
        ScaledResolution sr=new ScaledResolution(mc);
        int mx=Mouse.getEventX()*sr.getScaledWidth()/Math.max(1,mc.displayWidth),my=sr.getScaledHeight()-Mouse.getEventY()*sr.getScaledHeight()/Math.max(1,mc.displayHeight)-1;
        if(mx>=listX-4&&mx<=listRight+12&&my>=listTop&&my<listBottom)setScroll(scrollOffset+(wheel<0?3:-3));
    }
    @Override protected void keyTyped(char c,int key)throws IOException{
        if(key==Keyboard.KEY_ESCAPE){mc.displayGuiScreen(null);return;}
        if(key==Keyboard.KEY_F1){mc.displayGuiScreen(new GuiMirrorHelp(this,config));return;}
        if(key==Keyboard.KEY_UP){selectSlot(selectedSlot-1);return;}
        if(key==Keyboard.KEY_DOWN){selectSlot(selectedSlot+1);return;}
        if(key==Keyboard.KEY_PRIOR){setScroll(scrollOffset-VISIBLE_SLOTS);return;}
        if(key==Keyboard.KEY_NEXT){setScroll(scrollOffset+VISIBLE_SLOTS);return;}
        if(key==Keyboard.KEY_HOME){selectSlot(1);return;}
        if(key==Keyboard.KEY_END){selectSlot(MAX_SLOTS);return;}
        if(key>=Keyboard.KEY_1&&key<=Keyboard.KEY_9){selectSlot(key-Keyboard.KEY_1+1);return;}
        if(key==Keyboard.KEY_0){selectSlot(10);return;}
        super.keyTyped(c,key);
    }
    @Override protected void actionPerformed(GuiButton b)throws IOException{
        int slot=selectedSlot;
        if((b.id==BTN_RECORD||b.id==BTN_PLAY||b.id==BTN_LOOP)&&returner()!=null&&returner().isRecovering())returner().cancel();
        switch(b.id){
            case BTN_RECORD:if(manager.scheduleRecording(slot)){msg("\u00a7a"+s("Запись слота "+slot+" начнётся через "+config.getStartDelay()+"с","Recording of slot "+slot+" starts in "+config.getStartDelay()+"s","Запис слота "+slot+" почнеться через "+config.getStartDelay()+"с","Aufnahme von Slot "+slot+" startet in "+config.getStartDelay()+"s","Nagranie slotu "+slot+" zacznie się za "+config.getStartDelay()+"s"));mc.displayGuiScreen(null);}
                else msg("\u00a7e"+s("Этот слот нельзя записать сейчас","This slot cannot be recorded right now","Цей слот зараз не можна записати","Dieser Slot kann gerade nicht aufgenommen werden","Tego slotu nie można teraz nagrać"));break;
            case BTN_PLAY:{com.mirror.recorder.handler.BaritoneReturnController r=returner();manager.setPreferredLoopMode(slot,false);if(r!=null&&r.request(slot,false))mc.displayGuiScreen(null);break;}
            case BTN_LOOP:{com.mirror.recorder.handler.BaritoneReturnController r=returner();manager.setPreferredLoopMode(slot,true);if(r!=null&&r.request(slot,true))mc.displayGuiScreen(null);break;}
            case BTN_STOP:{com.mirror.recorder.handler.BaritoneReturnController r=returner();
                if(manager.isBusy()||(r!=null&&(r.isBusy()||r.isRecovering()))){if(r!=null)r.cancel();manager.stopAll();msg("\u00a7e"+s("Действие остановлено","Action stopped","Дію зупинено","Aktion gestoppt","Akcja zatrzymana"));}break;}
            case BTN_SETTINGS:if(!manager.isBusy())mc.displayGuiScreen(new GuiMirrorSettings(manager,config,this));break;
            case BTN_CLOSE:mc.displayGuiScreen(null);break;
        }
    }
    public void contextRecord(int slot){if(manager.scheduleRecording(slot)){msg("\u00a7a"+s("Запись слота "+slot+" начнётся через "+config.getStartDelay()+"с","Recording of slot "+slot+" starts in "+config.getStartDelay()+"s","Запис слота "+slot+" почнеться через "+config.getStartDelay()+"с","Aufnahme von Slot "+slot+" startet in "+config.getStartDelay()+"s","Nagranie slotu "+slot+" zacznie się za "+config.getStartDelay()+"s"));mc.displayGuiScreen(null);}else{mc.displayGuiScreen(this);msg("\u00a7e"+s("Этот слот нельзя записать сейчас","This slot cannot be recorded right now","Цей слот зараз не можна записати","Dieser Slot kann gerade nicht aufgenommen werden","Tego slotu nie można teraz nagrać"));}}
    public void contextPlay(int slot,boolean loop){com.mirror.recorder.handler.BaritoneReturnController r=returner();manager.setPreferredLoopMode(slot,loop);if(r!=null&&r.request(slot,loop))mc.displayGuiScreen(null);else mc.displayGuiScreen(this);}
    public void contextDelete(int slot){String n=manager.getSlotName(slot);if(n==null||n.isEmpty())n=s("Запись ","Recording ","Запис ","Aufnahme ","Nagranie ")+slot;mc.displayGuiScreen(new GuiMirrorConfirm(this,slot,n));}
    public void confirmDelete(int slot,boolean confirmed){
        mc.displayGuiScreen(this);
        if(!confirmed)return;
        if((returner()!=null&&(returner().isSlotBusy(slot)||returner().isRecoveringSlot(slot)))||!manager.canDeleteSlot(slot)){msg("\u00a7e"+s("Удаление отменено: слот сейчас используется","Deletion cancelled: the slot is in use","Видалення скасовано: слот зараз використовується","Löschen abgebrochen: der Slot wird benutzt","Usuwanie anulowane: slot jest w użyciu"));return;}
        int del=manager.deleteSlotStatus(slot);
        if(del==com.mirror.recorder.storage.StorageManager.DELETE_TRASHED)msg("\u00a7a"+s("Слот "+slot+" удалён · копия лежит в корзине","Slot "+slot+" deleted · a copy is kept in the trash","Слот "+slot+" видалено · копія лежить у кошику","Slot "+slot+" gelöscht · eine Kopie liegt im Papierkorb","Slot "+slot+" usunięty · kopia leży w koszu"));
        else if(del==com.mirror.recorder.storage.StorageManager.DELETE_NO_COPY)msg("\u00a7e"+s("Слот "+slot+" удалён · копия не сохранена (файл нечитаем)","Slot "+slot+" deleted · no trash copy (the file was unreadable)","Слот "+slot+" видалено · копію не збережено (файл нечитабельний)","Slot "+slot+" gelöscht · keine Papierkorb-Kopie (Datei unlesbar)","Slot "+slot+" usunięty · brak kopii w koszu (plik nieczytelny)"));
        else if(del==com.mirror.recorder.storage.StorageManager.DELETE_PARTIAL)msg("\u00a7e"+s("Слот "+slot+" удалён, но часть служебных файлов осталась","Slot "+slot+" deleted, but some service files remain","Слот "+slot+" видалено, але частина службових файлів залишилась","Slot "+slot+" gelöscht, aber einige Hilfsdateien bleiben","Slot "+slot+" usunięty, ale część plików pomocniczych została"));
        else msg("\u00a7c"+s("Не удалось удалить слот. Данные сохранены","Could not delete the slot. Data is intact","Не вдалося видалити слот. Дані збережено","Slot konnte nicht gelöscht werden. Daten sind intakt","Nie udało się usunąć slotu. Dane są nietknięte"));
    }
    public void notifyResult(String text){msg(text);}
    private void msg(String text){com.mirror.recorder.handler.TransientChat.show(PREFIX+text);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
