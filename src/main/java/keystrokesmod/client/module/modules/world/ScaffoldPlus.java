package keystrokesmod.client.module.modules.world;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.google.common.eventbus.Subscribe;

import keystrokesmod.client.event.impl.GameLoopEvent;
import keystrokesmod.client.event.impl.LookEvent;
import keystrokesmod.client.event.impl.MoveInputEvent;
import keystrokesmod.client.event.impl.Render2DEvent;
import keystrokesmod.client.event.impl.UpdateEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.Utils;
import keystrokesmod.client.utils.font.FontUtil;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraft.client.settings.KeyBinding;

public class ScaffoldPlus extends Module {

    private TickSetting tower, keepY, safeWalk, sprintDisable, render;
    private SliderSetting delay, extend;
    private ComboSetting rotations;

    private float yaw, pitch;
    private int blockCount;
    private BlockPos targetBlock;
    private double startY;
    private long lastPlaceTime;

    public ScaffoldPlus() {
        super("Scaffold+", ModuleCategory.world);
        this.registerSetting(tower = new TickSetting("Tower", true));
        this.registerSetting(keepY = new TickSetting("Keep Y", true));
        this.registerSetting(safeWalk = new TickSetting("SafeWalk (Eagle)", true));
        this.registerSetting(sprintDisable = new TickSetting("Disable Sprint", true));
        this.registerSetting(rotations = new ComboSetting("Rotations", RotMode.Normal));
        this.registerSetting(delay = new SliderSetting("Delay (ms)", 0, 0, 500, 10));
        this.registerSetting(extend = new SliderSetting("Extend", 0, 0, 4, 1));
        this.registerSetting(render = new TickSetting("Render Target", true));
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer != null) {
            startY = Math.floor(mc.thePlayer.posY);
            yaw = mc.thePlayer.rotationYaw;
            pitch = mc.thePlayer.rotationPitch;
        } else {
            startY = 0;
        }
        targetBlock = null;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        targetBlock = null;
        if (mc.gameSettings != null && mc.gameSettings.keyBindSneak != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
        }
        super.onDisable();
    }

    @Subscribe
    public void gameLoopEvent(GameLoopEvent e) {
        if (!Utils.Player.isPlayerInGame()) return;

        updateBlockCount();

        if (sprintDisable.isToggled()) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
            mc.thePlayer.setSprinting(false);
        }

        double yLevel = mc.thePlayer.posY - 1.0D;
        if (keepY.isToggled() && !mc.gameSettings.keyBindJump.isKeyDown()) {
            if (mc.thePlayer.posY - 1.0D >= startY) {
                yLevel = startY - 1.0D;
            }
        } else {
            startY = Math.floor(mc.thePlayer.posY);
        }

        BlockPos under = new BlockPos(mc.thePlayer.posX, yLevel, mc.thePlayer.posZ);

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
                targetBlock = under; 
                
                // Rotations
                RotMode rotMode = (RotMode) rotations.getMode();
                if (rotMode == RotMode.Normal) {
                    float[] rots = getRotations(neighbor, side);
                    yaw = rots[0];
                    pitch = rots[1];
                } else if (rotMode == RotMode.Backwards) {
                    yaw = mc.thePlayer.rotationYaw + 180f;
                    pitch = 80f;
                }

                // Place
                if (System.currentTimeMillis() - lastPlaceTime >= delay.getInput()) {
                    int blockSlot = Utils.Player.getBlockSlot();
                    if (blockSlot != -1) {
                        int prevSlot = mc.thePlayer.inventory.currentItem;
                        mc.thePlayer.inventory.currentItem = blockSlot;

                        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), neighbor, side, new net.minecraft.util.Vec3(neighbor.getX() + 0.5, neighbor.getY() + 0.5, neighbor.getZ() + 0.5))) {
                            mc.thePlayer.swingItem();
                            lastPlaceTime = System.currentTimeMillis();
                            
                            // Tower Logic
                            if (tower.isToggled() && mc.gameSettings.keyBindJump.isKeyDown() && !Utils.Player.isMoving()) {
                                mc.thePlayer.motionY = 0.42f;
                            }
                        }

                        mc.thePlayer.inventory.currentItem = prevSlot;
                    }
                }
            } else {
                targetBlock = null; 
            }
        } else {
            yaw = mc.thePlayer.rotationYaw;
            pitch = mc.thePlayer.rotationPitch;
            targetBlock = null; 
        }

        // SafeWalk (Eagle)
        if (safeWalk.isToggled()) {
            if (Utils.Player.playerOverAir() && mc.thePlayer.onGround) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
            } else {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
            }
        }
    }

    @Subscribe
    public void updateEvent(UpdateEvent e) {
        RotMode rotMode = (RotMode) rotations.getMode();
        if (rotMode != RotMode.None) {
            e.setPitch(pitch);
            e.setYaw(yaw);
        }
    }

    @Subscribe
    public void lookEvent(LookEvent e) {
        RotMode rotMode = (RotMode) rotations.getMode();
        if (rotMode != RotMode.None) {
            e.setPitch(pitch);
            e.setYaw(yaw);
        }
    }

    @Subscribe
    public void moveEvent(MoveInputEvent e) {
        RotMode rotMode = (RotMode) rotations.getMode();
        if (rotMode != RotMode.None) {
            e.setYaw(yaw);
        }
    }

    @Subscribe
    public void onRender2D(Render2DEvent e) {
        if (!Utils.Player.isPlayerInGame()) return;
        ScaledResolution sr = new ScaledResolution(mc);
        FontUtil.normal.drawCenteredSmoothString(blockCount + " blocks", (int) (sr.getScaledWidth() / 2f), (int) (sr.getScaledHeight() / 2f + 15), blockCount <= 16 ? 0xff0000 : -1);
    }

    @Subscribe
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!render.isToggled() || targetBlock == null || !Utils.Player.isPlayerInGame()) return;

        double x = targetBlock.getX() - mc.getRenderManager().viewerPosX;
        double y = targetBlock.getY() - mc.getRenderManager().viewerPosY;
        double z = targetBlock.getZ() - mc.getRenderManager().viewerPosZ;

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 0.5f, 0f, 0.4f);

        GL11.glBegin(GL11.GL_QUADS);
        // Bottom
        GL11.glVertex3d(0, 0, 0); GL11.glVertex3d(1, 0, 0); GL11.glVertex3d(1, 0, 1); GL11.glVertex3d(0, 0, 1);
        // Top
        GL11.glVertex3d(0, 1, 0); GL11.glVertex3d(0, 1, 1); GL11.glVertex3d(1, 1, 1); GL11.glVertex3d(1, 1, 0);
        // Front
        GL11.glVertex3d(0, 0, 0); GL11.glVertex3d(0, 1, 0); GL11.glVertex3d(1, 1, 0); GL11.glVertex3d(1, 0, 0);
        // Back
        GL11.glVertex3d(0, 0, 1); GL11.glVertex3d(1, 0, 1); GL11.glVertex3d(1, 1, 1); GL11.glVertex3d(0, 1, 1);
        // Left
        GL11.glVertex3d(0, 0, 0); GL11.glVertex3d(0, 0, 1); GL11.glVertex3d(0, 1, 1); GL11.glVertex3d(0, 1, 0);
        // Right
        GL11.glVertex3d(1, 0, 0); GL11.glVertex3d(1, 1, 0); GL11.glVertex3d(1, 1, 1); GL11.glVertex3d(1, 0, 1);
        GL11.glEnd();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    private float[] getRotations(BlockPos pos, EnumFacing facing) {
        double x = pos.getX() + 0.5 - mc.thePlayer.posX + (double) facing.getFrontOffsetX() / 2.0;
        double z = pos.getZ() + 0.5 - mc.thePlayer.posZ + (double) facing.getFrontOffsetZ() / 2.0;
        double y = pos.getY() + 0.5 - (mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight()) + (double) facing.getFrontOffsetY() / 2.0;
        double dist = MathHelper.sqrt_double(x * x + z * z);
        float yaw = (float) (Math.atan2(z, x) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float) (-(Math.atan2(y, dist) * 180.0 / Math.PI));
        return new float[]{mc.thePlayer.rotationYaw + MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw), mc.thePlayer.rotationPitch + MathHelper.wrapAngleTo180_float(pitch - mc.thePlayer.rotationPitch)};
    }

    private void updateBlockCount() {
        blockCount = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemBlock) {
                blockCount += stack.stackSize;
            }
        }
    }

    public enum RotMode {
        None, Normal, Backwards
    }
}
