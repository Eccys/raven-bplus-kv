package keystrokesmod.client.module.modules.combat;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.eventbus.Subscribe;

import keystrokesmod.client.event.impl.PacketEvent;
import keystrokesmod.client.event.impl.UpdateEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.Module.ModuleCategory;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.Utils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.*;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

public class Backtrack extends Module {
    
    // Mode enum
    public enum BacktrackMode {
        Legacy, Modern
    }
    
    // Legacy position enum
    public enum LegacyPosition {
        ClientPos, ServerPos
    }
    
    // Style enum
    public enum BacktrackStyle {
        Pulse, Smooth
    }
    
    // ESP mode enum
    public enum ESPMode {
        None, Box, Model, Wireframe
    }
    
    // Mode settings
    private ComboSetting<BacktrackMode> mode;
    private SliderSetting nextBacktrackDelay;
    private SliderSetting maxDelay;
    private SliderSetting minDelay;
    
    // Legacy settings
    private ComboSetting<LegacyPosition> legacyPos;
    
    // Modern settings
    private ComboSetting<BacktrackStyle> style;
    private SliderSetting distance;
    private TickSetting smart;
    
    // ESP settings
    private ComboSetting<ESPMode> espMode;
    private SliderSetting wireframeWidth;
    private SliderSetting espColorR;
    private SliderSetting espColorG;
    private SliderSetting espColorB;
    
    // Data structures
    private final Queue<QueueData> packetQueue = new LinkedList<>();
    private final Queue<Vec3Data> positions = new LinkedList<>();
    private final Map<EntityLivingBase, List<Vec3>> backtrackedPlayer = new ConcurrentHashMap<>();
    
    private EntityLivingBase target;
    private long globalTimer = System.currentTimeMillis();
    private boolean shouldRender = true;
    private boolean ignoreWholeTick = false;
    private long delayForNextBacktrack = 0L;
    private long modernDelay = 80L;
    
    public Backtrack() {
        super("Backtrack", ModuleCategory.combat);
        
        // Main mode
        this.registerSetting(mode = new ComboSetting<>("Mode", BacktrackMode.Modern));
        this.registerSetting(nextBacktrackDelay = new SliderSetting("Next Backtrack Delay", 2000, 0, 10000, 100));
        this.registerSetting(maxDelay = new SliderSetting("Max Delay", 200, 0, 2000, 50));
        this.registerSetting(minDelay = new SliderSetting("Min Delay", 100, 0, 2000, 50));
        
        // Legacy settings
        this.registerSetting(legacyPos = new ComboSetting<>("Caching mode", LegacyPosition.ClientPos));
        
        // Modern settings
        this.registerSetting(style = new ComboSetting<>("Style", BacktrackStyle.Smooth));
        this.registerSetting(distance = new SliderSetting("Distance", 3.5, 1.0, 8.0, 0.1));
        this.registerSetting(smart = new TickSetting("Smart", true));
        
        // ESP settings
        this.registerSetting(espMode = new ComboSetting<>("ESP-Mode", ESPMode.Box));
        this.registerSetting(wireframeWidth = new SliderSetting("Wireframe Width", 1.5, 0.5, 5.0, 0.1));
        this.registerSetting(espColorR = new SliderSetting("ESP Color R", 255, 0, 255, 1));
        this.registerSetting(espColorG = new SliderSetting("ESP Color G", 0, 0, 255, 1));
        this.registerSetting(espColorB = new SliderSetting("ESP Color B", 0, 0, 255, 1));
        
        modernDelay = getRandomDelay((long)minDelay.getInput(), (long)maxDelay.getInput());
    }
    
    @Override
    public void onEnable() {
        clearPackets();
        backtrackedPlayer.clear();
        target = null;
        globalTimer = System.currentTimeMillis();
        ignoreWholeTick = false;
        delayForNextBacktrack = 0L;
        modernDelay = getRandomDelay((long)minDelay.getInput(), (long)maxDelay.getInput());
    }
    
    @Override
    public void onDisable() {
        clearPackets();
        backtrackedPlayer.clear();
        target = null;
    }
    
    private void clearPackets() {
        packetQueue.clear();
        positions.clear();
    }
    
    private long getRandomDelay(long min, long max) {
        long low = Math.min(min, max);
        long high = Math.max(min, max);
        return low + (long)(Math.random() * (high - low));
    }
    
    private long getSupposedDelay() {
        return mode.getMode() == BacktrackMode.Modern ? modernDelay : (long)maxDelay.getInput();
    }
    
    @Subscribe
    public void onPacketSend(PacketEvent event) {
        if (event.getPacket() instanceof C03PacketPlayer) {
            C03PacketPlayer packet = (C03PacketPlayer) event.getPacket();
            if (!packet.isMoving() && !packet.getRotating()) {
                return;
            }
            
            if (mode.getMode() == BacktrackMode.Legacy) {
                handleLegacyMode(packet);
            } else {
                handleModernMode(packet);
            }
        }
    }
    
    private void handleLegacyMode(C03PacketPlayer packet) {
        if (mc.thePlayer != null && target != null) {
            if (legacyPos.getMode() == LegacyPosition.ClientPos) {
                packetQueue.add(new QueueData(packet, System.currentTimeMillis()));
            } else {
                // Server position handling
                Vec3 serverPos = new Vec3(target.posX, target.posY, target.posZ);
                positions.add(new Vec3Data(serverPos, System.currentTimeMillis()));
            }
        }
    }
    
    private void handleModernMode(C03PacketPlayer packet) {
        if (mc.thePlayer != null && target != null) {
            long currentTime = System.currentTimeMillis();
            
            if (currentTime - globalTimer >= getSupposedDelay()) {
                globalTimer = currentTime;
                
                if (mode.getMode() == BacktrackMode.Modern) {
                    modernDelay = getRandomDelay((long)minDelay.getInput(), (long)maxDelay.getInput());
                }
                
                packetQueue.add(new QueueData(packet, currentTime));
                positions.add(new Vec3Data(new Vec3(target.posX, target.posY, target.posZ), currentTime));
            }
        }
    }
    
    @Subscribe
    public void onPacketReceive(PacketEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        Packet<?> packet = event.getPacket();
        
        if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport teleport = (S18PacketEntityTeleport) packet;
            Entity entity = mc.theWorld.getEntityByID(teleport.getEntityId());
            
            if (entity instanceof EntityLivingBase && entity == target) {
                handleTeleport((EntityLivingBase) entity);
            }
        } else if (packet instanceof S14PacketEntity) {
            S14PacketEntity entityPacket = (S14PacketEntity) packet;
            Entity entity = entityPacket.getEntity(mc.theWorld);
            if (entity instanceof EntityLivingBase && entity == target) {
                handleEntityMove((EntityLivingBase) entity);
            }
        }
    }
    
    private void handleTeleport(EntityLivingBase entity) {
        Vec3 newPos = new Vec3(entity.posX, entity.posY, entity.posZ);
        positions.add(new Vec3Data(newPos, System.currentTimeMillis()));
        
        if (!backtrackedPlayer.containsKey(entity)) {
            backtrackedPlayer.put(entity, new ArrayList<>());
        }
        
        List<Vec3> positions = backtrackedPlayer.get(entity);
        positions.add(newPos);
        
        // Limit stored positions
        if (positions.size() > 50) {
            positions.remove(0);
        }
    }
    
    private void handleEntityMove(EntityLivingBase entity) {
        Vec3 newPos = new Vec3(entity.posX, entity.posY, entity.posZ);
        
        if (!backtrackedPlayer.containsKey(entity)) {
            backtrackedPlayer.put(entity, new ArrayList<>());
        }
        
        List<Vec3> positions = backtrackedPlayer.get(entity);
        positions.add(newPos);
        
        // Limit stored positions
        if (positions.size() > 50) {
            positions.remove(0);
        }
    }
    
    @Subscribe
    public void onUpdate(UpdateEvent event) {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        
        // Update target
        updateTarget();
        
        // Clean old data
        cleanOldData();
        
        // Process packet queue
        processPacketQueue();
    }
    
    private void updateTarget() {
        target = null;
        double closestDistance = Double.MAX_VALUE;
        
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer || !isValidTarget(player)) {
                continue;
            }
            
            double distance = mc.thePlayer.getDistanceToEntity(player);
            if (distance < closestDistance && distance <= 8.0) {
                closestDistance = distance;
                target = player;
            }
        }
    }
    
    private boolean isValidTarget(EntityPlayer player) {
        if (player.isDead || player.getHealth() <= 0) {
            return false;
        }
        
        // Add any additional target validation logic here
        return true;
    }
    
    private void cleanOldData() {
        long currentTime = System.currentTimeMillis();
        long maxAge = 5000; // 5 seconds
        
        // Clean packet queue
        packetQueue.removeIf(data -> currentTime - data.timestamp > maxAge);
        
        // Clean positions
        positions.removeIf(data -> currentTime - data.timestamp > maxAge);
        
        // Clean backtracked positions
        for (List<Vec3> positions : backtrackedPlayer.values()) {
            positions.removeIf(pos -> {
                // This is a simplified check - in reality you'd need to track timestamps
                return positions.size() > 20;
            });
        }
    }
    
    private void processPacketQueue() {
        if (packetQueue.isEmpty()) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        Iterator<QueueData> iterator = packetQueue.iterator();
        
        while (iterator.hasNext()) {
            QueueData data = iterator.next();
            
            if (currentTime - data.timestamp >= getSupposedDelay()) {
                // Send the packet
                mc.thePlayer.sendQueue.addToSendQueue(data.packet);
                iterator.remove();
            }
        }
    }
    
    // Helper classes
    private static class QueueData {
        public final Packet<?> packet;
        public final long timestamp;
        
        public QueueData(Packet<?> packet, long timestamp) {
            this.packet = packet;
            this.timestamp = timestamp;
        }
    }
    
    private static class Vec3Data {
        public final Vec3 position;
        public final long timestamp;
        
        public Vec3Data(Vec3 position, long timestamp) {
            this.position = position;
            this.timestamp = timestamp;
        }
    }
}
