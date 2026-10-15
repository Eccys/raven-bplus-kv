package keystrokesmod.client.module.modules.combat;

import com.google.common.eventbus.Subscribe;
import keystrokesmod.client.event.impl.PacketEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.entity.Entity;

public class Criticals extends Module {
    public static ComboSetting mode;

    public Criticals() {
        super("Criticals", ModuleCategory.combat);
        this.registerSetting(mode = new ComboSetting("Mode", Mode.Packet));
    }

    @Subscribe
    public void onPacket(PacketEvent e) {
        if (mc.thePlayer == null) {
            return;
        }
        if (e.isOutgoing()) {
            if (e.getPacket() instanceof C02PacketUseEntity) {
                C02PacketUseEntity packet = (C02PacketUseEntity) e.getPacket();
                if (packet.getAction() == C02PacketUseEntity.Action.ATTACK) {
                    if (mc.thePlayer.onGround) {
                        doCrit();
                    }
                }
            }
        }
    }

    private void doCrit() {
        if (mc.thePlayer == null || mc.getNetHandler() == null) {
            return;
        }
        if (mode.getMode() == Mode.Packet) {
            double posX = mc.thePlayer.posX;
            double posY = mc.thePlayer.posY;
            double posZ = mc.thePlayer.posZ;
            mc.getNetHandler().addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(posX, posY + 0.0625, posZ, true));
            mc.getNetHandler().addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(posX, posY, posZ, false));
            mc.getNetHandler().addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(posX, posY + 1.1E-5, posZ, false));
            mc.getNetHandler().addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(posX, posY, posZ, false));
        } else if (mode.getMode() == Mode.Jump) {
            mc.thePlayer.jump();
        }
    }

    public enum Mode {
        Packet, Jump
    }
}
