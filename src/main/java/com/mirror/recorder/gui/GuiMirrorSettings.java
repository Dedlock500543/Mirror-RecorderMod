package com.mirror.recorder.gui;
import com.mirror.recorder.config.RecorderConfig;
import com.mirror.recorder.manager.RecorderManager;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
/**
 * Настройки мода: слева список категорий, справа только параметры выбранной категории.
 * Разделители убраны — иерархия задаётся заголовком категории, отступами и карточками строк.
 */
public class GuiMirrorSettings extends GuiScreen{

    private static final int SAVE=212,CANCEL=213,HELP_PREV=220,HELP_NEXT=221,VIS_PATH=230,VIS_MARK=231,TAB_BASE=300,TAB_COUNT=5,DEC_BASE=1000,INC_BASE=2000,ACT_BASE=3000;
    private static final int
        K_PLAY_MOVE=7,K_PLAY_JUMP=8,K_PLAY_SNEAK=9,K_PLAY_SPRINT=10,K_PLAY_CLICK=11,K_PLAY_CHAT=12,K_ROTATION=13,K_STOP=14,
        K_AUTO_RETURN=15,K_ROUTE=16,K_CURSOR=17,K_RET_TOL=18,K_RET_SPEED=19,K_RET_TIME=20,K_ADVANCED=21,
        K_SLOT_INFO=22,K_DELAY=23,K_SKIP=24,K_SLOT_HINT=25,
        K_MODE=26,K_RADIUS=27,K_BEACON=28,K_PULSE=29,K_MLABEL=30,K_MDIST=31,K_MUNLIM=32,K_MWALLS=33,K_MCOLOR=34,
        K_TIPS=35,K_LANG=36,K_RESET=37,
        K_HELP=38,K_H1=39,K_H2=40,K_H3=41,K_H4=42,K_H5=43,
        K_PATH=44,K_STYLE=45,K_WIDTH=46,K_LENGTH=47,K_PSTEP=48,K_PSIZE=49,K_HEIGHT=50,K_FADE=51,K_LWALLS=52,K_RESET_LOOK=53,K_COLOR_TARGET=54,K_SPEED=55,K_SOUNDS=56,K_GUARD_DMG=57,K_TRASH=58,K_SPEED_DEF=59,K_LIMIT=60,K_AUTO_LEAVE=61,K_LEAVE_CYCLES=62,K_VISIBLE_CHAT=63,K_EXACT_PLACE=64,K_STOP_MOUSE=65,K_STOP_MOUSE_PX=66;
    private static final int T_TOGGLE=0,T_STEP=1,T_CYCLE=2,T_ACTION=3,T_INFO=4,T_HEAD=5,HEAD_KEY_BASE=-2000,GROUP_HEAD_H=18,GROUP_HEAD_STEP=24;

    private static final class Row{
        final int type,key,color;final String label,note,icon;final String[] tip;
        Row(int type,int key,String label,String note,String icon,int color,String[] tip){
            this.type=type;this.key=key;this.label=label;this.note=note;this.icon=icon;this.color=color;this.tip=tip;
        }
    }

    private final RecorderManager manager;private final RecorderConfig config;private final GuiScreen parent;
    private final List<Row> rows=new ArrayList<Row>();
    private GuiColorWheel wheel;private StyledButton save,cancel;
    private static int colorTarget=0,visElement=0;private int tab=1,scroll=0,visPrevL,visPrevT,visPrevR,visPrevB;private boolean colorPanelOpen=false;private int[] rowY=new int[0];
    private int[] snapI;private float[] snapF;private boolean[] snapB;private String snapLang="ru";
    private boolean saved=false,openingChild=false,saveFailed=false,wheelTouched=false;
    private int winX,winY,winW,winH,headH,sideX,sideW,sideTop,sideRowH,contentX,contentRight,contentTop,contentBottom,listBottom,footerY,rowH,visibleRows,maxScroll,wheelSize,colorLabelY=-1;
    private float sbHover=0f,sbHoverFrom=0f;private boolean sbDrag=false,sbHoverTarget=false;private long sbHoverAt=0L;private int sbGrab=0;
    private int helpSection=0,helpScroll=0,helpMaxScroll=0,helpListX,helpListRight,helpListTop,helpRowH=14,helpTextX,helpTextRight,helpTextTop,helpVisible=1;
    private final List<String> helpLines=new ArrayList<String>();
    // static: свёрнутые секции переживают закрытие/открытие экрана (в т.ч. через /mirror gui) в рамках сессии игры.
    private static final boolean[][] collapsed=new boolean[TAB_COUNT][24];

    public GuiMirrorSettings(RecorderManager m,RecorderConfig c,GuiScreen p){this(m,c,p,0);}
    public GuiMirrorSettings(RecorderManager m,RecorderConfig c,GuiScreen p,int startTab){manager=m;config=c;parent=p;tab=Math.max(TABS[0],Math.min(TAB_COUNT-1,startTab));snapshot();}

    private static String s(String ru,String en,String uk,String de,String pl){return Lang.s(ru,en,uk,de,pl);}
    private static String[] tt(String ru,String en,String uk,String de,String pl){return s(ru,en,uk,de,pl).split("\n");}

    // ------------------------------------------------ снимок значений
    private int[] ints(){return new int[]{config.getStartDelay(),config.getPathLineR(),config.getPathLineG(),config.getPathLineB(),
        config.getPathLineAlpha(),config.getStartMarkerMode(),config.getReturnTimeoutSeconds(),config.getPathLineStyle(),
        config.getPathLineLength(),config.getPathPointStep(),config.getMarkerOwnR(),config.getMarkerOwnG(),config.getMarkerOwnB(),
        config.getMarkerOwnAlpha(),config.getMarkerLabelDistance(),config.getLoopLimit(),config.getAutoLeaveCycles(),config.getStopOnMouseThreshold()};}
    private void applyInts(int[] v){config.setStartDelay(v[0]);config.setPathLineR(v[1]);config.setPathLineG(v[2]);config.setPathLineB(v[3]);
        config.setPathLineAlpha(v[4]);config.setStartMarkerMode(v[5]);config.setReturnTimeoutSeconds(v[6]);config.setPathLineStyle(v[7]);
        config.setPathLineLength(v[8]);config.setPathPointStep(v[9]);config.setMarkerR(v[10]);config.setMarkerG(v[11]);config.setMarkerB(v[12]);
        config.setMarkerAlpha(v[13]);config.setMarkerLabelDistance(v[14]);if(v.length>15)config.setLoopLimit(v[15]);if(v.length>16)config.setAutoLeaveCycles(v[16]);if(v.length>17)config.setStopOnMouseThreshold(v[17]);}
    private float[] floats(){return new float[]{config.getPathLineWidth(),config.getReturnPositionTolerance(),config.getReturnAlignSpeed(),
        config.getPathPointSize(),config.getPathLineHeight(),config.getMarkerRadius(),config.getMarkerBeaconHeight(),config.getPlaybackSpeed()};}
    private void applyFloats(float[] v){config.setPathLineWidth(v[0]);config.setReturnPositionTolerance(v[1]);config.setReturnAlignSpeed(v[2]);
        config.setPathPointSize(v[3]);config.setPathLineHeight(v[4]);config.setMarkerRadius(v[5]);config.setMarkerBeaconHeight(v[6]);config.setPlaybackSpeed(v[7]);}
    private boolean[] bools(){return new boolean[]{config.isStopOnMove(),config.isApplyRotation(),config.isRouteStabilization(),
        config.isPathLineEnabled(),config.isShowTooltips(),config.isAutoReturnEnabled(),config.isCursorStabilization(),config.isSkipLoopStartDelay(),
        config.isPlaybackMovement(),config.isPlaybackJump(),config.isPlaybackSneak(),config.isPlaybackSprint(),config.isPlaybackInteraction(),config.isPlaybackChat(),
        config.isPathLineFade(),config.isPathLineThroughWalls(),config.isMarkerCustomColor(),config.isMarkerPulse(),config.isMarkerLabel(),
        config.isMarkerLabelUnlimited(),config.isMarkerThroughWalls(),config.isSoundsEnabled(),config.isGuardOnDamage(),config.isAutoLeaveEnabled(),config.isExactPlacement(),config.isVisibleChat(),config.isStopOnMouseMove()};}
    private void applyBools(boolean[] v){config.setStopOnMove(v[0]);config.setApplyRotation(v[1]);config.setRouteStabilization(v[2]);
        config.setPathLineEnabled(v[3]);config.setShowTooltips(v[4]);config.setAutoReturnEnabled(v[5]);config.setCursorStabilization(v[6]);config.setSkipLoopStartDelay(v[7]);
        config.setPlaybackMovement(v[8]);config.setPlaybackJump(v[9]);config.setPlaybackSneak(v[10]);config.setPlaybackSprint(v[11]);config.setPlaybackInteraction(v[12]);config.setPlaybackChat(v[13]);
        config.setPathLineFade(v[14]);config.setPathLineThroughWalls(v[15]);config.setMarkerCustomColor(v[16]);config.setMarkerPulse(v[17]);config.setMarkerLabel(v[18]);
        config.setMarkerLabelUnlimited(v[19]);config.setMarkerThroughWalls(v[20]);config.setSoundsEnabled(v[21]);config.setGuardOnDamage(v[22]);if(v.length>23)config.setAutoLeaveEnabled(v[23]);if(v.length>24)config.setExactPlacement(v[24]);if(v.length>25)config.setVisibleChat(v[25]);if(v.length>26)config.setStopOnMouseMove(v[26]);}
    private void snapshot(){snapI=ints();snapF=floats();snapB=bools();snapLang=config.getLanguage();}
    private void restore(){applyInts(snapI);applyFloats(snapF);applyBools(snapB);config.setLanguage(snapLang);}
    private boolean changed(){return !Arrays.equals(snapI,ints())||!Arrays.equals(snapF,floats())||!Arrays.equals(snapB,bools())||!snapLang.equals(config.getLanguage());}

    // ------------------------------------------------ категории
    private String tabName(int i){switch(i){case 0:return s("Запись","Recording","Запис","Aufnahme","Nagrywanie");case 1:return s("Повтор","Replay","Повтор","Wiedergabe","Powtórka");case 2:return s("Визуализация","Visualization","Візуалізація","Visualisierung","Wizualizacja");case 3:return s("Общие","General","Загальні","Allgemein","Ogólne");default:return s("Справка","Help","Довідка","Hilfe","Pomoc");}}
    private String tabHint(int i){switch(i){case 0:return s("Что попадает в слот","What goes into a slot","Що потрапляє в слот","Was in den Slot kommt","Co trafia do slotu");case 1:return s("Запись, повтор, цикл","Record, replay, loop","Запис, повтор, цикл","Aufnahme, Wiedergabe, Schleife","Nagrywanie, powtórka, pętla");case 2:return s("Что рисуется в мире","What is drawn in the world","Що малюється у світі","Was in der Welt gezeichnet wird","Co jest rysowane w świecie");case 3:return s("Подсказки, язык, сброс","Tooltips, language, reset","Підказки, мова, скидання","Tooltips, Sprache, Reset","Podpowiedzi, język, reset");default:return s("Как работает мод","How the mod works","Як працює мод","Wie die Mod funktioniert","Jak działa mod");}}
    private String tabIcon(int i){switch(i){case 0:return "\u25cf";case 1:return "\u25b6";case 2:return "\u2248";case 3:return "\u2699";default:return "?";}}
    private int tabColor(int i){switch(i){case 0:return UiTheme.RED;case 1:return UiTheme.BLUE;case 2:return UiTheme.PURPLE;case 3:return UiTheme.ACCENT;default:return UiTheme.GRAY;}}
    private boolean hasColorPanel(){return tab==2&&colorPanelOpen;}
    /** Строка недоступна, пока не включена настройка, от которой она зависит. */
    private boolean rowLocked(int key){return (key==K_LEAVE_CYCLES&&!config.isAutoLeaveEnabled())||(key==K_STOP_MOUSE_PX&&!config.isStopOnMouseMove());}
    private StyledButton add(boolean locked,StyledButton b){b.enabled=!locked;return add(b);}

    private void head(String label){rows.add(new Row(T_HEAD,-1000-rows.size(),label,null,null,UiTheme.TEXT_MUTE,null));}
    private void add(int type,int key,String label,String note,String icon,int color,String[] tip){rows.add(new Row(type,key,label,note,icon,color,tip));}

    private static final int GROUP_GAP=12,ROW_GAP=5;
    /** Видимые категории. Индексы совпадают с tabName/tabColor. */
    private static final int[] TABS={0,1,2,3};
    private static int tabIndex(int t){for(int i=0;i<TABS.length;i++)if(TABS[i]==t)return i;return 0;}
    private int collapseSlot(int group){return group+(tab==2&&visElement==1?12:0);}
    private String archiveTitle(String label){
        if(label==null||label.length()==0)return "";String low=label.toLowerCase(java.util.Locale.ROOT);
        return low.substring(0,1).toUpperCase(java.util.Locale.ROOT)+low.substring(1);
    }
    private void applyArchiveGroups(){
        List<Row> out=new ArrayList<Row>();int group=-1;
        for(Row r:rows){
            if(r.type==T_HEAD){group++;int slot=collapseSlot(group);boolean shut=collapsed[tab][slot];String title=(group+1)+". "+archiveTitle(r.label);out.add(new Row(T_HEAD,HEAD_KEY_BASE-slot,title,shut?s("Свернуто · ПКМ, чтобы открыть","Collapsed · right-click to open","Згорнуто · ПКМ, щоб відкрити","Eingeklappt · Rechtsklick zum Öffnen","Zwinięte · PPM, aby otworzyć"):s("Открыто · ПКМ, чтобы свернуть","Open · right-click to collapse","Відкрито · ПКМ, щоб згорнути","Offen · Rechtsklick zum Einklappen","Otwarte · PPM, aby zwinąć"),null,r.color,null));continue;}
            if(group<0||!collapsed[tab][collapseSlot(group)])out.add(r);
        }
        rows.clear();rows.addAll(out);
    }
    private boolean toggleArchiveAt(int mx,int my){
        for(int i=0;i<rows.size();i++){Row r=rows.get(i);if(r.type!=T_HEAD||rowY[i]<0)continue;int y=rowY[i];if(mx>=contentX+6&&mx<contentRight-6&&my>=y&&my<y+GROUP_HEAD_H){int slot=HEAD_KEY_BASE-r.key;if(slot>=0&&slot<collapsed[tab].length){collapsed[tab][slot]=!collapsed[tab][slot];scroll=0;initGui();return true;}}}
        return false;
    }
    private void buildRows(){
        rows.clear();
        int c=tabColor(tab);
        switch(tab){
            case 0:
                head(s("ЗАПИСЬ","RECORDING","ЗАПИС","AUFNAHME","NAGRYWANIE"));
                head(s("СТОП ПРИ ДВИЖЕНИИ","STOP ON MOVEMENT","СТОП ПРИ РУСІ","STOPP BEI BEWEGUNG","STOP PRZY RUCHU"));
                add(T_TOGGLE,K_STOP,s("Остановить при моём движении","Stop on my movement","Зупинити при моєму русі","Bei eigener Bewegung stoppen","Zatrzymaj przy moim ruchu"),s("Нажатие W A S D или Space прерывает повтор","W A S D or Space aborts the playback","Натискання W A S D або Space перериває повтор","W A S D oder Space bricht die Wiedergabe ab","Wciśnięcie W A S D lub Space przerywa powtórkę"),"\u25a0",c,
                    tt("\u00a7fОстановка, если вы пошли сами\n\u00a77Вкл: нажали W, A, S, D или Space — повтор сразу прекращается.\n\u00a77Удобно, когда нужно быстро вмешаться.\n\u00a78Обычно: вкл.","\u00a7fStop when you move\n\u00a77On: pressing W, A, S, D or Space stops the playback at once.\n\u00a77Handy when you need to step in quickly.\n\u00a78Usually: on.","§fЗупинка, якщо ви пішли самі\n§7Увімк: натиснули W, A, S, D або Space — повтор одразу припиняється.\n§7Зручно, коли треба швидко втрутитися.\n§8Зазвичай: увімк.","§fStopp, wenn du dich selbst bewegst\n§7An: W, A, S, D oder Space gedrückt — die Wiedergabe stoppt sofort.\n§7Praktisch, wenn du schnell eingreifen willst.\n§8Meistens: an.","§fZatrzymanie, gdy ruszysz sam\n§7Wł: wciśnięcie W, A, S, D lub Space natychmiast przerywa powtórkę.\n§7Wygodne, gdy trzeba szybko zareagować.\n§8Zwykle: wł."));
                add(T_TOGGLE,K_STOP_MOUSE,s("Останавливать при движении мыши","Stop on mouse movement","Зупиняти при русі миші","Bei Mausbewegung stoppen","Zatrzymaj przy ruchu myszy"),s("Двинули мышью — повтор прекращается","Moving the mouse aborts the playback","Рухнули мишею — повтор припиняється","Mausbewegung bricht die Wiedergabe ab","Ruch myszy przerywa powtórkę"),"\u2196",c,
                    tt("§fОстановка при движении курсора\n§7Вкл: сдвинули мышь — повтор сразу прекращается.\n§7Клавиши W A S D и Space сюда не входят: у них своя настройка выше.\n§7Порог ниже задаёт, сколько пикселей считать движением.\n§7Работает и при открытом окне: инвентарь, настройки, меню паузы.\n§8Обычно: выкл.","§fStop on cursor movement\n§7On: moving the mouse stops the playback at once.\n§7W A S D and Space are not included here, they have their own switch above.\n§7The threshold below sets how many pixels count as movement.\n§7Works with an open screen too: inventory, settings, pause menu.\n§8Usually: off.","§fЗупинка при русі курсора\n§7Увімк: зсунули мишу — повтор одразу припиняється.\n§7Клавіші W A S D і Space сюди не входять: у них своє налаштування вище.\n§7Поріг нижче задає, скільки пікселів вважати рухом.\n§7Працює і при відкритому вікні: інвентар, налаштування, меню паузи.\n§8Зазвичай: вимк.","§fStopp bei Mausbewegung\n§7An: Mausbewegung stoppt die Wiedergabe sofort.\n§7W A S D und Space gehören nicht dazu, dafür gibt es den Schalter darüber.\n§7Der Schwellwert darunter legt fest, ab wie vielen Pixeln das zählt.\n§7Funktioniert auch bei offenem Fenster: Inventar, Einstellungen, Pausenmenü.\n§8Meistens: aus.","§fZatrzymanie przy ruchu myszy\n§7Wł: ruch myszy natychmiast przerywa powtórkę.\n§7W A S D i Space nie wchodzą tutaj, mają własny przełącznik powyżej.\n§7Próg poniżej określa, ile pikseli liczy się jako ruch.\n§7Działa też przy otwartym ekranie: ekwipunek, ustawienia, menu pauzy.\n§8Zwykle: wył."));
                add(T_STEP,K_STOP_MOUSE_PX,s("Порог движения мыши","Mouse movement threshold","Поріг руху миші","Schwelle der Mausbewegung","Próg ruchu myszy"),s("Пикселей за 0.2 с: меньше — чувствительнее","Pixels per 0.2 s: lower is more sensitive","Пікселів за 0.2 с: менше — чутливіше","Pixel pro 0,2 s: kleiner ist empfindlicher","Pikseli na 0,2 s: mniej znaczy czulej"),"\u25cf",c,
                    tt("§fПорог движения мыши\n§7Сколько пикселей мышь должна пройти, чтобы повтор остановился: движение суммируется за 0.2 секунды.\n§7Меньше — чувствительнее, больше — дрожание руки не мешает.\n§8От 1 до 50 px, обычно 6. Ctrl — шаг 5.","§fMouse movement threshold\n§7How many pixels the mouse must travel to stop the playback, summed over 0.2 seconds.\n§7Lower is more sensitive, higher ignores a shaky hand.\n§8From 1 to 50 px, usually 6. Ctrl for a step of 5.","§fПоріг руху миші\n§7Скільки пікселів миша має пройти, щоб повтор зупинився: рух додається за 0.2 секунди.\n§7Менше — чутливіше, більше — дрижання руки не заважає.\n§8Від 1 до 50 px, зазвичай 6. Ctrl — крок 5.","§fSchwelle der Mausbewegung\n§7Wie viele Pixel die Maus zurücklegen muss, damit die Wiedergabe stoppt, summiert über 0,2 Sekunden.\n§7Kleiner ist empfindlicher, größer ignoriert eine zittrige Hand.\n§8Von 1 bis 50 px, meist 6. Ctrl für Schritt 5.","§fPróg ruchu myszy\n§7Ile pikseli mysz musi przejechać, aby powtórka się zatrzymała, sumowane przez 0,2 sekundy.\n§7Mniej znaczy czulej, więcej ignoruje drżenie ręki.\n§8Od 1 do 50 px, zwykle 6. Ctrl to krok 5."));
                head(s("ОСТАНОВКА ПРИ УРОНЕ","STOP WHEN HURT","ЗУПИНКА ПРИ ШКОДІ","STOPP BEI SCHADEN","ZATRZYMANIE PRZY OBRAŻENIACH"));
                add(T_TOGGLE,K_GUARD_DMG,s("Стоп при уроне","Stop when hurt","Стоп при шкоді","Stopp bei Schaden","Stop przy obrażeniach"),s("Кто-то ударил — сразу стоп","Any hit stops the run","Хтось вдарив — одразу стоп","Jeder Treffer stoppt den Lauf","Ktoś uderzył — od razu stop"),"\u2694",c,
                    tt("§fСтоп при уроне\n§7Как только по вам попали — моб, игрок или падение — запись и повтор сразу останавливаются.\n§7Так вы не продолжите бегать вслепую, когда на вас напали.","§fStop when hurt\n§7The moment something hits you - a mob, a player or a fall - recording and playback stop.\n§7That way you never keep running blind while under attack.","§fСтоп при шкоді\n§7Щойно по вас влучили — моб, гравець або падіння — запис і повтор одразу зупиняються.\n§7Так ви не продовжите бігати наосліп, коли на вас напали.","§fStopp bei Schaden\n§7Sobald dich etwas trifft — ein Mob, ein Spieler oder ein Sturz — stoppen Aufnahme und Wiedergabe sofort.\n§7So läufst du nicht blind weiter, während du angegriffen wirst.","§fStop przy obrażeniach\n§7Gdy tylko coś cię trafi — mob, gracz lub upadek — nagrywanie i powtórka od razu się zatrzymują.\n§7Nie biegniesz więc dalej w ciemno, gdy ktoś cię atakuje."));
                break;
            case 1:
                head(s("ЧТО ПОВТОРЯТЬ","WHAT TO REPLAY","ЩО ПОВТОРЮВАТИ","WAS WIEDERHOLEN","CO ODTWARZAĆ"));
                add(T_TOGGLE,K_PLAY_MOVE,s("Повторять движение","Replay movement","Повторювати рух","Bewegung wiederholen","Powtarzaj ruch"),s("Шаги из записи","Steps from the recording","Кроки із запису","Schritte aus der Aufnahme","Kroki z nagrania"),"\u25b6",c,
                    tt("\u00a7fПовторять движение\n\u00a77Вкл: персонаж пройдёт тот же путь, что и вы при записи.\n\u00a77Выкл: стоит на месте, но всё остальное повторяет.\n\u00a78Обычно: вкл.","\u00a7fReplay movement\n\u00a77On: the player walks the same path you did.\n\u00a77Off: it stays in place but still repeats everything else.\n\u00a78Usually: on.","§fПовторювати рух\n§7Увімк: персонаж пройде той самий шлях, що й ви під час запису.\n§7Вимк: стоїть на місці, але все інше повторює.\n§8Зазвичай: увімк.","§fBewegung wiederholen\n§7An: der Spieler läuft denselben Weg wie du bei der Aufnahme.\n§7Aus: bleibt stehen, wiederholt aber alles andere.\n§8Meistens: an.","§fPowtarzaj ruch\n§7Wł: postać przejdzie tę samą drogę, co ty podczas nagrywania.\n§7Wył: stoi w miejscu, ale powtarza wszystko inne.\n§8Zwykle: wł."));
                add(T_TOGGLE,K_PLAY_JUMP,s("Повторять прыжки","Replay jumps","Повторювати стрибки","Sprünge wiederholen","Powtarzaj skoki"),s("Пробел из записи","Space from the recording","Пробіл із запису","Leertaste aus der Aufnahme","Spacja z nagrania"),"\u2191",c,
                    tt("\u00a7fПовторять прыжки\n\u00a77Вкл: пробел нажимается там же, где нажимали вы.\n\u00a77Выкл: прыжки пропускаются.\n\u00a78Обычно: вкл.","\u00a7fReplay jumps\n\u00a77On: space is pressed where you pressed it.\n\u00a77Off: jumps are skipped.\n\u00a78Usually: on.","§fПовторювати стрибки\n§7Увімк: пробіл натискається там, де натискали ви.\n§7Вимк: стрибки пропускаються.\n§8Зазвичай: увімк.","§fSprünge wiederholen\n§7An: Leertaste wird gedrückt, wo du sie gedrückt hast.\n§7Aus: Sprünge werden übersprungen.\n§8Meistens: an.","§fPowtarzaj skoki\n§7Wł: spacja jest wciskana tam, gdzie ty ją wcisnąłeś.\n§7Wył: skoki są pomijane.\n§8Zwykle: wł."));
                add(T_TOGGLE,K_PLAY_SNEAK,s("Повторять приседание","Replay sneaking","Повторювати присідання","Schleichen wiederholen","Powtarzaj kucanie"),s("Shift из записи","Shift from the recording","Shift із запису","Shift aus der Aufnahme","Shift z nagrania"),"\u2193",c,
                    tt("\u00a7fПовторять приседание\n\u00a77Вкл: Shift нажимается как в записи.\n\u00a77Выкл: персонаж не приседает.\n\u00a78Обычно: вкл.","\u00a7fReplay sneaking\n\u00a77On: Shift is pressed just like in the recording.\n\u00a77Off: the player never sneaks.\n\u00a78Usually: on.","§fПовторювати присідання\n§7Увімк: Shift натискається як у записі.\n§7Вимк: персонаж не присідає.\n§8Зазвичай: увімк.","§fSchleichen wiederholen\n§7An: Shift wird wie in der Aufnahme gedrückt.\n§7Aus: der Spieler schleicht nie.\n§8Meistens: an.","§fPowtarzaj kucanie\n§7Wł: Shift jest wciskany jak w nagraniu.\n§7Wył: postać nie kuca.\n§8Zwykle: wł."));
                add(T_TOGGLE,K_PLAY_SPRINT,s("Повторять бег","Replay sprinting","Повторювати біг","Sprinten wiederholen","Powtarzaj sprint"),s("Спринт из записи","Sprint from the recording","Спринт із запису","Sprint aus der Aufnahme","Sprint z nagrania"),"\u00bb",c,
                    tt("\u00a7fПовторять бег\n\u00a77Вкл: где вы бежали — там персонаж тоже побежит.\n\u00a77Выкл: весь путь проходит шагом.\n\u00a78Обычно: вкл.","\u00a7fReplay sprinting\n\u00a77On: it sprints where you sprinted.\n\u00a77Off: the whole route is walked.\n\u00a78Usually: on.","§fПовторювати біг\n§7Увімк: де ви бігли — там персонаж теж побіжить.\n§7Вимк: весь шлях проходить кроком.\n§8Зазвичай: увімк.","§fSprinten wiederholen\n§7An: es sprintet, wo du gesprintet bist.\n§7Aus: die ganze Strecke wird gelaufen.\n§8Meistens: an.","§fPowtarzaj sprint\n§7Wł: biegnie tam, gdzie ty biegłeś.\n§7Wył: cała trasa jest pokonywana pieszo.\n§8Zwykle: wł."));
                add(T_TOGGLE,K_PLAY_CLICK,s("Повторять клики и предметы","Replay clicks and items","Повторювати кліки й предмети","Klicks und Gegenstände wiederholen","Powtarzaj kliknięcia i przedmioty"),s("Удары, еда, блоки, хотбар","Attacks, food, blocks, hotbar","Удари, їжа, блоки, хотбар","Angriffe, Essen, Blöcke, Hotbar","Uderzenia, jedzenie, bloki, hotbar"),"\u2694",c,
                    tt("\u00a7fПовторять клики и предметы\n\u00a77Это удары, еда, установка блоков и смена предмета в руке.\n\u00a77Выкл: персонаж только ходит, ничего не трогает.\n\u00a78Обычно: вкл.","\u00a7fReplay clicks and items\n\u00a77That means attacks, eating, placing blocks and hotbar swaps.\n\u00a77Off: the player only moves and touches nothing.\n\u00a78Usually: on.","§fПовторювати кліки й предмети\n§7Це удари, їжа, встановлення блоків і зміна предмета в руці.\n§7Вимк: персонаж тільки ходить, нічого не чіпає.\n§8Зазвичай: увімк.","§fKlicks und Gegenstände wiederholen\n§7Das sind Angriffe, Essen, Blöcke platzieren und Hotbar-Wechsel.\n§7Aus: der Spieler läuft nur, fasst nichts an.\n§8Meistens: an.","§fPowtarzaj kliknięcia i przedmioty\n§7To uderzenia, jedzenie, stawianie bloków i zmiana przedmiotu w ręce.\n§7Wył: postać tylko chodzi, niczego nie dotyka.\n§8Zwykle: wł."));
                add(T_TOGGLE,K_EXACT_PLACE,s("Ставить блоки точно","Place blocks exactly","Ставити блоки точно","Blöcke exakt setzen","Stawiaj bloki dokładnie"),s("Блок встанет туда же, куда при записи","The block lands where it did on record","Блок стане туди ж, куди при записі","Der Block landet wie bei der Aufnahme","Blok stanie tam, gdzie przy nagraniu"),"\u2694",c,
                    tt("§fСтавить блоки точно\n§7Вкл: повтор ставит блок в записанную точку, а не туда, куда смотрит камера.\n§7Выкл: блок ставится по текущему взгляду.\n§8Обычно: вкл.","§fPlace blocks exactly\n§7On: replay places the block at the recorded spot, not where the camera looks.\n§7Off: the block follows the current view.\n§8Usually: on.","§fСтавити блоки точно\n§7Увімк: повтор ставить блок у записану точку, а не туди, куди дивиться камера.\n§7Вимк: блок ставиться за поточним поглядом.\n§8Зазвичай: увімк.","§fBlöcke exakt setzen\n§7An: die Wiedergabe setzt den Block an die aufgezeichnete Stelle, nicht dorthin, wo die Kamera schaut.\n§7Aus: der Block folgt der aktuellen Sicht.\n§8Meistens: an.","§fStawiaj bloki dokładnie\n§7Wł: powtórka stawia blok w zapisanym miejscu, a nie tam, gdzie patrzy kamera.\n§7Wył: blok idzie za bieżącym widokiem.\n§8Zwykle: wł."));
                add(T_TOGGLE,K_PLAY_CHAT,s("Повторять чат","Replay chat","Повторювати чат","Chat wiederholen","Powtarzaj czat"),s("Сообщения отправляются заново","Messages are sent again","Повідомлення надсилаються заново","Nachrichten werden erneut gesendet","Wiadomości są wysyłane ponownie"),"\u2709",c,
                    tt("\u00a7fПовторять чат\n\u00a77Вкл: сообщения и команды из записи отправятся снова.\n\u00a77Выкл: чат пропускается.\n\u00a78Будьте осторожны, если в записи были команды.","\u00a7fReplay chat\n\u00a77On: messages and commands from the recording are sent again.\n\u00a77Off: chat is skipped.\n\u00a78Be careful if the recording contains commands.","§fПовторювати чат\n§7Увімк: повідомлення й команди із запису надішлються знову.\n§7Вимк: чат пропускається.\n§8Будьте обережні, якщо в записі були команди.","§fChat wiederholen\n§7An: Nachrichten und Befehle aus der Aufnahme werden erneut gesendet.\n§7Aus: Chat wird übersprungen.\n§8Vorsicht, wenn die Aufnahme Befehle enthält.","§fPowtarzaj czat\n§7Wł: wiadomości i komendy z nagrania zostaną wysłane ponownie.\n§7Wył: czat jest pomijany.\n§8Uważaj, jeśli nagranie zawiera komendy."));
                add(T_TOGGLE,K_VISIBLE_CHAT,s("Живой чат","Live chat","Живий чат","Lebendiger Chat","Żywy czat"),s("Чат открывается и печатает по буквам","Chat opens and types letter by letter","Чат відкривається й друкує по буквах","Chat öffnet sich und tippt Buchstabe für Buchstabe","Czat otwiera się i pisze litera po literze"),"\u2709",c,
                    tt("§fЖивой чат\n§7Вкл: окно чата открывается на экране, а текст набирается по буквам, как у вас.\n§7Выкл: сообщение уходит сразу, окно не открывается.\n§8Обычно: вкл.","§fLive chat\n§7On: the chat window opens on screen and the text is typed letter by letter, like you did.\n§7Off: the message is sent instantly, no window.\n§8Usually: on.","§fЖивий чат\n§7Увімк: вікно чату відкривається на екрані, а текст набирається по буквах, як у вас.\n§7Вимк: повідомлення йде одразу, вікно не відкривається.\n§8Зазвичай: увімк.","§fLebendiger Chat\n§7An: das Chatfenster öffnet sich und der Text wird Buchstabe für Buchstabe getippt.\n§7Aus: die Nachricht geht sofort raus, ohne Fenster.\n§8Meistens: an.","§fŻywy czat\n§7Wł: okno czatu otwiera się i tekst jest pisany litera po literze.\n§7Wył: wiadomość wysyła się od razu, bez okna.\n§8Zwykle: wł."));
                add(T_TOGGLE,K_ROTATION,s("Повторять поворот мыши","Replay mouse rotation","Повторювати поворот миші","Mausdrehung wiederholen","Powtarzaj obrót myszy"),s("Направление взгляда из записи","Recorded look direction","Напрямок погляду із запису","Blickrichtung aus der Aufnahme","Kierunek wzroku z nagrania"),"\u25cb",c,
                    tt("\u00a7fПовторять поворот мыши\n\u00a77Вкл: камера смотрит туда же, куда смотрели вы.\n\u00a77Выкл: обзором управляете вы сами.\n\u00a78Обычно: вкл.","\u00a7fReplay mouse look\n\u00a77On: the camera looks where you looked.\n\u00a77Off: you control the view yourself.\n\u00a78Usually: on.","§fПовторювати поворот миші\n§7Увімк: камера дивиться туди ж, куди дивилися ви.\n§7Вимк: оглядом керуєте ви самі.\n§8Зазвичай: увімк.","§fMausdrehung wiederholen\n§7An: die Kamera schaut dorthin, wo du hingeschaut hast.\n§7Aus: du steuerst die Sicht selbst.\n§8Meistens: an.","§fPowtarzaj obrót myszy\n§7Wł: kamera patrzy tam, gdzie ty patrzyłeś.\n§7Wył: widokiem sterujesz sam.\n§8Zwykle: wł."));
                head(s("ЗАПУСК","START","ЗАПУСК","START","START"));
                add(T_STEP,K_DELAY,s("Задержка перед стартом","Start delay","Затримка перед стартом","Startverzögerung","Opóźnienie przed startem"),s("Точная пауза перед записью или повтором","Precise pause before recording or playback","Точна пауза перед записом або повтором","Genaue Pause vor Aufnahme oder Wiedergabe","Dokładna pauza przed nagraniem lub powtórką"),"\u23f1",c,
                    tt("§fПауза перед стартом\n§7Сколько секунд мод ждёт после нажатия кнопки.\n§7Это время, чтобы убрать руки с клавиатуры.\n§8Ctrl — менять сразу на 5 секунд.","§fPause before start\n§7How many seconds the mod waits after you press the button.\n§7That is your time to let go of the keyboard.\n§8Hold Ctrl to change it by 5 seconds at a time.","§fПауза перед стартом\n§7Скільки секунд мод чекає після натискання кнопки.\n§7Це час, щоб забрати руки з клавіатури.\n§8Ctrl — змінювати одразу на 5 секунд.","§fPause vor dem Start\n§7Wie viele Sekunden die Mod nach dem Tastendruck wartet.\n§7Das ist deine Zeit, die Hände von der Tastatur zu nehmen.\n§8Ctrl ändert in 5-Sekunden-Schritten.","§fPauza przed startem\n§7Ile sekund mod czeka po naciśnięciu przycisku.\n§7To czas, by zabrać ręce z klawiatury.\n§8Ctrl — zmiana od razu o 5 sekund."));
                add(T_TOGGLE,K_SKIP,s("Задержка только перед первым циклом","Delay before first cycle only","Затримка лише перед першим циклом","Verzögerung nur vor der ersten Runde","Opóźnienie tylko przed pierwszym cyklem"),s("Следующие повторы начинаются сразу","Later repeats begin immediately","Наступні повтори починаються одразу","Weitere Wiederholungen starten sofort","Kolejne powtórki zaczynają się od razu"),"\u21bb",c,
                    tt("§fОтсчёт только перед первым кругом\n§7Вкл: 3-2-1 будет один раз, дальше круги идут без пауз.\n§7Выкл: отсчёт перед каждым кругом.","§fCountdown before the first round only\n§7On: you hear 3-2-1 once, then rounds run without pauses.\n§7Off: a countdown before every round.","§fВідлік лише перед першим колом\n§7Увімк: 3-2-1 буде один раз, далі кола йдуть без пауз.\n§7Вимк: відлік перед кожним колом.","§fCountdown nur vor der ersten Runde\n§7An: 3-2-1 ertönt einmal, danach laufen die Runden ohne Pausen.\n§7Aus: Countdown vor jeder Runde.","§fOdliczanie tylko przed pierwszą rundą\n§7Wł: 3-2-1 słychać raz, dalej rundy idą bez pauz.\n§7Wył: odliczanie przed każdą rundą."));
                add(T_STEP,K_SPEED,s("Скорость повтора","Replay speed","Швидкість повтору","Wiedergabetempo","Prędkość powtórki"),s("Быстрее или медленнее записи","Faster or slower than the recording","Швидше або повільніше за запис","Schneller oder langsamer als die Aufnahme","Szybciej lub wolniej niż nagranie"),"\u00bb",c,
                    tt("§fСкорость повтора\n§71.00x — всё идёт точно так, как вы записали.\n§7Больше 1 — быстрее, меньше 1 — медленнее.\n§8От 0.25x до 4.00x. Ctrl — шаг 0.5, Shift — шаг 0.05.\n§cНа большой скорости часть кликов может пропускаться.","§fReplay speed\n§71.00x plays exactly as you recorded it.\n§7Above 1 is faster, below 1 is slower.\n§8From 0.25x to 4.00x. Ctrl steps by 0.5, Shift by 0.05.\n§cAt high speed some clicks may be skipped.","§fШвидкість повтору\n§71.00x — все йде точно так, як ви записали.\n§7Більше 1 — швидше, менше 1 — повільніше.\n§8Від 0.25x до 4.00x. Ctrl — крок 0.5, Shift — крок 0.05.\n§cНа великій швидкості частина кліків може пропускатися.","§fWiedergabetempo\n§71.00x läuft exakt so, wie du es aufgenommen hast.\n§7Über 1 ist schneller, unter 1 langsamer.\n§8Von 0.25x bis 4.00x. Ctrl schaltet um 0.5, Shift um 0.05.\n§cBei hohem Tempo können einzelne Klicks ausgelassen werden.","§fPrędkość powtórki\n§71.00x — wszystko idzie dokładnie tak, jak nagrałeś.\n§7Powyżej 1 — szybciej, poniżej 1 — wolniej.\n§8Od 0.25x do 4.00x. Ctrl — krok 0.5, Shift — krok 0.05.\n§cPrzy dużej prędkości część kliknięć może być pomijana."));
                head(s("ЦИКЛ","LOOP","ЦИКЛ","SCHLEIFE","PĘTLA"));
                add(T_STEP,K_LIMIT,s("Сколько кругов в цикле","Loop rounds","Скільки кіл у циклі","Runden in der Schleife","Ile rund w pętli"),s("0 — без ограничения","0 means unlimited","0 — без обмежень","0 heißt unbegrenzt","0 — bez limitu"),"\u21bb",c,
                    tt("§fСколько кругов в цикле\n§7Цикл сам остановится после этого числа кругов.\n§7«Без ограничения» — крутится, пока не нажмёте «Остановить».\n§8Шаг 2, Shift — 1, Ctrl — 10.","§fLoop rounds\n§7The loop stops itself after this many rounds.\n§7Unlimited keeps looping until you press Stop.\n§8Step 2, Shift for 1, Ctrl for 10.","§fСкільки кіл у циклі\n§7Цикл сам зупиниться після цієї кількості кіл.\n§7«Без обмежень» — крутиться, поки не натиснете «Зупинити».\n§8Крок 2, Shift — 1, Ctrl — 10.","§fRunden in der Schleife\n§7Die Schleife stoppt nach so vielen Runden von selbst.\n§7Unbegrenzt läuft sie, bis du «Stopp» drückst.\n§8Schritt 2, Shift für 1, Ctrl für 10.","§fIle rund w pętli\n§7Pętla zatrzyma się sama po tej liczbie rund.\n§7«Bez limitu» — kręci się, dopóki nie naciśniesz «Zatrzymaj».\n§8Krok 2, Shift — 1, Ctrl — 10."));
                add(T_TOGGLE,K_AUTO_LEAVE,s("Выход с сервера после N циклов","Leave the server after N cycles","Вихід із сервера після N циклів","Server nach N Runden verlassen","Wyjście z serwera po N cyklach"),s("Мод сам отключится, когда круги закончатся","The mod disconnects when the rounds are done","Мод сам відключиться, коли кола закінчаться","Die Mod trennt selbst, wenn die Runden vorbei sind","Mod sam się rozłączy, gdy rundy się skończą"),"\u25a0",c,
                    tt("§fАвтовыход с сервера\n§7Вкл: как только пройдёт заданное число кругов, мод завершит повтор и выйдет с сервера в меню.\n§7В одиночной игре — выход в главное меню.\n§8Настройка общая для всех слотов.","§fLeave the server automatically\n§7On: once the given number of rounds is done, the mod ends the playback and disconnects to the menu.\n§7In single player it returns to the main menu.\n§8This setting is shared by all slots.","§fАвтовихід із сервера\n§7Увімк: щойно пройде задана кількість кіл, мод завершить повтор і вийде із сервера в меню.\n§7В одиночній грі — вихід у головне меню.\n§8Налаштування спільне для всіх слотів.","§fServer automatisch verlassen\n§7An: sobald die angegebene Anzahl Runden erreicht ist, beendet die Mod die Wiedergabe und trennt die Verbindung.\n§7Im Einzelspieler geht es zurück ins Hauptmenü.\n§8Diese Einstellung gilt für alle Slots.","§fAutomatyczne wyjście z serwera\n§7Wł: gdy minie zadana liczba rund, mod zakończy powtórkę i rozłączy się do menu.\n§7W trybie jednoosobowym wraca do menu głównego.\n§8Ustawienie jest wspólne dla wszystkich slotów."));
                add(T_STEP,K_LEAVE_CYCLES,s("Циклов до выхода","Cycles before leaving","Циклів до виходу","Runden bis zum Verlassen","Cykli do wyjścia"),s("Считаются полностью пройденные круги","Fully finished rounds are counted","Рахуються повністю пройдені кола","Gezählt werden vollständig beendete Runden","Liczone są w pełni ukończone rundy"),"\u21bb",c,
                    tt("§fСколько кругов до выхода\n§7Круг считается пройденным, когда повтор дошёл до последнего кадра.\n§7Если «Сколько кругов в цикле» меньше этого числа, цикл закончится раньше и выхода не будет — мод предупредит об этом на старте.\n§8Шаг 1, Ctrl — 10.","§fHow many rounds before leaving\n§7A round counts as done when the playback reached its last frame.\n§7If «Loop rounds» is smaller than this number, the loop ends earlier and nothing disconnects; the mod warns about it at start.\n§8Step 1, Ctrl for 10.","§fСкільки кіл до виходу\n§7Коло вважається пройденим, коли повтор дійшов до останнього кадру.\n§7Якщо «Скільки кіл у циклі» менше за це число, цикл закінчиться раніше і виходу не буде — мод попередить про це на старті.\n§8Крок 1, Ctrl — 10.","§fWie viele Runden bis zum Verlassen\n§7Eine Runde zählt als beendet, wenn die Wiedergabe den letzten Frame erreicht hat.\n§7Ist «Runden in der Schleife» kleiner als diese Zahl, endet die Schleife früher und es wird nicht getrennt; die Mod warnt beim Start davor.\n§8Schritt 1, Ctrl für 10.","§fIle rund do wyjścia\n§7Runda liczy się jako ukończona, gdy powtórka dotarła do ostatniej klatki.\n§7Jeśli «Ile rund w pętli» jest mniejsze od tej liczby, pętla skończy się wcześniej i nie będzie rozłączenia; mod ostrzeże o tym przy starcie.\n§8Krok 1, Ctrl — 10."));
                head(s("ВОЗВРАТ В ТОЧКУ СТАРТА","RETURN TO START","ПОВЕРНЕННЯ ДО СТАРТУ","RÜCKKEHR ZUM START","POWRÓT DO STARTU"));
                add(T_TOGGLE,K_AUTO_RETURN,s("Возврат в точку старта","Return to the start point","Повернення в точку старту","Rückkehr zum Startpunkt","Powrót do punktu startu"),s("Перед повтором и между циклами","Before playback and between cycles","Перед повтором і між циклами","Vor der Wiedergabe und zwischen Runden","Przed powtórką i między cyklami"),"\u2691",c,
                    tt("\u00a7fВозврат в точку старта\n\u00a77Вкл: перед повтором мод сам приведёт вас туда, где начиналась запись.\n\u00a77Выкл: повтор начнётся там, где вы стоите сейчас.\n\u00a78Нужен мод Baritone.","\u00a7fReturn to the start point\n\u00a77On: before playback the mod walks you back to where the recording began.\n\u00a77Off: playback starts wherever you are standing now.\n\u00a78Requires the Baritone mod.","§fПовернення в точку старту\n§7Увімк: перед повтором мод сам приведе вас туди, де починався запис.\n§7Вимк: повтор почнеться там, де ви стоїте зараз.\n§8Потрібен мод Baritone.","§fRückkehr zum Startpunkt\n§7An: vor der Wiedergabe bringt die Mod dich dorthin zurück, wo die Aufnahme begann.\n§7Aus: die Wiedergabe startet dort, wo du gerade stehst.\n§8Benötigt die Baritone-Mod.","§fPowrót do punktu startu\n§7Wł: przed powtórką mod sam zaprowadzi cię tam, gdzie zaczęło się nagranie.\n§7Wył: powtórka zacznie się tam, gdzie teraz stoisz.\n§8Wymaga moda Baritone."));
                add(T_TOGGLE,K_ROUTE,s("Стабилизация маршрута","Route stabilization","Стабілізація маршруту","Routenstabilisierung","Stabilizacja trasy"),s("Мягкая правка ввода при сносе","Gentle input correction on drift","М'яке виправлення вводу при зносі","Sanfte Eingabekorrektur bei Abdrift","Łagodna korekta sterowania przy dryfie"),"\u2248",c,
                    tt("\u00a7fДержаться маршрута\n\u00a77Если персонажа сносит в сторону, мод чуть-чуть подправит ход.\n\u00a77Помогает, когда мешают углы, лестницы или вода.\n\u00a78Обычно: вкл.","\u00a7fStay on the route\n\u00a77If the player drifts aside, the mod nudges the movement back.\n\u00a77Helps around corners, stairs and water.\n\u00a78Usually: on.","§fТриматися маршруту\n§7Якщо персонажа зносить убік, мод трохи поправить хід.\n§7Допомагає, коли заважають кути, драбини або вода.\n§8Зазвичай: увімк.","§fAuf der Route bleiben\n§7Wenn der Spieler seitlich abdriftet, korrigiert die Mod den Lauf leicht.\n§7Hilft bei Ecken, Leitern und Wasser.\n§8Meistens: an.","§fTrzymaj się trasy\n§7Gdy postać zbacza na bok, mod lekko koryguje ruch.\n§7Pomaga przy rogach, drabinach i wodzie.\n§8Zwykle: wł."));
                add(T_INFO,K_CURSOR,s("Плавный возврат взгляда","Smooth first-frame look","Плавне повернення погляду","Sanfte Blickrückkehr","Płynny powrót wzroku"),s("Всегда включён перед началом повтора","Always enabled before playback","Завжди увімкнено перед початком повтору","Vor der Wiedergabe immer aktiv","Zawsze włączony przed powtórką"),"\u25cb",c,
                    tt("\u00a7fВернуть направление взгляда\n\u00a77Перед стартом камера повернётся так же, как в начале записи.\n\u00a77Срабатывает один раз, потом обзор идёт как обычно.\n\u00a78Обычно: вкл.","\u00a7fRestore the look direction\n\u00a77Before the start the camera turns the same way as at the beginning of the recording.\n\u00a77It happens once, then the view works as usual.\n\u00a78Usually: on.","§fПовернути напрямок погляду\n§7Перед стартом камера повернеться так само, як на початку запису.\n§7Спрацьовує один раз, потім огляд іде як зазвичай.\n§8Зазвичай: увімк.","§fBlickrichtung wiederherstellen\n§7Vor dem Start dreht sich die Kamera so wie zu Beginn der Aufnahme.\n§7Passiert einmal, danach läuft die Sicht wie gewohnt.\n§8Meistens: an.","§fPrzywróć kierunek wzroku\n§7Przed startem kamera obróci się tak samo jak na początku nagrania.\n§7Działa raz, potem widok działa jak zwykle.\n§8Zwykle: wł."));
                add(T_ACTION,K_ADVANCED,s("Расширенные параметры","Advanced parameters","Розширені параметри","Erweiterte Parameter","Parametry zaawansowane"),s("Отдельный экран стабилизации","Separate stabilization screen","Окремий екран стабілізації","Separater Stabilisierungsbildschirm","Osobny ekran stabilizacji"),"\u2699",UiTheme.BLUE,
                    tt("\u00a7fДополнительные настройки\n\u00a77Те же настройки возврата, только на отдельном экране и с пояснениями.\n\u00a78Ничего нового включать не нужно.","\u00a7fExtra settings\n\u00a77The same return settings on a separate screen with explanations.\n\u00a78Nothing new to switch on there.","§fДодаткові налаштування\n§7Ті самі налаштування повернення, тільки на окремому екрані й з поясненнями.\n§8Нічого нового вмикати не потрібно.","§fZusätzliche Einstellungen\n§7Dieselben Rückkehr-Einstellungen auf einem separaten Bildschirm mit Erklärungen.\n§8Dort gibt es nichts Neues einzuschalten.","§fDodatkowe ustawienia\n§7Te same ustawienia powrotu, tylko na osobnym ekranie i z wyjaśnieniami.\n§8Nie trzeba tam niczego nowego włączać."));
                break;
            case 2:
                if(visElement==0){
                    head(s("МАРШРУТ","ROUTE","МАРШРУТ","ROUTE","TRASA"));
                    add(T_TOGGLE,K_PATH,s("Показывать маршрут","Show the route","Показувати маршрут","Route anzeigen","Pokazuj trasę"),s("Линия пути появится в мире во время повтора","The path line appears in the world during playback","Лінія шляху з'явиться у світі під час повтору","Die Weglinie erscheint während der Wiedergabe in der Welt","Linia trasy pojawi się w świecie podczas powtórki"),"≈",c,
                        tt("§fМаршрут\n§7Это линия в мире по тому пути, который вы записали.\n§7Выкл: линия не рисуется, повтор идёт как обычно.","§fRoute\n§7A line in the world along the path you recorded.\n§7Off: nothing is drawn, playback still runs.","§fМаршрут\n§7Це лінія у світі за тим шляхом, який ви записали.\n§7Вимк: лінія не малюється, повтор іде як зазвичай.","§fRoute\n§7Eine Linie in der Welt entlang des Weges, den du aufgenommen hast.\n§7Aus: nichts wird gezeichnet, die Wiedergabe läuft normal.","§fTrasa\n§7To linia w świecie wzdłuż drogi, którą nagrałeś.\n§7Wył: linia nie jest rysowana, powtórka działa normalnie."));
                    add(T_CYCLE,K_STYLE,s("Как рисовать маршрут","How the route is drawn","Як малювати маршрут","Wie die Route gezeichnet wird","Jak rysować trasę"),s("Линия, линия с точками или одни точки","Line, line with dots, or dots only","Лінія, лінія з точками або одні точки","Linie, Linie mit Punkten oder nur Punkte","Linia, linia z kropkami lub same kropki"),"─",c,
                        tt("§fВид маршрута\n§7Линия — сплошная дорожка.\n§7Линия и точки — дорожка с отметками.\n§7Точки — только отметки без линии.","§fRoute style\n§7Line is a solid trail.\n§7Line + points adds marks.\n§7Points shows marks only.","§fВигляд маршруту\n§7Лінія — суцільна доріжка.\n§7Лінія і точки — доріжка з позначками.\n§7Точки — тільки позначки без лінії.","§fRoutenstil\n§7Linie ist ein durchgehender Pfad.\n§7Linie + Punkte ergänzt Markierungen.\n§7Punkte zeigt nur Markierungen.","§fStyl trasy\n§7Linia — ciągła ścieżka.\n§7Linia i kropki — ścieżka ze znacznikami.\n§7Kropki — tylko znaczniki bez linii."));
                    add(T_ACTION,K_COLOR_TARGET,s("Цвет маршрута","Route color","Колір маршруту","Routenfarbe","Kolor trasy"),s(colorPanelOpen?"Палитра открыта — крутите колесо ниже":"Нажмите, чтобы открыть палитру",colorPanelOpen?"Palette is open — use the wheel below":"Click to open the palette",colorPanelOpen?"Палітра відкрита — крутіть колесо нижче":"Натисніть, щоб відкрити палітру",colorPanelOpen?"Palette ist offen — nutze das Rad unten":"Klicke, um die Palette zu öffnen",colorPanelOpen?"Paleta otwarta — kręć kółkiem poniżej":"Kliknij, aby otworzyć paletę"),"✦",c,
                        tt("§fЦвет маршрута\n§7Меняет цвет и прозрачность линии и точек на превью и в мире.","§fRoute color\n§7Changes the color and fade of the line and dots in the preview and in the world.","§fКолір маршруту\n§7Змінює колір і прозорість лінії та точок на прев'ю і в світі.","§fRoutenfarbe\n§7Ändert Farbe und Transparenz von Linie und Punkten in der Vorschau und in der Welt.","§fKolor trasy\n§7Zmienia kolor i przezroczystość linii i kropek na podglądzie i w świecie."));
                    head(s("ГЕОМЕТРИЯ","GEOMETRY","ГЕОМЕТРІЯ","GEOMETRIE","GEOMETRIA"));
                    add(T_STEP,K_HEIGHT,s("Высота над землёй","Height above ground","Висота над землею","Höhe über dem Boden","Wysokość nad ziemią"),s("Линия поднимется выше блоков","The line lifts above the blocks","Лінія підніметься вище блоків","Die Linie hebt sich über die Blöcke","Linia uniesie się ponad bloki"),"↑",c,
                        tt("§fВысота маршрута\n§7Насколько линия выше земли.\n§8Это только картинка, на ходьбу не влияет.","§fRoute height\n§7How high the line sits above the ground.\n§8Visual only.","§fВисота маршруту\n§7Наскільки лінія вище землі.\n§8Це лише картинка, на ходьбу не впливає.","§fRoutenhöhe\n§7Wie hoch die Linie über dem Boden liegt.\n§8Nur Optik, beeinflusst das Laufen nicht.","§fWysokość trasy\n§7O ile linia jest nad ziemią.\n§8To tylko obrazek, nie wpływa na chodzenie."));
                    add(T_STEP,K_LENGTH,s("Какую часть пути видно","How much of the path is shown","Яку частину шляху видно","Wie viel des Weges sichtbar ist","Jaka część drogi jest widoczna"),s("Сколько кадров маршрута рисуется впереди","How many upcoming frames are drawn","Скільки кадрів маршруту малюється попереду","Wie viele kommende Frames gezeichnet werden","Ile nadchodzących klatek trasy jest rysowanych"),"∞",c,
                        tt("§fДлина маршрута\n§7Больше число — дальше виден путь.\n§8Только картинка.","§fRoute length\n§7A higher number shows more of the path ahead.\n§8Visual only.","§fДовжина маршруту\n§7Більше число — далі видно шлях.\n§8Лише картинка.","§fRoutenlänge\n§7Eine höhere Zahl zeigt mehr vom Weg voraus.\n§8Nur Optik.","§fDługość trasy\n§7Większa liczba — dalej widać drogę.\n§8Tylko obrazek."));
                    add(T_STEP,K_WIDTH,s("Толщина линии маршрута","Route line thickness","Товщина лінії маршруту","Stärke der Routenlinie","Grubość linii trasy"),s("Линия станет толще или тоньше","The line gets thicker or thinner","Лінія стане товщою або тоншою","Die Linie wird dicker oder dünner","Linia stanie się grubsza lub cieńsza"),"─",c,
                        tt("§fТолщина линии\n§7Толще — маршрут заметнее.\n§8Не меняет движение.","§fLine width\n§7Thicker makes the route easier to see.\n§8Does not change movement.","§fТовщина лінії\n§7Товще — маршрут помітніший.\n§8Не змінює рух.","§fLinienstärke\n§7Dicker macht die Route sichtbarer.\n§8Ändert die Bewegung nicht.","§fGrubość linii\n§7Grubsza — trasa bardziej widoczna.\n§8Nie zmienia ruchu."));
                    if(config.getPathLineStyle()!=0){
                        add(T_STEP,K_PSIZE,s("Размер точек на маршруте","Size of the route dots","Розмір точок на маршруті","Größe der Routenpunkte","Rozmiar kropek na trasie"),s("Точки на линии станут крупнее или мельче","The dots on the path grow or shrink","Точки на лінії стануть більшими або меншими","Die Punkte auf dem Weg wachsen oder schrumpfen","Kropki na linii staną się większe lub mniejsze"),"●",c,
                            tt("§fРазмер точек\n§7Крупнее — их проще заметить.\n§8Видно только если включены точки.","§fDot size\n§7Bigger dots are easier to see.\n§8Only if dots are enabled.","§fРозмір точок\n§7Більші — їх легше помітити.\n§8Видно лише якщо точки увімкнені.","§fPunktgröße\n§7Größere Punkte sind leichter zu sehen.\n§8Nur sichtbar, wenn Punkte aktiv sind.","§fRozmiar kropek\n§7Większe kropki łatwiej zauważyć.\n§8Widoczne tylko przy włączonych kropkach."));
                        add(T_STEP,K_PSTEP,s("Как часто ставить точки","How often to place dots","Як часто ставити точки","Wie oft Punkte gesetzt werden","Jak często stawiać kropki"),s("Точки на маршруте чаще или реже","Dots along the path appear more or less often","Точки на маршруті частіше або рідше","Punkte erscheinen öfter oder seltener","Kropki na trasie częściej lub rzadziej"),"·",c,
                            tt("§fЧастота точек\n§7Меньше шаг — точки чаще.\n§8Видно только если включены точки.","§fDot spacing\n§7A smaller step means more dots.\n§8Only if dots are enabled.","§fЧастота точок\n§7Менший крок — точки частіше.\n§8Видно лише якщо точки увімкнені.","§fPunktabstand\n§7Ein kleinerer Schritt bedeutet mehr Punkte.\n§8Nur sichtbar, wenn Punkte aktiv sind.","§fCzęstotliwość kropek\n§7Mniejszy krok — kropki częściej.\n§8Widoczne tylko przy włączonych kropkach."));
                    }
                    head(s("ВИДИМОСТЬ","VISIBILITY","ВИДИМІСТЬ","SICHTBARKEIT","WIDOCZNOŚĆ"));
                    add(T_TOGGLE,K_FADE,s("Линия бледнеет к концу","The line fades toward the end","Лінія блідне до кінця","Die Linie verblasst zum Ende","Linia blednie ku końcowi"),s("Дальний конец пути станет прозрачнее","The far end of the path becomes see-through","Далекий кінець шляху стане прозорішим","Das ferne Ende des Weges wird durchsichtig","Dalszy koniec drogi stanie się przezroczysty"),"░",c,
                        tt("§fЗатухание\n§7Ближний кусок яркий, дальний почти исчезает.","§fFade\n§7The near part stays bright, the far part almost vanishes.","§fЗгасання\n§7Ближній шматок яскравий, далекий майже зникає.","§fVerblassen\n§7Der nahe Teil bleibt hell, der ferne verschwindet fast.","§fWygasanie\n§7Bliski fragment jest jasny, dalszy niemal znika."));
                    add(T_TOGGLE,K_LWALLS,s("Маршрут виден сквозь стены","Route stays visible through walls","Маршрут видно крізь стіни","Route bleibt durch Wände sichtbar","Trasa widoczna przez ściany"),s("Линия не спрячется за блоками","The line will not hide behind blocks","Лінія не сховається за блоками","Die Linie versteckt sich nicht hinter Blöcken","Linia nie schowa się za blokami"),"□",c,
                        tt("§fСквозь стены\n§7Маршрут рисуется поверх блоков.\n§8Только картинка.","§fThrough walls\n§7The route is drawn over blocks.\n§8Visual only.","§fКрізь стіни\n§7Маршрут малюється поверх блоків.\n§8Лише картинка.","§fDurch Wände\n§7Die Route wird über den Blöcken gezeichnet.\n§8Nur Optik.","§fPrzez ściany\n§7Trasa jest rysowana nad blokami.\n§8Tylko obrazek."));
                    add(T_ACTION,K_RESET_LOOK,s("Сбросить оформление","Reset the look","Скинути оформлення","Aussehen zurücksetzen","Zresetuj wygląd"),s("Вернуть обычный вид линии и метки","Bring back the normal line and marker","Повернути звичайний вигляд лінії та мітки","Normale Linie und Markierung wiederherstellen","Przywróć zwykły wygląd linii i znacznika"),"↻",UiTheme.AMBER,
                        tt("§fСброс вида\n§7Обычные цвета и размеры линии и метки.\n§8Записи не трогает.","§fReset look\n§7Normal colors and sizes for the line and marker.\n§8Recordings stay.","§fСкидання вигляду\n§7Звичайні кольори й розміри лінії та мітки.\n§8Записи не чіпає.","§fOptik zurücksetzen\n§7Normale Farben und Größen für Linie und Markierung.\n§8Aufnahmen bleiben.","§fReset wyglądu\n§7Zwykłe kolory i rozmiary linii i znacznika.\n§8Nagrań nie rusza."));
                }else{
                    head(s("МЕТКА СТАРТА","START MARKER","МІТКА СТАРТУ","STARTMARKIERUNG","ZNACZNIK STARTU"));
                    add(T_CYCLE,K_MODE,s("Вид метки старта","Start marker style","Вигляд мітки старту","Stil der Startmarkierung","Styl znacznika startu"),s("Выкл, точка, кольцо или луч в месте начала записи","Off, a dot, a ring, or a beam at the start point","Вимк, точка, кільце або промінь у місці початку запису","Aus, Punkt, Ring oder Strahl am Startpunkt","Wył., kropka, pierścień lub wiązka w miejscu startu"),"⚑",c,
                        tt("§fМетка старта\n§7Значок в мире там, где началась запись.\n§7Выкл — метки нет. Точка, кольцо или луч — разные виды одного значка.","§fStart marker\n§7A mark in the world where the recording began.\n§7Off hides it. Dot, ring or beam are just looks.","§fМітка старту\n§7Значок у світі там, де почався запис.\n§7Вимк — мітки немає. Точка, кільце або промінь — різні види одного значка.","§fStartmarkierung\n§7Ein Zeichen in der Welt, wo die Aufnahme begann.\n§7Aus blendet es aus. Punkt, Ring oder Strahl sind nur Stile.","§fZnacznik startu\n§7Znak w świecie tam, gdzie zaczęło się nagranie.\n§7Wył. — brak znacznika. Kropka, pierścień lub wiązka — różne warianty znaku."));
                    if(config.getStartMarkerMode()>0){
                        add(T_TOGGLE,K_MCOLOR,s("Свой цвет у метки","Own color for the marker","Свій колір у мітки","Eigene Farbe für die Markierung","Własny kolor znacznika"),s("Метка не копирует цвет маршрута","The marker stops copying the route color","Мітка не копіює колір маршруту","Die Markierung übernimmt die Routenfarbe nicht","Znacznik nie kopiuje koloru trasy"),"✦",c,
                            tt("§fОтдельный цвет\n§7Вкл: метка красится сама.\n§7Выкл: метка того же цвета, что и маршрут.","§fSeparate color\n§7On: the marker has its own color.\n§7Off: it uses the route color.","§fОкремий колір\n§7Увімк: мітка фарбується сама.\n§7Вимк: мітка того ж кольору, що й маршрут.","§fEigene Farbe\n§7An: die Markierung hat ihre eigene Farbe.\n§7Aus: sie nutzt die Routenfarbe.","§fOsobny kolor\n§7Wł: znacznik ma własny kolor.\n§7Wył: używa koloru trasy."));
                        add(T_ACTION,K_COLOR_TARGET,s("Цвет метки","Marker color","Колір мітки","Markierungsfarbe","Kolor znacznika"),s(colorPanelOpen?"Палитра открыта — крутите колесо ниже":"Нажмите, чтобы открыть палитру",colorPanelOpen?"Palette is open — use the wheel below":"Click to open the palette",colorPanelOpen?"Палітра відкрита — крутіть колесо нижче":"Натисніть, щоб відкрити палітру",colorPanelOpen?"Palette ist offen — nutze das Rad unten":"Klicke, um die Palette zu öffnen",colorPanelOpen?"Paleta otwarta — kręć kółkiem poniżej":"Kliknij, aby otworzyć paletę"),"✦",c,
                            tt("§fЦвет метки\n§7Меняет цвет значка на превью и в мире.\n§8Если свой цвет выключен, палитра включит его сама.","§fMarker color\n§7Changes the mark in the preview and in the world.\n§8If own color is off, the palette turns it on.","§fКолір мітки\n§7Змінює колір значка на прев'ю і в світі.\n§8Якщо свій колір вимкнено, палітра увімкне його сама.","§fMarkierungsfarbe\n§7Ändert das Zeichen in Vorschau und Welt.\n§8Ist die eigene Farbe aus, aktiviert die Palette sie automatisch.","§fKolor znacznika\n§7Zmienia kolor znaku na podglądzie i w świecie.\n§8Jeśli własny kolor jest wyłączony, paleta włączy go sama."));
                        if(config.getStartMarkerMode()>=2){
                            head(s("ГЕОМЕТРИЯ","GEOMETRY","ГЕОМЕТРІЯ","GEOMETRIE","GEOMETRIA"));
                            add(T_STEP,K_RADIUS,s("Размер кольца метки","Size of the marker ring","Розмір кільця мітки","Größe des Markierungsrings","Rozmiar pierścienia znacznika"),s("Кольцо вокруг точки старта станет шире","The ring around the start point grows","Кільце навколо точки старту стане ширшим","Der Ring um den Startpunkt wächst","Pierścień wokół punktu startu stanie się szerszy"),"○",c,
                                tt("§fРазмер кольца\n§7Шире кольцо — метку проще найти.","§fRing size\n§7A wider ring is easier to spot.","§fРозмір кільця\n§7Ширше кільце — мітку легше знайти.","§fRinggröße\n§7Ein breiterer Ring ist leichter zu finden.","§fRozmiar pierścienia\n§7Szerszy pierścień łatwiej dostrzec."));
                        }
                        if(config.getStartMarkerMode()==3){
                            add(T_STEP,K_BEACON,s("Высота луча метки","Height of the marker beam","Висота променя мітки","Höhe des Markierungsstrahls","Wysokość wiązki znacznika"),s("Вертикальный луч станет выше","The vertical beam grows taller","Вертикальний промінь стане вищим","Der vertikale Strahl wird höher","Pionowa wiązka stanie się wyższa"),"↑",c,
                                tt("§fВысота луча\n§7Выше луч — его видно издалека.","§fBeam height\n§7A taller beam is visible from farther away.","§fВисота променя\n§7Вищий промінь — його видно здалеку.","§fStrahlhöhe\n§7Ein höherer Strahl ist aus der Ferne sichtbar.","§fWysokość wiązki\n§7Wyższa wiązka widoczna z daleka."));
                        }
                        head(s("ПОДПИСЬ И ЭФФЕКТЫ","LABEL AND EFFECTS","ПІДПИС І ЕФЕКТИ","BESCHRIFTUNG","PODPIS I EFEKTY"));
                        add(T_TOGGLE,K_PULSE,s("Метка пульсирует","The marker pulses","Мітка пульсує","Die Markierung pulsiert","Znacznik pulsuje"),s("Значок будет мягко мигать","The mark softly blinks","Значок буде м'яко блимати","Das Zeichen blinkt sanft","Znak będzie łagodnie migać"),"●",c,
                            tt("§fПульсация\n§7Метка то ярче, то тусклее — её проще заметить.","§fPulse\n§7The mark brightens and fades so it is easier to see.","§fПульсація\n§7Мітка то яскравіша, то тьмяніша — її легше помітити.","§fPulsieren\n§7Die Markierung wird heller und dunkler — so fällt sie auf.","§fPulsacja\n§7Znacznik jaśnieje i przygasa — łatwiej go zauważyć."));
                        add(T_TOGGLE,K_MLABEL,s("Подпись у метки","Label on the marker","Підпис біля мітки","Beschriftung der Markierung","Podpis przy znaczniku"),s("Над значком появится текст «Начало записи»","A Start label appears above the mark","Над значком з'явиться текст «Початок запису»","Über dem Zeichen erscheint ein Start-Text","Nad znakiem pojawi się tekst «Początek nagrania»"),"T",c,
                            tt("§fПодпись\n§7Над меткой пишется, что это начало записи.","§fLabel\n§7Text above the mark says this is the start.","§fПідпис\n§7Над міткою пишеться, що це початок запису.","§fBeschriftung\n§7Der Text über der Markierung sagt, dass hier der Start ist.","§fPodpis\n§7Tekst nad znacznikiem wskazuje początek nagrania."));
                        if(config.isMarkerLabel()){
                            add(T_STEP,K_MDIST,s("Как далеко видна подпись","How far the label stays visible","Як далеко видно підпис","Wie weit die Beschriftung sichtbar ist","Jak daleko widać podpis"),s("Подпись пропадёт, если отойти дальше","The label hides if you walk farther","Підпис зникне, якщо відійти далі","Die Beschriftung verschwindet, wenn du weiter weggehst","Podpis zniknie, gdy odejdziesz dalej"),"↔",c,
                                tt("§fДальность подписи\n§7Дальше число — подпись видна с большего расстояния.","§fLabel distance\n§7A higher number keeps the label visible from farther away.","§fДальність підпису\n§7Більше число — підпис видно з більшої відстані.","§fBeschriftungsreichweite\n§7Ein höherer Wert hält die Beschriftung aus größerer Entfernung sichtbar.","§fZasięg podpisu\n§7Większa liczba — podpis widać z większej odległości."));
                            add(T_TOGGLE,K_MUNLIM,s("Подпись с любого расстояния","Show the label at any distance","Підпис з будь-якої відстані","Beschriftung aus jeder Entfernung","Podpis z dowolnej odległości"),s("Текст метки виден даже издалека","The marker text stays visible even far away","Текст мітки видно навіть здалеку","Der Markierungstext bleibt selbst fern sichtbar","Tekst znacznika widać nawet z daleka"),"∞",c,
                                tt("§fБез лимита\n§7Подпись не исчезает, как бы далеко вы ни отошли.","§fNo limit\n§7The label never hides, no matter how far you walk.","§fБез ліміту\n§7Підпис не зникає, як би далеко ви не відійшли.","§fKein Limit\n§7Die Beschriftung verschwindet nie, egal wie weit du gehst.","§fBez limitu\n§7Podpis nie znika, niezależnie jak daleko odejdziesz."));
                        }
                        head(s("ВИДИМОСТЬ","VISIBILITY","ВИДИМІСТЬ","SICHTBARKEIT","WIDOCZNOŚĆ"));
                        add(T_TOGGLE,K_MWALLS,s("Метка видна сквозь стены","Marker stays visible through walls","Мітку видно крізь стіни","Markierung bleibt durch Wände sichtbar","Znacznik widoczny przez ściany"),s("Значок не спрячется за блоками","The mark will not hide behind blocks","Значок не сховається за блоками","Das Zeichen versteckt sich nicht hinter Blöcken","Znak nie schowa się za blokami"),"□",c,
                            tt("§fСквозь стены\n§7Метку видно даже за холмом или стеной.","§fThrough walls\n§7The mark stays visible behind hills or walls.","§fКрізь стіни\n§7Мітку видно навіть за пагорбом або стіною.","§fDurch Wände\n§7Die Markierung bleibt hinter Hügeln und Wänden sichtbar.","§fPrzez ściany\n§7Znacznik widać nawet za wzgórzem lub ścianą."));
                    }
                    add(T_ACTION,K_RESET_LOOK,s("Сбросить оформление","Reset the look","Скинути оформлення","Aussehen zurücksetzen","Zresetuj wygląd"),s("Вернуть обычный вид линии и метки","Bring back the normal line and marker","Повернути звичайний вигляд лінії та мітки","Normale Linie und Markierung wiederherstellen","Przywróć zwykły wygląd linii i znacznika"),"↻",UiTheme.AMBER,
                        tt("§fСброс вида\n§7Обычные цвета и размеры линии и метки.\n§8Записи не трогает.","§fReset look\n§7Normal colors and sizes for the line and marker.\n§8Recordings stay.","§fСкидання вигляду\n§7Звичайні кольори й розміри лінії та мітки.\n§8Записи не чіпає.","§fOptik zurücksetzen\n§7Normale Farben und Größen für Linie und Markierung.\n§8Aufnahmen bleiben.","§fReset wyglądu\n§7Zwykłe kolory i rozmiary linii i znacznika.\n§8Nagrań nie rusza."));
                }
                break;
            case 3:
                head(s("ИНТЕРФЕЙС","INTERFACE","ІНТЕРФЕЙС","OBERFLÄCHE","INTERFEJS"));
                add(T_TOGGLE,K_TIPS,s("Подсказки при наведении","Hover tooltips","Підказки при наведенні","Tooltips beim Überfahren","Podpowiedzi po najechaniu"),s("Краткое объяснение рядом с курсором","Short explanation near the cursor","Коротке пояснення поруч із курсором","Kurze Erklärung neben dem Cursor","Krótkie wyjaśnienie przy kursorze"),"?",c,
                    tt("§fПодсказки\n§7Маленькие объяснения рядом с курсором во всех окнах мода.\n§8Это и есть такая подсказка.","§fTooltips\n§7Small explanations next to your cursor in every mod window.\n§8This box is exactly such a tooltip.","§fПідказки\n§7Маленькі пояснення поруч із курсором у всіх вікнах мода.\n§8Це і є така підказка.","§fTooltips\n§7Kleine Erklärungen neben dem Cursor in jedem Mod-Fenster.\n§8Diese Box ist genau so ein Tooltip.","§fPodpowiedzi\n§7Małe wyjaśnienia obok kursora we wszystkich oknach moda.\n§8To właśnie taka podpowiedź."));
                add(T_TOGGLE,K_SOUNDS,s("Звуки мода","Mod sounds","Звуки мода","Mod-Sounds","Dźwięki moda"),s("Отсчёт, старт, конец повтора","Countdown, start, finish","Відлік, старт, кінець повтору","Countdown, Start, Ende","Odliczanie, start, koniec powtórki"),"\u266b",c,
                    tt("§fЗвуки\n§7Тихие сигналы: отсчёт перед стартом, начало записи, начало и конец повтора, остановка.\n§7Выкл: мод работает совсем молча.\n§8Громкость берётся из настроек звука Minecraft.","§fSounds\n§7Quiet cues: the countdown, recording start, playback start and finish, and stops.\n§7Off: the mod stays completely silent.\n§8Volume follows your Minecraft sound settings.","§fЗвуки\n§7Тихі сигнали: відлік перед стартом, початок запису, початок і кінець повтору, зупинка.\n§7Вимк: мод працює зовсім мовчки.\n§8Гучність береться з налаштувань звуку Minecraft.","§fSounds\n§7Leise Signale: Countdown, Aufnahmestart, Wiedergabestart und -ende, Stopp.\n§7Aus: die Mod arbeitet völlig lautlos.\n§8Die Lautstärke folgt den Minecraft-Soundeinstellungen.","§fDźwięki\n§7Ciche sygnały: odliczanie przed startem, początek nagrania, początek i koniec powtórki, zatrzymanie.\n§7Wył: mod działa całkowicie cicho.\n§8Głośność z ustawień dźwięku Minecrafta."));
                add(T_CYCLE,K_LANG,s("Язык интерфейса","Interface language","Мова інтерфейсу","Sprache der Oberfläche","Język interfejsu"),s("Русский или английский","Russian or English","П'ять мов на вибір","Fünf Sprachen zur Wahl","Pięć języków do wyboru"),"\u2709",c,
                    tt("§fЯзык\n§7Меняет язык всех надписей: русский или английский.\n§8Ваши записи от этого не меняются.","§fLanguage\n§7Switches all text between Russian and English.\n§8Your recordings stay untouched.","§fМова\n§7Змінює мову всіх написів: російська, англійська, українська, німецька або польська.\n§8Ваші записи від цього не змінюються.","§fSprache\n§7Stellt alle Texte um: Russisch, Englisch, Ukrainisch, Deutsch oder Polnisch.\n§8Deine Aufnahmen bleiben unverändert.","§fJęzyk\n§7Zmienia język wszystkich napisów: rosyjski, angielski, ukraiński, niemiecki lub polski.\n§8Twoje nagrania pozostają nietknięte."));
                head(s("ДАННЫЕ","DATA","ДАНІ","DATEN","DANE"));
                add(T_ACTION,K_TRASH,s("Корзина записей","Recording trash","Кошик записів","Aufnahme-Papierkorb","Kosz nagrań"),s("Вернуть удалённую запись","Bring a deleted recording back","Повернути видалений запис","Gelöschte Aufnahme zurückholen","Przywróć usunięte nagranie"),"\u21bb",UiTheme.GREEN,
                    tt("§fКорзина\n§7Каждая удалённая запись сначала попадает сюда, а не исчезает навсегда.\n§7Оттуда её можно вернуть в свой слот одной кнопкой.\n§8Хранится 100 последних удалений.","§fTrash\n§7Every deleted recording lands here first instead of disappearing for good.\n§7From there one button puts it back into its slot.\n§8The last 100 deletions are kept.","§fКошик\n§7Кожен видалений запис спочатку потрапляє сюди, а не зникає назавжди.\n§7Звідти його можна повернути у свій слот однією кнопкою.\n§8Зберігається 100 останніх видалень.","§fPapierkorb\n§7Jede gelöschte Aufnahme landet zuerst hier, statt für immer zu verschwinden.\n§7Von dort holt sie ein Klick zurück in ihren Slot.\n§8Die letzten 100 Löschungen werden aufbewahrt.","§fKosz\n§7Każde usunięte nagranie trafia najpierw tutaj, zamiast znikać na zawsze.\n§7Stamtąd jeden przycisk przywraca je do swojego slotu.\n§8Przechowywanych jest 100 ostatnich usunięć."));
                add(T_ACTION,K_RESET,s("Сбросить до заводских настроек","Reset to factory settings","Скинути до заводських налаштувань","Auf Werkseinstellungen zurücksetzen","Przywróć ustawienia fabryczne"),s("Записи в слотах сохранятся","Recordings remain untouched","Записи в слотах збережуться","Aufnahmen bleiben unberührt","Nagrania w slotach zostaną"),"\u21bb",UiTheme.RED,
                    tt("§fСброс настроек\n§7Вернёт все настройки такими, какими они были при установке мода.\n§7Сначала спросим подтверждение.\n§8Записи в слотах останутся на месте.","§fReset settings\n§7Puts every setting back the way it was when the mod was installed.\n§7You will be asked to confirm first.\n§8Your recordings stay where they are.","§fСкидання налаштувань\n§7Поверне всі налаштування такими, якими вони були при встановленні мода.\n§7Спочатку запитаємо підтвердження.\n§8Записи в слотах залишаться на місці.","§fEinstellungen zurücksetzen\n§7Setzt alles auf den Stand bei der Installation der Mod zurück.\n§7Du wirst vorher um Bestätigung gebeten.\n§8Deine Aufnahmen bleiben, wo sie sind.","§fReset ustawień\n§7Przywraca wszystkie ustawienia do stanu z momentu instalacji moda.\n§7Najpierw poprosimy o potwierdzenie.\n§8Nagrania w slotach zostają na miejscu."));
                break;
            case 4:
                break;
        }
        applyArchiveGroups();
    }

    // ------------------------------------------------ значения
    private String duration(int frames){int sec=Math.max(0,frames/20);return sec>=60?(sec/60)+s("м ","m ","хв ","m ","min ")+(sec%60)+s("с","s","с","s","s"):sec+s("с","s","с","s","s");}
    private String markerName(){
        switch(config.getStartMarkerMode()){case 0:return s("Выкл","Off","Вимк","Aus","Wył.");case 1:return s("Точка","Point","Точка","Punkt","Kropka");case 2:return s("Кольцо","Ring","Кільце","Ring","Pierścień");default:return s("Луч","Beacon","Промінь","Strahl","Wiązka");}
    }
    private String styleName(){
        switch(config.getPathLineStyle()){case 0:return s("Линия","Line","Лінія","Linie","Linia");case 1:return s("Линия и точки","Line + points","Лінія і точки","Linie + Punkte","Linia i kropki");default:return s("Точки","Points","Точки","Punkte","Kropki");}
    }
    private String valueOf(int key){
        switch(key){
            case K_DELAY:return config.getStartDelay()+s(" с"," s"," с"," s"," s");
            case K_LIMIT:return config.getLoopLimit()==0?s("Без ограничения","Unlimited","Без обмежень","Unbegrenzt","Bez limitu"):String.valueOf(config.getLoopLimit());
            case K_SPEED:return String.format(java.util.Locale.ROOT,"%.2f",config.getPlaybackSpeed())+"x";
            case K_LEAVE_CYCLES:return String.valueOf(config.getAutoLeaveCycles());
            case K_STOP_MOUSE_PX:return config.getStopOnMouseThreshold()+" px";
            case K_RET_TOL:return String.format(java.util.Locale.ROOT,"%.2f",config.getReturnPositionTolerance());
            case K_RET_SPEED:return String.format(java.util.Locale.ROOT,"%.1f",config.getReturnAlignSpeed());
            case K_RET_TIME:return config.getReturnTimeoutSeconds()+s(" с"," s"," с"," s"," s");
            case K_RADIUS:return String.format(java.util.Locale.ROOT,"%.1f",config.getMarkerRadius());
            case K_BEACON:return String.format(java.util.Locale.ROOT,"%.0f",config.getMarkerBeaconHeight());
            case K_MDIST:return config.isMarkerLabelUnlimited()?s("Без лимита","Unlimited","Без ліміту","Unbegrenzt","Bez limitu"):config.getMarkerLabelDistance()+s(" бл."," bl."," бл."," Bl."," bl.");
            case K_WIDTH:return String.format(java.util.Locale.ROOT,"%.1f",config.getPathLineWidth());
            case K_LENGTH:return String.valueOf(config.getPathLineLength());
            case K_PSTEP:return String.valueOf(config.getPathPointStep());
            case K_PSIZE:return String.format(java.util.Locale.ROOT,"%.2f",config.getPathPointSize());
            case K_HEIGHT:return String.format(java.util.Locale.ROOT,"%.1f",config.getPathLineHeight());
            case K_MODE:return markerName();
            case K_STYLE:return styleName();
            case K_LANG:return Lang.name();
            case K_COLOR_TARGET:return colorTarget==0?s("Линия","Path","Маршрут","Route","Trasa"):s("Метка","Marker","Мітка","Markierung","Znacznik");
            default:return "";
        }
    }
    private boolean stateOf(int key){
        switch(key){
            case K_PLAY_MOVE:return config.isPlaybackMovement();
            case K_PLAY_JUMP:return config.isPlaybackJump();
            case K_PLAY_SNEAK:return config.isPlaybackSneak();
            case K_PLAY_SPRINT:return config.isPlaybackSprint();
            case K_PLAY_CLICK:return config.isPlaybackInteraction();
            case K_PLAY_CHAT:return config.isPlaybackChat();
            case K_EXACT_PLACE:return config.isExactPlacement();
            case K_VISIBLE_CHAT:return config.isVisibleChat();
            case K_ROTATION:return config.isApplyRotation();
            case K_STOP:return config.isStopOnMove();
            case K_STOP_MOUSE:return config.isStopOnMouseMove();
            case K_AUTO_RETURN:return config.isAutoReturnEnabled();
            case K_ROUTE:return config.isRouteStabilization();
            case K_CURSOR:return config.isCursorStabilization();
            case K_SKIP:return config.isSkipLoopStartDelay();
            case K_AUTO_LEAVE:return config.isAutoLeaveEnabled();
            case K_PULSE:return config.isMarkerPulse();
            case K_MLABEL:return config.isMarkerLabel();
            case K_MUNLIM:return config.isMarkerLabelUnlimited();
            case K_MWALLS:return config.isMarkerThroughWalls();
            case K_MCOLOR:return config.isMarkerCustomColor();
            case K_TIPS:return config.isShowTooltips();
            case K_SOUNDS:return config.isSoundsEnabled();
            case K_GUARD_DMG:return config.isGuardOnDamage();
            case K_PATH:return config.isPathLineEnabled();
            case K_FADE:return config.isPathLineFade();
            case K_LWALLS:return config.isPathLineThroughWalls();
            default:return false;
        }
    }
    private void toggleKey(int key){
        boolean v=!stateOf(key);
        switch(key){
            case K_PLAY_MOVE:config.setPlaybackMovement(v);break;
            case K_PLAY_JUMP:config.setPlaybackJump(v);break;
            case K_PLAY_SNEAK:config.setPlaybackSneak(v);break;
            case K_PLAY_SPRINT:config.setPlaybackSprint(v);break;
            case K_PLAY_CLICK:config.setPlaybackInteraction(v);break;
            case K_PLAY_CHAT:config.setPlaybackChat(v);break;
            case K_EXACT_PLACE:config.setExactPlacement(v);break;
            case K_VISIBLE_CHAT:config.setVisibleChat(v);break;
            case K_ROTATION:config.setApplyRotation(v);break;
            case K_STOP:config.setStopOnMove(v);break;
            case K_STOP_MOUSE:config.setStopOnMouseMove(v);break;
            case K_AUTO_RETURN:config.setAutoReturnEnabled(v);break;
            case K_ROUTE:config.setRouteStabilization(v);break;
            case K_CURSOR:config.setCursorStabilization(v);break;
            case K_SKIP:config.setSkipLoopStartDelay(v);break;
            case K_AUTO_LEAVE:config.setAutoLeaveEnabled(v);break;
            case K_PULSE:config.setMarkerPulse(v);break;
            case K_MLABEL:config.setMarkerLabel(v);break;
            case K_MUNLIM:config.setMarkerLabelUnlimited(v);break;
            case K_MWALLS:config.setMarkerThroughWalls(v);break;
            case K_MCOLOR:config.setMarkerCustomColor(v);break;
            case K_TIPS:config.setShowTooltips(v);break;
            case K_SOUNDS:config.setSoundsEnabled(v);break;
            case K_GUARD_DMG:config.setGuardOnDamage(v);break;
            case K_PATH:config.setPathLineEnabled(v);break;
            case K_FADE:config.setPathLineFade(v);break;
            case K_LWALLS:config.setPathLineThroughWalls(v);break;
            default:break;
        }
    }
    private void step(int key,int dir){
        boolean fine=Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)||Keyboard.isKeyDown(Keyboard.KEY_RSHIFT),coarse=Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)||Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        switch(key){
            case K_DELAY:config.setStartDelay(config.getStartDelay()+dir*(coarse?5:1));break;
            case K_LEAVE_CYCLES:config.setAutoLeaveCycles(config.getAutoLeaveCycles()+dir*(coarse?10:1));break;
            case K_STOP_MOUSE_PX:config.setStopOnMouseThreshold(config.getStopOnMouseThreshold()+dir*(coarse?5:1));break;
            case K_LIMIT:{int cur=config.getLoopLimit(),st=coarse?10:fine?1:2;config.setLoopLimit(dir>0?(cur==0?2:cur+st):(cur==0?0:cur-st<2?0:cur-st));break;}
            case K_SPEED:config.setPlaybackSpeed(config.getPlaybackSpeed()+dir*(fine?0.05f:coarse?0.5f:0.25f));break;
            case K_RET_TOL:config.setReturnPositionTolerance(config.getReturnPositionTolerance()+dir*(coarse?0.05f:0.01f));break;
            case K_RET_SPEED:config.setReturnAlignSpeed(config.getReturnAlignSpeed()+dir*(fine?0.05f:coarse?0.2f:0.1f));break;
            case K_RET_TIME:config.setReturnTimeoutSeconds(config.getReturnTimeoutSeconds()+dir*(fine?1:coarse?15:5));break;
            case K_RADIUS:config.setMarkerRadius(config.getMarkerRadius()+dir*(fine?0.05f:coarse?0.25f:0.1f));break;
            case K_BEACON:config.setMarkerBeaconHeight(config.getMarkerBeaconHeight()+dir*(fine?1f:coarse?5f:2f));break;
            case K_MDIST:config.setMarkerLabelDistance(config.getMarkerLabelDistance()+dir*(fine?1:coarse?32:8));break;
            case K_WIDTH:config.setPathLineWidth(config.getPathLineWidth()+dir*(fine?0.1f:coarse?1f:0.5f));break;
            case K_LENGTH:config.setPathLineLength(config.getPathLineLength()+dir*(fine?5:coarse?50:20));break;
            case K_PSTEP:config.setPathPointStep(config.getPathPointStep()+dir*(coarse?5:1));break;
            case K_PSIZE:config.setPathPointSize(config.getPathPointSize()+dir*(fine?0.01f:coarse?0.1f:0.05f));break;
            case K_HEIGHT:config.setPathLineHeight(config.getPathLineHeight()+dir*(fine?0.05f:coarse?0.25f:0.1f));break;
            default:break;
        }
    }

    // ------------------------------------------------ разметка
    private StyledButton add(StyledButton b){buttonList.add(b);return b;}
    private int stepBtn(){return Math.min(rowH-8,16);}
    private int stepValW(){int need=48;if(fontRenderer!=null)need=Math.max(need,fontRenderer.getStringWidth(s("Без ограничения","Unlimited","Без обмежень","Unbegrenzt","Bez limitu"))+8);return Math.min(need,Math.max(48,contentRight-contentX-160));}
    private int stepBlockW(){return stepBtn()*2+stepValW();}
    private int stepLabelW(){
        if(rows==null||rows.isEmpty())return 120;
        int w=0;
        for(Row r:rows){
            if(r.type!=T_STEP)continue;
            int lw=r.label==null?0:fontRenderer.getStringWidth(r.label);
            if(r.note!=null&&rowH>=26)lw=Math.max(lw,fontRenderer.getStringWidth(r.note));
            w=Math.max(w,lw);
        }
        int room=Math.max(90,contentRight-contentX-58-stepBlockW());
        return Math.max(90,Math.min(w+4,room));
    }
    private int stepBlockX(){
        int icon=Math.min(rowH-8,14)+12;
        return Math.min(contentRight-12-stepBlockW(),contentX+6+icon+stepLabelW()+12);
    }
    // ---- полоса прокрутки списка функций
    /** Сколько строк влезает, если начать с заданной строки: возвращает индекс последней видимой. */
    private int lastVisibleRow(int from,int startY){
        int cursor=startY,count=0,last=from-1;
        for(int i=from;i<rows.size();i++){
            Row r=rows.get(i);
            if(r.type==T_HEAD){if(count>0)cursor+=GROUP_GAP-ROW_GAP;if(cursor+GROUP_HEAD_H>listBottom-4)break;count++;last=i;cursor+=GROUP_HEAD_STEP;continue;}
            if(cursor+rowH>listBottom-4)break;
            count++;last=i;cursor+=rowH+ROW_GAP;
        }
        return last;
    }
    /** Прокрутка доводится ровно до последней строки: недостижимых пунктов не остаётся. */
    private int computeMaxScroll(int startY){
        int total=rows==null?0:rows.size();
        if(total<=0)return 0;
        for(int s=0;s<total;s++)if(lastVisibleRow(s,startY)>=total-1)return s;
        return total-1;
    }
    private boolean sbActive(){return tab!=4&&rows!=null&&maxScroll>0;}
    private int sbTop(){return contentTop+4;}
    private int sbBottom(){return listBottom-4;}
    private int sbThumbH(){int track=sbBottom()-sbTop();return Math.min(track,Math.max(16,track*visibleRows/Math.max(1,rows.size())));}
    private int sbThumbY(){int track=sbBottom()-sbTop(),max=Math.max(1,maxScroll);return sbTop()+(track-sbThumbH())*Math.max(0,Math.min(max,scroll))/max;}
    private void sbSetFromMouse(int my){
        int span=Math.max(1,sbBottom()-sbTop()-sbThumbH()),max=maxScroll;
        int ns=Math.max(0,Math.min(max,Math.round((my-sbGrab-sbTop())/(float)span*max)));
        if(ns!=scroll){scroll=ns;initGui();}
    }
    private int valueBoxX(){return stepBlockX()+stepBtn();}

    @Override public void initGui(){
        buttonList.clear();wheel=null;colorLabelY=-1;
        winW=Math.min(width-14,596);winH=Math.min(height-14,412);winX=(width-winW)/2;winY=(height-winH)/2;
        headH=Math.min(38,Math.max(24,winH/9));footerY=winY+winH-26;
        sideX=winX+12;sideW=Math.max(96,Math.min(140,winW/4));sideTop=winY+headH+26;
        contentX=sideX+sideW+12;contentRight=winX+winW-12;contentTop=winY+headH+26;contentBottom=footerY-10;
        sideRowH=Math.max(18,Math.min(26,(contentBottom-sideTop-(TABS.length-1)*ROW_GAP)/TABS.length));
        for(int n=0;n<TABS.length;n++){
            int i=TABS[n];
            int y=sideTop+n*(sideRowH+ROW_GAP);
            StyledButton b=add(new StyledButton(TAB_BASE+i,sideX,y,sideW,sideRowH,tabName(i)).accent(tabColor(i)).icon(tabIcon(i)).compact()
                .tip(tt("\u00a7f"+tabName(i)+"\n\u00a77"+tabHint(i)+"\n\u00a78Нажмите, чтобы открыть этот раздел настроек.","\u00a7f"+tabName(i)+"\n\u00a77"+tabHint(i)+"\n\u00a78Click to open this group of settings.","\u00a7f"+tabName(i)+"\n\u00a77"+tabHint(i)+"\n\u00a78Натисніть, щоб відкрити цей розділ налаштувань.","\u00a7f"+tabName(i)+"\n\u00a77"+tabHint(i)+"\n\u00a78Klicke, um diese Einstellungsgruppe zu öffnen.","\u00a7f"+tabName(i)+"\n\u00a77"+tabHint(i)+"\n\u00a78Kliknij, aby otworzyć tę grupę ustawień.")));
            if(i==tab)b.primary();
        }
        openingChild=false;
        buildRows();
        rowY=new int[rows.size()];Arrays.fill(rowY,-1);
        int colorReserve=hasColorPanel()?78:0;
        listBottom=contentBottom-colorReserve;
        int avail=listBottom-contentTop-4;rowH=26;if(avail<rowH+5)rowH=Math.max(14,avail);
        visibleRows=Math.max(1,(avail+5)/(rowH+5));
        visPrevL=contentX+6;visPrevT=contentTop+6;visPrevR=contentRight-6;visPrevB=visPrevT;
        int cursorY=contentTop+4,placed=0;
        if(tab==2){
            boolean tight=colorPanelOpen&&contentBottom-contentTop<170;
            int ph=tight?0:Math.max(54,Math.min(colorPanelOpen?60:78,(contentBottom-contentTop)/3));
            visPrevB=visPrevT+ph;int chipY=ph>0?visPrevB+4:visPrevT,chipH=20,gap=6,chipW=Math.max(88,(contentRight-contentX-12-gap)/2);
            StyledButton pathChip=add(new StyledButton(VIS_PATH,contentX+6,chipY,chipW,chipH,s("Маршрут","Route","Маршрут","Route","Trasa")).accent(UiTheme.PURPLE).icon("\u2248").compact()
                .tip(tt("§fМаршрут\n§7Линия пути в мире. Все настройки ниже меняют только её.","§fRoute\n§7The path line in the world. Settings below change only this.","§fМаршрут\n§7Лінія шляху у світі. Усі налаштування нижче змінюють лише її.","§fRoute\n§7Die Weglinie in der Welt. Alle Einstellungen darunter ändern nur sie.","§fTrasa\n§7Linia drogi w świecie. Wszystkie ustawienia poniżej zmieniają tylko ją.")));
            StyledButton markChip=add(new StyledButton(VIS_MARK,contentX+12+chipW,chipY,chipW,chipH,s("Метка старта","Start mark","Мітка старту","Startmarkierung","Znacznik startu")).accent(UiTheme.AMBER).icon("\u2691").compact()
                .tip(tt("§fМетка старта\n§7Значок в месте начала записи. Настройки ниже меняют только её.","§fStart mark\n§7The mark where the recording began. Settings below change only this.","§fМітка старту\n§7Значок у місці початку запису. Налаштування нижче змінюють лише її.","§fStartmarkierung\n§7Das Zeichen am Startpunkt der Aufnahme. Die Einstellungen darunter ändern nur sie.","§fZnacznik startu\n§7Znak w miejscu początku nagrania. Ustawienia poniżej zmieniają tylko go.")));
            if(visElement==0)pathChip.primary();else markChip.primary();
            cursorY=chipY+chipH+6;
        }
        maxScroll=computeMaxScroll(cursorY);
        scroll=Math.max(0,Math.min(maxScroll,scroll));
        for(int i=scroll;i<rows.size();i++){
            Row r=rows.get(i);
            if(r.type==T_HEAD){
                if(placed>0)cursorY+=GROUP_GAP-ROW_GAP;
                if(cursorY+GROUP_HEAD_H>listBottom-4)break;
                rowY[i]=cursorY;placed++;cursorY+=GROUP_HEAD_STEP;continue;
            }
            if(cursorY+rowH>listBottom-4)break;
            int y=cursorY;rowY[i]=y;placed++;cursorY+=rowH+ROW_GAP;
            int w=contentRight-contentX-12;
            if(r.type==T_TOGGLE)add(new StyledButton(ACT_BASE+r.key,contentX+6,y,w,rowH,r.label).asToggle(stateOf(r.key)).icon(r.icon).sub(r.note).tip(r.tip));
            else if(r.type==T_CYCLE){StyledButton cb=add(new StyledButton(ACT_BASE+r.key,contentX+6,y,w,rowH,r.label).accent(r.color).icon(r.icon).sub(r.note).value(valueOf(r.key)).tip(r.tip));if(r.key==K_COLOR_TARGET&&colorPanelOpen)cb.primary();}
            else if(r.type==T_ACTION)add(new StyledButton(ACT_BASE+r.key,contentX+6,y,w,rowH,r.label).accent(r.color).icon(r.icon).sub(r.note).tip(r.tip));
            else if(r.type==T_STEP){
                int bs=stepBtn(),by=y+(rowH-bs)/2,bx=stepBlockX();boolean lock=rowLocked(r.key);
                add(lock,new StyledButton(DEC_BASE+r.key,bx,by,bs,bs,"\u2212").accent(UiTheme.GRAY).compact().flat()
                    .tip(tt("\u00a7fМеньше\n\u00a77Уменьшает: "+r.label+"\n\u00a78Shift — мелкий шаг, Ctrl — крупный.","\u00a7fLess\n\u00a77Decreases: "+r.label+"\n\u00a78Shift for a tiny step, Ctrl for a big one.","\u00a7fМенше\n\u00a77Зменшує: "+r.label+"\n\u00a78Shift — дрібний крок, Ctrl — великий.","\u00a7fWeniger\n\u00a77Verringert: "+r.label+"\n\u00a78Shift für einen kleinen Schritt, Ctrl für einen großen.","\u00a7fMniej\n\u00a77Zmniejsza: "+r.label+"\n\u00a78Shift — mały krok, Ctrl — duży.")));
                add(lock,new StyledButton(INC_BASE+r.key,bx+bs+stepValW(),by,bs,bs,"+").accent(UiTheme.GRAY).compact().flat()
                    .tip(tt("\u00a7fБольше\n\u00a77Увеличивает: "+r.label+"\n\u00a78Shift — мелкий шаг, Ctrl — крупный.","\u00a7fMore\n\u00a77Increases: "+r.label+"\n\u00a78Shift for a tiny step, Ctrl for a big one.","\u00a7fБільше\n\u00a77Збільшує: "+r.label+"\n\u00a78Shift — дрібний крок, Ctrl — великий.","\u00a7fMehr\n\u00a77Erhöht: "+r.label+"\n\u00a78Shift für einen kleinen Schritt, Ctrl für einen großen.","\u00a7fWięcej\n\u00a77Zwiększa: "+r.label+"\n\u00a78Shift — mały krok, Ctrl — duży.")));
                if(r.key==K_SPEED){
                    int dx=bx+stepBlockW()+6,dw=Math.min(86,contentRight-12-dx);
                    if(dw>=42)add(new StyledButton(ACT_BASE+K_SPEED_DEF,dx,by,dw,bs,s("По умолчанию","Default","За замовчуванням","Standard","Domyślnie")).accent(UiTheme.GRAY).compact().flat()
                        .tip(tt("\u00a7fОбычная скорость\n\u00a77Ставит 1.00x \u2014 точно как было записано.","\u00a7fNormal speed\n\u00a77Sets 1.00x - exactly as recorded.","§fЗвичайна швидкість\n§7Ставить 1.00x — точно як було записано.","§fNormales Tempo\n§7Setzt 1.00x — exakt wie aufgenommen.","§fNormalna prędkość\n§7Ustawia 1.00x — dokładnie jak nagrano.")));
                }
            }
        }
        if(placed>0)visibleRows=placed;
        if(hasColorPanel()){
            int paletteTop=Math.max(cursorY+6,contentBottom-72);
            paletteTop=Math.min(paletteTop,Math.max(contentTop+6,contentBottom-42));
            colorLabelY=paletteTop;
            wheelSize=Math.max(24,Math.min(48,contentBottom-8-(paletteTop+13)));
            wheel=new GuiColorWheel(contentX+8,paletteTop+13,wheelSize);
            if(colorTarget==1)wheel.setColorRGBA(config.getMarkerOwnR(),config.getMarkerOwnG(),config.getMarkerOwnB(),config.getMarkerOwnAlpha());
            else wheel.setColorRGBA(config.getPathLineR(),config.getPathLineG(),config.getPathLineB(),config.getPathLineAlpha());
        }
        int bw=Math.min(112,(winW-40)/4),bh=20;
        cancel=add(new StyledButton(CANCEL,winX+winW-12-bw*2-6,footerY,bw,bh,Lang.t("common.cancel")).accent(UiTheme.GRAY)
            .tip(tt("\u00a7fОтмена\n\u00a77Вернёт настройки такими, какими они были, когда вы сюда зашли.\n\u00a78Ничего не сохранится.","\u00a7fCancel\n\u00a77Puts the settings back the way they were when you opened this screen.\n\u00a78Nothing will be saved.","§fСкасувати\n§7Поверне налаштування такими, якими вони були, коли ви сюди зайшли.\n§8Нічого не збережеться.","§fAbbrechen\n§7Setzt die Einstellungen auf den Stand beim Öffnen dieses Bildschirms zurück.\n§8Nichts wird gespeichert.","§fAnuluj\n§7Przywraca ustawienia do stanu z momentu wejścia na ten ekran.\n§8Nic nie zostanie zapisane.")));
        save=add(new StyledButton(SAVE,winX+winW-12-bw,footerY,bw,bh,Lang.t("common.save")).primary().accent(UiTheme.GREEN)
            .tip(tt("\u00a7fСохранить\n\u00a77Запомнит настройки и вернёт вас назад.\n\u00a78Настройки сохранятся и после выхода из игры.","\u00a7fSave\n\u00a77Keeps your settings and takes you back.\n\u00a78They stay saved after you quit the game.","§fЗберегти\n§7Запам'ятає налаштування й поверне вас назад.\n§8Налаштування збережуться і після виходу з гри.","§fSpeichern\n§7Merkt sich deine Einstellungen und bringt dich zurück.\n§8Sie bleiben auch nach dem Beenden des Spiels gespeichert.","§fZapisz\n§7Zapamięta ustawienia i wrócisz do poprzedniego ekranu.\n§8Ustawienia zostaną zapisane także po wyjściu z gry.")));
        if(tab==4)layoutHelp();
    }

    // ------------------------------------------------ встроенная справка
    private void layoutHelp(){
        String[][] d=GuiMirrorHelp.data();
        helpSection=Math.max(0,Math.min(d.length-1,helpSection));
        int avail=contentRight-contentX-12;
        helpListX=contentX+6;helpListRight=helpListX+Math.max(74,Math.min(152,(int)(avail*0.38f)));
        helpListTop=contentTop+6;
        helpRowH=Math.max(11,Math.min(18,(contentBottom-helpListTop-38)/Math.max(1,d.length)));
        helpTextX=helpListRight+8;helpTextRight=contentRight-6;helpTextTop=contentTop+6;
        helpLines.clear();
        String[] body=d[helpSection];
        int wrapW=Math.max(60,helpTextRight-helpTextX-20);
        for(int i=1;i<body.length;i++){
            if(body[i]==null||body[i].length()==0){helpLines.add("");continue;}
            helpLines.addAll(fontRenderer.listFormattedStringToWidth(body[i],wrapW));
        }
        helpVisible=Math.max(1,(contentBottom-helpTextTop-24)/10);
        helpMaxScroll=Math.max(0,helpLines.size()-helpVisible);
        helpScroll=Math.max(0,Math.min(helpMaxScroll,helpScroll));
        int nb=22,navY=contentBottom-26;
        add(new StyledButton(HELP_PREV,helpListX+8,navY,nb,20,"\u2039").accent(UiTheme.GRAY).compact()
            .tip(tt("\u00a7fНазад\n\u00a77Предыдущий раздел справки.","\u00a7fBack\n\u00a77The previous help section.","§fНазад\n§7Попередній розділ довідки.","§fZurück\n§7Der vorherige Hilfeabschnitt.","§fWstecz\n§7Poprzednia sekcja pomocy.")));
        add(new StyledButton(HELP_NEXT,helpListRight-8-nb,navY,nb,20,"\u203a").accent(UiTheme.GRAY).compact()
            .tip(tt("\u00a7fДальше\n\u00a77Следующий раздел справки.","\u00a7fNext\n\u00a77The next help section.","§fДалі\n§7Наступний розділ довідки.","§fWeiter\n§7Der nächste Hilfeabschnitt.","§fDalej\n§7Następna sekcja pomocy.")));
    }
    private void drawHelpPanel(int mx,int my){
        String[][] d=GuiMirrorHelp.data();
        UiTheme.card(helpListX,helpListTop-4,helpListRight,contentBottom,UiTheme.DEEP,UiTheme.BORDER);
        for(int i=0;i<d.length;i++){
            int y=helpListTop+i*helpRowH;
            if(y+helpRowH>contentBottom-32)break;
            boolean sel=i==helpSection,hov=mx>=helpListX&&mx<helpListRight&&my>=y&&my<y+helpRowH;
            if(sel||hov)UiTheme.round(helpListX+3,y,helpListRight-3,y+helpRowH-1,sel?UiTheme.CARD_SEL:UiTheme.CARD_HOVER);
            UiTheme.dot(helpListX+8,y+(helpRowH-6)/2,sel?UiTheme.PURPLE:UiTheme.GRAY);
            UiTheme.drawFitted(fontRenderer,d[i][0],helpListX+19,y+(helpRowH-8)/2,helpListRight-helpListX-26,sel?UiTheme.TEXT:UiTheme.TEXT_DIM);
        }
        UiTheme.card(helpTextX,helpTextTop-4,helpTextRight,contentBottom,UiTheme.CARD,UiTheme.BORDER);
        UiTheme.drawFittedShadow(fontRenderer,"\u00a7l"+d[helpSection][0],helpTextX+9,helpTextTop+2,helpTextRight-helpTextX-18,UiTheme.TEXT);
        int y=helpTextTop+16,textW=Math.max(20,helpTextRight-helpTextX-18);
        UiTheme.beginClip(mc,helpTextX+4,helpTextTop+14,helpTextRight-8,contentBottom-4);
        for(int i=helpScroll;i<helpLines.size()&&i<helpScroll+helpVisible;i++){
            UiTheme.drawFitted(fontRenderer,helpLines.get(i),helpTextX+9,y,textW,UiTheme.TEXT_DIM);y+=10;
        }
        UiTheme.endClip();
        if(helpLines.size()>helpVisible)UiTheme.scrollBar(helpTextRight-5,helpTextTop+14,contentBottom-4,helpVisible,helpLines.size(),helpScroll,UiTheme.PURPLE);
        drawCenteredString(fontRenderer,(helpSection+1)+" / "+d.length,helpNavCenter(),contentBottom-20,UiTheme.TEXT_MUTE);
    }
    private int helpNavCenter(){return helpListX+(helpListRight-helpListX)/2;}
    private void selectHelp(int index){
        int total=GuiMirrorHelp.data().length;
        helpSection=(index%total+total)%total;helpScroll=0;initGui();
    }

    private void applyWheel(){
        if(wheel==null)return;
        wheelTouched=true;
        if(visElement==1||colorTarget==1){config.setMarkerCustomColor(true);config.setMarkerR(wheel.getRed());config.setMarkerG(wheel.getGreen());config.setMarkerB(wheel.getBlue());config.setMarkerAlpha(wheel.getAlpha());}
        else{config.setPathLineR(wheel.getRed());config.setPathLineG(wheel.getGreen());config.setPathLineB(wheel.getBlue());config.setPathLineAlpha(wheel.getAlpha());}
    }

    // ------------------------------------------------ отрисовка
    private void valueRow(Row r,int y,int mx,int my){
        int l=contentX+6,rr=contentRight-6;
        boolean hov=mx>=l&&mx<rr&&my>=y&&my<y+rowH;
        UiTheme.rowCard(l,y,rr,y+rowH,hov,false,r.color);
        int size=Math.min(rowH-8,14),tx=l+8,bs=stepBtn(),vx=stepBlockX();boolean lock=rowLocked(r.key);
        if(r.icon!=null&&rowH>=16){UiTheme.badge(fontRenderer,r.icon,l+6,y+(rowH-size)/2,size,r.color,true);tx=l+6+size+6;}
        int lim=Math.max(10,(r.type==T_STEP?vx-8:rr-10)-tx);
        boolean two=r.note!=null&&rowH>=26;int fh=fontRenderer.FONT_HEIGHT,blockY=y+Math.max(0,(rowH-(two?fh*2+1:fh))/2);
        UiTheme.drawFittedShadow(fontRenderer,r.label,tx,blockY,lim,lock?UiTheme.TEXT_MUTE:UiTheme.TEXT);
        if(two)UiTheme.drawFitted(fontRenderer,r.note,tx,blockY+fh+1,lim,UiTheme.TEXT_MUTE);
        if(r.type==T_STEP){
            int by=y+(rowH-bs)/2,bw=stepBlockW();
            UiTheme.card(vx,by,vx+bw,by+bs,UiTheme.DEEP,UiTheme.BORDER);
            UiTheme.drawCenteredFitted(fontRenderer,valueOf(r.key),vx+bs+stepValW()/2f,by+(bs-fontRenderer.FONT_HEIGHT)/2,stepValW()-4,lock?UiTheme.TEXT_MUTE:UiTheme.TEXT,false);
        }
    }
    @Override public void drawScreen(int mx,int my,float pt){
        int bd=UiTheme.GLASS_BACKDROP;
        drawGradientRect(0,0,width,height,bd,bd);
        UiTheme.window(winX,winY,winX+winW,winY+winH,headH);
        UiTheme.header(fontRenderer,winX,winY,winX+winW,headH,s("Настройки","Settings","Налаштування","Einstellungen","Ustawienia"),
            s("Разделы слева · наведите на строку, чтобы понять, что она меняет","Sections on the left · hover a row to see what it changes","Розділи ліворуч · наведіть на рядок, щоб зрозуміти, що він змінює","Abschnitte links · fahre über eine Zeile, um zu sehen, was sie ändert","Sekcje po lewej · najedź na wiersz, aby zobaczyć, co zmienia"),tabColor(tab),80);
        if(changed()){String chip=s("ИЗМЕНЕНО","CHANGED","ЗМІНЕНО","GEÄNDERT","ZMIENIONO");int cw=fontRenderer.getStringWidth(chip)+8;UiTheme.chip(fontRenderer,chip,winX+winW-12-cw,winY+13,UiTheme.AMBER);}
        UiTheme.drawFittedShadow(fontRenderer,"\u00a7l"+tabName(tab),contentX+6,contentTop-15,Math.max(24,contentRight-contentX-70),UiTheme.TEXT);
        int sideBottom=sideTop+(TABS.length-1)*(sideRowH+ROW_GAP)+sideRowH;
        UiTheme.card(sideX-4,sideTop-4,sideX+sideW+4,sideBottom+4,UiTheme.FUNCTION_LIST_GLASS,UiTheme.BORDER_HI);
        if(tab!=4)UiTheme.card(contentX,contentTop,contentRight,listBottom,UiTheme.FUNCTION_LIST_GLASS,UiTheme.BORDER_HI);
        if(tab==2&&visPrevB>visPrevT+8)VisualPreview.draw(fontRenderer,config,visPrevL,visPrevT,visPrevR,visPrevB,visElement);
        if(tab==4)drawHelpPanel(mx,my);
        for(int i=0;i<rows.size();i++){
            if(rowY[i]<0)continue;
            Row r=rows.get(i);
            if(r.type==T_HEAD){int gy=rowY[i];boolean hov=mx>=contentX+6&&mx<contentRight-6&&my>=gy&&my<gy+GROUP_HEAD_H;UiTheme.rowCard(contentX+6,gy,contentRight-6,gy+GROUP_HEAD_H,hov,false,tabColor(tab));UiTheme.drawFitted(fontRenderer,r.label,contentX+14,gy+(GROUP_HEAD_H-fontRenderer.FONT_HEIGHT)/2,Math.max(24,contentRight-contentX-54),UiTheme.TEXT);UiTheme.controlSymbol(collapsed[tab][HEAD_KEY_BASE-r.key]?"+":"−",contentRight-19,gy+GROUP_HEAD_H/2f,0.85f,UiTheme.TEXT_DIM);continue;}
            if(r.type==T_STEP||r.type==T_INFO)valueRow(r,rowY[i],mx,my);
        }
        if(sbActive()){
            long now=System.currentTimeMillis();boolean target=sbDrag||(mx>=contentRight-7&&mx<contentRight&&my>=sbTop()&&my<sbBottom());
            if(target!=sbHoverTarget){sbHoverFrom=sbHover;sbHoverTarget=target;sbHoverAt=now;}
            sbHover=UiTheme.transition(sbHoverFrom,sbHoverTarget?1f:0f,sbHoverAt,now,UiTheme.HOVER_MS);
            UiTheme.scrollBar(contentRight-6,sbTop(),sbBottom(),visibleRows,rows.size(),scroll,tabColor(tab),sbHover);
        }
        if(wheel!=null){
            String title=colorTarget==1?s("ЦВЕТ МЕТКИ","MARKER COLOR","КОЛІР МІТКИ","MARKIERUNGSFARBE","KOLOR ZNACZNIKA"):s("ЦВЕТ ЛИНИИ","LINE COLOR","КОЛІР ЛІНІЇ","LINIENFARBE","KOLOR LINII");
            fontRenderer.drawString(title,contentX+8,colorLabelY,UiTheme.TEXT_MUTE);
            wheel.draw();
            if(colorTarget==1&&!config.isMarkerCustomColor())
                UiTheme.drawFitted(fontRenderer,s("Включите «Свой цвет метки»","Turn on the own marker color","Увімкніть «Свій колір мітки»","Aktiviere «Eigene Farbe für die Markierung»","Włącz «Własny kolor znacznika»"),contentX+8+wheelSize+80,colorLabelY,Math.max(20,contentRight-contentX-wheelSize-100),UiTheme.TEXT_MUTE);
        }
        if(saveFailed)UiTheme.drawFitted(fontRenderer,s("Не удалось сохранить настройки слота","Could not store the slot settings","Не вдалося зберегти налаштування слота","Slot-Einstellungen konnten nicht gespeichert werden","Nie udało się zapisać ustawień slotu"),winX+16,footerY+6,Math.max(20,winW-260),UiTheme.RED);
        super.drawScreen(mx,my,pt);
        drawTips(mx,my);
    }
    private void drawTips(int mx,int my){
        if(!config.isShowTooltips())return;
        for(GuiButton g:buttonList)if(g instanceof StyledButton){StyledButton b=(StyledButton)g;
            if(b.visible&&b.tooltip!=null&&mx>=b.x&&my>=b.y&&mx<b.x+b.width&&my<b.y+b.height){if(UiTheme.tipReady(b))UiTheme.tooltip(fontRenderer,b.tooltip,mx,my,width,height);return;}}
        for(int i=0;i<rows.size();i++){
            if(rowY[i]<0)continue;
            Row r=rows.get(i);
            if((r.type==T_STEP||r.type==T_INFO)&&r.tip!=null&&mx>=contentX+6&&mx<contentRight-6&&my>=rowY[i]&&my<rowY[i]+rowH){if(UiTheme.tipReady(r))UiTheme.tooltip(fontRenderer,r.tip,mx,my,width,height);return;}
        }
    }

    // ------------------------------------------------ ввод
    @Override public void handleMouseInput()throws IOException{
        super.handleMouseInput();
        int d=Mouse.getEventDWheel();
        if(d!=0&&tab==4){
            helpScroll=Math.max(0,Math.min(helpMaxScroll,helpScroll+(d>0?-2:2)));
            return;
        }
        if(d!=0){
            int max=maxScroll,old=scroll;
            scroll=Math.max(0,Math.min(max,scroll+(d>0?-1:1)));
            if(scroll!=old)initGui();
        }
    }
    @Override protected void mouseClicked(int mx,int my,int btn)throws IOException{
        if(btn==1&&toggleArchiveAt(mx,my))return;
        if(btn==0&&sbActive()&&mx>=contentRight-7&&mx<contentRight&&my>=sbTop()&&my<sbBottom()){
            int ty=sbThumbY(),th=sbThumbH();
            sbGrab=my>=ty&&my<ty+th?my-ty:th/2;
            sbDrag=true;sbSetFromMouse(my);return;
        }
        super.mouseClicked(mx,my,btn);
        if(tab==2&&btn==0){int hit=VisualPreview.hit(mx,my,visPrevL,visPrevT,visPrevR,visPrevB);if(hit>=0&&hit!=visElement){visElement=hit;colorTarget=hit;colorPanelOpen=false;scroll=0;initGui();return;}}
        if(tab==4&&btn==0&&mx>=helpListX&&mx<helpListRight){
            String[][] d=GuiMirrorHelp.data();
            for(int i=0;i<d.length;i++){
                int y=helpListTop+i*helpRowH;
                if(my>=y&&my<y+helpRowH-1){selectHelp(i);return;}
            }
        }
        if(wheel!=null&&btn==0&&wheel.mouseClicked(mx,my))applyWheel();
    }
    @Override protected void mouseClickMove(int mx,int my,int btn,long time){
        if(sbDrag){sbSetFromMouse(my);return;}
        if(wheel!=null&&wheel.isDragging()){wheel.mouseDragged(mx,my);applyWheel();}
    }
    @Override protected void mouseReleased(int mx,int my,int state){
        sbDrag=false;
        super.mouseReleased(mx,my,state);
        if(wheel!=null)wheel.mouseReleased();
    }
    @Override protected void keyTyped(char c,int key)throws IOException{
        if(key==Keyboard.KEY_ESCAPE&&colorPanelOpen){colorPanelOpen=false;initGui();return;}
        if(key==Keyboard.KEY_ESCAPE){mc.displayGuiScreen(parent);return;}
        if(key==Keyboard.KEY_UP||key==Keyboard.KEY_LEFT){tab=TABS[(tabIndex(tab)+TABS.length-1)%TABS.length];scroll=0;initGui();return;}
        if(key==Keyboard.KEY_DOWN||key==Keyboard.KEY_RIGHT){tab=TABS[(tabIndex(tab)+1)%TABS.length];scroll=0;initGui();return;}
        super.keyTyped(c,key);
    }
    @Override protected void actionPerformed(GuiButton b)throws IOException{
        int id=b.id;
        if(id==VIS_PATH){visElement=0;colorTarget=0;colorPanelOpen=false;scroll=0;initGui();return;}
        if(id==VIS_MARK){visElement=1;colorTarget=1;colorPanelOpen=false;scroll=0;initGui();return;}
        if(id>=TAB_BASE&&id<TAB_BASE+TAB_COUNT){tab=id-TAB_BASE;scroll=0;wheelTouched=false;colorPanelOpen=false;initGui();return;}
        if(id==HELP_PREV){selectHelp(helpSection-1);return;}
        if(id==HELP_NEXT){selectHelp(helpSection+1);return;}
        if(id==CANCEL){mc.displayGuiScreen(parent);return;}
        if(id==SAVE){
            int slot=config.getActiveSlot();
            if(manager.getSlotFrameCount(slot)>0&&!manager.saveSlotSettings(slot)){saveFailed=true;return;}
            if(!config.save()){saveFailed=true;return;}
            saved=true;snapshot();mc.displayGuiScreen(parent);return;
        }
        if(id>=ACT_BASE){
            int key=id-ACT_BASE;
            if(key==K_ADVANCED){openingChild=true;mc.displayGuiScreen(new GuiStabilizationSettings(this,config));return;}
            if(key==K_TRASH){openingChild=true;mc.displayGuiScreen(new GuiMirrorTrash(this,manager));return;}
            if(key==K_RESET){openingChild=true;mc.displayGuiScreen(new GuiResetConfirm(this));return;}
            if(key==K_RESET_LOOK){config.resetAppearance();initGui();return;}
            if(key==K_MODE){config.setStartMarkerMode((config.getStartMarkerMode()+1)%4);initGui();return;}
            if(key==K_STYLE){config.setPathLineStyle((config.getPathLineStyle()+1)%3);initGui();return;}
            if(key==K_LANG){config.setLanguage(Lang.next());initGui();return;}
            if(key==K_SPEED_DEF){config.setPlaybackSpeed(1f);initGui();return;}
            if(key==K_COLOR_TARGET){colorTarget=visElement;colorPanelOpen=!colorPanelOpen;if(visElement==1)config.setMarkerCustomColor(true);initGui();return;}
            toggleKey(key);
            if(b instanceof StyledButton)((StyledButton)b).setOn(stateOf(key));
            if(key==K_AUTO_LEAVE)initGui();
            return;
        }
        if(id>=INC_BASE){step(id-INC_BASE,1);return;}
        if(id>=DEC_BASE){step(id-DEC_BASE,-1);return;}
    }

    /** Ответ окна подтверждения заводского сброса. */
    void finishTrash(){openingChild=false;mc.displayGuiScreen(this);}
    void finishFactoryReset(boolean confirmed){
        openingChild=false;
        if(confirmed){
            int activeSlot=config.getActiveSlot();
            config.resetAll();
            if(!config.save())saveFailed=true;
            if(manager.getSlotFrameCount(activeSlot)>0&&!manager.saveSlotSettings(activeSlot))saveFailed=true;
            snapshot();scroll=0;colorPanelOpen=false;
        }
        mc.displayGuiScreen(this);
    }
    @Override public void onGuiClosed(){if(!saved&&!openingChild)restore();}
    @Override public boolean doesGuiPauseGame(){return false;}
}
