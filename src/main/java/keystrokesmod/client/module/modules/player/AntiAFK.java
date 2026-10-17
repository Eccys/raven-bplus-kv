package keystrokesmod.client.module.modules.player;

import com.google.common.eventbus.Subscribe;
import keystrokesmod.client.event.impl.TickEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.CoolDown;
import keystrokesmod.client.utils.Utils;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class AntiAFK extends Module {

    private final CoolDown actionCd = new CoolDown(0);
    private final CoolDown releaseCd = new CoolDown(0);
    private final SliderSetting delay;
    private final ComboSetting mode;
    private final TickSetting rotate;
    private Action currentAction;
    private boolean actionHeld;

    public AntiAFK() {
        super("AntiAFK", ModuleCategory.player);
        this.registerSettings(
                delay = new SliderSetting("Delay (s)", 30, 5, 300, 1),
                mode = new ComboSetting("Action", Action.Jump),
                rotate = new TickSetting("Rotate", true)
        );
    }

    @Subscribe
    public void onTick(TickEvent e) {
        if (!Utils.Player.isPlayerInGame())
            return;

        if (isPlayerActive()) {
            actionCd.setCooldown((long) delay.getInput() * 1000);
            actionCd.start();
            releaseAction();
            return;
        }

        if (!actionCd.hasFinished())
            return;

        Action action = (Action) mode.getMode();
        if (actionHeld) {
            if (releaseCd.hasFinished()) {
                releaseAction();
            }
            return;
        }

        currentAction = action;
        actionHeld = true;

        switch (action) {
            case Jump:
                pressKey(mc.gameSettings.keyBindJump.getKeyCode());
                break;
            case Forward:
                pressKey(mc.gameSettings.keyBindForward.getKeyCode());
                break;
            case Backward:
                pressKey(mc.gameSettings.keyBindBack.getKeyCode());
                break;
            case Strafe:
                pressKey(mc.thePlayer.ticksExisted % 2 == 0
                        ? mc.gameSettings.keyBindRight.getKeyCode()
                        : mc.gameSettings.keyBindLeft.getKeyCode());
                break;
        }

        if (rotate.isToggled()) {
            mc.thePlayer.rotationYaw += mc.thePlayer.ticksExisted % 2 == 0 ? 15 : -15;
        }

        releaseCd.setCooldown(500);
        releaseCd.start();
    }

    @Override
    public void onDisable() {
        releaseAction();
    }

    private void pressKey(int key) {
        KeyBinding.setKeyBindState(key, true);
    }

    private void releaseAction() {
        if (!actionHeld) return;

        if (mc != null && mc.gameSettings != null && currentAction != null) {
            switch (currentAction) {
                case Jump:
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
                    break;
                case Forward:
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
                    break;
                case Backward:
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
                    break;
                case Strafe:
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), false);
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), false);
                    break;
            }
        }

        actionHeld = false;
        currentAction = null;
        actionCd.setCooldown((long) delay.getInput() * 1000);
        actionCd.start();
    }

    private boolean isPlayerActive() {
        if (mc.thePlayer == null) return true;

        if (mc.thePlayer.motionX != 0 || mc.thePlayer.motionZ != 0 || mc.thePlayer.motionY != 0)
            return true;

        if (Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode()) ||
                Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode()) ||
                Keyboard.isKeyDown(mc.gameSettings.keyBindLeft.getKeyCode()) ||
                Keyboard.isKeyDown(mc.gameSettings.keyBindRight.getKeyCode()) ||
                Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode()) ||
                Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()) ||
                Keyboard.isKeyDown(mc.gameSettings.keyBindSprint.getKeyCode()))
            return true;

        if (Mouse.isButtonDown(0) || Mouse.isButtonDown(1) || Mouse.isButtonDown(2))
            return true;

        return false;
    }

    public enum Action {
        Jump,
        Forward,
        Backward,
        Strafe
    }
}
