package keystrokesmod.client.module.modules.player;

import com.google.common.eventbus.Subscribe;
import keystrokesmod.client.event.impl.Render2DEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.DoubleSliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.CoolDown;
import keystrokesmod.client.utils.Utils;
import net.minecraft.inventory.ContainerChest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;

public class ChestStealer extends Module {

    private final DoubleSliderSetting firstDelay;
    private final DoubleSliderSetting delay;
    private final DoubleSliderSetting closeDelay;
    private final TickSetting autoClose;
    private boolean inChest;
    private boolean closingChest;
    private final CoolDown delayTimer = new CoolDown(0);
    private final CoolDown closeTimer = new CoolDown(0);
    private ArrayList<Slot> sortedSlots;
    private ContainerChest chest;

    public ChestStealer() {
        super("ChestStealer", ModuleCategory.player);
        this.registerSetting(firstDelay = new DoubleSliderSetting("Open delay", 250, 450, 0, 1000, 1));
        this.registerSetting(delay = new DoubleSliderSetting("Delay", 150, 250, 0, 1000, 1));
        this.registerSetting(autoClose = new TickSetting("Auto Close", false));
        this.registerSetting(closeDelay = new DoubleSliderSetting("Close delay", 150, 250, 0, 1000, 1));
    }

    @Override
    public void onDisable() {
        inChest = false;
        closingChest = false;
        sortedSlots = null;
        chest = null;
    }

    @Subscribe
    public void onRender2D(Render2DEvent e) {
        if (Utils.Player.isPlayerInChest()) {
            if (!inChest || mc.thePlayer.openContainer != chest) {
                chest = (ContainerChest) mc.thePlayer.openContainer;
                delayTimer.setCooldown((long) ThreadLocalRandom.current().nextDouble(firstDelay.getInputMin(),
                        firstDelay.getInputMax() + 0.01));
                delayTimer.start();
                sortedSlots = null;
                closingChest = false;
                inChest = true;
            }
            if (!delayTimer.hasFinished()) {
                return;
            }
            if (sortedSlots == null) {
                generatePath(chest);
            }
            if (sortedSlots != null && !sortedSlots.isEmpty()) {
                mc.playerController.windowClick(mc.thePlayer.openContainer.windowId, sortedSlots.get(0).s, 0, 1,
                        mc.thePlayer);
                delayTimer.setCooldown((long) ThreadLocalRandom.current().nextDouble(delay.getInputMin(),
                        delay.getInputMax() + 0.01));
                delayTimer.start();
                sortedSlots.remove(0);
            } else if (sortedSlots != null && autoClose.isToggled()) {
                if (!closingChest) {
                    closeTimer.setCooldown((long) ThreadLocalRandom.current().nextDouble(closeDelay.getInputMin(),
                            closeDelay.getInputMax() + 0.01));
                    closeTimer.start();
                    closingChest = true;
                } else if (closeTimer.hasFinished()) {
                    mc.thePlayer.closeScreen();
                    inChest = false;
                    closingChest = false;
                    sortedSlots = null;
                }
            }
        } else {
            inChest = false;
            closingChest = false;
            sortedSlots = null;
        }
    }

    public void generatePath(ContainerChest chest) {
        ArrayList<Slot> slots = new ArrayList<Slot>();
        for (int i = 0; i < chest.getLowerChestInventory().getSizeInventory(); i++) {
            if (chest.getInventory().get(i) != null)
                slots.add(new Slot(i));
        }
        Slot[] ss = sort(slots.toArray(new Slot[slots.size()]));
        ArrayList<Slot> newSlots = new ArrayList<Slot>();
        if (ss != null) {
            Collections.addAll(newSlots, ss);
        }
        this.sortedSlots = newSlots;
    }

    public static Slot[] sort(Slot[] in) {
        if (in == null || in.length == 0) {
            return in;
        }
        for (Slot slot : in) {
            if (slot != null) {
                slot.visited = false;
            }
        }
        Slot[] out = new Slot[in.length];
        Slot current = in[ThreadLocalRandom.current().nextInt(0, in.length)];
        for (int i = 0; i < in.length; i++) {
            if (i == in.length - 1) {
                out[in.length - 1] = Arrays.stream(in).filter(p -> p != null && !p.visited).findAny().orElse(null);
                break;
            }
            if (current == null) {
                break;
            }
            Slot finalCurrent = current;
            out[i] = finalCurrent;
            finalCurrent.visit();
            current = Arrays.stream(in).filter(p -> p != null && !p.visited)
                    .min(Comparator.comparingDouble(p -> p.getDistance(finalCurrent))).orElse(null);
        }
        return out;
    }

    public static class Slot {
        final int x;
        final int y;
        final int s;
        boolean visited;

        public Slot(int s) {
            this.x = s % 9;
            this.y = s / 9;
            this.s = s;
        }

        public int getSlotIndex() {
            return s;
        }

        public double getDistance(Slot s) {
            return Math.abs(this.x - s.x) + Math.abs(this.y - s.y);
        }

        public void visit() {
            visited = true;
        }
    }
}