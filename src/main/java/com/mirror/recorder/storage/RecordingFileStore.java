package com.mirror.recorder.storage;
import com.mirror.recorder.debug.MirrorDebug;
import net.minecraft.nbt.*;
import org.apache.logging.log4j.*;
import java.io.*;
import java.util.zip.*;
/** Файловый ввод-вывод: чтение, запись, атомарное сохранение, backup. Не знает о кадрах и NBT-формате. */
public class RecordingFileStore{
    private static final Logger LOG=LogManager.getLogger("MirrorRecorder/Storage");
    private static final long MAX_FILE_BYTES=64L*1024L*1024L,MAX_NBT_BYTES=256L*1024L*1024L;
    private final File saveDir;
    public RecordingFileStore(File saveDir){this.saveDir=saveDir;}
    public File nbtFile(int s){return new File(saveDir,"slot_"+s+".nbt");}
    public File tmpFile(int s){return new File(saveDir,"slot_"+s+".tmp");}
    public File bakFile(int s){return new File(saveDir,"slot_"+s+".bak");}
    /** Ограниченное чтение: лимит на размер файла и объём распакованного NBT. */
    public NBTTagCompound tryRead(File f){
        if(f==null||!f.isFile()||f.length()<=0L||f.length()>MAX_FILE_BYTES)return null;
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(f)),16384))){
            return CompressedStreamTools.read(in,new NBTSizeTracker(MAX_NBT_BYTES));
        }catch(Exception e){LOG.error("Read error for {}",f.getName(),e);return null;}}
    /** Запись с быстрым сжатием. */
    public void writeFast(NBTTagCompound root,File file)throws IOException{
        try(FileOutputStream fos=new FileOutputStream(file);FastGzip gz=new FastGzip(fos);DataOutputStream out=new DataOutputStream(gz)){
            CompressedStreamTools.write(root,out);}}
    /** Проверка записанного файла потоком, без сборки NBT-дерева. */
    public boolean verifyStream(File f){
        if(f==null||!f.isFile()||f.length()<=0L||f.length()>MAX_FILE_BYTES)return false;
        try(GZIPInputStream in=new GZIPInputStream(new BufferedInputStream(new FileInputStream(f)),16384)){
            byte[] buf=new byte[16384];long total=0L;int n;while((n=in.read(buf))>=0){total+=n;if(total>MAX_NBT_BYTES)return false;}return total>0L;
        }catch(Exception e){LOG.error("Verification read failed for {}",f.getName(),e);return false;}}
    /** Сначала атомарное перемещение через NIO, затем обычное с заменой, и только потом renameTo. */
    public boolean moveFile(File from,File to){
        if(from==null||to==null||!from.isFile())return false;
        try{java.nio.file.Files.move(from.toPath(),to.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE);return true;
        }catch(Exception atomicUnsupported){
            try{java.nio.file.Files.move(from.toPath(),to.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);return true;
            }catch(Exception replaceFailed){return from.renameTo(to);}}}
    /** Атомарное сохранение: tmp → verify → rotate backup → commit. */
    public boolean writeAtomically(int slot,NBTTagCompound root){
        File tmp=tmpFile(slot),nbt=nbtFile(slot),bak=bakFile(slot);
        try{if(tmp.exists()&&!tmp.delete())return false;
            writeFast(root,tmp);
            if(!verifyStream(tmp)){LOG.error("Written file for slot {} failed verification, keeping the old one",slot);MirrorDebug.log("STORAGE","verification failed for slot "+slot+", backup untouched");tmp.delete();return false;}
            if(nbt.exists()){
                // Основной файл повреждён: он не должен вытеснить целую резервную копию.
                boolean primaryOk=verifyStream(nbt);
                boolean backupOk=bak.isFile()&&verifyStream(bak);
                if(!primaryOk){
                    MirrorDebug.log("STORAGE","corrupt primary for slot "+slot+(backupOk?", keeping the good backup":", no good backup"));}
                if(!primaryOk&&backupOk){if(!nbt.delete()){tmp.delete();return false;}}
                else{if(bak.exists()&&!bak.delete()){tmp.delete();return false;}if(!moveFile(nbt,bak)){tmp.delete();return false;}}}
            if(!moveFile(tmp,nbt)){if(bak.exists())moveFile(bak,nbt);tmp.delete();return false;}
            return true;
        }catch(Exception e){LOG.error("Atomic write failed for slot {}",slot,e);if(tmp.exists())tmp.delete();if(!nbt.exists()&&bak.exists())moveFile(bak,nbt);return false;}}
    /** Лучшая доступная копия для архивации в корзину: предпочитаем .bak (целое предыдущее поколение). */
    public NBTTagCompound bestRootForBackup(int slot,RecordingCodec codec){
        NBTTagCompound n=tryRead(nbtFile(slot));
        java.util.List<com.mirror.recorder.model.Frame> nFrames=n!=null&&codec.validStructure(n)?codec.decodeRoot(n):null;int nSkipped=codec.getLastSkippedFrames();
        if(nFrames!=null&&nSkipped==0)return n;
        NBTTagCompound b=tryRead(bakFile(slot));
        java.util.List<com.mirror.recorder.model.Frame> bFrames=b!=null&&codec.validStructure(b)?codec.decodeRoot(b):null;int bSkipped=codec.getLastSkippedFrames();
        if(bFrames!=null&&bSkipped==0)return b;
        if(nFrames!=null&&(bFrames==null||nFrames.size()>bFrames.size()||(nFrames.size()==bFrames.size()&&nSkipped<=bSkipped)))return n;
        return b!=null?b:n;}
    private static final class FastGzip extends GZIPOutputStream{FastGzip(OutputStream out)throws IOException{super(out,16384);def.setLevel(Deflater.BEST_SPEED);}}
}
