package keystrokesmod.client.module.modules.combat;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

import org.lwjgl.input.Mouse;

import com.google.common.eventbus.Subscribe;

import keystrokesmod.client.event.impl.ForgeEvent;
import keystrokesmod.client.main.Raven;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.modules.client.Targets;
import keystrokesmod.client.module.setting.impl.DescriptionSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.Utils;
import keystrokesmod.client.module.modules.world.AntiBot;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class AimAssist extends Module {
    public static SliderSetting speedYaw, complimentYaw, speedPitch, complimentPitch;
    public static SliderSetting fov;
    public static SliderSetting distance;
    public static SliderSetting pitchOffSet;
    public static TickSetting clickAim;
    public static TickSetting stopWhenOver;
    public static TickSetting aimPitch;
    public static TickSetting weaponOnly;
    public static TickSetting aimInvis;
    public static TickSetting breakBlocks;
    public static TickSetting blatantMode;
    public static ArrayList<Entity> friends = new ArrayList<>();

    public AimAssist() {
        super("AimAssist", ModuleCategory.combat);
        this.registerSetting(new DescriptionSetting("Set targets in Client->Targets"));
        this.registerSetting(speedYaw = new SliderSetting("Speed 1 (yaw)", 45.0D, 5.0D, 100.0D, 1.0D));
        this.registerSetting(complimentYaw = new SliderSetting("Speed 2 (yaw)", 15.0D, 2D, 97.0D, 1.0D));
        this.registerSetting(speedPitch = new SliderSetting("Speed 1 (pitch)", 45.0D, 5.0D, 100.0D, 1.0D));
        this.registerSetting(complimentPitch = new SliderSetting("Speed 2 (pitch)", 15.0D, 2D, 97.0D, 1.0D));
        this.registerSetting(fov = new SliderSetting("FOV", 90.0D, 10.0D, 360.0D, 1.0D));
        this.registerSetting(distance = new SliderSetting("Distance", 4.5D, 1.0D, 10.0D, 0.1D));
        this.registerSetting(pitchOffSet = new SliderSetting("pitchOffSet (blocks)", 0.0D, -2, 2, 0.050D));
        this.registerSetting(clickAim = new TickSetting("Click aim", false));
        this.registerSetting(breakBlocks = new TickSetting("Break blocks", true));
        this.registerSetting(weaponOnly = new TickSetting("Weapon only", false));
        this.registerSetting(blatantMode = new TickSetting("Blatant mode", false));
        this.registerSetting(aimPitch = new TickSetting("Aim pitch", false));
    }

    @Subscribe
    public void onRender(ForgeEvent fe) {
        if (!(fe.getEvent() instanceof net.minecraftforge.client.event.RenderWorldLastEvent))
            return;

        try {
            if (!Utils.Client.currentScreenMinecraft() || !Utils.Player.isPlayerInGame())
                return;

            if (breakBlocks.isToggled() && (mc.objectMouseOver != null)) {
                BlockPos p = mc.objectMouseOver.getBlockPos();
                if (p != null) {
                    Block bl = mc.theWorld.getBlockState(p).getBlock();
                    if ((bl != Blocks.air) && !(bl instanceof BlockLiquid) && (bl != null))
                        return;
                }
            }

            if (!weaponOnly.isToggled() || Utils.Player.isPlayerHoldingWeapon()) {
                if (!clickAim.isToggled() || Mouse.isButtonDown(0)) {
                    Entity en = this.getEnemy();
                    if (en != null) {
                        if (blatantMode.isToggled()) {
                            Utils.Player.aim(en, (float) pitchOffSet.getInput());
                        } else {
                            double n = Utils.Player.fovFromEntity(en);
                            if ((n > 1.0D) || (n < -1.0D)) {
                                double complimentSpeed = n
                                        * (ThreadLocalRandom.current().nextDouble(complimentYaw.getInput() - 1.47328,
                                                complimentYaw.getInput() + 2.48293) / 100);
                                float val = (float) (-(complimentSpeed + (n / (101.0D - (float) ThreadLocalRandom.current()
                                        .nextDouble(speedYaw.getInput() - 4.723847, speedYaw.getInput())))));
                                mc.thePlayer.rotationYaw += val * 0.4F; 
                            }
                            if (aimPitch.isToggled()) {
                                double pitchDiff = Utils.Player.PitchFromEntity(en, (float) pitchOffSet.getInput());
                                if (pitchDiff > 1.0D || pitchDiff < -1.0D) {
                                    double complimentSpeed = pitchDiff
                                            * (ThreadLocalRandom.current().nextDouble(complimentPitch.getInput() - 1.47328,
                                                    complimentPitch.getInput() + 2.48293) / 100);

                                    float val = (float) (-(complimentSpeed
                                            + (pitchDiff / (101.0D - (float) ThreadLocalRandom.current()
                                                    .nextDouble(speedPitch.getInput() - 4.723847,
                                                            speedPitch.getInput())))));

                                    mc.thePlayer.rotationPitch += val * 0.4F;
                                    if (mc.thePlayer.rotationPitch > 90.0F) mc.thePlayer.rotationPitch = 90.0F;
                                    if (mc.thePlayer.rotationPitch < -90.0F) mc.thePlayer.rotationPitch = -90.0F;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Entity getEnemy() {
        for (net.minecraft.entity.player.EntityPlayer ep : mc.theWorld.playerEntities) {
            if (ep != mc.thePlayer && !AntiBot.bot(ep) && !Targets.isAFriend(ep) && mc.thePlayer.getDistanceToEntity(ep) <= distance.getInput() && Utils.Player.fov(ep, (float) fov.getInput())) {
                return ep;
            }
        }
        return null;
    }

    public static void addFriend(Entity entityPlayer) {
        friends.add(entityPlayer);
    }

    public static boolean addFriend(String name) {
        boolean found = false;
        for (Entity entity : mc.theWorld.getLoadedEntityList())
            if (entity.getName().equalsIgnoreCase(name) || entity.getCustomNameTag().equalsIgnoreCase(name))
                if (!Targets.isAFriend(entity)) {
                    addFriend(entity);
                    found = true;
                }

        return found;
    }

    public static boolean removeFriend(String name) {
        if (name == null) return false;
        boolean removed = false;
        for (int i = friends.size() - 1; i >= 0; i--) {
            Entity entity = friends.get(i);
            if (entity != null && (name.equalsIgnoreCase(entity.getName()) || name.equalsIgnoreCase(entity.getCustomNameTag()))) {
                friends.remove(i);
                removed = true;
            }
        }
        return removed;
    }

    public static boolean removeFriend(Entity entityPlayer) {
        try {
            return friends.remove(entityPlayer);
        } catch (Exception eeeeee) {
            eeeeee.printStackTrace();
            return false;
        }
    }

    public static ArrayList<Entity> getFriends() {
        return friends;
    }
}