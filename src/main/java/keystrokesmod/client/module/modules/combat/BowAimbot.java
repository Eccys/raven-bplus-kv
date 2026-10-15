package keystrokesmod.client.module.modules.combat;

import com.google.common.eventbus.Subscribe;
import keystrokesmod.client.event.impl.ForgeEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.modules.client.Targets;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.Utils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemBow;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderWorldLastEvent;

public class BowAimbot extends Module {
    public static SliderSetting fov, distance;
    public static TickSetting predict;

    public BowAimbot() {
        super("BowAimbot", ModuleCategory.combat);
        this.registerSetting(fov = new SliderSetting("FOV", 90.0D, 10.0D, 360.0D, 1.0D));
        this.registerSetting(distance = new SliderSetting("Distance", 40.0D, 5.0D, 100.0D, 1.0D));
        this.registerSetting(predict = new TickSetting("Predict", true));
    }

    @Subscribe
    public void onRender(ForgeEvent fe) {
        if (!(fe.getEvent() instanceof RenderWorldLastEvent)) return;
        if (!Utils.Player.isPlayerInGame()) return;

        if (mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem().getItem() instanceof ItemBow && mc.thePlayer.isUsingItem()) {
            EntityLivingBase target = Targets.getTarget() instanceof EntityLivingBase ? (EntityLivingBase) Targets.getTarget() : null;
            if (target == null) target = Utils.Player.getClosestPlayer(distance.getInput());

            if (target != null && Utils.Player.fov(target, (float) fov.getInput())) {
                float[] rots = getBowRotations(target);
                if (rots != null && !Float.isNaN(rots[0]) && !Float.isNaN(rots[1])) {
                    mc.thePlayer.rotationYaw = rots[0];
                    mc.thePlayer.rotationPitch = MathHelper.clamp_float(rots[1], -90.0f, 90.0f);
                }
            }
        }
    }

    public static float[] calculateBowAngles(double x, double y, double z, double motionX, double motionZ, int itemInUseDuration, boolean predictTarget) {
        double bowPower = itemInUseDuration / 20.0;
        bowPower = (bowPower * bowPower + bowPower * 2.0) / 3.0;
        if (bowPower < 0.1) bowPower = 0.1;
        if (bowPower > 1.0) bowPower = 1.0;

        double velocity = bowPower * 3.0;

        if (predictTarget) {
            double initialDist = MathHelper.sqrt_double(x * x + z * z);
            double time = initialDist / velocity;
            x += motionX * time;
            z += motionZ * time;
        }

        double dist = MathHelper.sqrt_double(x * x + z * z);
        if (dist < 1e-4) {
            return new float[]{0.0f, y >= 0 ? -90.0f : 90.0f};
        }

        float yaw = (float) (Math.atan2(z, x) * 180.0 / Math.PI) - 90.0f;

        // Gravity compensation using actual arrow velocity
        double v2 = velocity * velocity;
        double v4 = v2 * v2;
        double g = 0.05;
        double discriminant = v4 - g * (g * dist * dist + 2.0 * y * v2);
        double pitch;
        if (discriminant >= 0.0) {
            pitch = -Math.toDegrees(Math.atan((v2 - Math.sqrt(discriminant)) / (g * dist)));
        } else {
            pitch = -(Math.atan2(y, dist) * 180.0 / Math.PI);
        }

        if (Double.isNaN(pitch) || Double.isInfinite(pitch)) {
            pitch = -(Math.atan2(y, dist) * 180.0 / Math.PI);
        }

        return new float[]{yaw, MathHelper.clamp_float((float) pitch, -90.0f, 90.0f)};
    }

    private float[] getBowRotations(Entity target) {
        double x = target.posX - mc.thePlayer.posX;
        double z = target.posZ - mc.thePlayer.posZ;
        double y = target.posY + target.getEyeHeight() - 0.1 - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();

        float[] rawAngles = calculateBowAngles(
                x, y, z,
                target.posX - target.prevPosX,
                target.posZ - target.prevPosZ,
                mc.thePlayer.getItemInUseDuration(),
                predict.isToggled()
        );

        return new float[]{
                mc.thePlayer.rotationYaw + MathHelper.wrapAngleTo180_float(rawAngles[0] - mc.thePlayer.rotationYaw),
                rawAngles[1]
        };
    }
}
