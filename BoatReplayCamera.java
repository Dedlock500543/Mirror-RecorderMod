package com.mirror.recorder.handler;

import com.mirror.recorder.debug.MirrorDebug;
import com.mirror.recorder.model.Frame;
import java.lang.reflect.Field;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.util.math.MathHelper;

/**
 * Reconciles recorded END look with vanilla's later passenger rotation, without steering the boat.
 */
final class BoatReplayCamera {
  private static final Field DELTA_ROTATION = findDeltaRotation(EntityBoat.class);
  private Entity tickPlayer;
  private EntityBoat tickBoat;
  private float previousYaw, previousPitch, previousHeadYaw;
  private boolean prepared;
  private boolean warned;

  void beginTick(Entity player, EntityBoat boat) {
    tickPlayer = player;
    tickBoat = boat;
    previousYaw = player.rotationYaw;
    previousPitch = player.rotationPitch;
    previousHeadYaw = player.getRotationYawHead();
    prepared = false;
  }

  void anchor(float yaw, float pitch) {
    previousYaw = yaw;
    previousPitch = pitch;
    previousHeadYaw = yaw;
  }

  void preparePassenger(Entity player, EntityBoat boat, Frame frame) {
    if (player != tickPlayer) return;
    // A use action or mount packet can change riding identity after START in the same frame.
    // Keep the START look snapshot, but admit the passenger hook for the new boat.
    if (boat != tickBoat) {
      tickBoat = boat;
      prepared = false;
    }
    if (prepared) return;
    prepared = true;
    try {
      if (DELTA_ROTATION == null)
        throw new IllegalAccessException("boat deltaRotation unavailable");
      float targetYaw = previousYaw + MathHelper.wrapDegrees(frame.yaw - previousYaw);
      // InputUpdate runs after boat physics but before Entity.updateRidden calls updatePassenger.
      // END yaw already includes that native delta; replaying it twice causes a sawtooth camera.
      player.rotationYaw = targetYaw - DELTA_ROTATION.getFloat(boat);
      player.rotationPitch = MathHelper.clamp(frame.pitch, -90f, 90f);
      player.setRotationYawHead(player.rotationYaw);
      // Tick START sets the click angle before Entity.onUpdate snapshots prevRotation. Keep the
      // real previous END angle instead, so vanilla interpolation includes the boat turn once.
      player.prevRotationYaw = previousYaw;
      player.prevRotationPitch = previousPitch;
      if (player instanceof EntityLivingBase)
        ((EntityLivingBase) player).prevRotationYawHead = previousHeadYaw;
    } catch (IllegalAccessException e) {
      if (!warned) MirrorDebug.log("BOAT", "passenger look compensation failed: " + e);
      warned = true;
    }
  }

  static Field findDeltaRotation(Class<?> type) {
    for (String name : new String[] {"deltaRotation", "field_184475_as"}) {
      try {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
      } catch (ReflectiveOperationException | SecurityException ignored) {
        // The same helper must work in mapped development and SRG production clients.
      }
    }
    return null;
  }

  void reset() {
    tickPlayer = null;
    tickBoat = null;
    prepared = false;
  }
}
