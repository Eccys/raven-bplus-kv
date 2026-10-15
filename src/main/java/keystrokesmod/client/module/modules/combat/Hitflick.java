package keystrokesmod.client.module.modules.combat;

import com.google.common.eventbus.Subscribe;
import keystrokesmod.client.event.impl.ForgeEvent;
import keystrokesmod.client.event.impl.UpdateEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.Utils;
import net.minecraftforge.event.entity.player.AttackEntityEvent;

public class Hitflick extends Module {
    public static ComboSetting direction;
    public static SliderSetting customAngle;
    public static SliderSetting flickDuration;
    public static TickSetting onlySword;
    public static TickSetting silent;
    private boolean isFlicking = false;
    private float flickYaw = 0;
    private long flickUntil = 0;
    private float originalYaw = 0;

    public Hitflick() {
        super("Hitflick", ModuleCategory.combat);
        this.registerSetting(direction = new ComboSetting("Direction", FlickDirection.Right));
        this.registerSetting(customAngle = new SliderSetting("Custom angle", 90, 1, 180, 1));
        this.registerSetting(flickDuration = new SliderSetting("Duration (ms)", 100, 10, 500, 10));
        this.registerSetting(onlySword = new TickSetting("Only sword", true));
        this.registerSetting(silent = new TickSetting("Silent", true));
    }

    @Subscribe
    public void onHit(ForgeEvent fe) {
        if (!(fe.getEvent() instanceof AttackEntityEvent)) return;
        if (!Utils.Player.isPlayerInGame() || mc.thePlayer == null) return;
        if (onlySword.isToggled() && !Utils.Player.isPlayerHoldingSword()) return;

        float angle;
        switch ((FlickDirection) direction.getMode()) {
            case Left:
                angle = -90;
                break;
            case Right:
                angle = 90;
                break;
            case Back:
                angle = 180;
                break;
            case Custom:
                angle = (float) customAngle.getInput();
                break;
            default:
                angle = 90;
        }

        if (!isFlicking) {
            originalYaw = mc.thePlayer.rotationYaw;
        }
        flickYaw = originalYaw + angle;
        isFlicking = true;
        flickUntil = System.currentTimeMillis() + (long) flickDuration.getInput();

        if (!silent.isToggled()) {
            mc.thePlayer.rotationYaw = flickYaw;
        }
    }

    @Subscribe
    public void onUpdate(UpdateEvent e) {
        if (!isFlicking || !Utils.Player.isPlayerInGame()) return;

        if (System.currentTimeMillis() < flickUntil) {
            e.setYaw(flickYaw);
            if (!silent.isToggled()) {
                mc.thePlayer.rotationYaw = flickYaw;
            }
        } else {
            isFlicking = false;
            e.setYaw(originalYaw);
            if (!silent.isToggled()) {
                mc.thePlayer.rotationYaw = originalYaw;
            }
        }
    }

    @Override
    public void onDisable() {
        if (isFlicking && !silent.isToggled() && Utils.Player.isPlayerInGame()) {
            mc.thePlayer.rotationYaw = originalYaw;
        }
        isFlicking = false;
    }

    public enum FlickDirection {
        Left, Right, Back, Custom
    }
}
