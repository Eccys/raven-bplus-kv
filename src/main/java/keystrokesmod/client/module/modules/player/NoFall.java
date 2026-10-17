package keystrokesmod.client.module.modules.player;

import com.google.common.eventbus.Subscribe;
import keystrokesmod.client.event.impl.PacketEvent;
import keystrokesmod.client.event.impl.TickEvent;
import keystrokesmod.client.event.impl.UpdateEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.module.setting.impl.DescriptionSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.Utils;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition;
import net.minecraft.util.MathHelper;

public class NoFall extends Module {
    public static DescriptionSetting warning, desc;
    public static ComboSetting mode;
    public static TickSetting onlyOnDamage, smartMode, debugMode;
    public static SliderSetting fallDistance, minHeight, maxDamage, packetDelay;
    
    int ticks;
    double dist;
    boolean spoofing;
    long lastPacketTime = 0;
    boolean lastOnGround;
    double lastY;

    public NoFall() {
        super("NoFall", ModuleCategory.player);

        this.registerSetting(desc = new DescriptionSetting("Prevent fall damage"));
        this.registerSetting(warning = new DescriptionSetting("Some modes may flag on certain servers"));
        this.registerSetting(mode = new ComboSetting("Mode", Mode.Spoof));
        
        // Advanced settings
        this.registerSetting(onlyOnDamage = new TickSetting("Only on Damage", false));
        this.registerSetting(smartMode = new TickSetting("Smart Mode", true));
        this.registerSetting(debugMode = new TickSetting("Debug Mode", false));
        
        // Thresholds
        this.registerSetting(fallDistance = new SliderSetting("Fall Distance", 2.5, 1.0, 10.0, 0.5));
        this.registerSetting(minHeight = new SliderSetting("Min Height", 3.0, 1.0, 20.0, 1.0));
        this.registerSetting(maxDamage = new SliderSetting("Max Damage", 4.0, 1.0, 20.0, 1.0));
        this.registerSetting(packetDelay = new SliderSetting("Packet Delay", 100, 0, 500, 10));
    }

    @Override
    public void onEnable() {
        ticks = 0;
        dist = 0;
        spoofing = false;
        lastPacketTime = 0;
        lastOnGround = mc.thePlayer != null && mc.thePlayer.onGround;
        lastY = mc.thePlayer != null ? mc.thePlayer.posY : 0;
    }

    @Override
    public void onDisable() {
        ticks = 0;
        dist = 0;
        spoofing = false;
        lastPacketTime = 0;
    }

    @Subscribe
    public void onTick(TickEvent e) {
        if (!Utils.Player.isPlayerInGame()) return;
        
        switch ((Mode) mode.getMode()) {
            case Spoof:
                handleSpoofMode();
                break;
            case HypixelSpoof:
                handleHypixelSpoofMode();
                break;
            case Verus:
                handleVerusMode();
                break;
            case Packet:
                handlePacketMode();
                break;
            case Matrix:
                handleMatrixMode();
                break;
            case Spartan:
                handleSpartanMode();
                break;
            case AAC:
                handleAACMode();
                break;
            case Vulcan:
                handleVulcanMode();
                break;
        }
        
        if (debugMode.isToggled()) {
            debugInfo();
        }
    }

    @Subscribe
    public void onUpdateEvent(UpdateEvent e) {
        if (!Utils.Player.isPlayerInGame()) return;
        
        // Track position changes for smart mode
        if (smartMode.isToggled()) {
            handleSmartMode();
        }
    }

    @Subscribe
    public void onPacketEvent(PacketEvent event) {
        if (!Utils.Player.isPlayerInGame() || !event.isOutgoing()) return;
        
        if (event.getPacket() instanceof C03PacketPlayer && (Mode) mode.getMode() == Mode.Packet) {
            C03PacketPlayer packet = (C03PacketPlayer) event.getPacket();
            
            // Cancel non-ground packets and send a ground packet instead without recursing on our own ground packet
            if (!packet.isOnGround() && shouldModifyPacket()) {
                event.setCancelled(true);
                sendGroundPacket();
            }
        }
    }

    private void handleSpoofMode() {
        if (mc.thePlayer.fallDistance > fallDistance.getInput()) {
            if (!onlyOnDamage.isToggled() || wouldTakeDamage()) {
                mc.thePlayer.onGround = true;
            }
        }
    }

    private void handleHypixelSpoofMode() {
        if (mc.thePlayer.onGround) {
            ticks = 0;
            dist = 0;
            spoofing = false;
        } else {
            if (mc.thePlayer.fallDistance > fallDistance.getInput()) {
                if (spoofing) {
                    ticks++;
                    mc.thePlayer.onGround = true;

                    if (ticks >= 2) {
                        spoofing = false;
                        ticks = 0;
                        dist = mc.thePlayer.fallDistance;
                    }
                } else {
                    if (mc.thePlayer.fallDistance - dist > 2) {
                        spoofing = true;
                    }
                }
            }
        }
    }

    private void handleVerusMode() {
        if (mc.thePlayer.onGround) {
            dist = 0;
            spoofing = false;
        } else {
            if (mc.thePlayer.fallDistance > fallDistance.getInput()) {
                if (spoofing) {
                    mc.thePlayer.onGround = true;
                    mc.thePlayer.motionY = 0;
                    spoofing = false;
                    dist = mc.thePlayer.fallDistance;
                } else {
                    if (mc.thePlayer.fallDistance - dist > 2) {
                        spoofing = true;
                    }
                }
            }
        }
    }

    private void handlePacketMode() {
        // Packet mode is handled in onPacketEvent
        if (mc.thePlayer.fallDistance > fallDistance.getInput()) {
            if (!onlyOnDamage.isToggled() || wouldTakeDamage()) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastPacketTime > packetDelay.getInput()) {
                    sendGroundPacket();
                    lastPacketTime = currentTime;
                }
            }
        }
    }

    private void handleMatrixMode() {
        if (mc.thePlayer.onGround) {
            dist = 0;
            spoofing = false;
        } else {
            if (mc.thePlayer.fallDistance > 3.0) {
                if (!spoofing) {
                    spoofing = true;
                    mc.thePlayer.motionY = Math.max(mc.thePlayer.motionY, -0.5);
                }
                
                if (spoofing && mc.thePlayer.fallDistance > 5.0) {
                    mc.thePlayer.onGround = true;
                    spoofing = false;
                    dist = mc.thePlayer.fallDistance;
                }
            }
        }
    }

    private void handleSpartanMode() {
        if (mc.thePlayer.fallDistance > 2.5) {
            if (mc.thePlayer.ticksExisted % 3 == 0) { // Every 3 ticks
                mc.thePlayer.onGround = true;
            }
        }
    }

    private void handleAACMode() {
        if (mc.thePlayer.fallDistance > 3.0) {
            if (!spoofing) {
                spoofing = true;
            }
            
            if (spoofing) {
                mc.thePlayer.motionY = Math.max(mc.thePlayer.motionY, -0.3);
                
                if (mc.thePlayer.fallDistance > 8.0) {
                    mc.thePlayer.onGround = true;
                    spoofing = false;
                }
            }
        } else if (mc.thePlayer.onGround) {
            spoofing = false;
        }
    }

    private void handleVulcanMode() {
        if (mc.thePlayer.onGround) {
            dist = 0;
            spoofing = false;
        } else {
            if (mc.thePlayer.fallDistance > 2.0) {
                if (!spoofing) {
                    spoofing = true;
                }
                
                if (spoofing) {
                    // Smooth motion reduction
                    mc.thePlayer.motionY *= 0.8;
                    
                    if (mc.thePlayer.fallDistance - dist > 3.0) {
                        mc.thePlayer.onGround = true;
                        spoofing = false;
                        dist = mc.thePlayer.fallDistance;
                    }
                }
            }
        }
    }

    private void handleSmartMode() {
        double currentY = mc.thePlayer.posY;
        boolean currentOnGround = mc.thePlayer.onGround;
        
        // Detect when we're falling
        if (!currentOnGround && lastOnGround && currentY < lastY) {
            // Just started falling
        }
        
        // Predict landing
        if (!currentOnGround && mc.thePlayer.motionY < 0) {
            double predictedDamage = predictFallDamage();
            
            if (predictedDamage > maxDamage.getInput()) {
                // Would take too much damage, activate protection
                activateNoFall();
            }
        }
        
        lastOnGround = currentOnGround;
        lastY = currentY;
    }

    private void activateNoFall() {
        switch ((Mode) mode.getMode()) {
            case Spoof:
            case HypixelSpoof:
            case Verus:
                mc.thePlayer.onGround = true;
                break;
            case Packet:
                sendGroundPacket();
                break;
            default:
                mc.thePlayer.onGround = true;
                break;
        }
    }

    private void sendGroundPacket() {
        C04PacketPlayerPosition packet = new C04PacketPlayerPosition(
            mc.thePlayer.posX,
            mc.thePlayer.posY,
            mc.thePlayer.posZ,
            true
        );
        mc.thePlayer.sendQueue.addToSendQueue(packet);
    }

    private boolean wouldTakeDamage() {
        double predictedDamage = predictFallDamage();
        return predictedDamage > 0;
    }

    private double predictFallDamage() {
        if (mc.thePlayer == null) return 0;
        return calculateFallDamage(mc.thePlayer.fallDistance, mc.thePlayer.onGround, mc.thePlayer.getTotalArmorValue());
    }

    public static double calculateFallDamage(double fallDistance, boolean onGround, int totalArmorValue) {
        if (onGround || fallDistance <= 3.0) return 0;

        double damage = (fallDistance - 3.0) * 0.5;
        if (totalArmorValue > 0) {
            damage *= (1.0 - Math.min(20, totalArmorValue) * 0.04);
        }

        return Math.max(0, damage);
    }

    private boolean shouldModifyPacket() {
        return mc.thePlayer.fallDistance > fallDistance.getInput() &&
               (!onlyOnDamage.isToggled() || wouldTakeDamage());
    }

    private void debugInfo() {
        if (mc.thePlayer != null) {
            String info = String.format(
                "FallDist: %.2f, OnGround: %s, MotionY: %.3f, Spoofing: %s",
                mc.thePlayer.fallDistance,
                mc.thePlayer.onGround,
                mc.thePlayer.motionY,
                spoofing
            );
            
            Utils.Player.sendMessageToSelf(info);
        }
    }

    public enum Mode {
        Spoof, HypixelSpoof, Verus, Packet, Matrix, Spartan, AAC, Vulcan
    }
}
