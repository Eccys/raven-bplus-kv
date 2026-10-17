package keystrokesmod.client.module.modules.combat;

import com.google.common.eventbus.Subscribe;
import keystrokesmod.client.event.impl.ForgeEvent;
import keystrokesmod.client.event.impl.UpdateEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.Utils;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;

public class Clutch extends Module {
    public static TickSetting onlySword;
    private boolean isBlocking = false;
    private long lastHitTime = 0;
    private long lastPlaceTime = 0;
    private float lastHealth = 20f;

    public Clutch() {
        super("Clutch", ModuleCategory.combat);
        this.registerSetting(onlySword = new TickSetting("Only sword", true));
    }

    @Override
    public void onEnable() {
        lastHealth = mc != null && mc.thePlayer != null ? mc.thePlayer.getHealth() : 20f;
        lastHitTime = 0;
        isBlocking = false;
    }

    @Subscribe
    public void onLivingUpdate(ForgeEvent fe) {
        if (!(fe.getEvent() instanceof LivingEvent.LivingUpdateEvent)) return;
        LivingEvent.LivingUpdateEvent e = (LivingEvent.LivingUpdateEvent) fe.getEvent();
        if (e.entity != mc.thePlayer || mc.thePlayer == null) return;

        float health = mc.thePlayer.getHealth();
        if (health < lastHealth) {
            lastHitTime = System.currentTimeMillis();
        }
        lastHealth = health;
    }

    @Subscribe
    public void onUpdate(UpdateEvent e) {
        if (mc.thePlayer == null) return;

        if (shouldClutch()) {
            holdBlock();
            placeBlockUnder();
        } else {
            releaseBlock();
        }
    }

    private boolean shouldClutch() {
        if (!Utils.Player.isPlayerInGame() || !mc.thePlayer.isEntityAlive()) return false;

        long timeSinceHit = System.currentTimeMillis() - lastHitTime;
        if (timeSinceHit > 2000) return false;

        if (mc.thePlayer.onGround) return false;
        if (mc.thePlayer.motionY >= -0.05) return false;

        return true;
    }

    private void holdBlock() {
        if (onlySword.isToggled() && !Utils.Player.isPlayerHoldingSword()) {
            releaseBlock();
            return;
        }
        if (!isBlocking) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
            isBlocking = true;
        }
    }

    private void releaseBlock() {
        if (isBlocking) {
            if (mc != null && mc.gameSettings != null) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
            }
            isBlocking = false;
        }
    }

    private void placeBlockUnder() {
        if (System.currentTimeMillis() - lastPlaceTime < 100) return;

        int slot = Utils.Player.getBlockSlot();
        if (slot == -1) return;

        int prevSlot = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = slot;

        BlockPos under = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 1, mc.thePlayer.posZ);

        if (mc.theWorld.isAirBlock(under)) {
            BlockPos neighbor = null;
            EnumFacing side = null;

            if (!mc.theWorld.isAirBlock(under.down())) {
                neighbor = under.down();
                side = EnumFacing.UP;
            } else if (!mc.theWorld.isAirBlock(under.north())) {
                neighbor = under.north();
                side = EnumFacing.SOUTH;
            } else if (!mc.theWorld.isAirBlock(under.south())) {
                neighbor = under.south();
                side = EnumFacing.NORTH;
            } else if (!mc.theWorld.isAirBlock(under.east())) {
                neighbor = under.east();
                side = EnumFacing.WEST;
            } else if (!mc.theWorld.isAirBlock(under.west())) {
                neighbor = under.west();
                side = EnumFacing.EAST;
            }

            if (neighbor != null) {
                Vec3 hitVec = new Vec3(neighbor.getX() + 0.5, neighbor.getY() + 0.5, neighbor.getZ() + 0.5);
                mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), neighbor, side, hitVec);
                mc.thePlayer.swingItem();
                lastPlaceTime = System.currentTimeMillis();
            }
        }

        mc.thePlayer.inventory.currentItem = prevSlot;
    }

    @Override
    public void onDisable() {
        releaseBlock();
        lastHitTime = 0;
        super.onDisable();
    }
}
