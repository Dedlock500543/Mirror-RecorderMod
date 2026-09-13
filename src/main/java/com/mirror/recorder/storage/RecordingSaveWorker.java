package com.mirror.recorder.storage;
import com.mirror.recorder.model.Frame;
import net.minecraft.nbt.NBTTagCompound;
import org.apache.logging.log4j.*;
import java.util.*;

/**
 * Фоновое сохранение записей: NBT-сериализация, сжатие и запись файла больше не
 * останавливают главный поток. Чекпоинты и остановка записи лишь кладут готовый
 * снимок в очередь и возвращаются за доли миллисекунды.
 *
 * <p>Устройство: один демон-поток, по слоту хранится только самая свежая заявка —
 * новый чекпоинт вытесняет предыдущий, пока тот не взят в работу. Удаление, корзина,
 * метаданные, дублирование и экспорт сначала ждут тишины по слоту ({@link #awaitQuiescent}),
 * поэтому отмена заявок не нужна: конфликтующие операции просто не пересекаются
 * с фоновой записью, а при таймауте честно отказываются.
 *
 * <p>Потокобезопасность: путь сохранения (кодек, файловое хранилище, журнал отладки)
 * не трогает состояние игры и общие изменяемые поля — кодирование идёт только
 * по переданному снимку, кадры неизменяемы. Кэш сводок в StorageManager закрыт
 * собственной блокировкой. Порядок блокировок везде один: менеджер → воркер;
 * колбэк результата вызывается БЕЗ блокировки воркера, поэтому цикла нет.
 */
public final class RecordingSaveWorker{
    private static final Logger LOG=LogManager.getLogger("MirrorRecorder/Storage");
    /** Сколько операции чтения-изменения-записи (удаление, метаданные, дублирование, экспорт) ждут тихого слота. */
    public static final long QUIESCE_TIMEOUT_MS=5000L;
    /** Сколько хук завершения ждёт фоновый поток, прежде чем досохранить остатки самому. */
    private static final long SHUTDOWN_JOIN_MS=30000L;
    /** Результат сохранения: вызывается из фонового потока, реализация обязана быть потокобезопасной. */
    public interface SaveCallback{void saved(int slot,boolean ok);}
    /** Снимок записи: список частный (вызывающий передаёт копию), кадры неизменяемы. */
    private static final class Job{final List<Frame> frames;final NBTTagCompound settings;Job(List<Frame> f,NBTTagCompound s){frames=f;settings=s;}}
    private final Object lock=new Object();
    private final Map<Integer,Job> pending=new HashMap<Integer,Job>();
    private int activeSlot=-1;
    private boolean stopping=false;
    private Thread worker=null;
    private final StorageManager storage;
    private volatile SaveCallback callback=null;
    public RecordingSaveWorker(StorageManager storage){this.storage=storage;}
    public void setCallback(SaveCallback cb){callback=cb;}
    /** Поставить слот на сохранение: вытесняет предыдущую заявку по этому слоту — в работу идёт только самая свежая. */
    public void queue(int slot,List<Frame> frames,NBTTagCompound settings){
        if(frames==null||frames.isEmpty()||settings==null)return;
        synchronized(lock){
            // JVM уже уходит, а хук завершения сам досохранит остатки: тихий отказ, не новая заявка.
            if(stopping)return;
            pending.put(Integer.valueOf(slot),new Job(frames,settings));
            if(worker==null){worker=new Thread(new Runnable(){public void run(){loop();}},"MirrorRecorder-save-worker");worker.setDaemon(true);worker.setPriority(Thread.NORM_PRIORITY-1);worker.start();}
            lock.notifyAll();
        }
    }
    /** Ждать, пока по слоту не останется ни заявки, ни активной записи. False = таймаут, операцию продолжать нельзя. */
    public boolean awaitQuiescent(int slot,long timeoutMs){
        long deadline=System.currentTimeMillis()+Math.max(0L,timeoutMs);
        synchronized(lock){
            while(pending.containsKey(Integer.valueOf(slot))||activeSlot==slot){
                long wait=deadline-System.currentTimeMillis();
                if(wait<=0L)return false;
                try{lock.wait(wait);}catch(InterruptedException e){Thread.currentThread().interrupt();return false;}
            }
            return true;
        }
    }
    /** Для хука завершения: остановить приём заявок, дождаться поток и досохранить остатки синхронно. */
    public void shutdownFlush(){
        synchronized(lock){stopping=true;lock.notifyAll();}
        Thread w;
        synchronized(lock){w=worker;}
        if(w!=null){try{w.join(SHUTDOWN_JOIN_MS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
        List<Map.Entry<Integer,Job>> rest;
        synchronized(lock){
            if(pending.isEmpty())return;
            rest=new ArrayList<Map.Entry<Integer,Job>>(pending.entrySet());pending.clear();
        }
        for(Map.Entry<Integer,Job> e:rest){
            int slot=e.getKey().intValue();
            // Поток завис на этом слоте (мёртвый диск): не перезаписываем его временный файл.
            synchronized(lock){if(activeSlot==slot)continue;}
            boolean ok;
            try{ok=runJob(slot,e.getValue());}
            catch(Throwable t){LOG.error("Background save failed",t);ok=false;}
            report(slot,ok);
        }
    }
    private void loop(){
        for(;;){
            Map.Entry<Integer,Job> next=null;
            synchronized(lock){
                while(pending.isEmpty()&&!stopping){try{lock.wait();}catch(InterruptedException e){Thread.currentThread().interrupt();worker=null;return;}}
                if(stopping&&pending.isEmpty())return;
                Iterator<Map.Entry<Integer,Job>> it=pending.entrySet().iterator();
                if(it.hasNext()){next=it.next();it.remove();}
                if(next!=null)activeSlot=next.getKey().intValue();
            }
            // Остановку объявили, пока забирали: круг вернётся и выйдет через проверку выше.
            if(next==null)continue;
            int slot=next.getKey().intValue();
            boolean ok;
            try{ok=runJob(slot,next.getValue());}
            catch(Throwable t){LOG.error("Background save failed",t);ok=false;}
            synchronized(lock){activeSlot=-1;lock.notifyAll();}
            // Колбэк — строго после снятия active: результат относится к settled-состоянию слота.
            report(slot,ok);
        }
    }
    /** Тихо ли по слоту: нет ни заявки в очереди, ни активной записи. */
    public boolean isSettled(int slot){
        synchronized(lock){return activeSlot!=slot&&!pending.containsKey(Integer.valueOf(slot));}
    }
    private boolean runJob(int slot,Job job){
        try{
            if(storage.saveRecording(slot,job.frames,job.settings))return true;
            return storage.saveRecordingLenient(slot,job.frames,job.settings);
        }catch(Throwable t){LOG.error("Background save failed for slot {}",Integer.valueOf(slot),t);return false;}
    }
    private void report(int slot,boolean ok){
        SaveCallback cb=callback;
        if(cb==null){if(!ok)LOG.error("Recording for slot {} remains only in memory (no callback)",Integer.valueOf(slot));return;}
        try{cb.saved(slot,ok);}catch(Throwable t){LOG.error("Save callback failed",t);}
    }
}
