package keystrokesmod.client.module.modules.combat;

import com.google.common.eventbus.Subscribe;
import keystrokesmod.client.event.impl.MoveInputEvent;
import keystrokesmod.client.main.Raven;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.modules.combat.aura.KillAura;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.utils.Utils;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;

public class TargetStrafe extends Module {
    public static SliderSetting speed, distance;
    private int direction = 1;

    public TargetStrafe() {
        super("TargetStrafe", ModuleCategory.combat);
        this.registerSetting(speed = new SliderSetting("Speed", 1.0D, 0.5D, 2.0D, 0.1D));
        this.registerSetting(distance = new SliderSetting("Distance", 2.5D, 1.0D, 5.0D, 0.1D));
    }

    @Subscribe
    public void onMoveInput(MoveInputEvent e) {
        if (!Utils.Player.isPlayerInGame() || mc.thePlayer == null) return;

        EntityLivingBase target = getTarget();
        if (target == null) return;

        if (mc.thePlayer.isCollidedHorizontally) {
            direction = -direction;
        }

        double dist = mc.thePlayer.getDistanceToEntity(target);

        if (dist > distance.getInput() + 0.5) {
            e.setForward(1.0f);
            e.setStrafe(0.0f);
        } else {
            e.setForward(0.0f);
            e.setStrafe((float) (direction * speed.getInput()));
        }

        float yaw = getYawToTarget(target);
        e.setYaw(yaw);
    }

    private EntityLivingBase getTarget() {
        if (Raven.moduleManager == null) return null;
        KillAura ka = (KillAura) Raven.moduleManager.getModuleByClazz(KillAura.class);
        if (ka != null && ka.isEnabled() && ka.target != null && ka.target.isEntityAlive())
            return ka.target;
        return null;
    }

    private float getYawToTarget(EntityLivingBase target) {
        return calculateYawToTarget(mc.thePlayer.posX, mc.thePlayer.posZ, target.posX, target.posZ);
    }

    public static float calculateYawToTarget(double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        if (dx == 0.0D && dz == 0.0D) {
            return 0.0f;
        }
        return (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0f;
    }
}
