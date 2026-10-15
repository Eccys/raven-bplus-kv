package keystrokesmod.client.module.modules.movement;

import com.google.common.eventbus.Subscribe;
import keystrokesmod.client.event.impl.ForgeEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.Module.ModuleCategory;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.living.LivingEvent;

public class Jetpack extends Module {
    
    public static ComboSetting mode;
    public static SliderSetting climbSpeed;
    public static SliderSetting fallSpeed;
    public static TickSetting noFall;
    public static TickSetting onlyWalls;
    
    public enum JetpackMode {
        VANILLA,        // Normal jetpack flying
        FAST,           // Fast jetpack flying
        FREECAM,        // Freecam-like flying
        BOUNCE         // Bounce flying
    }

    public Jetpack() {
        super("Jetpack", ModuleCategory.movement);
        this.registerSetting(mode = new ComboSetting("Mode", JetpackMode.VANILLA));
        this.registerSetting(climbSpeed = new SliderSetting("Climb Speed", 1.0, 0.5, 3.0, 0.1));
        this.registerSetting(fallSpeed = new SliderSetting("Fall Speed", 0.5, 0.1, 1.0, 0.1));
        this.registerSetting(noFall = new TickSetting("No Fall Damage", true));
        this.registerSetting(onlyWalls = new TickSetting("Only Walls", false));
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        if (fe.getEvent() instanceof LivingEvent.LivingUpdateEvent) {
            LivingEvent.LivingUpdateEvent event = (LivingEvent.LivingUpdateEvent) fe.getEvent();
            if (event.entity instanceof EntityPlayerSP) {
                EntityPlayerSP player = (EntityPlayerSP) event.entity;
                if (Utils.Player.isPlayerInGame()) {
                    handleJetpackFly(player);
                }
            }
        }
    }

    private void handleJetpackFly(EntityPlayerSP player) {
        if (onlyWalls.isToggled() && !player.isCollidedHorizontally) {
            return;
        }
        JetpackMode currentMode = (JetpackMode) mode.getMode();
        
        switch (currentMode) {
            case VANILLA:
                handleVanillaJetpack(player);
                break;
            case FAST:
                handleFastJetpack(player);
                break;
            case FREECAM:
                handleFreecamJetpack(player);
                break;
            case BOUNCE:
                handleBounceJetpack(player);
                break;
        }
    }

    private void handleVanillaJetpack(EntityPlayerSP player) {
        if (player.movementInput.jump) {
            // Set motion to fly up
            player.motionY = climbSpeed.getInput();
            
            // Prevent fall damage
            if (noFall.isToggled()) {
                player.fallDistance = 0;
            }
        }
    }

    private void handleFastJetpack(EntityPlayerSP player) {
        if (player.movementInput.jump) {
            // Faster flying
            player.motionY = climbSpeed.getInput() * 1.5f;
            
            // Add horizontal movement boost
            float speed = 0.1f * (float) climbSpeed.getInput();
            player.motionX *= (1.0f + speed);
            player.motionZ *= (1.0f + speed);
            
            if (noFall.isToggled()) {
                player.fallDistance = 0;
            }
        }
    }

    private void handleFreecamJetpack(EntityPlayerSP player) {
        // Full control flying
        if (player.movementInput.jump) {
            player.motionY = climbSpeed.getInput();
        } else if (player.movementInput.sneak) {
            player.motionY = -fallSpeed.getInput();
        } else {
            player.motionY = 0;
        }
        
        if (noFall.isToggled()) {
            player.fallDistance = 0;
        }
    }

    private void handleBounceJetpack(EntityPlayerSP player) {
        if (player.movementInput.jump) {
            // Bounce effect
            if (player.motionY < 0) {
                player.motionY = -player.motionY * 0.8f; // Bounce up
                player.motionY = Math.max(player.motionY, climbSpeed.getInput());
            } else {
                player.motionY = climbSpeed.getInput();
            }
            
            if (noFall.isToggled()) {
                player.fallDistance = 0;
            }
        }
    }

    @Override
    public void onEnable() {
        // Reset fall distance when enabling
        if (Utils.Player.isPlayerInGame()) {
            EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
            if (player != null && noFall.isToggled()) {
                player.fallDistance = 0;
            }
        }
    }

    @Override
    public void onDisable() {
        // Reset any modified values when disabling
        if (Utils.Player.isPlayerInGame()) {
            EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
            if (player != null) {
                // Let the game handle falling normally again
            }
        }
    }
}
