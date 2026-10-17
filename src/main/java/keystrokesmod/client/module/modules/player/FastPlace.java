package keystrokesmod.client.module.modules.player;

import com.google.common.eventbus.Subscribe;
import keystrokesmod.client.event.impl.TickEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;

public class FastPlace extends Module {
    public static SliderSetting delaySlider, projSlider;
    public static TickSetting blockOnly, projSeparate;

    public static final Field rightClickDelayTimerField;

    static {
        rightClickDelayTimerField = ReflectionHelper.findField(Minecraft.class, "field_71467_ac",
                "rightClickDelayTimer");

        if (rightClickDelayTimerField != null) {
            rightClickDelayTimerField.setAccessible(true);
        }
    }

    public FastPlace() {
        super("FastPlace", ModuleCategory.player);

        this.registerSetting(delaySlider = new SliderSetting("Delay", 0.0D, 0.0D, 4.0D, 1.0D));
        this.registerSetting(blockOnly = new TickSetting("Blocks only", true));
        this.registerSetting(projSeparate = new TickSetting("Separate Projectile Delay", true));
        this.registerSetting(projSlider = new SliderSetting("Projectile Delay", 2.0D, 0.0D, 4.0D, 1.0D));

    }

    @Override
    public boolean canBeEnabled() {
        return rightClickDelayTimerField != null;
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (Utils.Player.isPlayerInGame() && mc.inGameHasFocus && rightClickDelayTimerField != null) {
            ItemStack item = mc.thePlayer.getHeldItem();
            boolean isProjectile = item != null && (item.getItem() instanceof ItemSnowball || item.getItem() instanceof ItemEgg);
            if (isProjectile && projSeparate.isToggled()) {
                applyDelay((int) projSlider.getInput());
            } else if (!blockOnly.isToggled() || (item != null && item.getItem() instanceof ItemBlock)) {
                applyDelay((int) delaySlider.getInput());
            }
        }
    }

    private void applyDelay(int c) {
        try {
            if (c == 0) {
                rightClickDelayTimerField.set(mc, 0);
            } else if (c != 4) {
                int d = rightClickDelayTimerField.getInt(mc);
                if (d == 4) {
                    rightClickDelayTimerField.set(mc, c);
                }
            }
        } catch (IllegalAccessException | IndexOutOfBoundsException ignored) {
        }
    }
}
