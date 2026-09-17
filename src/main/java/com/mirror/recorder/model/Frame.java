package com.mirror.recorder.model;
import net.minecraft.nbt.NBTTagCompound;
public final class Frame {
    public final double x,y,z,motionX,motionY,motionZ;
    public final float yaw,pitch,moveForward,moveStrafe,guiX,guiY;
    public final boolean onGround,sneak,sprint,leftClick,rightClick,jump,guiClick;
    public final String chatMessage,guiScreen;
    /** Shift в момент клика по интерфейсу и экран, открытый в этот тик: без них повтор не воспроизводит shift-клик и закрытие окна. */
    public final boolean guiShift,hasScreenState,dropAll,worldReset;public final String openScreen;
    /** guiButton: 2 для среднего клика (0/1 выводятся из leftClick/rightClick, поэтому не хранятся). guiKeys: клавиши окна, по 6 int на событие. */
    public final int guiButton;public final int[] guiKeys;
    public final long tickIndex;
    public static final int K_ATTACK=1,K_USE=2,K_FORWARD=4,K_BACK=8,K_LEFT=16,K_RIGHT=32,K_JUMP=64,K_SNEAK=128,K_SPRINT=256,K_DROP=512,K_SWAP=1024,K_INVENTORY=2048,K_PICK=4096;
    /** Мгновенные действия: в кадре это всегда «нажали сейчас», удержания не бывает. */
    public static final int K_INSTANT=K_DROP|K_SWAP|K_INVENTORY|K_PICK;
    public final int keyMask,hotbarSlot;public final int attackClicks,useClicks;public final float dYaw,dPitch;public final boolean hasKeyMask;
    /** Капы событий одного тика: сами клики и записанный порядок кликов. */
    public static final int MAX_CLICKS=100,MAX_CLICK_SEQ=200,MAX_KEY_EVENTS=64,MAX_GUI_EVENTS=64,MAX_SCREEN_NAME=160;
    /** Порядок кликов внутри тика: 0 — атака, 1 — использование. Состояние «нажато/не нажато» порядок не хранит,
     *  поэтому он хранится отдельно: два клика в одном тике повторяются той же последовательностью, что записаны.
     *  Пустой массив — записи старого формата, где порядка нет. */
    public final int[] clickSeq;
    /** Смещение курсора от центра экрана в пикселях интерфейса: это тот же ввод, но без зависимости от размера окна. */
    public final int guiCenterX,guiCenterY;public final boolean hasGuiCenter;
    /** Все клавиши тика, которые не покрыты keyMask: по 2 int на событие — код и 1 нажата / 0 отпущена. */
    public final int[] keyEvents;
    /** Нарисованный курсор: где он был на экране в этот тик, в пикселях интерфейса. */
    public final boolean hasCursor;public final float curX,curY;
    /** Записанная цель установки блока: сам блок, сторона и точка попадания. */
    public final boolean hasTarget;public final int tgtX,tgtY,tgtZ,tgtFace;public final float hitX,hitY,hitZ;
    /** Полёт на элитрах в этот тик: глиссадой правят скорость и взгляд, а не клавиши, поэтому такой кадр повторяется скоростью. */
    public final boolean elytra;
    public boolean key(int bit){return (keyMask&bit)!=0;}
    private Frame(Builder b){x=b.x;y=b.y;z=b.z;yaw=b.yaw;pitch=b.pitch;motionX=b.motionX;motionY=b.motionY;motionZ=b.motionZ;onGround=b.onGround;sneak=b.sneak;sprint=b.sprint;leftClick=b.leftClick;rightClick=b.rightClick;jump=b.jump;moveForward=b.moveForward;moveStrafe=b.moveStrafe;guiX=b.guiX;guiY=b.guiY;guiClick=b.guiClick;guiScreen=b.guiScreen;chatMessage=b.chatMessage;tickIndex=b.tickIndex;keyMask=b.keyMask;hotbarSlot=b.hotbarSlot;dYaw=b.dYaw;dPitch=b.dPitch;hasKeyMask=b.hasKeyMask;guiCenterX=b.guiCenterX;guiCenterY=b.guiCenterY;hasGuiCenter=b.hasGuiCenter;guiShift=b.guiShift;openScreen=b.openScreen;hasScreenState=b.hasScreenState;guiButton=b.guiButton;guiKeys=b.guiKeys;dropAll=b.dropAll;worldReset=b.worldReset;attackClicks=b.attackClicks>0?b.attackClicks:(b.leftClick?1:0);useClicks=b.useClicks>0?b.useClicks:(b.rightClick?1:0);clickSeq=b.clickSeq;keyEvents=b.keyEvents==null?new int[0]:b.keyEvents;hasCursor=b.hasCursor;curX=b.curX;curY=b.curY;hasTarget=b.hasTarget;tgtX=b.tgtX;tgtY=b.tgtY;tgtZ=b.tgtZ;tgtFace=b.tgtFace;hitX=b.hitX;hitY=b.hitY;hitZ=b.hitZ;elytra=b.elytra;}
    public static Builder builder(){return new Builder();}
    public static final class Builder{
        private double x,y,z,motionX,motionY,motionZ;private float yaw,pitch,moveForward,moveStrafe,guiX,guiY;
        private boolean onGround,sneak,sprint,leftClick,rightClick,jump,guiClick;private String chatMessage,guiScreen;private long tickIndex;private int keyMask=0,hotbarSlot=-1;private float dYaw=0f,dPitch=0f;private boolean hasKeyMask=false;private int guiCenterX=0,guiCenterY=0;private boolean hasGuiCenter=false;private boolean guiShift=false,hasScreenState=false;private String openScreen=null;private static final int[] EMPTY=new int[0];private int guiButton=0;private int[] guiKeys=EMPTY;private boolean dropAll=false,worldReset=false;private int attackClicks=0,useClicks=0;private int[] clickSeq=EMPTY;
        private int[] keyEvents=EMPTY;private boolean hasCursor=false;private float curX=0f,curY=0f;
        private boolean hasTarget=false;private int tgtX=0,tgtY=0,tgtZ=0,tgtFace=-1;private float hitX=0f,hitY=0f,hitZ=0f;private boolean elytra=false;
        public Builder x(double v){x=v;return this;} public Builder y(double v){y=v;return this;} public Builder z(double v){z=v;return this;}
        public Builder yaw(float v){yaw=v;return this;} public Builder pitch(float v){pitch=v;return this;}
        public Builder motionX(double v){motionX=v;return this;} public Builder motionY(double v){motionY=v;return this;} public Builder motionZ(double v){motionZ=v;return this;}
        public Builder onGround(boolean v){onGround=v;return this;} public Builder sneak(boolean v){sneak=v;return this;}
        public Builder sprint(boolean v){sprint=v;return this;} public Builder leftClick(boolean v){leftClick=v;return this;} public Builder rightClick(boolean v){rightClick=v;return this;} public Builder jump(boolean v){jump=v;return this;}
        public Builder moveForward(float v){moveForward=v;return this;} public Builder moveStrafe(float v){moveStrafe=v;return this;}
        public Builder guiClick(boolean v){guiClick=v;return this;}public Builder guiX(float v){guiX=v;return this;}public Builder guiY(float v){guiY=v;return this;}public Builder guiScreen(String v){guiScreen=v!=null&&v.length()>160?v.substring(0,160):v;return this;}
        public Builder chatMessage(String v){chatMessage=v;return this;} public Builder tickIndex(long v){tickIndex=v;return this;}
        public Builder keyMask(int v){keyMask=v;hasKeyMask=true;return this;} public Builder hotbarSlot(int v){hotbarSlot=v<-1?-1:(v>8?8:v);return this;}
        public Builder dYaw(float v){dYaw=v;return this;} public Builder dPitch(float v){dPitch=v;return this;}
        public Builder guiCenter(boolean has,int x,int y){if(!has)return this;guiCenterX=x;guiCenterY=y;hasGuiCenter=true;return this;}
        public Builder guiShift(boolean v){guiShift=v;return this;}
        public Builder openScreen(String v){if(v==null||v.isEmpty()){openScreen=null;return this;}openScreen=v.length()>MAX_SCREEN_NAME?v.substring(0,MAX_SCREEN_NAME):v;return this;}
        public Builder screenState(boolean v){hasScreenState=v;return this;}
        public Builder guiButton(int v){guiButton=v;return this;}
        /** События окна идут блоками по 6 чисел: неполный хвост и перебор капа отбрасываются (импорт чужого .mrr). */
        public Builder guiKeys(int[] v){
            if(v==null||v.length<6){guiKeys=EMPTY;return this;}
            int cap=MAX_GUI_EVENTS*6;int room=v.length<cap?v.length:cap;room-=room%6;
            if(room<=0){guiKeys=EMPTY;return this;}
            int[] out=new int[room];System.arraycopy(v,0,out,0,room);guiKeys=out;return this;
        }
        public Builder dropAll(boolean v){dropAll=v;return this;}
        public Builder worldReset(boolean v){worldReset=v;return this;}
        // Кап кликов за тик: 100, а не 10 — макросы и быстрые кликеры больше не теряют нажатия.
        public Builder attackClicks(int v){attackClicks=v<0?0:(v>MAX_CLICKS?MAX_CLICKS:v);return this;} public Builder useClicks(int v){useClicks=v<0?0:(v>MAX_CLICKS?MAX_CLICKS:v);return this;}
        /** Порядок кликов тика: чужие коды отбрасываются, длина ограничена MAX_CLICK_SEQ. */
        public Builder clickSeq(int[] v){
            if(v==null||v.length==0){clickSeq=EMPTY;return this;}
            int room=v.length<MAX_CLICK_SEQ?v.length:MAX_CLICK_SEQ;int[] out=new int[room];int n=0;
            for(int i=0;i<v.length&&n<room;i++)if(v[i]==0||v[i]==1)out[n++]=v[i];
            if(n==room){clickSeq=out;return this;}
            int[] exact=new int[n];System.arraycopy(out,0,exact,0,n);clickSeq=exact;return this;
        }
        /** Все клавиши тика: нечётный хвост и перебор капа отбрасываются. */
        public Builder keyEvents(int[] v){
            if(v==null||v.length<2){keyEvents=EMPTY;return this;}
            int cap=MAX_KEY_EVENTS*2;int room=v.length<cap?v.length:cap;room-=room%2;
            if(room<=0){keyEvents=EMPTY;return this;}
            int[] out=new int[room];System.arraycopy(v,0,out,0,room);keyEvents=out;return this;
        }
        public Builder cursor(boolean has,float x,float y){if(!has)return this;hasCursor=true;curX=x;curY=y;return this;}
        public Builder target(boolean has,int x,int y,int z,int face,float hx,float hy,float hz){if(!has)return this;hasTarget=true;tgtX=x;tgtY=y;tgtZ=z;tgtFace=face;hitX=hx;hitY=hy;hitZ=hz;return this;}
        public Builder elytra(boolean v){elytra=v;return this;}
        public Builder readKeys(NBTTagCompound t){if(t!=null&&t.hasKey("KeyMask")){hasKeyMask=true;keyMask=t.getInteger("KeyMask");hotbarSlot=t.hasKey("Hotbar")?t.getInteger("Hotbar"):-1;if(hotbarSlot<-1)hotbarSlot=-1;if(hotbarSlot>8)hotbarSlot=8;dYaw=t.hasKey("DYaw")?t.getFloat("DYaw"):0f;dPitch=t.hasKey("DPitch")?t.getFloat("DPitch"):0f;}return this;}
        public Frame build(){return new Frame(this);}
    }
    public NBTTagCompound toNBT(){
        NBTTagCompound t=new NBTTagCompound();
        t.setDouble("X",x);t.setDouble("Y",y);t.setDouble("Z",z);t.setFloat("Yaw",yaw);t.setFloat("Pitch",pitch);
        t.setDouble("MX",motionX);t.setDouble("MY",motionY);t.setDouble("MZ",motionZ);
        t.setBoolean("OnGround",onGround);t.setBoolean("Sneak",sneak);t.setBoolean("Sprint",sprint);
        t.setBoolean("LeftClick",leftClick);t.setBoolean("RightClick",rightClick);if(attackClicks>1)t.setByte("LClicks",(byte)attackClicks);if(useClicks>1)t.setByte("RClicks",(byte)useClicks);t.setBoolean("Jump",jump);t.setLong("TickIndex",tickIndex);
        t.setFloat("MoveForward",moveForward);t.setFloat("MoveStrafe",moveStrafe);
        if(hasKeyMask){t.setInteger("KeyMask",keyMask);t.setInteger("Hotbar",hotbarSlot);if(dYaw!=0f)t.setFloat("DYaw",dYaw);if(dPitch!=0f)t.setFloat("DPitch",dPitch);if(dropAll)t.setBoolean("DropAll",true);}
        if(chatMessage!=null&&!chatMessage.isEmpty())t.setString("ChatMsg",chatMessage);if(guiClick&&guiScreen!=null&&!guiScreen.isEmpty()){t.setBoolean("GuiClick",true);t.setFloat("GuiX",guiX);t.setFloat("GuiY",guiY);t.setString("GuiScreen",guiScreen);if(hasGuiCenter){t.setInteger("GuiCX",guiCenterX);t.setInteger("GuiCY",guiCenterY);}if(guiShift)t.setBoolean("GuiShift",true);if(guiButton!=0)t.setByte("GuiBtn",(byte)guiButton);}if(openScreen!=null&&!openScreen.isEmpty())t.setString("GuiOpenScreen",openScreen);if(guiKeys.length>0)t.setIntArray("GuiKeys",guiKeys);if(clickSeq.length>0)t.setIntArray("ClickSeq",clickSeq);if(worldReset)t.setBoolean("WorldReset",true);
        if(keyEvents!=null&&keyEvents.length>0)t.setIntArray("KeyEvents",keyEvents);
        if(hasCursor){t.setFloat("CurX",curX);t.setFloat("CurY",curY);}
        if(hasTarget){t.setInteger("TgtX",tgtX);t.setInteger("TgtY",tgtY);t.setInteger("TgtZ",tgtZ);t.setByte("TgtFace",(byte)tgtFace);t.setFloat("HitX",hitX);t.setFloat("HitY",hitY);t.setFloat("HitZ",hitZ);}
        if(elytra)t.setBoolean("Elytra",true);
        return t;
    }
    public static Frame fromNBT(NBTTagCompound t){return fromNBT(t,false);}
    /** screenStateKnown=true только для записей формата 3+: в старых файлах открытый экран не сохранялся. */
    public static Frame fromNBT(NBTTagCompound t,boolean screenStateKnown){
        return builder().screenState(screenStateKnown).guiShift(t.hasKey("GuiShift")&&t.getBoolean("GuiShift")).openScreen(t.hasKey("GuiOpenScreen")?t.getString("GuiOpenScreen"):null).guiButton(t.hasKey("GuiBtn")?t.getByte("GuiBtn"):0).guiKeys(t.hasKey("GuiKeys")?t.getIntArray("GuiKeys"):null).clickSeq(t.hasKey("ClickSeq")?t.getIntArray("ClickSeq"):null).dropAll(t.hasKey("DropAll")&&t.getBoolean("DropAll")).worldReset(t.hasKey("WorldReset")&&t.getBoolean("WorldReset"))
            .x(t.hasKey("X")?t.getDouble("X"):0).y(t.hasKey("Y")?t.getDouble("Y"):0).z(t.hasKey("Z")?t.getDouble("Z"):0)
            .yaw(t.hasKey("Yaw")?t.getFloat("Yaw"):0).pitch(t.hasKey("Pitch")?t.getFloat("Pitch"):0)
            .motionX(t.hasKey("MX")?t.getDouble("MX"):0).motionY(t.hasKey("MY")?t.getDouble("MY"):0).motionZ(t.hasKey("MZ")?t.getDouble("MZ"):0)
            .onGround(t.hasKey("OnGround")&&t.getBoolean("OnGround")).sneak(t.hasKey("Sneak")&&t.getBoolean("Sneak"))
            .sprint(t.hasKey("Sprint")&&t.getBoolean("Sprint")).leftClick(t.hasKey("LeftClick")&&t.getBoolean("LeftClick")).rightClick(t.hasKey("RightClick")&&t.getBoolean("RightClick"))
            .jump(t.hasKey("Jump")&&t.getBoolean("Jump"))
            .moveForward(t.hasKey("MoveForward")?t.getFloat("MoveForward"):0).moveStrafe(t.hasKey("MoveStrafe")?t.getFloat("MoveStrafe"):0)
            .guiClick(t.hasKey("GuiClick")&&t.getBoolean("GuiClick")).guiX(t.hasKey("GuiX")?t.getFloat("GuiX"):0).guiY(t.hasKey("GuiY")?t.getFloat("GuiY"):0).guiScreen(t.hasKey("GuiScreen")?t.getString("GuiScreen"):null).guiCenter(t.hasKey("GuiCX")&&t.hasKey("GuiCY"),t.hasKey("GuiCX")?t.getInteger("GuiCX"):0,t.hasKey("GuiCY")?t.getInteger("GuiCY"):0)
            .tickIndex(t.hasKey("TickIndex")?t.getLong("TickIndex"):0).chatMessage(t.hasKey("ChatMsg")?t.getString("ChatMsg"):null).attackClicks(t.hasKey("LClicks")?t.getByte("LClicks"):0).useClicks(t.hasKey("RClicks")?t.getByte("RClicks"):0).keyEvents(t.hasKey("KeyEvents")?t.getIntArray("KeyEvents"):null).cursor(t.hasKey("CurX")&&t.hasKey("CurY"),t.hasKey("CurX")?t.getFloat("CurX"):0f,t.hasKey("CurY")?t.getFloat("CurY"):0f).target(t.hasKey("TgtX")&&t.hasKey("TgtY")&&t.hasKey("TgtZ"),t.hasKey("TgtX")?t.getInteger("TgtX"):0,t.hasKey("TgtY")?t.getInteger("TgtY"):0,t.hasKey("TgtZ")?t.getInteger("TgtZ"):0,t.hasKey("TgtFace")?t.getByte("TgtFace"):-1,t.hasKey("HitX")?t.getFloat("HitX"):0f,t.hasKey("HitY")?t.getFloat("HitY"):0f,t.hasKey("HitZ")?t.getFloat("HitZ"):0f).elytra(t.hasKey("Elytra")&&t.getBoolean("Elytra")).readKeys(t).build();
    }
}
