package keystrokesmod.client.module.modules.render;

import com.google.common.eventbus.Subscribe;
import keystrokesmod.client.event.impl.ForgeEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.utils.Utils;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemExpBottle;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

public class Projectiles extends Module {

    public static SliderSetting w;

    public Projectiles() {
        super("Projectiles", ModuleCategory.render);
        this.registerSetting(w = new SliderSetting("Thickness", 2.0D, 1.0D, 10.0D, 1.0D));
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        if (fe.getEvent() instanceof RenderWorldLastEvent) {
            if (!Utils.Player.isPlayerInGame())
                return;
            RenderWorldLastEvent renderEvent = (RenderWorldLastEvent) fe.getEvent();
            float partialTicks = renderEvent.partialTicks;
            EntityPlayerSP player = mc.thePlayer;

            ItemStack heldStack = player.getCurrentEquippedItem();
            if (heldStack == null)
                return;
            Item item = heldStack.getItem();
            if (item == null)
                return;

            boolean isBow = item instanceof ItemBow;
            boolean isSplashPotion = (item instanceof ItemPotion) && ItemPotion.isSplash(heldStack.getMetadata());
            boolean isThrowable = (item instanceof ItemSnowball) || (item instanceof ItemEgg)
                    || (item instanceof ItemEnderPearl) || (item instanceof ItemExpBottle)
                    || (item instanceof ItemFishingRod) || isSplashPotion;

            if (!isBow && !isThrowable)
                return;

            // calculate starting position
            double arrowPosX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks
                    - Math.cos((float) Math.toRadians(player.rotationYaw)) * 0.16F;
            double arrowPosY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks
                    + player.getEyeHeight() - 0.1;
            double arrowPosZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks
                    - Math.sin((float) Math.toRadians(player.rotationYaw)) * 0.16F;

            // calculate starting motion
            float arrowMotionFactor = isBow ? 1.0F : 0.4F;
            float yaw = (float) Math.toRadians(player.rotationYaw);
            float pitch = (float) Math.toRadians(player.rotationPitch);
            float arrowMotionX = (float) (-Math.sin(yaw) * Math.cos(pitch) * arrowMotionFactor);
            float arrowMotionY = (float) (-Math.sin((float) Math.toRadians(player.rotationPitch - (isSplashPotion || item instanceof ItemExpBottle ? 20.0F : 0.0F))) * arrowMotionFactor);
            float arrowMotionZ = (float) (Math.cos(yaw) * Math.cos(pitch) * arrowMotionFactor);
            double arrowMotion = Math
                    .sqrt(arrowMotionX * arrowMotionX + arrowMotionY * arrowMotionY + arrowMotionZ * arrowMotionZ);
            if (arrowMotion <= 1e-6)
                return;
            arrowMotionX /= arrowMotion;
            arrowMotionY /= arrowMotion;
            arrowMotionZ /= arrowMotion;

            if (isBow) {
                float bowPower = (72000 - player.getItemInUseCount()) / 20F;
                bowPower = (bowPower * bowPower + bowPower * 2F) / 3F;

                if (bowPower > 1F || bowPower <= 0.1F)
                    bowPower = 1F;

                bowPower *= 3F;
                arrowMotionX *= bowPower;
                arrowMotionY *= bowPower;
                arrowMotionZ *= bowPower;
            } else {
                float velocity = isSplashPotion ? 0.5F : (item instanceof ItemExpBottle ? 0.7F : 1.5F);
                arrowMotionX *= velocity;
                arrowMotionY *= velocity;
                arrowMotionZ *= velocity;
            }

            // GL settings
            GL11.glPushMatrix();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glLineWidth((float) w.getInput());

            RenderManager renderManager = mc.getRenderManager();

            // draw trajectory line
            double gravity = isBow ? 0.05D : (isSplashPotion ? 0.05D : (item instanceof ItemExpBottle ? 0.07D : 0.03D));
            GL11.glColor4f(0, 1, 0, 0.75F);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int i = 0; i < 1000; i++) {
                GL11.glVertex3d(arrowPosX - renderManager.viewerPosX, arrowPosY - renderManager.viewerPosY,
                        arrowPosZ - renderManager.viewerPosZ);

                Vec3 prevPos = new Vec3(arrowPosX, arrowPosY, arrowPosZ);
                arrowPosX += arrowMotionX * 0.1;
                arrowPosY += arrowMotionY * 0.1;
                arrowPosZ += arrowMotionZ * 0.1;
                arrowMotionX *= 0.999D;
                arrowMotionY *= 0.999D;
                arrowMotionZ *= 0.999D;
                arrowMotionY -= gravity * 0.1;
                Vec3 nextPos = new Vec3(arrowPosX, arrowPosY, arrowPosZ);

                MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(prevPos, nextPos);
                if (hit != null && hit.hitVec != null) {
                    arrowPosX = hit.hitVec.xCoord;
                    arrowPosY = hit.hitVec.yCoord;
                    arrowPosZ = hit.hitVec.zCoord;
                    GL11.glVertex3d(arrowPosX - renderManager.viewerPosX, arrowPosY - renderManager.viewerPosY,
                            arrowPosZ - renderManager.viewerPosZ);
                    break;
                }
            }
            GL11.glEnd();

            // draw end of trajectory line
            double renderX = arrowPosX - renderManager.viewerPosX;
            double renderY = arrowPosY - renderManager.viewerPosY;
            double renderZ = arrowPosZ - renderManager.viewerPosZ;

            AxisAlignedBB landingBox = new AxisAlignedBB(
                    renderX - 0.25, renderY - 0.25, renderZ - 0.25,
                    renderX + 0.25, renderY + 0.25, renderZ + 0.25);
            GL11.glColor4f(0F, 1F, 0F, 0.75F);
            RenderGlobal.drawSelectionBoundingBox(landingBox);
            Utils.HUD.dbb(landingBox, 0F, 1F, 0F);

            // GL resets
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
            GL11.glPopMatrix();

        }
    }
}
