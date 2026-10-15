package keystrokesmod.client.module.modules.player;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.google.common.eventbus.Subscribe;

import keystrokesmod.client.event.impl.PacketEvent;
import keystrokesmod.client.event.impl.TickEvent;
import keystrokesmod.client.event.impl.Render2DEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.Module.ModuleCategory;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.module.setting.impl.DescriptionSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.Utils;
import keystrokesmod.client.utils.font.FontUtil;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;

public class Blink extends Module {

    private final ConcurrentLinkedQueue<Packet<?>> packets = new ConcurrentLinkedQueue<>();
    
    public static DescriptionSetting desc;
    public static ComboSetting mode;
    public static TickSetting onlyMovement, onlyAttacks, autoRelease, showInfo;
    public static SliderSetting maxPackets, releaseDelay, packetLimit;
    
    private long lastReleaseTime = 0;
    private int packetCount = 0;
    private long startTime = 0;
    private boolean releasing = false;
    
    public enum BlinkMode {
        NORMAL,     // Buffer all packets
        MOVEMENT,   // Only movement packets
        COMBAT,     // Only combat packets
        SMART       // Smart packet management
    }

    public Blink() {
        super("Blink", ModuleCategory.player);
        this.registerSetting(desc = new DescriptionSetting("Delay packet sending"));
        this.registerSetting(mode = new ComboSetting("Mode", BlinkMode.NORMAL));
        this.registerSetting(onlyMovement = new TickSetting("Only Movement", false));
        this.registerSetting(onlyAttacks = new TickSetting("Only Attacks", false));
        this.registerSetting(autoRelease = new TickSetting("Auto Release", false));
        this.registerSetting(showInfo = new TickSetting("Show Info", true));
        this.registerSetting(maxPackets = new SliderSetting("Max Packets", 100, 10, 500, 10));
        this.registerSetting(releaseDelay = new SliderSetting("Release Delay (ms)", 100, 0, 1000, 50));
        this.registerSetting(packetLimit = new SliderSetting("Packet Limit", 200, 50, 1000, 50));
    }
    
    @Subscribe
    public void packetEvent(PacketEvent event) {
        if (releasing || !event.isOutgoing() || !Utils.Player.isPlayerInGame()) return;
        
        Packet<?> packet = event.getPacket();
        
        if (!shouldBufferPacket(packet)) return;
        
        if (packetCount >= packetLimit.getInput()) {
            releasePackets();
            return;
        }
        
        event.setCancelled(true);
        packets.add(packet);
        packetCount++;
        
        if (autoRelease.isToggled() && packetCount >= maxPackets.getInput()) {
            releasePackets();
        }
    }
    
    @Subscribe
    public void onTickEvent(TickEvent event) {
        if (!Utils.Player.isPlayerInGame()) return;
        
        if (autoRelease.isToggled()) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastReleaseTime >= releaseDelay.getInput() && !packets.isEmpty()) {
                releasePackets();
            }
        }
    }
    
    @Subscribe
    public void onRender2DEvent(Render2DEvent event) {
        if (!Utils.Player.isPlayerInGame() || !showInfo.isToggled()) return;
        
        ScaledResolution sr = new ScaledResolution(mc);
        int x = 5;
        int y = sr.getScaledHeight() - 30;
        
        String info = "Blink: " + packetCount + " packets";
        if (startTime > 0) {
            long duration = System.currentTimeMillis() - startTime;
            info += " (" + (duration / 1000) + "s)";
        }
        
        FontUtil.normal.drawString(info, x, y, 0xFFFF00);
        
        // Warning if too many packets
        if (packetCount > packetLimit.getInput() * 0.8) {
            String warning = "WARNING: High packet count!";
            FontUtil.normal.drawString(warning, x, y + 12, 0xFF0000);
        }
    }
    
    private boolean shouldBufferPacket(Packet<?> packet) {
        BlinkMode currentMode = (BlinkMode) mode.getMode();
        
        switch (currentMode) {
            case NORMAL:
                return true;
            case MOVEMENT:
                return packet instanceof C03PacketPlayer;
            case COMBAT:
                return packet instanceof C0APacketAnimation || 
                       packet instanceof C07PacketPlayerDigging ||
                       packet instanceof C08PacketPlayerBlockPlacement;
            case SMART:
                return isImportantPacket(packet);
            default:
                return true;
        }
    }
    
    private boolean isImportantPacket(Packet<?> packet) {
        // Smart mode - buffer important packets that affect gameplay
        // Always buffer movement packets in smart mode
        if (packet instanceof C03PacketPlayer) {
            return true;
        }
        
        // Always buffer combat actions
        return packet instanceof C0APacketAnimation || 
               packet instanceof C07PacketPlayerDigging ||
               packet instanceof C08PacketPlayerBlockPlacement;
    }
    
    private void releasePackets() {
        if (packets.isEmpty() || releasing) return;
        
        releasing = true;
        try {
            // Release all packets
            while (!packets.isEmpty()) {
                Packet<?> packet = packets.poll();
                if (packet != null && mc.getNetHandler() != null) {
                    mc.getNetHandler().addToSendQueue(packet);
                }
            }
            
            packetCount = 0;
            lastReleaseTime = System.currentTimeMillis();
        } finally {
            releasing = false;
        }
    }
    
    @Override
    public void onEnable() {
        packets.clear();
        packetCount = 0;
        lastReleaseTime = 0;
        startTime = System.currentTimeMillis();
    }
    
    @Override
    public void onDisable() {
        releasePackets();
        startTime = 0;
    }
    
    public int getPacketCount() {
        return packetCount;
    }
    
    public long getBlinkDuration() {
        return startTime > 0 ? System.currentTimeMillis() - startTime : 0;
    }
}
