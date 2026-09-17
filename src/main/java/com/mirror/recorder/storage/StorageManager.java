package com.mirror.recorder.storage;
import com.mirror.recorder.model.Frame;
import net.minecraft.nbt.*;
import net.minecraftforge.common.util.Constants;
import org.apache.logging.log4j.*;
import com.mirror.recorder.debug.MirrorDebug;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
/** Фасад: координирует RecordingCodec, RecordingFileStore и TrashStore.
 *  Публичный API не изменился — все вызовы (GUI, менеджер, обработчики) работают как раньше. */
public class StorageManager{
    private static final Logger LOG=LogManager.getLogger("MirrorRecorder/Storage");
    public static final int MAX_FRAMES=RecordingCodec.MAX_FRAMES,FORMAT_VERSION=RecordingCodec.FORMAT_VERSION,MAX_TRASH=TrashStore.MAX_TRASH;
    private final RecordingCodec codec=new RecordingCodec();
    private final RecordingFileStore fileStore;
    private final TrashStore trashStore;
    private final File saveDir,exportDir;
    private final Map<Integer,SlotSummary> summaries=new HashMap<Integer,SlotSummary>();
    private final RecordingSaveWorker saver;
    public StorageManager(File minecraftDir){
        saveDir=new File(minecraftDir,"mirror_recorder");exportDir=new File(saveDir,"exports");File trashDir=new File(saveDir,"trash");
        if(!saveDir.exists()&&!saveDir.mkdirs())LOG.error("Failed to create save directory");
        if(!exportDir.exists()&&!exportDir.mkdirs())LOG.error("Failed to create export directory");
        if(!trashDir.exists()&&!trashDir.mkdirs())LOG.error("Failed to create trash directory");
        fileStore=new RecordingFileStore(saveDir);trashStore=new TrashStore(saveDir,trashDir);saver=new RecordingSaveWorker(this);}
    private boolean validSlot(int s){return s>=1&&s<=100;}
    // === Запись и чтение ===
    public boolean saveRecording(int slot,List<Frame> frames,NBTTagCompound settings){
        if(!validSlot(slot)||frames==null||frames.isEmpty()||frames.size()>MAX_FRAMES){MirrorDebug.log("STORAGE","save rejected for slot "+slot+" (frames="+(frames==null?-1:frames.size())+", limit="+MAX_FRAMES+")");return false;}
        MirrorDebug.log("STORAGE","saving slot "+slot+", frames="+frames.size());
        SlotSummary meta=summary(slot);
        NBTTagCompound root=codec.encodeRecording(frames,settings,meta.name,meta.desc);
        if(root==null)return false;
        boolean ok=fileStore.writeAtomically(slot,root);if(!ok)LOG.error("Failed to save slot {}",slot);
        invalidateSummary(slot);return ok;}
    public boolean saveRecordingLenient(int slot,List<Frame> frames,NBTTagCompound settings){
        if(!validSlot(slot)||frames==null||frames.isEmpty()||frames.size()>MAX_FRAMES)return false;
        SlotSummary meta=summary(slot);
        NBTTagCompound root=codec.encodeRecordingLenient(frames,settings,meta.name,meta.desc);
        if(root==null)return false;
        int skipped=frames.size()-root.getTagList("Frames",Constants.NBT.TAG_COMPOUND).tagCount();
        if(skipped>0)LOG.warn("Slot {}: saved {}/{} frames ({} skipped)",slot,root.getTagList("Frames",Constants.NBT.TAG_COMPOUND).tagCount(),frames.size(),skipped);
        boolean ok=fileStore.writeAtomically(slot,root);invalidateSummary(slot);return ok;}
    // === Фоновая запись ===
    /** Поставить слот на фоновое сохранение: главный поток отдаёт готовый снимок и идёт дальше, результат приходит в SaveCallback. */
    public void queueRecordingSave(int slot,List<Frame> frames,NBTTagCompound settings){
        if(!validSlot(slot)||frames==null||frames.isEmpty()||frames.size()>MAX_FRAMES){MirrorDebug.log("STORAGE","background save rejected for slot "+slot);return;}
        saver.queue(slot,frames,settings);}
    public void setSaveCallback(RecordingSaveWorker.SaveCallback cb){saver.setCallback(cb);}
    /** Тишина по слоту: нет ни заявки в очереди, ни активной фоновой записи. False = таймаут, операцию продолжать нельзя. */
    public boolean awaitSlotQuiescent(int slot){return saver.awaitQuiescent(slot,RecordingSaveWorker.QUIESCE_TIMEOUT_MS);}
    /** Тихо ли по слоту прямо сейчас (для снятия флага unsaved только по последнему снимку). */
    public boolean isSlotSettled(int slot){return saver.isSettled(slot);}
    /** Для хука завершения: дождаться фоновый поток и досохранить остатки синхронно. */
    public void shutdownFlush(){saver.shutdownFlush();}
    /** Порядок: целый .nbt → целый .bak → частично уцелевшие кадры. */
    public List<Frame> loadRecording(int slot){
        if(!validSlot(slot))return new ArrayList<Frame>();
        List<Frame> main=codec.decodeRoot(fileStore.tryRead(fileStore.nbtFile(slot)));int mainSkipped=codec.getLastSkippedFrames();
        if(main!=null&&mainSkipped==0)return main;
        List<Frame> bak=codec.decodeRoot(fileStore.tryRead(fileStore.bakFile(slot)));int bakSkipped=codec.getLastSkippedFrames();
        if(bak!=null&&bakSkipped==0){LOG.warn("Recovered slot {} from validated backup",slot);MirrorDebug.log("STORAGE","slot "+slot+": main file damaged, recovered "+bak.size()+" frames from .bak");return bak;}
        if(main!=null&&(bak==null||main.size()>=bak.size())){LOG.warn("Slot {} loaded with {} damaged frames skipped",slot,mainSkipped);MirrorDebug.log("STORAGE","slot "+slot+": kept "+main.size()+" frames, skipped "+mainSkipped+" damaged");return main;}
        if(bak!=null){LOG.warn("Slot {} loaded from .bak with {} damaged frames skipped",slot,bakSkipped);MirrorDebug.log("STORAGE","slot "+slot+": kept "+bak.size()+" frames from .bak, skipped "+bakSkipped+" damaged");return bak;}
        MirrorDebug.log("STORAGE","slot "+slot+": no readable recording in .nbt or .bak");return new ArrayList<Frame>();}
    // === Удаление и корзина ===
    /** Коды результата удаления: GUI и команда показывают по ним точный текст. */
    public static final int DELETE_FAILED=0,DELETE_TRASHED=1,DELETE_NO_COPY=2,DELETE_PARTIAL=3;
    public boolean deleteRecording(int slot){return deleteRecordingEx(slot)!=DELETE_FAILED;}
    public int deleteRecordingEx(int slot){
        if(!validSlot(slot))return DELETE_FAILED;
        if(!awaitSlotQuiescent(slot)){LOG.error("Refusing to delete slot {}: background save did not finish in time",Integer.valueOf(slot));return DELETE_FAILED;}
        NBTTagCompound best=fileStore.bestRootForBackup(slot,codec);
        int status=DELETE_TRASHED;
        if(best!=null){
            if(!trashStore.archiveToTrash(slot,best)){LOG.error("Refusing to delete slot {}: trash copy failed",slot);return DELETE_FAILED;}
        }else{
            // Нечитаемый слот: удаляем без копии, но честно сообщаем об этом.
            status=DELETE_NO_COPY;LOG.warn("Deleting slot {} without a trash copy: no readable recording",Integer.valueOf(slot));
            MirrorDebug.log("STORAGE","slot "+slot+" deleted without trash copy (unreadable)");
        }
        File t=fileStore.tmpFile(slot),b=fileStore.bakFile(slot),n=fileStore.nbtFile(slot);
        boolean ok=true;
        if(n.exists()&&!n.delete()){LOG.error("Could not delete main file of slot {}",Integer.valueOf(slot));return DELETE_FAILED;}
        if(t.exists()&&!t.delete()){LOG.error("Could not delete temp file of slot {}",Integer.valueOf(slot));ok=false;}
        if(b.exists()&&!b.delete()){LOG.error("Could not delete backup of slot {}",Integer.valueOf(slot));ok=false;}
        invalidateSummary(slot);
        if(!ok)return DELETE_PARTIAL;return status;}
    public boolean restoreTrash(String name,int slot){if(!validSlot(slot))return false;if(!awaitSlotQuiescent(slot))return false;if(peekFrameCount(slot)>0)return false;boolean ok=trashStore.restoreTrash(name,slot,codec,fileStore);if(ok){invalidateSummary(slot);}return ok;}
    public boolean deleteTrash(String name){return trashStore.deleteTrash(name);}
    public boolean clearTrash(){return trashStore.clearTrash();}
    public String latestTrashFile(){return trashStore.latestTrashFile();}
    public List<String> listTrashFiles(){return trashStore.listTrashFiles();}
    public int trashSlot(String name){return trashStore.trashSlot(name,fileStore);}
    public int trashFrames(String name){return trashStore.trashFrames(name,fileStore);}
    public long trashTime(String name){return trashStore.trashTime(name,fileStore);}
    public String trashTitle(String name){return trashStore.trashTitle(name,fileStore);}
    public String getTrashDirectoryPath(){return trashStore.getTrashDir().getAbsolutePath();}
    // === Метаданные и настройки слота ===
    public int peekFrameCount(int slot){return summary(slot).count;}
    public String loadSlotName(int s){return summary(s).name;}
    public String loadSlotDesc(int s){return summary(s).desc;}
    public NBTTagCompound loadSlotSettings(int slot){NBTTagCompound r=readStructuralRoot(slot);return r!=null&&r.hasKey("SlotSettings",Constants.NBT.TAG_COMPOUND)?r.getCompoundTag("SlotSettings"):null;}
    public boolean saveSlotSettings(int slot,NBTTagCompound settings){if(settings==null||!awaitSlotQuiescent(slot))return false;NBTTagCompound r=readStructuralRoot(slot);if(r==null)return false;r.setTag("SlotSettings",settings);invalidateSummary(slot);return fileStore.writeAtomically(slot,r);}
    public boolean saveSlotMetadata(int slot,String name,String desc){if(!awaitSlotQuiescent(slot))return false;NBTTagCompound r=readStructuralRoot(slot);if(r==null)return false;r.setString("SlotName",codec.clean(name,40));r.setString("SlotDesc",codec.clean(desc,120));invalidateSummary(slot);return fileStore.writeAtomically(slot,r);}
    // === Дублирование ===
    public boolean duplicateRecording(int source,int target,String emptyLabel,String copySuffix){
        if(!validSlot(source)||!validSlot(target)||source==target)return false;
        if(!awaitSlotQuiescent(source)||!awaitSlotQuiescent(target))return false;
        if(peekFrameCount(target)>0)return false;
        NBTTagCompound r=readStructuralRoot(source);if(r==null)return false;
        String sourceName=codec.clean(r.getString("SlotName"),40);
        if(sourceName.isEmpty()){r.setString("SlotName",codec.clean(emptyLabel,40));boolean ok=fileStore.writeAtomically(target,r);if(ok)invalidateSummary(target);return ok;}
        String suffix=copySuffix==null?"":copySuffix;String base=sourceName;
        while(!suffix.isEmpty()&&base.endsWith(suffix))base=base.substring(0,base.length()-suffix.length()).trim();
        if(base.length()+suffix.length()>40)base=base.substring(0,Math.max(0,40-suffix.length())).trim();
        r.setString("SlotName",base+suffix);boolean ok=fileStore.writeAtomically(target,r);if(ok)invalidateSummary(target);return ok;}
    // === Экспорт и импорт ===
    public String exportRecording(int slot){
        if(!awaitSlotQuiescent(slot))return null;
        NBTTagCompound r=readStructuralRoot(slot);if(r==null)return null;
        String base=codec.safeName(codec.clean(r.getString("SlotName"),40));if(base.isEmpty())base="slot_"+slot;
        String stamp=new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(new Date());
        File out=new File(exportDir,base+"-slot-"+slot+"-"+stamp+".mrr");int suffix=2;
        while(out.exists())out=new File(exportDir,base+"-slot-"+slot+"-"+stamp+"-"+(suffix++)+".mrr");
        try(FileOutputStream fos=new FileOutputStream(out)){r.setLong("ExportedAt",System.currentTimeMillis());CompressedStreamTools.writeCompressed(r,fos);return out.getName();}
        catch(Exception e){LOG.error("Export failed for slot {}",slot,e);return null;}}
    public List<String> listExportFiles(){
        File[] files=exportDir.listFiles(new FilenameFilter(){public boolean accept(File d,String n){return n.toLowerCase(Locale.ROOT).endsWith(".mrr");}});
        List<File> all=new ArrayList<File>();if(files!=null)Collections.addAll(all,files);
        Collections.sort(all,new Comparator<File>(){public int compare(File a,File b){return Long.compare(b.lastModified(),a.lastModified());}});
        List<String> names=new ArrayList<String>();for(File f:all)if(f.isFile())names.add(f.getName());return names;}
    public boolean importRecording(int slot,String fileName){
        if(!validSlot(slot)||fileName==null)return false;
        if(!awaitSlotQuiescent(slot))return false;
        if(peekFrameCount(slot)>0)return false;
        File in=new File(exportDir,new File(fileName).getName());NBTTagCompound r=fileStore.tryRead(in);
        if(!codec.validRoot(r))return false;
        r.setInteger("Version",Math.max(1,Math.min(FORMAT_VERSION,r.hasKey("Version")?r.getInteger("Version"):2)));
        r.setInteger("FrameCount",r.getTagList("Frames",Constants.NBT.TAG_COMPOUND).tagCount());
        r.setBoolean("Imported",true);boolean ok=fileStore.writeAtomically(slot,r);if(ok)invalidateSummary(slot);return ok;}
    // === Файловая информация ===
    public long getLastModified(int slot){if(!validSlot(slot))return 0L;File f=fileStore.nbtFile(slot);if(!f.isFile())f=fileStore.bakFile(slot);return f.isFile()?f.lastModified():0L;}
    public long getFileSize(int slot){if(!validSlot(slot))return 0L;File f=fileStore.nbtFile(slot);if(!f.isFile())f=fileStore.bakFile(slot);return f.isFile()?f.length():0L;}
    public String getExportDirectoryPath(){return exportDir.getAbsolutePath();}
    public boolean isImportedRecording(int slot){return validSlot(slot)&&summary(slot).imported;}
    // === Внутренние: кэш и чтение ===
    private NBTTagCompound readStructuralRoot(int slot){
        if(!validSlot(slot))return null;
        NBTTagCompound r=fileStore.tryRead(fileStore.nbtFile(slot));if(codec.validStructure(r))return r;
        NBTTagCompound b=fileStore.tryRead(fileStore.bakFile(slot));return codec.validStructure(b)?b:null;}
    private SlotSummary summary(int slot){
        if(!validSlot(slot))return SlotSummary.EMPTY;
        File n=fileStore.nbtFile(slot),b=fileStore.bakFile(slot);
        long ns=n.isFile()?n.lastModified():0L,nz=n.isFile()?n.length():0L,bs=b.isFile()?b.lastModified():0L,bz=b.isFile()?b.length():0L;
        // Карту читают и главный поток, и фоновое сохранение; тяжёлое чтение файла — вне блокировки.
        synchronized(summaries){SlotSummary old=summaries.get(slot);if(old!=null&&old.matches(ns,nz,bs,bz))return old;}
        NBTTagCompound root=readStructuralRoot(slot);
        SlotSummary fresh=root==null?new SlotSummary(ns,nz,bs,bz,0,"","",false):new SlotSummary(ns,nz,bs,bz,root.getTagList("Frames",Constants.NBT.TAG_COMPOUND).tagCount(),codec.clean(root.getString("SlotName"),40),codec.clean(root.getString("SlotDesc"),120),root.getBoolean("Imported"));
        synchronized(summaries){summaries.put(slot,fresh);}return fresh;}
    private void invalidateSummary(int slot){synchronized(summaries){summaries.remove(slot);}}
    private static final class SlotSummary{static final SlotSummary EMPTY=new SlotSummary(0L,0L,0L,0L,0,"","",false);final long ns,nz,bs,bz;final int count;final String name,desc;final boolean imported;SlotSummary(long ns,long nz,long bs,long bz,int count,String name,String desc,boolean imported){this.ns=ns;this.nz=nz;this.bs=bs;this.bz=bz;this.count=count;this.name=name;this.desc=desc;this.imported=imported;}boolean matches(long nst,long nsz,long bst,long bsz){return ns==nst&&nz==nsz&&bs==bst&&bz==bsz;}}
}
