package com.mirror.recorder.gui;
import com.mirror.recorder.config.RecorderConfig;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
/**
 * Справка по функциям мода: слева разделы, справа описание с прокруткой.
 * Текст только читается — никаких настроек этот экран не меняет.
 */
public class GuiMirrorHelp extends GuiScreen{

    private static final int BACK=1,PREV=2,NEXT=3,LANG=4;
    private final RecorderConfig config;private final GuiScreen parent;
    private int section=0,scroll=0,maxScroll=0,animTarget=-1;
    private float visScroll=0f,scrollFrom=0f,sb=0f,sbFrom=0f;private long scrollAt=0L,sbAt=0L;private boolean sbDrag=false,sbT=false;
    private final float[] hovA=new float[32],hovFrom=new float[32];private final long[] hovAt=new long[32];private final boolean[] hovT=new boolean[32];
    private int winX,winY,winW,winH,headH,listX,listRight,listTop,rowH,textX,textRight,textTop,textBottom,footerY;
    private StyledButton back,prev,next,lang;
    private List<String> wrapped=new ArrayList<String>();
    public GuiMirrorHelp(GuiScreen parent,RecorderConfig config){this.parent=parent;this.config=config;}

    static String[][] data(){return Lang.pick(RU,EN,UK,DE,PL);}
    private String[][] sections(){return data();}
    private String title(int i){return sections()[i][0];}

    private void layout(){
        winW=Math.min(width-14,552);winH=Math.min(height-14,348);winX=(width-winW)/2;winY=(height-winH)/2;headH=Math.min(34,Math.max(24,winH/9));
        int pad=12,gap=12;listX=winX+pad;int listW=(int)((winW-pad*2-gap)*0.36f);listRight=listX+listW;
        listTop=winY+headH+22;footerY=winY+winH-32;
        rowH=Math.min(18,Math.max(12,(footerY-9-listTop)/Math.max(1,sections().length)));
        textX=listRight+gap;textRight=winX+winW-pad;textTop=listTop;textBottom=footerY-9;
    }
    private StyledButton add(StyledButton b){buttonList.add(b);return b;}
    @Override public void initGui(){
        buttonList.clear();layout();
        int bw=86,bh=20,nav=22;
        prev=add(new StyledButton(PREV,listX,footerY,nav,bh,"\u2039").accent(UiTheme.GRAY).tip(Lang.tip("tip.helpPrev")));
        next=add(new StyledButton(NEXT,listRight-nav,footerY,nav,bh,"\u203a").accent(UiTheme.GRAY).tip(Lang.tip("tip.helpNext")));
        lang=add(new StyledButton(LANG,winX+winW-12-bw*2-6,footerY,bw,bh,Lang.name()).accent(UiTheme.PURPLE).tip(Lang.tip("tip.lang")));
        back=add(new StyledButton(BACK,winX+winW-12-bw,footerY,bw,bh,Lang.t("common.back")).primary().accent(UiTheme.ACCENT).tip(Lang.tip("tip.back")));
        rewrap();
    }
    private void rewrap(){
        wrapped=new ArrayList<String>();
        String[] body=sections()[Math.max(0,Math.min(sections().length-1,section))];
        int w=Math.max(40,textRight-textX-20);
        for(int i=1;i<body.length;i++){
            String line=body[i];
            if(line.isEmpty()){wrapped.add("");continue;}
            wrapped.addAll(fontRenderer.listFormattedStringToWidth(line,w));
        }
        int visible=Math.max(1,(textBottom-textTop)/10);
        maxScroll=Math.max(0,wrapped.size()-visible);
        if(scroll>maxScroll)scroll=maxScroll;
        if(scroll<0)scroll=0;
    }
    private void select(int index){
        int total=sections().length;
        section=((index%total)+total)%total;scroll=0;rewrap();
    }
    @Override public void drawScreen(int mx,int my,float pt){
        int bd=UiTheme.GLASS_BACKDROP;
        drawGradientRect(0,0,width,height,bd,bd);
        UiTheme.window(winX,winY,winX+winW,winY+winH,headH);
        UiTheme.header(fontRenderer,winX,winY,winX+winW,headH,Lang.t("help.title"),null,UiTheme.PURPLE);
        String counter=(section+1)+" "+Lang.t("help.of")+" "+sections().length;
        int cw=fontRenderer.getStringWidth(counter)+8;
        UiTheme.chip(fontRenderer,counter,winX+winW-12-cw,winY+(headH-11)/2,UiTheme.PURPLE);
        UiTheme.sectionLabel(fontRenderer,Lang.t("help.sections"),listX,listTop-13,listRight-listX);
        UiTheme.sectionLabel(fontRenderer,title(section).toUpperCase(java.util.Locale.ROOT),textX,textTop-13,textRight-textX);
        drawList(mx,my);
        drawBody(mx,my);
        super.drawScreen(mx,my,pt);
        if(config.isShowTooltips()){
            for(GuiButton g:buttonList)if(g instanceof StyledButton){StyledButton b=(StyledButton)g;
                if(b.visible&&b.tooltip!=null&&mx>=b.x&&my>=b.y&&mx<b.x+b.width&&my<b.y+b.height){if(UiTheme.tipReady(b))UiTheme.tooltip(fontRenderer,b.tooltip,mx,my,width,height);return;}}
            if(mx>=listX&&mx<=listRight&&my>=listTop&&my<listTop+sections().length*rowH&&UiTheme.tipReady("helpList"))
                UiTheme.tooltip(fontRenderer,Lang.tip("tip.helpList"),mx,my,width,height);
        }
    }
    private void drawList(int mx,int my){
        int y=listTop;
        for(int i=0;i<sections().length;i++){
            int top=y,bottom=y+rowH-2;
            boolean sel=i==section,hov=mx>=listX&&mx<=listRight&&my>=top&&my<bottom;
            float hv=rowHover(i,hov);int slide=Math.round(UiTheme.ease(hv)*1.5f);
            UiTheme.rowCard(listX,top,listRight,bottom,hv,sel,UiTheme.ACCENT);
            UiTheme.dot(listX+6,top+(rowH-8)/2,sel?UiTheme.ACCENT:UiTheme.GRAY);
            UiTheme.drawFitted(fontRenderer,title(i),listX+18+slide,top+(rowH-fontRenderer.FONT_HEIGHT)/2,listRight-listX-26,sel?UiTheme.TEXT:UiTheme.lerp(UiTheme.TEXT_DIM,UiTheme.TEXT,UiTheme.ease(hv)));
            y+=rowH;
        }
    }
    private float rowHover(int index,boolean hovered){
        if(index<0||index>=hovA.length)return hovered?1f:0f;
        long now=System.currentTimeMillis();
        if(hovered!=hovT[index]){hovFrom[index]=hovA[index];hovT[index]=hovered;hovAt[index]=now;}
        hovA[index]=UiTheme.transition(hovFrom[index],hovered?1f:0f,hovAt[index],now,UiTheme.HOVER_MS);
        return hovA[index];
    }
    private int visibleLines(){return Math.max(1,(textBottom-textTop-10)/10);}
    private boolean inScrollBar(int mx,int my){return maxScroll>0&&mx>=textRight-10&&mx<=textRight-1&&my>=textTop+4&&my<textBottom-4;}
    private void dragScroll(int my){
        int trackTop=textTop+4,trackBottom=textBottom-4,track=trackBottom-trackTop;
        int h=Math.min(track,Math.max(18,track*visibleLines()/Math.max(1,wrapped.size()))),span=Math.max(1,track-h);
        scroll=Math.max(0,Math.min(maxScroll,Math.round((my-trackTop-h/2f)*maxScroll/span)));
        animTarget=scroll;scrollFrom=scroll;visScroll=scroll;scrollAt=0L;
    }
    private void drawBody(int mx,int my){
        long now=System.currentTimeMillis();
        if(animTarget!=scroll){scrollFrom=visScroll;scrollAt=now;animTarget=scroll;}
        visScroll=UiTheme.transition(scrollFrom,scroll,scrollAt,now,UiTheme.SCROLL_MS);
        UiTheme.card(textX,textTop,textRight,textBottom,UiTheme.CARD,UiTheme.BORDER);
        int visible=visibleLines(),baseY=textTop+6;
        boolean sbHovered=sbDrag||inScrollBar(mx,my);
        if(sbHovered!=sbT){sbFrom=sb;sbT=sbHovered;sbAt=now;}
        sb=UiTheme.transition(sbFrom,sbHovered?1f:0f,sbAt,now,UiTheme.HOVER_MS);
        int first=Math.max(0,(int)Math.floor(visScroll)),last=Math.min(wrapped.size()-1,first+visible);
        int textW=Math.max(20,textRight-textX-(maxScroll>0?20:18));
        UiTheme.beginClip(mc,textX+1,textTop+3,textRight-1,textBottom-3);
        for(int i=first;i<=last;i++){
            int y=Math.round(baseY+(i-visScroll)*10);
            if(y+8<textTop+2||y>textBottom-4)continue;
            UiTheme.drawFitted(fontRenderer,wrapped.get(i),textX+8,y,textW,UiTheme.TEXT_DIM);
        }
        UiTheme.endClip();
        if(maxScroll>0)UiTheme.scrollBar(textRight-8,textTop+4,textBottom-4,visible,wrapped.size(),visScroll,UiTheme.ACCENT,sb);
    }
    @Override protected void mouseClicked(int mx,int my,int btn)throws IOException{
        super.mouseClicked(mx,my,btn);
        if(btn==0&&inScrollBar(mx,my)){sbDrag=true;dragScroll(my);return;}
        if(mx>=listX&&mx<=listRight){int y=listTop;for(int i=0;i<sections().length;i++){if(my>=y&&my<y+rowH-2){select(i);return;}y+=rowH;}}
    }
    @Override protected void mouseClickMove(int mx,int my,int btn,long since){if(sbDrag&&btn==0){dragScroll(my);return;}super.mouseClickMove(mx,my,btn,since);}
    @Override protected void mouseReleased(int mx,int my,int state){sbDrag=false;super.mouseReleased(mx,my,state);}
    @Override public void handleMouseInput()throws IOException{
        super.handleMouseInput();int wheel=Mouse.getEventDWheel();if(wheel==0)return;
        scroll=Math.max(0,Math.min(maxScroll,scroll+(wheel<0?3:-3)));
    }
    @Override protected void keyTyped(char c,int key)throws IOException{
        if(key==Keyboard.KEY_ESCAPE){mc.displayGuiScreen(parent);return;}
        if(key==Keyboard.KEY_LEFT){select(section-1);return;}
        if(key==Keyboard.KEY_RIGHT){select(section+1);return;}
        if(key==Keyboard.KEY_UP){scroll=Math.max(0,scroll-1);return;}
        if(key==Keyboard.KEY_DOWN){scroll=Math.min(maxScroll,scroll+1);return;}
        super.keyTyped(c,key);
    }
    @Override protected void actionPerformed(GuiButton b)throws IOException{
        if(b.id==BACK){mc.displayGuiScreen(parent);return;}
        if(b.id==PREV){select(section-1);return;}
        if(b.id==NEXT){select(section+1);return;}
        if(b.id==LANG){config.setLanguage(Lang.next());config.save();section=Math.min(section,sections().length-1);initGui();return;}
    }
    @Override public boolean doesGuiPauseGame(){return false;}

    // ------------------------------------------------------------------
    // Содержание справки. Первая строка каждого блока — название раздела.
    // ------------------------------------------------------------------
    private static final String[][] RU={
        {"Что делает мод",
         "§fМод запоминает ваши нажатия и повторяет их",
         "§7Он записывает клавиши, кнопки мыши, повороты мышью и то, какой предмет вы держали в руке.",
         "§7Всё пишется по порядку, 20 шагов в секунду — так же, как игра считает время.",
         "",
         "§fЧто мод не запоминает",
         "§7Мир вокруг, вещи в инвентаре и то, что получилось в итоге.",
         "§7Мод повторит ровно те же нажатия, но результат может выйти другим.",
         "§7Пример: в записи вы копали землю, а сейчас перед вами камень — итог будет не таким же.",
         "",
         "§8Если игра лагает, мод немного помогает держаться пути. Об этом — в разделе «Если игра лагает»."},

        {"Как записать",
         "§fПо шагам",
         "§71. Слева выберите пустой слот — строку без записи.",
         "§72. Нажмите «Записать». Окно закроется и пойдёт отсчёт.",
         "§73. Играйте как обычно.",
         "§74. Чтобы закончить, напишите в чат: /mirror stop",
         "",
         "§fЧто попадёт в запись",
         "§7Ходьба: W, A, S, D — каждая клавиша отдельно.",
         "§7Прыжок, приседание, бег, Q (выбросить), F (сменить руку), E (инвентарь) и колёсико мыши.",
         "§7Клики левой и правой кнопкой — вместе с тем, сколько вы держали кнопку.",
         "§7Какой предмет был в руке и куда вы вели мышь.",
         "§7Сообщения в чат — если это включено в настройках.",
         "",
         "§8В один слот влезает до 60 минут игры."},

        {"Как повторить",
         "§fПо шагам",
         "§71. Слева выберите слот с записью.",
         "§72. Нажмите «Воспроизвести» — мод проиграет запись один раз.",
         "§7Перед началом мод сам приведёт вас туда, где начиналась запись, и повернёт взгляд как тогда.",
         "",
         "§fЧто будет во время повтора",
         "§7Клавиши нажимаются и отпускаются точно так же, как у вас.",
         "§7Долгие нажатия сохраняются: натянутый лук, еда, зелья, щит, удочка, ломание блока.",
         "§7Удары идут с той же скоростью — мод ничего не замедляет.",
         "§7Предмет в руке меняется сам.",
         "",
         "§fКак остановить",
         "§7Нажмите «Остановить», или напишите в чат /mirror stop, или просто пойдите сами — если включён «Стоп при моём движении»."},

        {"Повтор по кругу",
         "§fЗациклить",
         "§7Запись играется снова и снова. Перед каждым разом мод возвращает вас на старт.",
         "",
         "§fСколько раз повторить",
         "§70 — бесконечно, пока не остановите сами.",
         "§7Или поставьте от 2 до 1000 повторов — после последнего мод остановится сам.",
         "",
         "§fОтсчёт перед началом",
         "§7Включено: отсчёт будет один раз, только перед самым первым повтором.",
         "§7Выключено: отсчёт идёт каждый раз — удобно, если нужна пауза между повторами."},

        {"Возврат на старт",
         "§fЗачем это нужно",
         "§7Чтобы каждый повтор начинался с того же места, где вы начали запись.",
         "§7Сначала вас приводит туда мод Baritone, а потом наш мод точно доводит вас до нужной точки.",
         "§8Baritone не установлен? Ничего страшного: мод сам подправит позицию, если вы стоите рядом.",
         "",
         "§fТочность",
         "§7Насколько близко нужно встать к точке старта: от 0.05 до 0.5 блока.",
         "§7Меньше значение — точнее, но возврат займёт больше времени.",
         "",
         "§fСкорость подхода",
         "§7Как быстро мод подходит к точке: от 0.2 до 1.",
         "§7Ставьте меньше, если вокруг тесно и легко упасть.",
         "",
         "§fСколько ждать",
         "§7От 15 до 180 секунд. Если за это время вернуться не вышло, мод отменит повтор, а не будет висеть."},

        {"Если игра лагает",
         "§fЗачем это нужно",
         "§7Из-за лагов сервера или подвисаний игры те же нажатия могут увести вас немного в сторону.",
         "§7Мод мягко подталкивает персонажа назад на путь из записи. Сами нажатия он при этом не меняет.",
         "",
         "§fКак это работает",
         "§7Подталкивание слабое, телепортов нет.",
         "§7Если вас увело больше чем на 3 блока, повтор останавливается сам.",
         "§7В воде, в лаве, в полёте, на лестнице и на лошади помощь выключается.",
         "",
         "§fНаправление взгляда",
         "§7Перед стартом мод ставит взгляд туда, куда вы смотрели в начале записи.",
         "§cЛучше не отключать: повороты записаны как сдвиги мыши, поэтому от начального взгляда зависит весь путь."},

        {"Что повторять",
         "§fЗдесь можно отключить лишнее",
         "§7Ходьба: выключите — персонаж стоит на месте, но взгляд, клики и чат продолжают играться.",
         "§7Прыжки, приседание, бег — отдельные переключатели для пробела, Shift и клавиши бега.",
         "§7Клики: левая и правая кнопки мыши вместе с удержанием и сменой предмета в руке.",
         "§7Чат: сообщения и команды отправятся заново в те же моменты.",
         "§8Если что-то выключить, повтор будет уже не точным и путь может сбиться.",
         "§8Частые сообщения сервер может не пропустить — это его защита от спама, мод тут не поможет.",
         "",
         "§fСтоп при моём движении",
         "§7Нажали W, A, S, D или пробел — повтор сразу прекращается. На запись это не влияет."},

        {"Слоты и файлы",
         "§fСлоты",
         "§7Всего 100 слотов. В каждом лежит одна запись: название, описание и свои настройки.",
         "§7Настройки, сохранённые при выбранном слоте с записью, запомнятся именно для неё.",
         "",
         "§fЧто можно сделать с записью",
         "§7Переименовать, добавить описание, скопировать в другой слот, сохранить в файл, загрузить из файла или удалить.",
         "",
         "§fГде лежат файлы",
         "§7Записи: папка игры → mirror_recorder → slot_1.nbt, slot_2.nbt и так далее.",
         "§7Рядом лежит копия slot_1.bak — на случай сбоя.",
         "§7Файлы, сохранённые вручную: mirror_recorder → exports.",
         "§8Если игра закроется во время сохранения, мод возьмёт запись из копии.",
         "",
         "§fСтарые записи",
         "§7Записи из прежних версий мода тоже открываются и работают."},

        {"Внешний вид",
         "§fДва объекта в мире",
         "§7Маршрут — линия пути повтора. Метка — значок там, где началась запись.",
         "§7В настройках сверху есть превью: оно сразу показывает, что будет нарисовано.",
         "§7Кнопки «Маршрут» и «Метка старта» выбирают, какой объект вы сейчас меняете. Выбранный объект подсвечивается.",
         "",
         "§fКак настраивать",
         "§7Сначала выберите объект, потом категорию: что показать, вид, положение, размеры, эффекты.",
         "§7Каждая строка подписана простыми словами: что изменится на экране после нажатия.",
         "",
         "§fПанель на экране",
         "§7Показывает, что идёт сейчас: запись или повтор, номер слота, круг и сколько осталось.",
         "",
         "§fПодсказки и язык",
         "§7Эти подсказки при наведении можно отключить кнопкой на главном экране. Язык переключается в Настройках, закладка «Интерфейс».",
         "",
         "§fКоманды в чат",
         "§7/mirror gui — открыть окно мода.",
         "§7/mirror stop — остановить запись или повтор.", "§7/mirror pause — пауза и продолжение повтора.", "§7/mirror selftest — автотест движка: события и метки времени."},
        {"Защита и корзина",
         "§fСтоп при уроне",
         "§7Если по вам попали, мод сразу выключает запись или повтор и отпускает все клавиши.",
         "§8Тумблер: Настройки → Управление → «Стоп при уроне».",
         "",
         "§fКорзина",
         "§7Удалённая запись не исчезает: копия ложится в корзину.",
         "§7Настройки → Интерфейс → «Корзина»: нажмите карточку, затем большую зелёную кнопку «Вернуть в слот».",
         "§7«Последнее удаление» возвращает то, что убрали только что. «Очистить всё» спрашивает ещё раз.",
         "§8Хранятся 100 последних удалений, слот для возврата должен быть пустым."}
    };

    private static final String[][] EN={
        {"What the mod does",
         "§fThe mod remembers your key presses and plays them back",
         "§7It saves keys, mouse buttons, mouse movement and which item you held.",
         "§7Everything is stored in order, 20 steps per second — the same way the game counts time.",
         "",
         "§fWhat the mod does not save",
         "§7The world around you, your inventory and the result of your actions.",
         "§7It repeats the exact same presses, but the outcome can turn out different.",
         "§7Example: you dug dirt while recording, but now there is stone in front of you.",
         "",
         "§8If the game lags, the mod helps a little to stay on the path. See «If the game lags»."},

        {"How to record",
         "§fStep by step",
         "§71. Pick an empty slot on the left — a row with no recording.",
         "§72. Press «Record». The window closes and a countdown starts.",
         "§73. Just play as usual.",
         "§74. To finish, type in chat: /mirror stop",
         "",
         "§fWhat gets recorded",
         "§7Walking: W, A, S, D — every key separately.",
         "§7Jump, sneak, sprint, Q (drop), F (swap hands), E (inventory) and the mouse wheel.",
         "§7Left and right clicks — together with how long you held the button.",
         "§7Which item was in your hand and where you moved the mouse.",
         "§7Chat messages — if that is turned on in the settings.",
         "",
         "§8One slot fits up to 60 minutes of play."},

        {"How to play back",
         "§fStep by step",
         "§71. Pick a slot with a recording on the left.",
         "§72. Press «Play» — the mod plays the recording once.",
         "§7Before it starts, the mod walks you to where the recording began and turns your view as it was.",
         "",
         "§fWhat happens during playback",
         "§7Keys are pressed and released exactly as you did it.",
         "§7Long holds are kept: drawing a bow, eating, potions, shield, fishing rod, breaking blocks.",
         "§7Hits keep their original speed — the mod slows nothing down.",
         "§7The item in your hand switches by itself.",
         "",
         "§fHow to stop",
         "§7Press «Stop», type /mirror stop, or simply walk yourself — if «Stop on my movement» is on."},

        {"Playing in a loop",
         "§fLoop",
         "§7The recording plays again and again. Before each run the mod brings you back to the start.",
         "",
         "§fHow many times",
         "§70 — endless, until you stop it yourself.",
         "§7Or set 2 to 1000 runs — after the last one the mod stops by itself.",
         "",
         "§fCountdown before the start",
         "§7On: the countdown happens once, only before the very first run.",
         "§7Off: the countdown happens every time — handy if you want a pause between runs."},

        {"Return to the start",
         "§fWhy this is needed",
         "§7So every run starts from the same spot where you began recording.",
         "§7First Baritone walks you there, then our mod moves you precisely onto the exact point.",
         "§8No Baritone installed? That is fine: the mod adjusts your position itself if you stand nearby.",
         "",
         "§fHow precise",
         "§7How close you must stand to the start point: from 0.05 to 0.5 blocks.",
         "§7A smaller value is more precise but the return takes longer.",
         "",
         "§fApproach speed",
         "§7How fast the mod walks to the point: from 0.2 to 1.",
         "§7Set it lower if the area is tight and it is easy to fall.",
         "",
         "§fHow long to wait",
         "§7From 15 to 180 seconds. If the return fails in that time, the mod cancels the run instead of hanging."},

        {"If the game lags",
         "§fWhy this is needed",
         "§7Because of server lag or game freezes the same presses can take you slightly off course.",
         "§7The mod gently nudges your character back onto the recorded path. Your presses stay untouched.",
         "",
         "§fHow it works",
         "§7The nudge is weak and never teleports you.",
         "§7If you drift more than 3 blocks away, playback stops by itself.",
         "§7In water, in lava, while flying, on ladders and while riding the help turns off.",
         "",
         "§fView direction",
         "§7Before the start the mod points your view where you looked at the beginning of the recording.",
         "§cBetter keep it on: turns are saved as mouse shifts, so the starting view defines the whole path."},

        {"What to play back",
         "§fHere you can turn off what you do not need",
         "§7Walking: turn it off and the character stands still, while view, clicks and chat keep playing.",
         "§7Jump, sneak, sprint — separate switches for space, Shift and the sprint key.",
         "§7Clicks: left and right mouse buttons with holds and item switching.",
         "§7Chat: messages and commands are sent again at the same moments.",
         "§8If you turn something off, the playback is no longer exact and the path may drift.",
         "§8Frequent messages can be blocked by the server — that is its anti-spam, the mod cannot help.",
         "",
         "§fStop on my movement",
         "§7Press W, A, S, D or space and playback stops right away. Recording is not affected."},

        {"Slots and files",
         "§fSlots",
         "§7There are 100 slots. Each holds one recording: name, description and its own settings.",
         "§7Settings saved while a filled slot is selected are remembered for that recording.",
         "",
         "§fWhat you can do with a recording",
         "§7Rename it, add a description, copy it to another slot, save it to a file, load it from a file or delete it.",
         "",
         "§fWhere the files are",
         "§7Recordings: game folder → mirror_recorder → slot_1.nbt, slot_2.nbt and so on.",
         "§7A copy slot_1.bak sits next to it, just in case.",
         "§7Files you saved by hand: mirror_recorder → exports.",
         "§8If the game closes while saving, the mod takes the recording from the copy.",
         "",
         "§fOld recordings",
         "§7Recordings from older versions of the mod still open and work."},

        {"Look and feel",
         "§fTwo objects in the world",
         "§7Route is the playback path. Marker is the mark where the recording began.",
         "§7The preview at the top shows exactly what will be drawn.",
         "§7The Route and Start mark buttons pick which object you are editing. The selected one is highlighted.",
         "",
         "§fHow to edit",
         "§7Pick the object first, then a group: what to show, look, position, size, effects.",
         "§7Every row says in plain words what will change on screen.",
         "",
         "§fOn-screen panel",
         "§7Shows what is going on now: recording or playback, slot number, run and time left.",
         "",
         "§fTooltips and language",
         "§7These hover tooltips can be turned off with a button on the main screen. The language is switched in Settings, tab «Interface».",
         "",
         "§fChat commands",
         "§7/mirror gui — open the mod window.",
         "§7/mirror stop — stop recording or playback.", "§7/mirror pause — pause and resume playback.", "§7/mirror selftest — engine self-test: events and timestamps."},
        {"Safety and trash",
         "§fStop when hurt",
         "§7If something hits you, the mod stops recording or playback at once and releases every key.",
         "§8Toggle: Settings → Controls → \"Stop when hurt\".",
         "",
         "§fTrash",
         "§7A deleted recording does not vanish: a copy goes to the trash.",
         "§7Settings → Interface → Trash: tap a card, then the big green Restore to slot button.",
         "§7Last deletion brings back what you just removed. Empty all asks once more.",
         "§8The last 100 deletions are kept and the target slot has to be empty."}
    };
    private static final String[][] UK={
        {"Що робить мод", "§fМод запам'ятовує ваші натискання і повторює їх", "§7Він записує клавіші, кнопки миші, повороти мишею та те, який предмет ви тримали в руці.", "§7Все пишеться по порядку, 20 кроків на секунду — так само, як гра рахує час.", "", "§fЩо мод не запам'ятовує", "§7Світ навколо, речі в інвентарі та те, що вийшло зрештою.", "§7Мод повторить рівно ті самі натискання, але результат може вийти іншим.", "§7Приклад: у записі ви копали землю, а зараз перед вами камінь — підсумок буде не таким самим.", "", "§8Якщо гра лагає, мод трохи допомагає триматися шляху. Про це — у розділі «Якщо гра лагає»."},
        {"Як записати", "§fКрок за кроком", "§71. Ліворуч виберіть порожній слот — рядок без запису.", "§72. Натисніть «Записати». Вікно закриється і піде відлік.", "§73. Грайте як зазвичай.", "§74. Щоб закінчити, напишіть у чат: /mirror stop", "", "§fЩо потрапить у запис", "§7Ходьба: W, A, S, D — кожна клавіша окремо.", "§7Стрибок, присідання, біг, Q (викинути), F (змінити руку), E (інвентар) і коліщатко миші.", "§7Кліки лівою та правою кнопкою — разом із тим, скільки ви тримали кнопку.", "§7Який предмет був у руці і куди ви вели мишу.", "§7Повідомлення в чат — якщо це увімкнено в налаштуваннях.", "", "§8В один слот влазить до 60 хвилин гри."},
        {"Як повторити", "§fКрок за кроком", "§71. Ліворуч виберіть слот із записом.", "§72. Натисніть «Відтворити» — мод програє запис один раз.", "§7Перед початком мод сам приведе вас туди, де починався запис, і поверне погляд як тоді.", "", "§fЩо буде під час повтору", "§7Клавіші натискаються й відпускаються точно так само, як у вас.", "§7Довгі натискання зберігаються: натягнутий лук, їжа, зілля, щит, вудка, руйнування блоку.", "§7Удари йдуть з тією ж швидкістю — мод нічого не сповільнює.", "§7Предмет у руці змінюється сам.", "", "§fЯк зупинити", "§7Натисніть «Зупинити», або напишіть у чат /mirror stop, або просто підіть самі — якщо увімкнено «Стоп при моєму русі»."},
        {"Повтор по колу", "§fЗациклити", "§7Запис грається знову і знову. Перед кожним разом мод повертає вас на старт.", "", "§fСкільки разів повторити", "§70 — нескінченно, поки не зупините самі.", "§7Або поставте від 2 до 1000 повторів — після останнього мод зупиниться сам.", "", "§fВідлік перед початком", "§7Увімкнено: відлік буде один раз, тільки перед самим першим повтором.", "§7Вимкнено: відлік іде щоразу — зручно, якщо потрібна пауза між повторами."},
        {"Повернення на старт", "§fНавіщо це потрібно", "§7Щоб кожен повтор починався з того ж місця, де ви почали запис.", "§7Спочатку вас приводить туди мод Baritone, а потім наш мод точно доводить вас до потрібної точки.", "§8Baritone не встановлено? Нічого страшного: мод сам поправить позицію, якщо ви стоїте поруч.", "", "§fТочність", "§7Наскільки близько треба встати до точки старту: від 0.05 до 0.5 блока.", "§7Менше значення — точніше, але повернення займе більше часу.", "", "§fШвидкість підходу", "§7Як швидко мод підходить до точки: від 0.2 до 1.", "§7Ставте менше, якщо навколо тісно і легко впасти.", "", "§fСкільки чекати", "§7Від 15 до 180 секунд. Якщо за цей час повернутися не вийшло, мод скасує повтор, а не висітиме."},
        {"Якщо гра лагає", "§fНавіщо це потрібно", "§7Через лаги сервера або підвисання гри ті самі натискання можуть відвести вас трохи вбік.", "§7Мод м'яко підштовхує персонажа назад на шлях із запису. Самі натискання він при цьому не змінює.", "", "§fЯк це працює", "§7Підштовхування слабке, телепортів немає.", "§7Якщо вас віднесло більше ніж на 3 блоки, повтор зупиняється сам.", "§7У воді, в лаві, у польоті, на драбині та на коні допомога вимикається.", "", "§fНапрямок погляду", "§7Перед стартом мод ставить погляд туди, куди ви дивилися на початку запису.", "§cКраще не відключати: повороти записані як зсуви миші, тому від початкового погляду залежить весь шлях."},
        {"Що повторювати", "§fТут можна вимкнути зайве", "§7Ходьба: вимкніть — персонаж стоїть на місці, але погляд, кліки та чат продовжують гратися.", "§7Стрибки, присідання, біг — окремі перемикачі для пробілу, Shift і клавіші бігу.", "§7Кліки: ліва й права кнопки миші разом із утриманням і зміною предмета в руці.", "§7Чат: повідомлення й команди надішлються заново в ті самі моменти.", "§8Якщо щось вимкнути, повтор буде вже не точним і шлях може збитися.", "§8Часті повідомлення сервер може не пропустити — це його захист від спаму, мод тут не допоможе.", "", "§fСтоп при моєму русі", "§7Натиснули W, A, S, D або пробіл — повтор одразу припиняється. На запис це не впливає."},
        {"Слоти й файли", "§fСлоти", "§7Всього 100 слотів. У кожному лежить один запис: назва, опис і свої налаштування.", "§7Налаштування, збережені при вибраному слоті із записом, запам'ятаються саме для нього.", "", "§fЩо можна зробити із записом", "§7Перейменувати, додати опис, скопіювати в інший слот, зберегти у файл, завантажити з файлу або видалити.", "", "§fДе лежать файли", "§7Записи: папка гри → mirror_recorder → slot_1.nbt, slot_2.nbt і так далі.", "§7Поруч лежить копія slot_1.bak — на випадок збою.", "§7Файли, збережені вручну: mirror_recorder → exports.", "§8Якщо гра закриється під час збереження, мод візьме запис із копії.", "", "§fСтарі записи", "§7Записи з попередніх версій мода теж відкриваються і працюють."},
        {"Зовнішній вигляд", "§fДва об'єкти у світі", "§7Маршрут — лінія шляху повтору. Мітка — значок там, де почався запис.", "§7У налаштуваннях зверху є прев'ю: воно одразу показує, що буде намальовано.", "§7Кнопки «Маршрут» і «Мітка старту» вибирають, який об'єкт ви зараз змінюєте. Вибраний об'єкт підсвічується.", "", "§fЯк налаштовувати", "§7Спочатку виберіть об'єкт, потім категорію: що показати, вигляд, положення, розміри, ефекти.", "§7Кожен рядок підписаний простими словами: що зміниться на екрані після натискання.", "", "§fПанель на екрані", "§7Показує, що йде зараз: запис або повтор, номер слота, коло і скільки залишилось.", "", "§fПідказки й мова", "§7Ці підказки при наведенні можна вимкнути кнопкою на головному екрані. Там же змінюється мова.", "", "§fКоманди в чат", "§7/mirror gui — відкрити вікно мода.", "§7/mirror stop — зупинити запис або повтор."},
        {"Захист і кошик", "§fСтоп при шкоді", "§7Якщо по вас влучили, мод одразу вимикає запис або повтор і відпускає всі клавіші.", "§8Перемикач: Налаштування → Керування → «Стоп при шкоді».", "", "§fКошик", "§7Видалений запис не зникає: копія лягає у кошик.", "§7Налаштування → Інтерфейс → «Кошик»: натисніть картку, потім велику зелену кнопку «Повернути в слот».", "§7«Останнє видалення» повертає те, що прибрали щойно. «Очистити все» питає ще раз.", "§8Зберігаються 100 останніх видалень, слот для повернення має бути порожнім."},
    };
    private static final String[][] DE={
        {"Was die Mod macht", "§fDie Mod merkt sich deine Tastendrücke und spielt sie ab", "§7Sie speichert Tasten, Maustasten, Mausbewegungen und welchen Gegenstand du in der Hand hattest.", "§7Alles wird der Reihe nach gespeichert, 20 Schritte pro Sekunde — so zählt auch das Spiel die Zeit.", "", "§fWas die Mod nicht speichert", "§7Die Welt um dich herum, dein Inventar und das Ergebnis deiner Aktionen.", "§7Sie wiederholt exakt dieselben Tastendrücke, aber das Ergebnis kann anders ausfallen.", "§7Beispiel: In der Aufnahme hast du Erde gegraben, doch jetzt liegt Stein vor dir.", "", "§8Wenn das Spiel laggt, hilft die Mod ein wenig, auf dem Weg zu bleiben. Mehr dazu unter «Wenn das Spiel laggt»."},
        {"Wie aufnehmen", "§fSchritt für Schritt", "§71. Wähle links einen leeren Slot — eine Zeile ohne Aufnahme.", "§72. Drücke «Aufnehmen». Das Fenster schließt sich und ein Countdown startet.", "§73. Spiele einfach wie gewohnt.", "§74. Zum Beenden tippe in den Chat: /mirror stop", "", "§fWas aufgenommen wird", "§7Laufen: W, A, S, D — jede Taste einzeln.", "§7Springen, Schleichen, Sprinten, Q (fallen lassen), F (Hand wechseln), E (Inventar) und das Mausrad.", "§7Links- und Rechtsklicks — zusammen mit der Haltezeit.", "§7Welcher Gegenstand in der Hand war und wohin du die Maus bewegt hast.", "§7Chat-Nachrichten — falls in den Einstellungen aktiviert.", "", "§8In einen Slot passen bis zu 60 Minuten Spiel."},
        {"Wie abspielen", "§fSchritt für Schritt", "§71. Wähle links einen Slot mit einer Aufnahme.", "§72. Drücke «Abspielen» — die Mod spielt die Aufnahme einmal ab.", "§7Vor dem Start bringt die Mod dich dorthin, wo die Aufnahme begann, und dreht deinen Blick wie damals.", "", "§fWas während der Wiedergabe passiert", "§7Tasten werden genau so gedrückt und losgelassen, wie du es getan hast.", "§7Langes Halten bleibt erhalten: Bogen spannen, Essen, Tränke, Schild, Angel, Blöcke abbauen.", "§7Schläge behalten ihr Tempo — die Mod bremst nichts.", "§7Der Gegenstand in der Hand wechselt von selbst.", "", "§fWie stoppen", "§7Drücke «Stopp», tippe /mirror stop in den Chat oder laufe einfach selbst — falls «Stopp bei eigener Bewegung» an ist."},
        {"Wiedergabe in Schleife", "§fWiederholen", "§7Die Aufnahme läuft immer wieder. Vor jedem Durchlauf bringt die Mod dich zurück zum Start.", "", "§fWie oft wiederholen", "§70 — endlos, bis du selbst stoppst.", "§7Oder stelle 2 bis 1000 Durchläufe ein — nach dem letzten stoppt die Mod von selbst.", "", "§fCountdown vor dem Start", "§7An: der Countdown läuft einmal, nur vor dem allerersten Durchlauf.", "§7Aus: der Countdown läuft jedes Mal — praktisch, wenn du eine Pause zwischen den Durchläufen willst."},
        {"Rückkehr zum Start", "§fWozu das dient", "§7Damit jeder Durchlauf an derselben Stelle beginnt, an der du die Aufnahme gestartet hast.", "§7Zuerst bringt dich die Baritone-Mod dorthin, danach bewegt dich unsere Mod exakt auf den Punkt.", "§8Baritone nicht installiert? Kein Problem: die Mod korrigiert deine Position selbst, wenn du in der Nähe stehst.", "", "§fGenauigkeit", "§7Wie nah du am Startpunkt stehen musst: von 0.05 bis 0.5 Blöcke.", "§7Ein kleinerer Wert ist genauer, aber die Rückkehr dauert länger.", "", "§fAnnäherungstempo", "§7Wie schnell die Mod zum Punkt läuft: von 0.2 bis 1.", "§7Stelle es niedriger, wenn es eng ist und man leicht fallen kann.", "", "§fWie lange warten", "§7Von 15 bis 180 Sekunden. Wenn die Rückkehr in dieser Zeit scheitert, bricht die Mod den Durchlauf ab, statt zu hängen."},
        {"Wenn das Spiel laggt", "§fWozu das dient", "§7Wegen Server-Lag oder Spiel-Freezes können dieselben Tastendrücke dich leicht vom Kurs abbringen.", "§7Die Mod schubst deinen Charakter sanft zurück auf den aufgenommenen Weg. Die Tastendrücke bleiben unverändert.", "", "§fWie es funktioniert", "§7Der Schubs ist schwach, es gibt keine Teleports.", "§7Wenn du mehr als 3 Blöcke abdriftest, stoppt die Wiedergabe von selbst.", "§7Im Wasser, in Lava, beim Fliegen, auf Leitern und beim Reiten schaltet sich die Hilfe ab.", "", "§fBlickrichtung", "§7Vor dem Start richtet die Mod deinen Blick dorthin, wo du zu Beginn der Aufnahme hingeschaut hast.", "§cBesser aktiviert lassen: Drehungen sind als Mausbewegungen gespeichert, daher bestimmt der Startblick den ganzen Weg."},
        {"Was wiederholen", "§fHier kannst du abschalten, was du nicht brauchst", "§7Laufen: ausgeschaltet steht der Charakter still, während Blick, Klicks und Chat weiterlaufen.", "§7Springen, Schleichen, Sprinten — eigene Schalter für Leertaste, Shift und die Sprinttaste.", "§7Klicks: linke und rechte Maustaste samt Halten und Gegenstandswechsel.", "§7Chat: Nachrichten und Befehle werden zu denselben Momenten erneut gesendet.", "§8Wenn du etwas abschaltest, ist die Wiedergabe nicht mehr exakt und der Weg kann abweichen.", "§8Häufige Nachrichten kann der Server blockieren — das ist sein Spam-Schutz, die Mod kann da nicht helfen.", "", "§fStopp bei eigener Bewegung", "§7W, A, S, D oder Leertaste gedrückt — die Wiedergabe stoppt sofort. Die Aufnahme bleibt unberührt."},
        {"Slots und Dateien", "§fSlots", "§7Es gibt 100 Slots. Jeder enthält eine Aufnahme: Name, Beschreibung und eigene Einstellungen.", "§7Einstellungen, die bei gewähltem gefüllten Slot gespeichert werden, gelten genau für diese Aufnahme.", "", "§fWas du mit einer Aufnahme tun kannst", "§7Umbenennen, Beschreibung hinzufügen, in einen anderen Slot kopieren, in eine Datei speichern, aus einer Datei laden oder löschen.", "", "§fWo die Dateien liegen", "§7Aufnahmen: Spielordner → mirror_recorder → slot_1.nbt, slot_2.nbt und so weiter.", "§7Daneben liegt eine Kopie slot_1.bak — für den Fall eines Fehlers.", "§7Von Hand gespeicherte Dateien: mirror_recorder → exports.", "§8Wenn das Spiel beim Speichern abstürzt, nimmt die Mod die Aufnahme aus der Kopie.", "", "§fAlte Aufnahmen", "§7Aufnahmen aus früheren Mod-Versionen lassen sich weiterhin öffnen und nutzen."},
        {"Aussehen", "§fZwei Objekte in der Welt", "§7Route ist die Linie des Wiedergabewegs. Markierung ist das Zeichen, wo die Aufnahme begann.", "§7Die Vorschau oben in den Einstellungen zeigt sofort, was gezeichnet wird.", "§7Die Knöpfe «Route» und «Startmarkierung» wählen, welches Objekt du gerade änderst. Das gewählte wird hervorgehoben.", "", "§fWie einstellen", "§7Wähle zuerst das Objekt, dann eine Gruppe: was gezeigt wird, Stil, Position, Größe, Effekte.", "§7Jede Zeile sagt in einfachen Worten, was sich nach dem Klick ändert.", "", "§fAnzeige auf dem Bildschirm", "§7Zeigt, was gerade läuft: Aufnahme oder Wiedergabe, Slot-Nummer, Runde und verbleibende Zeit.", "", "§fTooltips und Sprache", "§7Diese Tooltips lassen sich mit einem Knopf auf dem Hauptbildschirm abschalten. Dort wechselst du auch die Sprache.", "", "§fChat-Befehle", "§7/mirror gui — öffnet das Mod-Fenster.", "§7/mirror stop — stoppt Aufnahme oder Wiedergabe."},
        {"Sicherheit und Papierkorb", "§fStopp bei Schaden", "§7Wenn dich etwas trifft, stoppt die Mod sofort Aufnahme oder Wiedergabe und lässt alle Tasten los.", "§8Schalter: Einstellungen → Steuerung → «Stopp bei Schaden».", "", "§fPapierkorb", "§7Eine gelöschte Aufnahme verschwindet nicht: eine Kopie landet im Papierkorb.", "§7Einstellungen → Oberfläche → «Papierkorb»: tippe eine Karte an, dann den großen grünen Knopf «Wiederherstellen in Slot».", "§7«Letzte Löschung» holt zurück, was du gerade entfernt hast. «Alles leeren» fragt noch einmal.", "§8Die letzten 100 Löschungen werden aufbewahrt, und der Zielslot muss leer sein."},
    };
    private static final String[][] PL={
        {"Co robi mod", "§fMod zapamiętuje twoje naciśnięcia i odtwarza je", "§7Zapisuje klawisze, przyciski myszy, ruchy myszy i to, który przedmiot trzymałeś w ręce.", "§7Wszystko zapisuje się po kolei, 20 kroków na sekundę — tak samo, jak gra liczy czas.", "", "§fCzego mod nie zapamiętuje", "§7Świat dookoła, przedmioty w ekwipunku i to, co wyszło w efekcie.", "§7Mod powtórzy dokładnie te same naciśnięcia, ale wynik może wyjść inny.", "§7Przykład: w nagraniu kopałeś ziemię, a teraz przed tobą kamień — wynik nie będzie taki sam.", "", "§8Jeśli gra się zacina, mod trochę pomaga trzymać się drogi. Więcej w sekcji «Jeśli gra się zacina»."},
        {"Jak nagrać", "§fKrok po kroku", "§71. Po lewej wybierz pusty slot — wiersz bez nagrania.", "§72. Naciśnij «Nagraj». Okno zamknie się i rozpocznie odliczanie.", "§73. Graj jak zwykle.", "§74. Aby zakończyć, wpisz na czacie: /mirror stop", "", "§fCo trafi do nagrania", "§7Chodzenie: W, A, S, D — każdy klawisz osobno.", "§7Skok, kucanie, bieg, Q (wyrzuć), F (zmień rękę), E (ekwipunek) i kółko myszy.", "§7Kliknięcia lewym i prawym przyciskiem — wraz z czasem przytrzymania.", "§7Który przedmiot był w ręce i dokąd prowadziłeś mysz.", "§7Wiadomości na czacie — jeśli włączone w ustawieniach.", "", "§8W jednym slocie mieści się do 60 minut gry."},
        {"Jak odtworzyć", "§fKrok po kroku", "§71. Po lewej wybierz slot z nagraniem.", "§72. Naciśnij «Odtwórz» — mod odtworzy nagranie raz.", "§7Przed startem mod sam zaprowadzi cię tam, gdzie zaczęło się nagranie, i obróci wzrok jak wtedy.", "", "§fCo będzie podczas powtórki", "§7Klawisze są wciskane i zwalniane dokładnie tak, jak u ciebie.", "§7Długie przytrzymania są zachowane: naciągnięty łuk, jedzenie, mikstury, tarcza, wędka, niszczenie bloku.", "§7Uderzenia idą w tym samym tempie — mod niczego nie spowalnia.", "§7Przedmiot w ręce zmienia się sam.", "", "§fJak zatrzymać", "§7Naciśnij «Zatrzymaj», wpisz na czacie /mirror stop albo po prostu rusz sam — jeśli włączone «Stop przy moim ruchu»."},
        {"Powtórka w pętli", "§fZapętl", "§7Nagranie odtwarza się w kółko. Przed każdym razem mod wraca cię na start.", "", "§fIle razy powtórzyć", "§70 — nieskończenie, dopóki sam nie zatrzymasz.", "§7Albo ustaw od 2 do 1000 powtórek — po ostatniej mod zatrzyma się sam.", "", "§fOdliczanie przed startem", "§7Włączone: odliczanie będzie raz, tylko przed samym pierwszym powtórem.", "§7Wyłączone: odliczanie za każdym razem — wygodne, gdy potrzebna pauza między powtórkami."},
        {"Powrót na start", "§fPo co to potrzebne", "§7Aby każda powtórka zaczynała się w tym samym miejscu, w którym zacząłeś nagranie.", "§7Najpierw zaprowadzi cię tam mod Baritone, a potem nasz mod precyzyjnie doprowadzi cię do punktu.", "§8Baritone nie jest zainstalowany? Nic nie szkodzi: mod sam poprawi pozycję, jeśli stoisz blisko.", "", "§fDokładność", "§7Jak blisko punktu startu trzeba stanąć: od 0.05 do 0.5 bloku.", "§7Mniejsza wartość — dokładniej, ale powrót zajmie więcej czasu.", "", "§fPrędkość podejścia", "§7Jak szybko mod podchodzi do punktu: od 0.2 do 1.", "§7Ustaw mniej, jeśli dookoła ciasno i łatwo spaść.", "", "§fIle czekać", "§7Od 15 do 180 sekund. Jeśli w tym czasie powrót się nie uda, mod anuluje powtórkę zamiast czekać bez końca."},
        {"Jeśli gra się zacina", "§fPo co to potrzebne", "§7Przez lagi serwera lub zacięcia gry te same naciśnięcia mogą zaprowadzić cię trochę w bok.", "§7Mod delikatnie popycha postać z powrotem na drogę z nagrania. Samych naciśnięć nie zmienia.", "", "§fJak to działa", "§7Pchnięcie jest słabe, teleportów nie ma.", "§7Jeśli zaniesie cię dalej niż 3 bloki, powtórka zatrzyma się sama.", "§7W wodzie, w lawie, w locie, na drabinie i na koniu pomoc się wyłącza.", "", "§fKierunek wzroku", "§7Przed startem mod ustawia wzrok tam, gdzie patrzyłeś na początku nagrania.", "§cLepiej nie wyłączać: obroty są zapisane jako ruchy myszy, więc od początkowego wzroku zależy cała droga."},
        {"Co powtarzać", "§fTu można wyłączyć zbędne", "§7Chodzenie: wyłącz — postać stoi w miejscu, ale wzrok, kliknięcia i czat nadal się odtwarzają.", "§7Skoki, kucanie, bieg — osobne przełączniki dla spacji, Shift i klawisza biegu.", "§7Kliknięcia: lewy i prawy przycisk myszy wraz z przytrzymaniem i zmianą przedmiotu w ręce.", "§7Czat: wiadomości i komendy zostaną wysłane ponownie w tych samych momentach.", "§8Jeśli coś wyłączysz, powtórka nie będzie już dokładna i droga może zbaczać.", "§8Częste wiadomości serwer może zablokować — to jego ochrona przed spamem, mod tu nie pomoże.", "", "§fStop przy moim ruchu", "§7Wciśnięte W, A, S, D lub spacja — powtórka od razu się przerywa. Na nagrywanie to nie wpływa."},
        {"Sloty i pliki", "§fSloty", "§7Jest 100 slotów. W każdym leży jedno nagranie: nazwa, opis i własne ustawienia.", "§7Ustawienia zapisane przy wybranym slocie z nagraniem zapamiętują się właśnie dla niego.", "", "§fCo można zrobić z nagraniem", "§7Zmienić nazwę, dodać opis, skopiować do innego slotu, zapisać do pliku, wczytać z pliku albo usunąć.", "", "§fGdzie leżą pliki", "§7Nagrania: folder gry → mirror_recorder → slot_1.nbt, slot_2.nbt i tak dalej.", "§7Obok leży kopia slot_1.bak — na wypadek awarii.", "§7Pliki zapisane ręcznie: mirror_recorder → exports.", "§8Jeśli gra zamknie się podczas zapisu, mod weźmie nagranie z kopii.", "", "§fStare nagrania", "§7Nagrania z poprzednich wersji moda też się otwierają i działają."},
        {"Wygląd", "§fDwa obiekty w świecie", "§7Trasa — linia drogi powtórki. Znacznik — znak tam, gdzie zaczęło się nagranie.", "§7W ustawieniach u góry jest podgląd: od razu pokazuje, co będzie narysowane.", "§7Przyciski «Trasa» i «Znacznik startu» wybierają, który obiekt teraz zmieniasz. Wybrany obiekt jest podświetlony.", "", "§fJak ustawiać", "§7Najpierw wybierz obiekt, potem kategorię: co pokazać, styl, pozycję, rozmiary, efekty.", "§7Każdy wiersz opisany prostymi słowami: co zmieni się na ekranie po kliknięciu.", "", "§fPanel na ekranie", "§7Pokazuje, co teraz trwa: nagrywanie lub powtórkę, numer slotu, rundę i ile zostało.", "", "§fPodpowiedzi i język", "§7Te podpowiedzi po najechaniu można wyłączyć przyciskiem na głównym ekranie. Tam też zmienia się język.", "", "§fKomendy na czacie", "§7/mirror gui — otwórz okno moda.", "§7/mirror stop — zatrzymaj nagrywanie lub powtórkę."},
        {"Ochrona i kosz", "§fStop przy obrażeniach", "§7Jeśli coś cię trafi, mod od razu wyłącza nagrywanie lub powtórkę i zwalnia wszystkie klawisze.", "§8Przełącznik: Ustawienia → Sterowanie → «Stop przy obrażeniach».", "", "§fKosz", "§7Usunięte nagranie nie znika: kopia trafia do kosza.", "§7Ustawienia → Interfejs → «Kosz»: kliknij kartę, potem duży zielony przycisk «Przywróć do slotu».", "§7«Ostatnie usunięcie» przywraca to, co właśnie usunięto. «Opróżnij wszystko» pyta jeszcze raz.", "§8Przechowywane jest 100 ostatnich usunięć, a slot docelowy musi być pusty."},
    };

}
