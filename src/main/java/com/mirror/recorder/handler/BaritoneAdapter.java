package com.mirror.recorder.handler;
import com.mirror.recorder.debug.MirrorDebug;
import java.lang.reflect.*;
/** Вся совместимость с Baritone через reflection. Ни один другой класс не знает API Baritone. */
public final class BaritoneAdapter{
    private BaritoneAdapter(){}
    private static boolean reflectionTried=false,reflectionOk=false;
    private static Constructor<?> cGoalNear;
    private static Method mGetProvider,mGetPrimary,mGetProcess,mSetGoal,mGetPathing,mIsPathing,mCancelEverything;
    /** Кэш: один раз пробуем, дальше возвращаем результат. */
    public static synchronized boolean isAvailable(){if(reflectionTried)return reflectionOk;reflectionTried=true;try{
        Class<?> api=Class.forName("baritone.api.BaritoneAPI"),goalClass=Class.forName("baritone.api.pathing.goals.GoalNear");
        cGoalNear=goalClass.getConstructor(int.class,int.class,int.class,int.class);
        mGetProvider=api.getMethod("getProvider");Object provider=mGetProvider.invoke(null);
        mGetPrimary=provider.getClass().getMethod("getPrimaryBaritone");Object b=mGetPrimary.invoke(provider);
        mGetProcess=b.getClass().getMethod("getCustomGoalProcess");Object process=mGetProcess.invoke(b);
        for(Method m:process.getClass().getMethods())if(m.getName().equals("setGoalAndPath")&&m.getParameterTypes().length==1&&m.getParameterTypes()[0].isAssignableFrom(goalClass)){mSetGoal=m;break;}
        mGetPathing=b.getClass().getMethod("getPathingBehavior");Object behavior=mGetPathing.invoke(b);
        mIsPathing=behavior.getClass().getMethod("isPathing");mCancelEverything=behavior.getClass().getMethod("cancelEverything");
        reflectionOk=mSetGoal!=null&&mGetProvider!=null&&mGetPrimary!=null&&mGetProcess!=null&&mGetPathing!=null&&mIsPathing!=null&&mCancelEverything!=null;
    }catch(Throwable ignored){reflectionOk=false;}MirrorDebug.probe("baritone reflection",reflectionOk,"setGoalAndPath / isPathing / cancelEverything");return reflectionOk;}
    /** Запуск навигации к точке. Возвращает handle (pathingBehavior) или null. */
    public static Object startPath(double x,double y,double z){
        try{if(!isAvailable())return null;
            Object provider=mGetProvider.invoke(null);Object baritone=mGetPrimary.invoke(provider);
            Object process=mGetProcess.invoke(baritone);
            Object goal=cGoalNear.newInstance((int)Math.floor(x),(int)Math.floor(y),(int)Math.floor(z),1);
            mSetGoal.invoke(process,goal);return mGetPathing.invoke(baritone);
        }catch(Throwable ignored){return null;}}
    /** Идёт ли сейчас поиск пути. */
    public static boolean isPathing(Object pathingBehavior){
        try{return pathingBehavior!=null&&mIsPathing!=null&&Boolean.TRUE.equals(mIsPathing.invoke(pathingBehavior));}
        catch(Throwable ignored){MirrorDebug.log("BARITONE","isPathing reflection failed: "+ignored);return false;}}
    /** Отмена всех целей и путей. */
    public static void cancelAll(Object pathingBehavior){
        try{if(pathingBehavior!=null&&mCancelEverything!=null)mCancelEverything.invoke(pathingBehavior);}
        catch(Throwable ignored){}}
}
