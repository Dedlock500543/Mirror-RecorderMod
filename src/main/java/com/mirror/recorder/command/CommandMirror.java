package com.mirror.recorder.command;
import com.mirror.recorder.config.RecorderConfig;
import com.mirror.recorder.manager.RecorderManager;
import com.mirror.recorder.handler.BaritoneReturnController;
import net.minecraft.command.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.*;
import javax.annotation.Nonnull;
import java.util.*;
public class CommandMirror extends CommandBase{
    private static final String PREFIX="§8[§bMirror§8]§r ";
    private static final int MIN_SLOT=RecorderManager.MIN_SLOT,MAX_SLOT=RecorderManager.MAX_SLOT,TPS=20;
    private final RecorderManager manager;private final RecorderConfig config;private final BaritoneReturnController returner;private volatile boolean guiOpenPending=false;
    public CommandMirror(RecorderManager m,RecorderConfig c,BaritoneReturnController r){manager=m;config=c;returner=r;}
    /** Ответы команды: пара «русский/английский», язык берётся из Lang (auto следует языку игры). */
    private static String L(String ru,String en,String uk,String de,String pl){return com.mirror.recorder.gui.Lang.s(ru,en,uk,de,pl);}
    @Override@Nonnull public String getName(){return "mirror";}
    @Override@Nonnull public String getUsage(@Nonnull ICommandSender s){return "/mirror <record|play|loop|pause|limit|stop|delete|list|name|marker|selftest|gui>";}
    @Override public int getRequiredPermissionLevel(){return 0;}
    @Override public boolean checkPermission(@Nonnull MinecraftServer srv,@Nonnull ICommandSender s){return true;}
    @Override public void execute(@Nonnull MinecraftServer srv,@Nonnull ICommandSender sender,@Nonnull String[] args) throws CommandException{
        if(args.length==0){help(sender);return;}
        switch(args[0].toLowerCase(Locale.ROOT)){
            case"record":doRecord(sender,args);break;case"play":doPlay(sender,args);break;case"loop":doLoop(sender,args);break;case"limit":doLimit(sender,args);break;
            case"pause":doPause(sender);break;case"selftest":doSelfTest(sender);break;
            case"stop":doStop(sender);break;case"delete":doDelete(sender,args);break;case"list":doList(sender,args);break;
            case"name":doMeta(sender,args,true);break;case"marker":doMeta(sender,args,false);break;case"gui":doGui(sender);break;
            default:err(sender,L("Неизвестная команда. Введите §f/mirror§c для справки.","Unknown command. Type §f/mirror§c for help.","Невідома команда. Введіть §f/mirror§c для довідки.","Unbekannter Befehl. Tippe §f/mirror§c für Hilfe.","Nieznana komenda. Wpisz §f/mirror§c, aby uzyskać pomoc."));
        }
    }
    private void help(ICommandSender s){
        raw(s,"§8§m──────────── §r §b§lMirror Recorder §8§m────────────");
        raw(s,"§b"+L("ДЕЙСТВИЯ","ACTIONS","ДІЇ","AKTIONEN","AKCJE"));
        line(s,"record <"+L("слот","slot","слот","Slot","slot")+">",L("начать запись","start recording","почати запис","Aufnahme starten","rozpocznij nagrywanie"));line(s,"play <"+L("слот","slot","слот","Slot","slot")+">",L("воспроизвести один раз","play once","відтворити один раз","einmal abspielen","odtwórz raz"));line(s,"loop <"+L("слот","slot","слот","Slot","slot")+">",L("воспроизводить по кругу","play in a loop","відтворювати по колу","in Schleife abspielen","odtwarzaj w pętli"));line(s,"limit <"+L("слот","slot","слот","Slot","slot")+"> <"+L("повторы","repeats","повтори","Wiederholungen","powtórki")+">",L("лимит циклов; 0 — бесконечно","loop limit; 0 — infinite","ліміт циклів; 0 — нескінченно","Schleifenlimit; 0 — unendlich","limit pętli; 0 — nieskończona"));line(s,"stop",L("остановить текущее действие; также убирает маркер восстановления","stop the current action; also removes the recovery marker","зупинити поточну дію; також прибирає маркер відновлення","die aktuelle Aktion stoppen; entfernt auch die Wiederherstellungsmarkierung","zatrzymaj bieżącą akcję; usuwa też znacznik odzyskiwania"));
        line(s,"pause",L("пауза и продолжение повтора","pause and resume playback","пауза та продовження відтворення","Wiedergabe pausieren und fortsetzen","pauza i wznowienie odtwarzania"));
        line(s,"selftest",L("автотест движка: события и временные метки","engine self-test: events and timestamps","автотест рушія: події та часові метки","Engine-Selbsttest: Ereignisse und Zeitstempel","autotest silnika: zdarzenia i znaczniki czasu"));
        raw(s,"§b"+L("СЛОТЫ","SLOTS","СЛОТИ","SLOTS","SLOTY"));
        line(s,"list ["+L("страница","page","сторінка","Seite","strona")+"]",L("показать слоты 1-100","show slots 1-100","показати слоти 1-100","Slots 1-100 anzeigen","pokaż sloty 1-100"));line(s,"delete <"+L("слот","slot","слот","Slot","slot")+">",L("удалить запись","delete the recording","видалити запис","Aufnahme löschen","usuń nagranie"));line(s,"name <"+L("слот","slot","слот","Slot","slot")+"> ["+L("текст","text","текст","Text","tekst")+"]",L("название записи","recording name","назва запису","Name der Aufnahme","nazwa nagrania"));line(s,"marker <"+L("слот","slot","слот","Slot","slot")+"> ["+L("текст","text","текст","Text","tekst")+"]",L("описание записи","recording description","опис запису","Beschreibung der Aufnahme","opis nagrania"));line(s,"gui",L("открыть интерфейс","open the interface","відкрити інтерфейс","Oberfläche öffnen","otwórz interfejs"));
        raw(s,L("§8Номер слота всегда указывается явно: §71–100","§8The slot number is always given explicitly: §71–100","§8Номер слота завжди вказується явно: §71–100","§8Die Slot-Nummer wird immer explizit angegeben: §71–100","§8Numer slotu zawsze podawany jawnie: §71–100"));
    }
    private void line(ICommandSender s,String cmd,String desc){raw(s,"§8 • §f/mirror "+cmd+" §8— §7"+desc);}
    private int requiredSlot(ICommandSender s,String[]a){if(a.length<2){chooseSlot(s);return -1;}return parseSlot(s,a[1]);}
    private void chooseSlot(ICommandSender s){warn(s,L("Укажите слот от 1 до 100. Список записей: §f/mirror list","Specify a slot from 1 to 100. List recordings: §f/mirror list","Вкажіть слот від 1 до 100. Список записів: §f/mirror list","Gib einen Slot von 1 bis 100 an. Aufnahmeliste: §f/mirror list","Podaj slot od 1 do 100. Lista nagrań: §f/mirror list"));}
    private boolean exactArgs(ICommandSender s,String[]a,int count,String usage){if(a.length==count)return true;warn(s,L("Использование: §f","Usage: §f","Використання: §f","Verwendung: §f","Użycie: §f")+usage);return false;}
    private void doRecord(ICommandSender s,String[]a){int slot=requiredSlot(s,a);if(slot<1)return;if(!exactArgs(s,a,2,"/mirror record <"+L("слот","slot","слот","Slot","slot")+">"))return;if(returner.isRecovering())returner.cancel();if(returner.isBusy()){warn(s,L("Сначала остановите возврат Baritone.","Stop the Baritone return first.","Спочатку зупиніть повернення Baritone.","Stoppe zuerst die Baritone-Rückkehr.","Najpierw zatrzymaj powrót Baritone."));return;}
        if(manager.isBusy()){busyMessage(s);return;}if(manager.getSlotFrameCount(slot)>0){warn(s,L("Слот ","Slot ","Слот ","Slot ","Slot ")+slot+L(" занят. Удалите запись или выберите другой слот."," is occupied. Delete the recording or pick another slot."," зайнятий. Видаліть запис або виберіть інший слот."," ist belegt. Lösche die Aufnahme oder wähle einen anderen Slot."," zajęty. Usuń nagranie lub wybierz inny slot."));return;}
        if(!selectTarget(s,slot))return;
        if(manager.scheduleRecording(slot))ok(s,L("Запись через §f","Recording in §f","Запис через §f","Aufnahme in §f","Nagranie za §f")+config.getStartDelay()+L("§a · слот §f","§a · slot §f","§a · слот §f","§a · Slot §f","§a · slot §f")+slot);else err(s,L("Не удалось начать запись.","Could not start recording.","Не вдалося почати запис.","Aufnahme konnte nicht gestartet werden.","Nie udało się rozpocząć nagrywania."));
    }
    private void doPlay(ICommandSender s,String[]a){int slot=requiredSlot(s,a);if(slot<1)return;if(!exactArgs(s,a,2,"/mirror play <"+L("слот","slot","слот","Slot","slot")+">"))return;if(returner.isRecovering())returner.cancel();
        if(manager.isBusy()){busyMessage(s);return;}if(manager.getSlotFrameCount(slot)==0){warn(s,L("Слот ","Slot ","Слот ","Slot ","Slot ")+slot+L(" пуст или повреждён."," is empty or corrupted."," порожній або пошкоджений."," ist leer oder beschädigt."," pusty lub uszkodzony."));return;}
        if(!selectTarget(s,slot))return;
        manager.setPreferredLoopMode(slot,false);returner.request(slot,false);
    }
    private void doLoop(ICommandSender s,String[]a){int slot=requiredSlot(s,a);if(slot<1)return;if(!exactArgs(s,a,2,"/mirror loop <"+L("слот","slot","слот","Slot","slot")+">"))return;if(returner.isRecovering())returner.cancel();
        if(manager.isBusy()){busyMessage(s);return;}if(manager.getSlotFrameCount(slot)==0){warn(s,L("Слот ","Slot ","Слот ","Slot ","Slot ")+slot+L(" пуст или повреждён."," is empty or corrupted."," порожній або пошкоджений."," ist leer oder beschädigt."," pusty lub uszkodzony."));return;}
        if(!selectTarget(s,slot))return;
        manager.setPreferredLoopMode(slot,true);returner.request(slot,true);
    }
    private void doLimit(ICommandSender s,String[]a){if(returner.isBusy()){warn(s,L("Лимит нельзя менять во время стабилизированного цикла.","The limit cannot be changed during a stabilized loop.","Ліміт не можна змінювати під час стабілізованого циклу.","Das Limit kann während einer stabilisierten Schleife nicht geändert werden.","Limitu nie można zmieniać podczas stabilizowanej pętli."));return;}if(!exactArgs(s,a,3,"/mirror limit <"+L("слот 1-100","slot 1-100","слот 1-100","Slot 1-100","slot 1-100")+"> <0|2-1000>"))return;if(manager.isBusy()){warn(s,L("Измените лимит после остановки текущего действия.","Change the limit after stopping the current action.","Змініть ліміт після зупинки поточної дії.","Ändere das Limit, nachdem die aktuelle Aktion gestoppt wurde.","Zmień limit po zatrzymaniu bieżącej akcji."));return;}int slot,v;try{slot=Integer.parseInt(a[1]);v=Integer.parseInt(a[2]);}catch(NumberFormatException e){warn(s,L("Формат: /mirror limit <слот 1-100> <0|2-1000>.","Format: /mirror limit <slot 1-100> <0|2-1000>.","Формат: /mirror limit <слот 1-100> <0|2-1000>.","Format: /mirror limit <Slot 1-100> <0|2-1000>.","Format: /mirror limit <slot 1-100> <0|2-1000>."));return;}if(slot<RecorderManager.MIN_SLOT||slot>RecorderManager.MAX_SLOT){warn(s,L("Слот должен быть от 1 до 100.","Slot must be between 1 and 100.","Слот має бути від 1 до 100.","Slot muss zwischen 1 und 100 liegen.","Slot musi być od 1 do 100."));return;}if(v<0||v>1000||v==1){warn(s,L("Лимит: 0 или 2-1000. 0 — бесконечный цикл; 1 не является циклом.","Limit: 0 or 2-1000. 0 — infinite loop; 1 is not a loop.","Ліміт: 0 або 2-1000. 0 — нескінченний цикл; 1 не є циклом.","Limit: 0 oder 2-1000. 0 — endlose Schleife; 1 ist keine Schleife.","Limit: 0 lub 2-1000. 0 — nieskończona pętla; 1 nie jest pętlą."));return;}if(manager.getSlotFrameCount(slot)<=0){warn(s,L("В слоте ","Slot ","У слоті ","In Slot ","W slocie ")+slot+L(" нет записи."," has no recording."," немає запису."," gibt es keine Aufnahme."," nie ma nagrania."));return;}if(!manager.setSlotLoopLimit(slot,v)){err(s,L("Не удалось сохранить лимит для слота ","Could not save the limit for slot ","Не вдалося зберегти ліміт для слота ","Limit für Slot konnte nicht gespeichert werden ","Nie udało się zapisać limitu dla slotu ")+slot+".");return;}config.save();ok(s,L("Слот §f","Slot §f","Слот §f","Slot §f","Slot §f")+slot+L("§7: лимит циклов §f","§7: loop limit §f","§7: ліміт циклів §f","§7: Schleifenlimit §f","§7: limit pętli §f")+(v==0?"∞":String.valueOf(v)));}
    /** Команда всегда работает с настройками указанного слота: сначала делаем его активным, потом запускаем. */
    private boolean selectTarget(ICommandSender s,int slot){
        if(config.getActiveSlot()==slot)return true;
        if(!manager.selectSlot(slot)){err(s,L("Не удалось выбрать слот ","Could not select slot ","Не вдалося вибрати слот ","Slot konnte nicht gewählt werden ","Nie udało się wybrać slotu ")+slot+".");return false;}
        config.save();return true;
    }
    private void doStop(ICommandSender s){if(returner.isRecovering()&&!manager.isBusy()){returner.cancel();return;}if(!manager.isBusy()&&!returner.isBusy()&&!returner.isRecovering()){warn(s,L("Нет активного действия.","Nothing is running.","Немає активної дії.","Es läuft nichts.","Brak aktywnej akcji."));return;}returner.cancel();manager.stopAll();send(s,"§e"+L("Остановлено.","Stopped.","Зупинено.","Gestoppt.","Zatrzymano."));}
    /** Пауза повтора: кадр остаётся на месте, продолжение идёт с того же кадра, а не с начала. */
    private void doPause(ICommandSender s){
        if(!manager.isPlaying()){warn(s,L("Нет активного воспроизведения.","Nothing is playing.","Немає активного відтворення.","Es läuft keine Wiedergabe.","Brak aktywnego odtwarzania."));return;}
        boolean pause=!manager.isPaused();manager.setPlaybackPaused(pause);
        if(pause)warn(s,L("Пауза · кадр §f","Paused · frame §f","Пауза · кадр §f","Pause · Frame §f","Pauza · klatka §f")+(manager.getCurrentFrameIndex()+1)+L("§e · продолжить: §f/mirror pause","§e · resume: §f/mirror pause","§e · продовжити: §f/mirror pause","§e · fortsetzen: §f/mirror pause","§e · wznów: §f/mirror pause"));
        else ok(s,L("Продолжаем с того же кадра.","Resuming from the same frame.","Продовжуємо з того самого кадру.","Weiter ab demselben Frame.","Kontynuujemy od tej samej klatki."));
    }
    /** Автотест движка: сравнение записи и повтора по событиям и временным меткам, без запуска в мире. */
    private void doSelfTest(ICommandSender s){
        com.mirror.recorder.debug.MirrorSelfTest.Result r=com.mirror.recorder.debug.MirrorSelfTest.run();
        raw(s,"§8§m──── §r §b§l"+L("Автотест движка","Engine self-test","Автотест рушія","Engine-Selbsttest","Autotest silnika")+" §8§m────");
        for(int i=0;i<r.lines.size();i++)raw(s,r.lines.get(i));
        if(r.isGreen())ok(s,L("Все проверки пройдены: ","All checks passed: ","Усі перевірки пройдено: ","Alle Prüfungen bestanden: ","Wszystkie testy zaliczone: ")+r.passed+"/"+r.total());
        else err(s,L("Провалено проверок: ","Failed checks: ","Провалено перевірок: ","Fehlgeschlagene Prüfungen: ","Nieudane testy: ")+r.failed+"/"+r.total());
    }
    private void doDelete(ICommandSender s,String[]a){int slot=requiredSlot(s,a);if(slot<1)return;if(!exactArgs(s,a,2,"/mirror delete <"+L("слот","slot","слот","Slot","slot")+">"))return;
        if(returner.isSlotBusy(slot)){warn(s,L("Слот ","Slot ","Слот ","Slot ","Slot ")+slot+L(" занят возвратом Baritone или циклом."," is busy with a Baritone return or a loop."," зайнятий поверненням Baritone або циклом."," ist mit einer Baritone-Rückkehr oder Schleife beschäftigt."," zajęty powrotem Baritone lub pętlą."));return;}if(returner.isRecoveringSlot(slot)){warn(s,L("Слот ","Slot ","Слот ","Slot ","Slot ")+slot+L(" ждёт вас на начальной точке. Сначала /mirror stop."," is waiting for you at the start point. Run /mirror stop first."," чекає вас на початковій точці. Спочатку /mirror stop."," wartet am Startpunkt auf dich. Zuerst /mirror stop."," czeka na ciebie w punkcie startu. Najpierw /mirror stop."));return;}
        if(manager.hasPendingAction()&&manager.getPendingSlot()==slot){warn(s,L("Слот ","Slot ","Слот ","Slot ","Slot ")+slot+L(" ожидает запуска. Сначала выполните /mirror stop."," is scheduled to start. Run /mirror stop first."," очікує запуску. Спочатку виконайте /mirror stop."," wartet auf den Start. Führe zuerst /mirror stop aus."," oczekuje na start. Najpierw wykonaj /mirror stop."));return;}
        if(manager.isPlaying()&&manager.getActiveOperationSlot()==slot){warn(s,L("Сначала остановите воспроизведение слота ","Stop playback of slot ","Спочатку зупиніть відтворення слота ","Stoppe zuerst die Wiedergabe von Slot ","Najpierw zatrzymaj odtwarzanie slotu ")+slot+".");return;}
        if(manager.isRecording()&&manager.getActiveOperationSlot()==slot){warn(s,L("Сначала остановите запись слота ","Stop recording of slot ","Спочатку зупиніть запис слота ","Stoppe zuerst die Aufnahme von Slot ","Najpierw zatrzymaj nagrywanie slotu ")+slot+".");return;}
        if(manager.getSlotFrameCount(slot)==0){warn(s,L("Слот ","Slot ","Слот ","Slot ","Slot ")+slot+L(" уже пуст."," is already empty."," уже порожній."," ist bereits leer."," już pusty."));return;}
        int del=manager.deleteSlotStatus(slot);
        if(del!=0)ok(s,L("Слот ","Slot ","Слот ","Slot ","Slot ")+slot+L(" удалён."," deleted."," видалено."," gelöscht."," usunięty.")
            +(del==com.mirror.recorder.storage.StorageManager.DELETE_NO_COPY?L(" Копии в корзине нет: файл нечитаем."," No trash copy: the file was unreadable."," Копії в кошику немає: файл нечитабельний."," Keine Papierkorb-Kopie: Datei unlesbar."," Brak kopii w koszu: plik nieczytelny."):""));else err(s,L("Не удалось удалить слот ","Could not delete slot ","Не вдалося видалити слот ","Slot konnte nicht gelöscht werden ","Nie udało się usunąć slotu ")+slot+L(". Файл не изменён.",". The file was not changed.",". Файл не змінено.",". Die Datei wurde nicht geändert.",". Plik nie został zmieniony."));
    }
    private void doList(ICommandSender s,String[] a){int page=(Math.max(1,config.getActiveSlot())-1)/10+1;if(a.length>2){warn(s,L("Использование: §f/mirror list [1-10]","Usage: §f/mirror list [1-10]","Використання: §f/mirror list [1-10]","Verwendung: §f/mirror list [1-10]","Użycie: §f/mirror list [1-10]"));return;}if(a.length==2)try{page=Integer.parseInt(a[1]);}catch(NumberFormatException e){warn(s,L("Страница: 1-10.","Page: 1-10.","Сторінка: 1-10.","Seite: 1-10.","Strona: 1-10."));return;}if(page<1||page>10){warn(s,L("Страница: 1-10.","Page: 1-10.","Сторінка: 1-10.","Seite: 1-10.","Strona: 1-10."));return;}int used=0;for(int i=MIN_SLOT;i<=MAX_SLOT;i++)if(manager.getSlotFrameCount(i)>0)used++;int from=(page-1)*10+1,to=Math.min(MAX_SLOT,from+9);raw(s,"§8§m──── §r §b§l"+L("Слоты","Slots","Слоти","Slots","Sloty")+" "+from+"-"+to+" §7"+used+"/100 §8· "+L("стр.","page","стор.","S.","str.")+" "+page+"/10");for(int i=from;i<=to;i++){int frames=manager.getSlotFrameCount(i);boolean active=manager.isSlotBusy(i)||returner.isSlotBusy(i);String name=manager.getSlotName(i);if(frames==0)raw(s,"§8○  "+i+"  §7"+L("пусто","empty","порожньо","leer","pusto"));else{if(name==null||name.isEmpty())name=L("Запись ","Recording ","Запис ","Aufnahme ","Nagranie ")+i;String mark=active?"§e▶":"§a●";raw(s,mark+"  §f"+i+"  "+name+"  §8· §7"+fmtDur(frames)+"  §8· "+frames+L(" к"," f"," к"," f"," k"));}}}
    private String fmtDur(int frames){int sec=Math.max(0,frames/TPS);return sec>=60?(sec/60)+L("м ","m ","хв ","m ","min ")+(sec%60)+L("с","s","с","s","s"):sec+L("с","s","с","s","s");}
    private void doMeta(ICommandSender s,String[]a,boolean isName){int slot=requiredSlot(s,a);if(slot<1)return;String kind=isName?L("Название","Name","Назва","Name","Nazwa"):L("Описание","Description","Опис","Beschreibung","Opis");
        if(manager.getSlotFrameCount(slot)==0){warn(s,L("Слот ","Slot ","Слот ","Slot ","Slot ")+slot+L(" пуст — сначала запишите действия."," is empty — record something first."," порожній — спочатку запишіть дії."," ist leer — nimm zuerst Aktionen auf."," pusty — najpierw nagraj akcje."));return;}
        if(manager.isSlotBusy(slot)||returner.isSlotBusy(slot)){warn(s,L("Нельзя изменять данные активного или ожидающего слота ","Cannot edit the data of the active or pending slot ","Не можна змінювати дані активного або очікуючого слота ","Daten des aktiven oder wartenden Slots können nicht geändert werden ","Nie można zmieniać danych aktywnego lub oczekującego slotu ")+slot+".");return;}
        if(a.length<3){String cur=isName?manager.getSlotName(slot):manager.getSlotDescription(slot);info(s,kind+" "+L("слота","of slot","слота","des Slots","slota")+" "+slot+": "+((cur==null||cur.isEmpty())?"§7"+L("не задано","not set","не задано","nicht gesetzt","nie ustawiono"):"§f"+cur));return;}
        String text=sanitize(join(a,2));int max=isName?40:120;
        if(text.length()>max){warn(s,kind+L(" слишком длинное. Максимум "," is too long. Maximum "," занадто довге. Максимум "," ist zu lang. Maximum "," zbyt długie. Maksimum ")+max+L(" символов."," characters."," символів."," Zeichen."," znaków."));return;}
        boolean saved=isName?manager.setSlotName(slot,text):manager.setSlotDescription(slot,text);
        if(saved)ok(s,kind+" "+L("слота","of slot","слота","des Slots","slota")+" "+slot+" §a→ §f"+(text.isEmpty()?L("не задано","not set","не задано","nicht gesetzt","nie ustawiono"):text));else err(s,L("Не удалось сохранить данные слота ","Could not save the data of slot ","Не вдалося зберегти дані слота ","Daten von Slot konnten nicht gespeichert werden ","Nie udało się zapisać danych slotu ")+slot+".");
    }
    private void busyMessage(ICommandSender s){
        if(returner.isBusy())warn(s,L("Baritone уже выполняет возврат или цикл.","Baritone is already returning or looping.","Baritone вже виконує повернення або цикл.","Baritone führt bereits eine Rückkehr oder Schleife aus.","Baritone już wykonuje powrót lub pętlę."));
        else if(manager.hasPendingAction())warn(s,L("Другое действие уже ожидает запуска. Используйте /mirror stop.","Another action is already scheduled. Use /mirror stop.","Інша дія вже очікує запуску. Використайте /mirror stop.","Eine andere Aktion wartet bereits auf den Start. Nutze /mirror stop.","Inna akcja już oczekuje na start. Użyj /mirror stop."));
        else if(manager.isRecording())warn(s,L("Сначала остановите текущую запись.","Stop the current recording first.","Спочатку зупиніть поточний запис.","Stoppe zuerst die aktuelle Aufnahme.","Najpierw zatrzymaj bieżące nagrywanie."));
        else if(manager.isPlaying())warn(s,L("Сначала остановите текущее воспроизведение.","Stop the current playback first.","Спочатку зупиніть поточне відтворення.","Stoppe zuerst die aktuelle Wiedergabe.","Najpierw zatrzymaj bieżące odtwarzanie."));
        else warn(s,L("Другое действие сейчас недоступно.","Another action is currently unavailable.","Інша дія зараз недоступна.","Eine andere Aktion ist gerade nicht verfügbar.","Inna akcja jest teraz niedostępna."));
    }
    private void doGui(ICommandSender s){
        if(FMLCommonHandler.instance().getSide()!=Side.CLIENT){err(s,L("Интерфейс доступен только на клиенте.","The interface is only available on the client.","Інтерфейс доступний лише на клієнті.","Die Oberfläche ist nur auf dem Client verfügbar.","Interfejs dostępny tylko po stronie klienta."));return;}
        net.minecraft.client.Minecraft mc=net.minecraft.client.Minecraft.getMinecraft();if(mc.player==null){err(s,L("Сначала войдите в мир.","Enter a world first.","Спочатку увійдіть у світ.","Betrete zuerst eine Welt.","Najpierw wejdź do świata."));return;}guiOpenPending=true;
    }
    @SubscribeEvent public void onClientTick(TickEvent.ClientTickEvent e){if(!guiOpenPending||e.phase!=TickEvent.Phase.END)return;net.minecraft.client.Minecraft mc=net.minecraft.client.Minecraft.getMinecraft();if(mc.player==null||mc.world==null){guiOpenPending=false;return;}if(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat)return;guiOpenPending=false;mc.displayGuiScreen(new com.mirror.recorder.gui.GuiMirrorMain(manager,config));}
    private int parseSlot(ICommandSender s,String arg){try{int v=Integer.parseInt(arg);if(v<MIN_SLOT||v>MAX_SLOT){chooseSlot(s);return-1;}return v;}catch(NumberFormatException e){chooseSlot(s);return-1;}}
    private String sanitize(String value){StringBuilder out=new StringBuilder();for(int i=0;i<value.length();i++){char ch=value.charAt(i);if(ch=='§'||Character.isISOControl(ch))continue;out.append(ch);}return out.toString().trim();}
    private static String join(String[]a,int from){StringBuilder sb=new StringBuilder();for(int i=from;i<a.length;i++){if(i>from)sb.append(' ');sb.append(a[i]);}return sb.toString();}
    private void raw(ICommandSender s,String msg){if(s==net.minecraft.client.Minecraft.getMinecraft().player)com.mirror.recorder.handler.TransientChat.show(msg);else s.sendMessage(new TextComponentString(msg));}private void send(ICommandSender s,String msg){if(s==net.minecraft.client.Minecraft.getMinecraft().player)com.mirror.recorder.handler.TransientChat.show(PREFIX+msg);else s.sendMessage(new TextComponentString(PREFIX+msg));}
    private void ok(ICommandSender s,String m){send(s,"§a"+m);}private void info(ICommandSender s,String m){send(s,"§b"+m);}private void warn(ICommandSender s,String m){send(s,"§e"+m);}private void err(ICommandSender s,String m){send(s,"§c"+m);}
    @Override@Nonnull public List<String> getTabCompletions(@Nonnull MinecraftServer srv,@Nonnull ICommandSender s,@Nonnull String[]args,net.minecraft.util.math.BlockPos pos){
        if(args.length==1)return getListOfStringsMatchingLastWord(args,Arrays.asList("record","play","loop","pause","limit","stop","delete","list","name","marker","selftest","gui"));
        if(args.length==2&&"limit".equalsIgnoreCase(args[0]))return getListOfStringsMatchingLastWord(args,Arrays.asList("1","2","3","4","5"));if(args.length==3&&"limit".equalsIgnoreCase(args[0]))return getListOfStringsMatchingLastWord(args,Arrays.asList("0","2","3","5","10","25","50","100"));
        if(args.length==2&&"list".equalsIgnoreCase(args[0]))return getListOfStringsMatchingLastWord(args,Arrays.asList("1","2","3","4","5","6","7","8","9","10"));
        if(args.length==2&&Arrays.asList("record","play","loop","delete","name","marker").contains(args[0].toLowerCase(Locale.ROOT))){List<String> slots=new ArrayList<String>();for(int i=MIN_SLOT;i<=MAX_SLOT;i++)slots.add(String.valueOf(i));return getListOfStringsMatchingLastWord(args,slots);}return Collections.emptyList();
    }
}
