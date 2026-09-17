package com.mirror.recorder.handler;
import com.mirror.recorder.config.RecorderConfig;
import net.minecraft.client.entity.EntityPlayerSP;
/** Следит только за уроном: по игроку попали — запись и повтор пора остановить. */
public final class SafetyGuard{
    private static final int GRACE_TICKS=20;
    private float lastTotal=-1f;private int grace=GRACE_TICKS;
    /** Страховка смотрит на здоровье И абсорбцию: удар, ушедший в золотые сердца, не меняет getHealth(), но урон всё равно получен. */
    private static float total(EntityPlayerSP player){return player.getHealth()+player.getAbsorptionAmount();}
    public void standby(EntityPlayerSP player){lastTotal=player==null?-1f:total(player);grace=GRACE_TICKS;}
    public String check(EntityPlayerSP player,RecorderConfig config){
        if(player==null||config==null)return null;
        float now=total(player),before=lastTotal;lastTotal=now;
        // Льготная секунда тикает всегда: иначе выключенный на время страж «вызревает» рывком при включении.
        if(grace>0)grace--;
        if(!config.isGuardOnDamage())return null;
        if(before>=0f&&now<before-0.01f)
            return com.mirror.recorder.gui.Lang.s("Остановлено: по вам попали","Stopped: you took damage","Зупинено: по вас влучили","Gestoppt: Sie wurden getroffen","Zatrzymano: otrzymano obrażenia");
        return null;
    }
}
