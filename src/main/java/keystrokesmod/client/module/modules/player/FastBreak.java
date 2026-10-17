package keystrokesmod.client.module.modules.player;

import com.google.common.eventbus.Subscribe;

import keystrokesmod.client.event.impl.PacketEvent;
import keystrokesmod.client.event.impl.UpdateEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.Module.ModuleCategory;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.module.setting.impl.DescriptionSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.Utils;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public class FastBreak extends Module {
    
    public static DescriptionSetting desc;
    public static ComboSetting mode;
    public static TickSetting onlyCreative, onlySurvival, instantBreak, checkReach;
    public static SliderSetting breakSpeed, breakDelay, range;
    public static SliderSetting swingSpeed, packetDelay;
    
    private long lastBreakTime = 0;
    private long lastSwingTime = 0;
    private BlockPos currentBlock = null;
    private int breakStage = 0;
    private boolean isBreaking = false;
    private boolean sendingPacket = false;
    
    public enum BreakMode {
        INSTANT,        // Instant block breaking
        PACKET,         // Packet-based fast break
        TIMER,          // Timer-based fast break
        SWITCH,         // Switch-based fast break
        HYBRID          // Hybrid of multiple methods
    }

    public FastBreak() {
        super("FastBreak", ModuleCategory.player);
        this.registerSetting(desc = new DescriptionSetting("Break blocks faster"));
        this.registerSetting(mode = new ComboSetting("Mode", BreakMode.PACKET));
        
        // Conditions
        this.registerSetting(onlyCreative = new TickSetting("Only Creative", false));
        this.registerSetting(onlySurvival = new TickSetting("Only Survival", true));
        this.registerSetting(instantBreak = new TickSetting("Instant Break", false));
        this.registerSetting(checkReach = new TickSetting("Check Reach", true));
        
        // Speed settings
        this.registerSetting(breakSpeed = new SliderSetting("Break Speed", 1.5, 1.0, 5.0, 0.1));
        this.registerSetting(breakDelay = new SliderSetting("Break Delay (ms)", 50, 0, 500, 10));
        this.registerSetting(range = new SliderSetting("Range", 6.0, 3.0, 10.0, 0.5));
        
        // Advanced settings
        this.registerSetting(swingSpeed = new SliderSetting("Swing Speed", 1.0, 0.5, 3.0, 0.1));
        this.registerSetting(packetDelay = new SliderSetting("Packet Delay (ms)", 25, 0, 200, 5));
    }

    @Override
    public void onEnable() {
        lastBreakTime = 0;
        lastSwingTime = 0;
        currentBlock = null;
        breakStage = 0;
        isBreaking = false;
        sendingPacket = false;
    }

    @Subscribe
    public void onUpdateEvent(UpdateEvent event) {
        if (!Utils.Player.isPlayerInGame()) return;
        
        // Check game mode conditions
        if (onlyCreative.isToggled() && !mc.thePlayer.capabilities.isCreativeMode) return;
        if (onlySurvival.isToggled() && mc.thePlayer.capabilities.isCreativeMode) return;
        
        BreakMode currentMode = (BreakMode) mode.getMode();
        
        switch (currentMode) {
            case INSTANT:
                handleInstantBreak();
                break;
            case PACKET:
                handlePacketBreak();
                break;
            case TIMER:
                handleTimerBreak();
                break;
            case SWITCH:
                handleSwitchBreak();
                break;
            case HYBRID:
                handleHybridBreak();
                break;
        }
        
        // Handle swinging
        handleSwinging();
    }

    @Subscribe
    public void onPacketEvent(PacketEvent event) {
        if (!Utils.Player.isPlayerInGame() || sendingPacket) return;
        
        if (event.getPacket() instanceof C07PacketPlayerDigging) {
            C07PacketPlayerDigging packet = (C07PacketPlayerDigging) event.getPacket();
            
            // Track current breaking block
            if (packet.getStatus() == C07PacketPlayerDigging.Action.START_DESTROY_BLOCK) {
                currentBlock = packet.getPosition();
                isBreaking = true;
                breakStage = 0;
            } else if (packet.getStatus() == C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK
                    || packet.getStatus() == C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK) {
                currentBlock = null;
                isBreaking = false;
                breakStage = 0;
            }
        }
    }

    private void sendDiggingPacket(C07PacketPlayerDigging.Action action, BlockPos pos, EnumFacing facing) {
        sendingPacket = true;
        try {
            mc.getNetHandler().addToSendQueue(new C07PacketPlayerDigging(action, pos, facing));
        } finally {
            sendingPacket = false;
        }
    }

    private void handleInstantBreak() {
        if (!isBreaking || currentBlock == null) return;
        
        if (checkReach.isToggled() && !isInRange(currentBlock)) return;
        
        long currentTime = System.currentTimeMillis();
        
        if (currentTime - lastBreakTime >= breakDelay.getInput()) {
            // Send instant break packet
            mc.playerController.onPlayerDestroyBlock(currentBlock, EnumFacing.DOWN);
            
            // Send animation
            mc.thePlayer.swingItem();
            
            lastBreakTime = currentTime;
        }
    }

    private void handlePacketBreak() {
        if (!isBreaking || currentBlock == null) return;
        
        if (checkReach.isToggled() && !isInRange(currentBlock)) return;
        
        long currentTime = System.currentTimeMillis();
        
        if (currentTime - lastBreakTime >= packetDelay.getInput()) {
            // Send damage packets without re-entering onPacketEvent
            sendDiggingPacket(C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, currentBlock, EnumFacing.DOWN);
            sendDiggingPacket(C07PacketPlayerDigging.Action.START_DESTROY_BLOCK, currentBlock, EnumFacing.DOWN);
            
            breakStage++;
            
            // Check if block should break
            if (breakStage >= (int) (10.0f / breakSpeed.getInput())) {
                mc.playerController.onPlayerDestroyBlock(currentBlock, EnumFacing.DOWN);
                isBreaking = false;
                currentBlock = null;
                breakStage = 0;
            }
            
            lastBreakTime = currentTime;
        }
    }

    private void handleTimerBreak() {
        if (!isBreaking || currentBlock == null) return;
        
        if (checkReach.isToggled() && !isInRange(currentBlock)) return;
        
        // Speed up timer for faster breaking
        Utils.Client.getTimer().timerSpeed = (float) breakSpeed.getInput();
        
        // Send swing packets faster
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSwingTime >= (1000.0f / (20.0f * swingSpeed.getInput()))) {
            mc.thePlayer.swingItem();
            lastSwingTime = currentTime;
        }
    }

    private void handleSwitchBreak() {
        if (!isBreaking || currentBlock == null) return;
        
        if (checkReach.isToggled() && !isInRange(currentBlock)) return;
        
        long currentTime = System.currentTimeMillis();
        
        if (currentTime - lastBreakTime >= breakDelay.getInput()) {
            // Switch between start and stop actions rapidly without cancelling our own break state
            if (breakStage % 2 == 0) {
                sendDiggingPacket(C07PacketPlayerDigging.Action.START_DESTROY_BLOCK, currentBlock, EnumFacing.DOWN);
            } else {
                sendDiggingPacket(C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK, currentBlock, EnumFacing.DOWN);
            }
            
            breakStage++;
            
            // Break after certain stages
            if (breakStage >= (int) (10.0f / breakSpeed.getInput())) {
                mc.playerController.onPlayerDestroyBlock(currentBlock, EnumFacing.DOWN);
                isBreaking = false;
                currentBlock = null;
                breakStage = 0;
            }
            
            lastBreakTime = currentTime;
        }
    }

    private void handleHybridBreak() {
        if (!isBreaking || currentBlock == null) return;
        
        if (checkReach.isToggled() && !isInRange(currentBlock)) return;
        
        long currentTime = System.currentTimeMillis();
        
        // Combine multiple methods
        if (currentTime - lastBreakTime >= packetDelay.getInput()) {
            // Use packet method
            handlePacketBreak();
        }
        
        // Also use timer boost
        if (Utils.Client.getTimer().timerSpeed < breakSpeed.getInput()) {
            Utils.Client.getTimer().timerSpeed = Math.min(Utils.Client.getTimer().timerSpeed + 0.1f, (float) breakSpeed.getInput());
        }
        
        // Send swing packets
        handleSwinging();
    }

    private void handleSwinging() {
        if (!isBreaking) return;
        
        long currentTime = System.currentTimeMillis();
        long swingInterval = (long) (1000.0f / (20.0f * swingSpeed.getInput()));
        
        if (currentTime - lastSwingTime >= swingInterval) {
            mc.thePlayer.swingItem();
            lastSwingTime = currentTime;
        }
    }

    private boolean isInRange(BlockPos pos) {
        if (pos == null || mc.thePlayer == null) return false;
        
        double distance = mc.thePlayer.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        double maxRange = range.getInput() * range.getInput();
        
        return distance <= maxRange;
    }

    @Override
    public void onDisable() {
        // Reset timer safely
        Utils.Client.resetTimer();
        
        // Reset breaking state
        isBreaking = false;
        currentBlock = null;
        breakStage = 0;
        sendingPacket = false;
    }

    public boolean isBreakingBlock() {
        return isBreaking;
    }

    public BlockPos getCurrentBlock() {
        return currentBlock;
    }

    public BreakMode getCurrentMode() {
        return (BreakMode) mode.getMode();
    }

    public double getBreakProgress() {
        if (currentBlock == null || !isBreaking) return 0;
        
        float blockHardness = mc.theWorld.getBlockState(currentBlock).getBlock().getBlockHardness(mc.theWorld, currentBlock);
        if (blockHardness <= 0) return 1.0;
        
        // Calculate progress based on break speed and stage
        return Math.min(1.0, (breakStage * breakSpeed.getInput()) / 10.0);
    }
}
