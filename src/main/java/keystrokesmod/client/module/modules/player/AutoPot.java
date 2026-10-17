package keystrokesmod.client.module.modules.player;

import java.util.List;

import com.google.common.eventbus.Subscribe;
import keystrokesmod.client.event.impl.TickEvent;
import keystrokesmod.client.event.impl.UpdateEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.Utils;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

public class AutoPot extends Module {
    public static SliderSetting healthThreshold, delay;
    public static TickSetting healOnly;
    private long lastThrow;
    private boolean shouldLookDown = false;
    private long lookDownUntil = 0;

    public AutoPot() {
        super("AutoPot", ModuleCategory.player);
        this.registerSetting(healthThreshold = new SliderSetting("Health %", 40.0D, 1.0D, 100.0D, 1.0D));
        this.registerSetting(delay = new SliderSetting("Delay (ms)", 500.0D, 100.0D, 3000.0D, 50.0D));
        this.registerSetting(healOnly = new TickSetting("Heal only", true));
    }

    @Override
    public void onDisable() {
        shouldLookDown = false;
        lookDownUntil = 0;
    }

    @Subscribe
    public void onTick(TickEvent e) {
        if (!Utils.Player.isPlayerInGame() || mc.thePlayer == null) return;

        long now = System.currentTimeMillis();
        if (now - lastThrow < delay.getInput()) return;

        float health = mc.thePlayer.getHealth();
        float maxHealth = mc.thePlayer.getMaxHealth();
        if (maxHealth <= 0.0f) return;
        float healthPct = (health / maxHealth) * 100.0f;

        if (healthPct > healthThreshold.getInput()) return;

        int potSlot = findBestPot();
        if (potSlot == -1) return;

        int oldSlot = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = potSlot;

        mc.thePlayer.rotationPitch = 90;
        shouldLookDown = true;
        lookDownUntil = now + 200;

        if (healOnly.isToggled()) {
            mc.playerController.updateController();
        }

        mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem());
        mc.thePlayer.inventory.currentItem = oldSlot;
        lastThrow = now;
    }

    @Subscribe
    public void onUpdate(UpdateEvent e) {
        if (shouldLookDown && System.currentTimeMillis() < lookDownUntil) {
            e.setPitch(90);
        } else {
            shouldLookDown = false;
        }
    }

    private int findBestPot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack == null || !(stack.getItem() instanceof ItemPotion)) continue;
            if (!ItemPotion.isSplash(stack.getMetadata())) continue;
            ItemPotion pot = (ItemPotion) stack.getItem();
            if (healOnly.isToggled()) {
                List<PotionEffect> effects = pot.getEffects(stack);
                if (effects != null) {
                    for (PotionEffect effect : effects) {
                        if (effect.getPotionID() == Potion.heal.id)
                            return i;
                    }
                }
            } else {
                return i;
            }
        }
        return -1;
    }
}
