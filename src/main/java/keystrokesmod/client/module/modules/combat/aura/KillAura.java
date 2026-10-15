package keystrokesmod.client.module.modules.combat.aura;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.lwjgl.input.Mouse;

import com.google.common.eventbus.Subscribe;

import keystrokesmod.client.event.impl.ForgeEvent;
import keystrokesmod.client.event.impl.GameLoopEvent;
import keystrokesmod.client.event.impl.LookEvent;
import keystrokesmod.client.event.impl.MoveInputEvent;
import keystrokesmod.client.event.impl.PacketEvent;
import keystrokesmod.client.event.impl.UpdateEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.modules.client.Targets;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.module.setting.impl.DescriptionSetting;
import keystrokesmod.client.module.setting.impl.DoubleSliderSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.CoolDown;
import keystrokesmod.client.utils.Utils;
import keystrokesmod.client.module.modules.world.AntiBot;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraftforge.client.event.RenderWorldLastEvent;

public class KillAura extends Module {

    public EntityPlayer target;
    
    public static SliderSetting reach, fov, maxRps;
    private DoubleSliderSetting cps;
    private TickSetting disableOnTp, disableWhenFlying, mouseDown, onlySurvival, fixMovement, raytrace, showAim;
    private ComboSetting blockMode, sortMode;
    
    private CoolDown coolDown = new CoolDown(1);
    private boolean leftDown, locked, isBlocking;
    private long leftDownTime, leftUpTime;
    private float yaw, pitch, prevYaw, prevPitch;

    public KillAura() {
        super("KillAura", ModuleCategory.combat);
        this.registerSetting(new DescriptionSetting("Advanced FDP-Style Aura"));
        this.registerSetting(reach = new SliderSetting("Reach (Blocks)", 3.3, 3, 6, 0.05));
        this.registerSetting(cps = new DoubleSliderSetting("Left CPS", 12, 16, 1, 60, 0.5));
        this.registerSetting(maxRps = new SliderSetting("Smoothness (RPS)", 36, 10, 200, 1));
        this.registerSetting(fov = new SliderSetting("FOV", 180, 10, 360, 1));
        
        this.registerSetting(sortMode = new ComboSetting("Sort Mode", SortMode.Distance));
        this.registerSetting(blockMode = new ComboSetting("AutoBlock", AutoBlockMode.None));
        
        this.registerSetting(raytrace = new TickSetting("Raytrace", false));
        this.registerSetting(fixMovement = new TickSetting("Movement Fix", true));
        
        this.registerSetting(onlySurvival = new TickSetting("Only Survival", true));
        this.registerSetting(disableOnTp = new TickSetting("Disable after tp", true));
        this.registerSetting(disableWhenFlying = new TickSetting("Disable when flying", true));
        this.registerSetting(mouseDown = new TickSetting("Mouse Down", false));
        this.registerSetting(showAim = new TickSetting("Show aim", false));
    }

    @Subscribe
    public void gameLoopEvent(GameLoopEvent e) {
        if (!Utils.Player.isPlayerInGame()) {
            resetAttackState();
            return;
        }
        
        Mouse.poll();
        EntityPlayer pTarget = getBestTarget();
        
        if ((pTarget == null)
            || (mc.currentScreen != null)
            || !(!onlySurvival.isToggled() || (mc.playerController.getCurrentGameType() == GameType.SURVIVAL))
            || !coolDown.hasFinished()
            || !(!mouseDown.isToggled() || Mouse.isButtonDown(0))
            || !(!disableWhenFlying.isToggled() || !mc.thePlayer.capabilities.isFlying)) {
            resetAttackState();
            return;
        }
        
        target = pTarget;
        
        if (target != null) {
            updateRotations();
            locked = true;
            
            // CPS Logic
            long currentTime = System.currentTimeMillis();
            if (leftDownTime == 0 && leftUpTime == 0) genLeftTimings();
            
            boolean shouldAttack = false;
            if (currentTime >= leftDownTime && currentTime < leftUpTime && !leftDown) {
                shouldAttack = true;
                leftDown = true;
            } else if (currentTime >= leftUpTime) {
                genLeftTimings();
                leftDown = false;
            }
            
            if (shouldAttack) {
                executeAttack();
            }
        }
    }
    
    private void updateRotations() {
        float[] rotations = Utils.Player.getTargetRotations(target, 0);
        float targetYaw = rotations[0];
        float targetPitch = rotations[1];
        
        // GCD Fix and Smoothing
        float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - yaw);
        float pitchDiff = MathHelper.wrapAngleTo180_float(targetPitch - pitch);
        
        float maxRotation = (float) maxRps.getInput() / 2.0F; 
        
        if (Math.abs(yawDiff) > maxRotation) {
            yawDiff = (yawDiff > 0 ? maxRotation : -maxRotation);
        }
        if (Math.abs(pitchDiff) > maxRotation) {
            pitchDiff = (pitchDiff > 0 ? maxRotation : -maxRotation);
        }
        
        prevYaw = yaw;
        prevPitch = pitch;
        
        yaw += yawDiff;
        pitch += pitchDiff;
        
        // GCD Fix
        float f = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        float gcd = f * f * f * 1.2F;
        yaw -= yaw % gcd;
        pitch -= pitch % gcd;
        pitch = MathHelper.clamp_float(pitch, -90.0F, 90.0F);
    }

    private void executeAttack() {
        if (raytrace.isToggled()) {
            Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
            Vec3 toTarget = new Vec3(target.posX, target.posY + (double) target.getEyeHeight(), target.posZ);
            MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eyes, toTarget);
            if (mop != null) {
                // There's a block between us and the target - swing but don't hit
                mc.thePlayer.swingItem();
                return;
            }
        }
        
        // Unblock before hitting
        if (isBlocking) {
            if (mc.getNetHandler() != null) {
                mc.getNetHandler().addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
            }
            isBlocking = false;
        }
        
        mc.thePlayer.swingItem();
        mc.playerController.attackEntity(mc.thePlayer, target);
        
        // Autoblock after hitting
        AutoBlockMode mode = (AutoBlockMode) blockMode.getMode();
        if (mode == AutoBlockMode.Spoof && mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword) {
            if (mc.getNetHandler() != null) {
                mc.getNetHandler().addToSendQueue(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                isBlocking = true;
            }
        } else if (mode == AutoBlockMode.Interact && mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword) {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
            isBlocking = true;
        } else if (mode == AutoBlockMode.BlockHit && mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword) {
            mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(), 1);
        }
    }

    private EntityPlayer getBestTarget() {
        List<EntityPlayer> validTargets = new ArrayList<>();
        
        for (EntityPlayer ep : mc.theWorld.playerEntities) {
            if (ep != mc.thePlayer && !AntiBot.bot(ep) && !Targets.isAFriend(ep) && mc.thePlayer.getDistanceToEntity(ep) <= reach.getInput() && Utils.Player.fov(ep, (float) fov.getInput())) {
                validTargets.add(ep);
            }
        }
        
        if (validTargets.isEmpty()) return null;
        
        SortMode mode = (SortMode) sortMode.getMode();
        switch (mode) {
            case Health:
                validTargets.sort(Comparator.comparingDouble(EntityLivingBase::getHealth));
                break;
            case Armor:
                validTargets.sort(Comparator.comparingInt(EntityLivingBase::getTotalArmorValue));
                break;
            case Distance:
            default:
                validTargets.sort(Comparator.comparingDouble(p -> mc.thePlayer.getDistanceToEntity(p)));
                break;
        }
        
        return validTargets.get(0);
    }

    public void genLeftTimings() {
        double clickSpeed = Utils.Client.ranModuleVal(cps, Utils.Java.rand()) + (0.4D * Utils.Java.rand().nextDouble());
        long delay = (long) Math.round(1000.0D / clickSpeed);
        
        this.leftUpTime = System.currentTimeMillis() + delay;
        this.leftDownTime = (System.currentTimeMillis() + (delay / 2L)) - Utils.Java.rand().nextInt(10);
    }

    @Subscribe
    public void onUpdate(UpdateEvent e) {
        if(!Utils.Player.isPlayerInGame() || !locked) return;
        e.setYaw(yaw);
        e.setPitch(pitch);
        if (showAim.isToggled() && mc.gameSettings.thirdPersonView > 0) {
            mc.thePlayer.rotationYaw = yaw;
            mc.thePlayer.rotationPitch = pitch;
        }
    }

    @Subscribe
    public void move(MoveInputEvent e) {
        if(!fixMovement.isToggled() || !locked) return;
        e.setYaw(yaw);
    }

    @Subscribe
    public void lookEvent(LookEvent e) {
        if (showAim.isToggled() && mc.gameSettings.thirdPersonView > 0 && locked) {
            e.setYaw(yaw);
            e.setPitch(pitch);
            e.setPrevYaw(prevYaw);
            e.setPrevPitch(prevPitch);
        }
    }

    @Subscribe
    public void onPacket(PacketEvent e) {
        if (disableOnTp.isToggled() && e.getPacket() instanceof S08PacketPlayerPosLook) {
            this.disable();
        }
    }

    @Subscribe
    public void renderWorldLast(ForgeEvent fe) {
        if((fe.getEvent() instanceof RenderWorldLastEvent) && (target != null)) {
            int red = (int) Math.max(0, Math.min(255, (20 - target.getHealth()) * 13));
            int green = 255 - red;
            int rgb = new Color(red, green, 0).getRGB();
            Utils.HUD.drawBoxAroundEntity(target, 2, 0, 0, rgb, false);
        }
    }

    @Override
    public void onDisable() {
        resetAttackState();
        super.onDisable();
    }

    @Override
    public void onEnable() {
        resetAttackState();
        super.onEnable();
    }

    private void resetAttackState() {
        if (isBlocking) {
            if (mc.getNetHandler() != null) {
                mc.getNetHandler().addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
            }
            isBlocking = false;
        }
        target = null;
        locked = false;
        leftDown = false;
        leftDownTime = 0;
        leftUpTime = 0;
        yaw = mc.thePlayer != null ? mc.thePlayer.rotationYaw : 0;
        pitch = mc.thePlayer != null ? mc.thePlayer.rotationPitch : 0;
        prevYaw = yaw;
        prevPitch = pitch;
    }

    public enum AutoBlockMode {
        None, Spoof, Interact, BlockHit
    }

    public enum SortMode {
        Distance, Health, Armor
    }
}
