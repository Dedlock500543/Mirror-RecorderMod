package com.mirror.recorder.handler;

import com.mirror.recorder.debug.MirrorDebug;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** All Baritone compatibility goes through reflection. No other class knows the Baritone API. */
public final class BaritoneAdapter {
  private BaritoneAdapter() {}

  private static boolean reflectionTried = false, reflectionOk = false;
  private static Constructor<?> cGoalNear;
  private static Method mGetProvider,
      mGetPrimary,
      mGetProcess,
      mSetGoal,
      mGetPathing,
      mIsPathing,
      mCancelEverything;
  /** Cache: try once, then return the result. */
  public static synchronized boolean isAvailable() {
    if (reflectionTried) return reflectionOk;
    reflectionTried = true;
    try {
      Class<?> api = Class.forName("baritone.api.BaritoneAPI"),
          goalClass = Class.forName("baritone.api.pathing.goals.GoalNear");
      cGoalNear = goalClass.getConstructor(int.class, int.class, int.class, int.class);
      mGetProvider = api.getMethod("getProvider");
      Object provider = mGetProvider.invoke(null);
      mGetPrimary = provider.getClass().getMethod("getPrimaryBaritone");
      Object b = mGetPrimary.invoke(provider);
      mGetProcess = b.getClass().getMethod("getCustomGoalProcess");
      Object process = mGetProcess.invoke(b);
      for (Method m : process.getClass().getMethods())
        if (m.getName().equals("setGoalAndPath")
            && m.getParameterTypes().length == 1
            && m.getParameterTypes()[0].isAssignableFrom(goalClass)) {
          mSetGoal = m;
          break;
        }
      mGetPathing = b.getClass().getMethod("getPathingBehavior");
      Object behavior = mGetPathing.invoke(b);
      mIsPathing = behavior.getClass().getMethod("isPathing");
      mCancelEverything = behavior.getClass().getMethod("cancelEverything");
      reflectionOk =
          mSetGoal != null
              && mGetProvider != null
              && mGetPrimary != null
              && mGetProcess != null
              && mGetPathing != null
              && mIsPathing != null
              && mCancelEverything != null;
    } catch (Throwable ignored) {
      reflectionOk = false;
    }
    MirrorDebug.probe(
        "baritone reflection", reflectionOk, "setGoalAndPath / isPathing / cancelEverything");
    return reflectionOk;
  }
  /** Starts navigation to a point. Returns a handle (pathingBehavior) or null. */
  public static Object startPath(double x, double y, double z) {
    try {
      if (!isAvailable()) return null;
      Object provider = mGetProvider.invoke(null);
      Object baritone = mGetPrimary.invoke(provider);
      Object process = mGetProcess.invoke(baritone);
      Object goal =
          cGoalNear.newInstance((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z), 1);
      mSetGoal.invoke(process, goal);
      return mGetPathing.invoke(baritone);
    } catch (Throwable ignored) {
      return null;
    }
  }

  public static boolean isPathing(Object pathingBehavior) {
    try {
      return pathingBehavior != null
          && mIsPathing != null
          && Boolean.TRUE.equals(mIsPathing.invoke(pathingBehavior));
    } catch (Throwable ignored) {
      MirrorDebug.log("BARITONE", "isPathing reflection failed: " + ignored);
      return false;
    }
  }

  public static void cancelAll(Object pathingBehavior) {
    try {
      if (pathingBehavior != null && mCancelEverything != null)
        mCancelEverything.invoke(pathingBehavior);
    } catch (Throwable ignored) {
    }
  }
}
