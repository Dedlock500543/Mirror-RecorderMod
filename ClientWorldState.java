package com.mirror.recorder.handler;

import com.mirror.recorder.manager.RecorderManager;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/** Reads vanilla's authoritative synchronization flags rather than guessing a grace duration. */
final class ClientWorldState {
  private static final Field HEALTH_VALID =
      ReflectionHelper.findField(EntityPlayerSP.class, "hasValidHealth", "field_175169_bQ");
  private static final Field TERRAIN_READY =
      ReflectionHelper.findField(
          NetHandlerPlayClient.class, "doneLoadingTerrain", "field_147309_h");

  private ClientWorldState() {}

  static void update(RecorderManager manager) {
    Minecraft mc = Minecraft.getMinecraft();
    // The server's death screen is authoritative even if terrain loading is still incomplete.
    boolean ready = ready(mc) || mc.currentScreen instanceof GuiGameOver;
    boolean dead =
        mc.currentScreen instanceof GuiGameOver
            || (mc.player != null && validHealth(mc.player) && mc.player.getHealth() <= 0f);
    manager.getWorldLifecycle().observe(mc.world, mc.player, ready, dead);
  }

  private static boolean ready(Minecraft mc) {
    return mc.world != null
        && mc.player != null
        && mc.getConnection() != null
        && !(mc.currentScreen instanceof GuiDownloadTerrain)
        && flag(TERRAIN_READY, mc.getConnection())
        && validHealth(mc.player)
        && mc.world.isBlockLoaded(new BlockPos(mc.player));
  }

  static boolean validHealth(EntityPlayerSP player) {
    return flag(HEALTH_VALID, player);
  }

  private static boolean flag(Field field, Object owner) {
    try {
      return field.getBoolean(owner);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("Cannot read vanilla synchronization flag", e);
    }
  }
}
