package com.mirror.recorder.handler;

import com.mirror.recorder.config.RecorderConfig;
import com.mirror.recorder.gui.Lang;
import net.minecraft.client.entity.EntityPlayerSP;

/** Observes all health updates; policy decides which operation a real hit may stop. */
public final class SafetyGuard {
  private final DamageObservation damage = new DamageObservation();

  public void standby(EntityPlayerSP player) {
    observe(player);
  }

  public String check(EntityPlayerSP player, RecorderConfig config) {
    boolean hit = observe(player);
    return hit && config != null && config.isGuardOnDamage()
        ? Lang.t("msg.stopped_you_took_damage")
        : null;
  }

  private boolean observe(EntityPlayerSP player) {
    return damage.observe(
        player,
        player == null ? 0f : player.getHealth(),
        player == null ? 0f : player.getAbsorptionAmount(),
        player != null && ClientWorldState.validHealth(player),
        player == null ? 0 : player.hurtTime);
  }
}
