package com.mirror.recorder.handler;

import com.mirror.recorder.manager.RecorderManager;
import net.minecraft.network.NetworkManager;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * World unload: busy operations are preserved; an active recording is checkpointed. When idle, the
 * manager is stopped and saved frame caches can be released.
 */
@SideOnly(Side.CLIENT)
public class WorldLifecycleHandler {
  private final RecorderManager manager;
  private volatile NetworkManager connection;

  public WorldLifecycleHandler(RecorderManager manager) {
    this.manager = manager;
  }

  @SubscribeEvent
  public void onConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
    connection = event.getManager();
    manager.getWorldLifecycle().connected();
  }

  @SubscribeEvent
  public void onDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
    // Netty may emit this twice; the client thread consumes one terminal transition.
    if (event.getManager() == connection) manager.getWorldLifecycle().disconnected();
  }

  @SubscribeEvent
  public void onWorldUnload(WorldEvent.Unload event) {
    if (!event.getWorld().isRemote) return;
    if (manager.isBusy()) manager.checkpointRecording();
    else manager.stopAll();
    manager.releaseIdleFrames();
  }
}
