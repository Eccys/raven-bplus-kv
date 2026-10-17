package keystrokesmod.client.module.modules.player;

import com.google.common.eventbus.Subscribe;
import keystrokesmod.client.event.impl.ForgeEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.Module.ModuleCategory;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.Utils;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.event.entity.living.LivingEvent;

public class AutoEat extends Module {
    
    public static SliderSetting healthThreshold;
    public static SliderSetting hungerThreshold;
    public static TickSetting preferGapples;
    public static TickSetting preferGolden;
    public static TickSetting checkInventory;
    
    private boolean isEating = false;
    private int eatingSlot = -1;
    private int prevSlot = -1;

    public AutoEat() {
        super("AutoEat", ModuleCategory.player);
        this.registerSetting(healthThreshold = new SliderSetting("Health Threshold", 15.0, 1.0, 20.0, 0.5));
        this.registerSetting(hungerThreshold = new SliderSetting("Hunger Threshold", 15.0, 1.0, 20.0, 0.5));
        this.registerSetting(preferGapples = new TickSetting("Prefer Gapples", true));
        this.registerSetting(preferGolden = new TickSetting("Prefer Golden Foods", true));
        this.registerSetting(checkInventory = new TickSetting("Check Inventory", true));
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        if (fe.getEvent() instanceof LivingEvent.LivingUpdateEvent) {
            LivingEvent.LivingUpdateEvent event = (LivingEvent.LivingUpdateEvent) fe.getEvent();
            if (!Utils.Player.isPlayerInGame() || event.entity != mc.thePlayer) {
                return;
            }

            EntityPlayerSP player = mc.thePlayer;

            if (shouldEat(player)) {
                if (!isEating) {
                    findAndEatFood(player);
                }
            } else if (isEating) {
                stopEating(player);
            }
        } else if (fe.getEvent() instanceof MouseEvent) {
            MouseEvent event = (MouseEvent) fe.getEvent();
            if (event.buttonstate && event.button == 1) { // Right click
                if (isEating && mc.thePlayer != null) {
                    stopEating(mc.thePlayer);
                }
            }
        }
    }

    private boolean shouldEat(EntityPlayerSP player) {
        float health = player.getHealth();
        int foodLevel = player.getFoodStats().getFoodLevel();
        
        return health <= healthThreshold.getInput() || foodLevel <= hungerThreshold.getInput();
    }

    private void findAndEatFood(EntityPlayerSP player) {
        int bestSlot = findBestFoodSlot(player);
        
        if (bestSlot != -1) {
            if (bestSlot >= 9) {
                // Swap inventory food into hotbar slot 8 via windowClick
                mc.playerController.windowClick(player.inventoryContainer.windowId, bestSlot, 8, 2, player);
                bestSlot = 8;
            }
            prevSlot = player.inventory.currentItem;
            eatingSlot = bestSlot;
            isEating = true;
            
            // Switch to food slot
            player.inventory.currentItem = eatingSlot;
            
            // Start eating
            if (player.isUsingItem()) {
                player.stopUsingItem();
            }
            
            // Hold use item and right click to start eating
            if (mc.gameSettings != null) {
                net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
            }
            mc.playerController.sendUseItem(player, mc.theWorld, player.inventory.getCurrentItem());
        }
    }

    private int findBestFoodSlot(EntityPlayerSP player) {
        int bestSlot = -1;
        double bestScore = -1;

        // Check hotbar first
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemFood) {
                double score = calculateFoodScore(stack);
                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = i;
                }
            }
        }

        // Check inventory if enabled and no food found in hotbar
        if (bestSlot == -1 && checkInventory.isToggled()) {
            for (int i = 9; i < player.inventory.mainInventory.length; i++) {
                ItemStack stack = player.inventory.getStackInSlot(i);
                if (stack != null && stack.getItem() instanceof ItemFood) {
                    double score = calculateFoodScore(stack);
                    if (score > bestScore) {
                        bestScore = score;
                        bestSlot = i;
                    }
                }
            }
        }

        return bestSlot;
    }

    private double calculateFoodScore(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemFood)) {
            return -1;
        }

        ItemFood food = (ItemFood) stack.getItem();
        double score = 0;

        // Base nutrition value
        score += food.getHealAmount(stack) * 2;
        score += food.getSaturationModifier(stack) * 4;

        // Prefer golden foods
        if (preferGolden.isToggled()) {
            if (stack.getDisplayName().toLowerCase().contains("golden")) {
                score += 10;
            }
        }

        // Prefer gapples
        if (preferGapples.isToggled()) {
            if (stack.getItem().getUnlocalizedName().contains("apple")) {
                score += 15;
            }
        }

        return score;
    }

    private void stopEating(EntityPlayerSP player) {
        if (mc != null && mc.gameSettings != null) {
            net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        }
        if (isEating && player != null && player.isUsingItem()) {
            player.stopUsingItem();
        }
        if (player != null && prevSlot >= 0 && prevSlot < 9) {
            player.inventory.currentItem = prevSlot;
        }
        isEating = false;
        eatingSlot = -1;
        prevSlot = -1;
    }

    @Override
    public void onDisable() {
        stopEating(mc != null ? mc.thePlayer : null);
        super.onDisable();
    }
}
