package com.mirror.recorder.storage;
import com.mirror.recorder.model.Frame;
import net.minecraft.nbt.*;
import net.minecraftforge.common.util.Constants;
import java.util.*;
/** Кодек: сериализация/десериализация кадров в NBT и валидация. Не знает о диске. */
public class RecordingCodec{
    public static final int FORMAT_VERSION=3,MAX_FRAMES=72000;
    private int lastSkippedFrames=0;
    public int getLastSkippedFrames(){return lastSkippedFrames;}
    /** Структурная проверка: Version и число кадров. */
    public boolean validStructure(NBTTagCompound r){
        if(r==null)return false;int v=r.hasKey("Version")?r.getInteger("Version"):0;
        if(v<0||v>FORMAT_VERSION)return false;
        NBTTagList list=r.getTagList("Frames",Constants.NBT.TAG_COMPOUND);
        int actual=list.tagCount();return actual>=1&&actual<=MAX_FRAMES;}
    /** Полная проверка: структура + каждый кадр. */
    public boolean validRoot(NBTTagCompound r){
        if(!validStructure(r))return false;
        NBTTagList list=r.getTagList("Frames",Constants.NBT.TAG_COMPOUND);
        for(int i=0;i<list.tagCount();i++){try{if(!validFrame(Frame.fromNBT(list.getCompoundTagAt(i))))return false;}catch(Exception e){return false;}}return true;}
    /** Декодирование кадров с пропуском повреждённых. */
    public List<Frame> decodeRoot(NBTTagCompound r){
        lastSkippedFrames=0;if(!validStructure(r))return null;
        NBTTagList list=r.getTagList("Frames",Constants.NBT.TAG_COMPOUND);
        List<Frame> out=new ArrayList<Frame>(list.tagCount());int skipped=0;
        boolean screenState=(r.hasKey("Version")?r.getInteger("Version"):0)>=3;
        for(int i=0;i<list.tagCount();i++){try{Frame f=Frame.fromNBT(list.getCompoundTagAt(i),screenState);if(validFrame(f))out.add(f);else skipped++;}catch(Exception e){skipped++;}}
        lastSkippedFrames=skipped;return out.isEmpty()?null:out;}
    /** Сборка корневого NBT из списка кадров. Возвращает null если ни один кадр не прошёл валидацию. */
    public NBTTagCompound encodeRecording(List<Frame> frames,NBTTagCompound settings,String name,String desc){
        NBTTagCompound root=new NBTTagCompound();root.setInteger("Version",FORMAT_VERSION);
        if(name!=null&&!name.isEmpty())root.setString("SlotName",name);
        if(desc!=null&&!desc.isEmpty())root.setString("SlotDesc",desc);
        NBTTagList list=new NBTTagList();for(Frame f:frames){if(!validFrame(f))return null;list.appendTag(f.toNBT());}
        root.setInteger("FrameCount",list.tagCount());root.setTag("Frames",list);
        if(settings!=null)root.setTag("SlotSettings",settings);return root;}
    /** Сборка с пропуском невалидных кадров. */
    public NBTTagCompound encodeRecordingLenient(List<Frame> frames,NBTTagCompound settings,String name,String desc){
        NBTTagCompound root=new NBTTagCompound();root.setInteger("Version",FORMAT_VERSION);
        if(name!=null&&!name.isEmpty())root.setString("SlotName",name);
        if(desc!=null&&!desc.isEmpty())root.setString("SlotDesc",desc);
        NBTTagList list=new NBTTagList();for(Frame f:frames){if(validFrame(f))list.appendTag(f.toNBT());}
        if(list.tagCount()==0)return null;
        root.setInteger("FrameCount",list.tagCount());root.setTag("Frames",list);
        if(settings!=null)root.setTag("SlotSettings",settings);return root;}
    /** Валидация одного кадра: координаты, углы, скорости, события, GUI. */
    public boolean validFrame(Frame f){
        return(f!=null&&finite(f.x)&&finite(f.y)&&finite(f.z)&&Math.abs(f.x)<=31000000d&&Math.abs(f.y)<=31000000d&&Math.abs(f.z)<=31000000d
            &&finite(f.motionX)&&finite(f.motionY)&&finite(f.motionZ)&&Math.abs(f.motionX)<=100d&&Math.abs(f.motionY)<=100d&&Math.abs(f.motionZ)<=100d
            &&finite(f.yaw)&&finite(f.pitch)&&Math.abs(f.pitch)<=90.001f
            &&finite(f.moveForward)&&finite(f.moveStrafe)&&Math.abs(f.moveForward)<=1.001f&&Math.abs(f.moveStrafe)<=1.001f
            &&f.tickIndex>=0&&f.tickIndex<MAX_FRAMES&&f.keyMask>=0&&f.hotbarSlot>=-1&&f.hotbarSlot<=8
            &&finite(f.dYaw)&&finite(f.dPitch)&&Math.abs(f.dYaw)<=360.001f&&Math.abs(f.dPitch)<=180.001f
            &&(f.chatMessage==null||f.chatMessage.length()<=256)
            &&(!f.guiClick||(finite(f.guiX)&&finite(f.guiY)&&f.guiX>=0f&&f.guiX<=1f&&f.guiY>=0f&&f.guiY<=1f
                &&f.guiScreen!=null&&f.guiScreen.length()>0&&f.guiScreen.length()<=160&&Math.abs(f.guiCenterX)<=16384&&Math.abs(f.guiCenterY)<=16384)))
            &&f.keyEvents!=null&&f.keyEvents.length%2==0&&f.keyEvents.length<=Frame.MAX_KEY_EVENTS*2
            &&f.guiKeys!=null&&f.guiKeys.length%6==0&&f.guiKeys.length<=Frame.MAX_GUI_EVENTS*6
            &&(f.openScreen==null||f.openScreen.length()<=Frame.MAX_SCREEN_NAME)
            &&finite(f.curX)&&finite(f.curY)&&Math.abs(f.curX)<=16384f&&Math.abs(f.curY)<=16384f
            &&(!f.hasTarget||(Math.abs(f.tgtX)<=31000000&&Math.abs(f.tgtY)<=31000000&&Math.abs(f.tgtZ)<=31000000
                &&f.tgtFace>=-1&&f.tgtFace<=5&&finite(f.hitX)&&finite(f.hitY)&&finite(f.hitZ)));}
    private boolean finite(double v){return !Double.isNaN(v)&&!Double.isInfinite(v);}
    public String clean(String v,int max){if(v==null)return"";StringBuilder b=new StringBuilder();for(int i=0;i<v.length()&&b.length()<max;i++){char c=v.charAt(i);if(c!='\u00a7'&&!Character.isISOControl(c))b.append(c);}return b.toString().trim();}
    public String safeName(String v){StringBuilder b=new StringBuilder();for(int i=0;i<v.length()&&b.length()<28;i++){char c=v.charAt(i);b.append(Character.isLetterOrDigit(c)||c=='-'||c=='_'?c:'_');}return b.toString().replaceAll("_+","_");}
}
