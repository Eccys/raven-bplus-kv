package keystrokesmod.client.module.modules.other;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.google.common.eventbus.Subscribe;

import keystrokesmod.client.event.impl.ForgeEvent;
import keystrokesmod.client.event.impl.PacketEvent;
import keystrokesmod.client.event.impl.TickEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.Module.ModuleCategory;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.module.setting.impl.DescriptionSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.Utils;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

public class FakeLag extends Module {
    
    public static DescriptionSetting desc;
    public static ComboSetting mode;
    public static TickSetting onlyOnAttack, onlyOnBreak, onlyOnPlace, debugMode;
    public static SliderSetting maxPackets, lagTime, releaseRate;
    
    private final ConcurrentLinkedQueue<Packet<?>> packetQueue = new ConcurrentLinkedQueue<>();
    private long lastReleaseTime = 0;
    private int packetsInQueue = 0;
    private long lagStartTime = 0;
    private boolean isLagging = false;
    private volatile boolean releasing = false;
    
    public enum LagMode {
        NORMAL, // Simple packet buffering
        BURST,  // Release packets in bursts
        SMOOTH,  // Smooth packet release
        SMART    // Smart packet management
    }

    public FakeLag() {
        super("FakeLag", ModuleCategory.other);
        this.registerSetting(desc = new DescriptionSetting("Delay packet sending"));
        this.registerSetting(mode = new ComboSetting("Mode", LagMode.NORMAL));
        
        // Conditions
        this.registerSetting(onlyOnAttack = new TickSetting("Only on Attack", false));
        this.registerSetting(onlyOnBreak = new TickSetting("Only on Break", false));
        this.registerSetting(onlyOnPlace = new TickSetting("Only on Place", false));
        this.registerSetting(debugMode = new TickSetting("Debug Mode", false));
        
        // Settings
        this.registerSetting(maxPackets = new SliderSetting("Max Packets", 50, 10, 200, 5));
        this.registerSetting(lagTime = new SliderSetting("Lag Time (ms)", 1000, 100, 5000, 100));
        this.registerSetting(releaseRate = new SliderSetting("Release Rate", 5, 1, 20, 1));
    }

    @Override
    public void onEnable() {
        packetQueue.clear();
        lastReleaseTime = 0;
        packetsInQueue = 0;
        lagStartTime = 0;
        isLagging = false;
        releasing = false;
    }

    @Override
    public void onDisable() {
        releaseAllPackets();
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        if (fe.getEvent() instanceof EntityJoinWorldEvent) {
            EntityJoinWorldEvent joinEvent = (EntityJoinWorldEvent) fe.getEvent();
            if (mc.thePlayer != null && joinEvent.entity == mc.thePlayer) {
                packetQueue.clear();
                packetsInQueue = 0;
                isLagging = false;
                lagStartTime = 0;
            }
        }
    }

    @Subscribe
    public void onPacketEvent(PacketEvent event) {
        if (releasing || !event.isOutgoing() || !Utils.Player.isPlayerInGame()) return;
        
        Packet<?> packet = event.getPacket();
        
        // Only handle specific packet types
        if (!shouldBufferPacket(packet)) return;
        
        // Check conditions
        if (!shouldBufferConditions(packet)) return;
        
        // Check queue limit: release oldest packets first, then enqueue this packet to preserve FIFO order
        if (packetsInQueue >= maxPackets.getInput()) {
            releasePackets(Math.max(1, (int) releaseRate.getInput()));
        }
        
        // Buffer the packet
        event.setCancelled(true);
        packetQueue.add(packet);
        packetsInQueue++;
        
        if (!isLagging) {
            isLagging = true;
            lagStartTime = System.currentTimeMillis();
        }
        
        if (debugMode.isToggled()) {
            Utils.Player.sendMessageToSelf("Buffered packet: " + packet.getClass().getSimpleName() + 
                                         " (Queue: " + packetsInQueue + ")");
        }
    }

    @Subscribe
    public void onTickEvent(TickEvent event) {
        if (!Utils.Player.isPlayerInGame()) {
            if (!packetQueue.isEmpty() || isLagging) {
                packetQueue.clear();
                packetsInQueue = 0;
                isLagging = false;
                lagStartTime = 0;
            }
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        LagMode currentMode = (LagMode) mode.getMode();
        
        switch (currentMode) {
            case NORMAL:
                handleNormalLag(currentTime);
                break;
            case BURST:
                handleBurstLag(currentTime);
                break;
            case SMOOTH:
                handleSmoothLag(currentTime);
                break;
            case SMART:
                handleSmartLag(currentTime);
                break;
        }
        
        if (debugMode.isToggled() && isLagging) {
            Utils.Player.sendMessageToSelf("Lagging: " + (currentTime - lagStartTime) + "ms, Queue: " + packetsInQueue);
        }
    }

    private boolean shouldBufferPacket(Packet<?> packet) {
        // Buffer movement packets and important action packets
        return packet instanceof C03PacketPlayer ||
               packet instanceof C0APacketAnimation ||
               packet instanceof C07PacketPlayerDigging ||
               packet instanceof C08PacketPlayerBlockPlacement;
    }

    private boolean shouldBufferConditions(Packet<?> packet) {
        if (!onlyOnAttack.isToggled() && !onlyOnBreak.isToggled() && !onlyOnPlace.isToggled()) {
            return true;
        }
        return (onlyOnAttack.isToggled() && packet instanceof C0APacketAnimation) ||
               (onlyOnBreak.isToggled() && packet instanceof C07PacketPlayerDigging) ||
               (onlyOnPlace.isToggled() && packet instanceof C08PacketPlayerBlockPlacement);
    }

    private void handleNormalLag(long currentTime) {
        if (isLagging && currentTime - lagStartTime >= lagTime.getInput()) {
            releasePackets((int) releaseRate.getInput());
            if (packetQueue.isEmpty()) {
                isLagging = false;
            }
        }
    }

    private void handleBurstLag(long currentTime) {
        if (isLagging && currentTime - lagStartTime >= lagTime.getInput()) {
            // Release all packets at once (burst)
            releaseAllPackets();
            isLagging = false;
        }
    }

    private void handleSmoothLag(long currentTime) {
        if (isLagging) {
            long timeSinceStart = currentTime - lagStartTime;
            long totalLagTime = Math.max(1L, (long) lagTime.getInput());
            
            if (timeSinceStart >= totalLagTime) {
                releaseAllPackets();
                isLagging = false;
            } else {
                long remainingTime = Math.max(50L, totalLagTime - timeSinceStart);
                int remainingTicks = Math.max(1, (int) (remainingTime / 50L));
                int packetsToRelease = Math.max(1, packetQueue.size() / remainingTicks);
                releasePackets(Math.min(packetsToRelease, (int) releaseRate.getInput()));
                if (packetQueue.isEmpty()) {
                    isLagging = false;
                }
            }
        }
    }

    private void handleSmartLag(long currentTime) {
        if (!isLagging) return;
        
        // Smart lag management based on player state
        boolean shouldRelease = false;
        int releaseCount = (int) releaseRate.getInput();
        
        // Release if player is taking damage or in danger
        if (mc.thePlayer.hurtTime > 0 || mc.thePlayer.fallDistance > 3.0f) {
            shouldRelease = true;
            releaseCount = packetsInQueue; // Release all immediately
        }
        // Release if lag time exceeded
        else if (currentTime - lagStartTime >= lagTime.getInput()) {
            shouldRelease = true;
        }
        // Release if queue is getting full
        else if (packetsInQueue >= maxPackets.getInput() * 0.8) {
            shouldRelease = true;
            releaseCount = (int) (packetsInQueue * 0.3); // Release 30%
        }
        
        if (shouldRelease) {
            releasePackets(releaseCount);
            if (packetQueue.isEmpty()) {
                isLagging = false;
            }
        }
    }

    private void releasePackets(int count) {
        if (packetQueue.isEmpty() || releasing) return;
        
        releasing = true;
        try {
            int released = 0;
            List<Packet<?>> toRelease = new ArrayList<>();
            
            while (released < count && !packetQueue.isEmpty()) {
                Packet<?> packet = packetQueue.poll();
                if (packet != null) {
                    toRelease.add(packet);
                    released++;
                }
            }
            
            // Send the packets
            if (mc.getNetHandler() != null) {
                for (Packet<?> packet : toRelease) {
                    mc.getNetHandler().addToSendQueue(packet);
                }
            }
            
            packetsInQueue = Math.max(0, packetsInQueue - released);
            
            if (debugMode.isToggled() && released > 0) {
                Utils.Player.sendMessageToSelf("Released " + released + " packets");
            }
        } finally {
            releasing = false;
        }
    }

    private void releaseAllPackets() {
        if (packetQueue.isEmpty() || releasing) return;
        
        releasing = true;
        try {
            List<Packet<?>> allPackets = new ArrayList<>(packetQueue);
            packetQueue.clear();
            
            if (mc.getNetHandler() != null) {
                for (Packet<?> packet : allPackets) {
                    mc.getNetHandler().addToSendQueue(packet);
                }
            }
            
            int released = allPackets.size();
            packetsInQueue = Math.max(0, packetsInQueue - released);
            
            if (debugMode.isToggled()) {
                Utils.Player.sendMessageToSelf("Released all " + released + " packets");
            }
        } finally {
            releasing = false;
        }
    }

    public int getQueuedPackets() {
        return packetsInQueue;
    }

    public boolean isLagging() {
        return isLagging;
    }

    public long getLagDuration() {
        return isLagging ? System.currentTimeMillis() - lagStartTime : 0;
    }
}
