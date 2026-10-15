package keystrokesmod.client.module.modules.world;



import com.google.common.eventbus.Subscribe;



import keystrokesmod.client.event.impl.ForgeEvent;
import keystrokesmod.client.event.impl.GameLoopEvent;

import keystrokesmod.client.event.impl.LookEvent;

import keystrokesmod.client.event.impl.MoveInputEvent;

import keystrokesmod.client.event.impl.Render2DEvent;

import keystrokesmod.client.event.impl.UpdateEvent;

import keystrokesmod.client.module.Module;

import keystrokesmod.client.module.Module.ModuleCategory;

import keystrokesmod.client.module.setting.impl.SliderSetting;

import keystrokesmod.client.module.setting.impl.TickSetting;

import keystrokesmod.client.utils.Utils;

import keystrokesmod.client.utils.font.FontUtil;

import net.minecraft.client.gui.ScaledResolution;

import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class Scaffold extends Module {

    private TickSetting eagle;
    private SliderSetting rps;
    private TickSetting render;
    private SliderSetting renderColorR;
    private SliderSetting renderColorG;
    private SliderSetting renderColorB;
    private SliderSetting renderAlpha;

    private float yaw, pitch;
    private int blockCount;
    private BlockPos targetBlock;

    public Scaffold() {
        super("Scaffold", ModuleCategory.world);
        this.registerSetting(eagle = new TickSetting("Shift", false));
        this.registerSetting(rps = new SliderSetting("Rotation speed", 80, 0, 300, 1));
        this.registerSetting(render = new TickSetting("Render", true));
        this.registerSetting(renderColorR = new SliderSetting("RenderColor-R", 255, 0, 255, 1));
        this.registerSetting(renderColorG = new SliderSetting("RenderColor-G", 0, 0, 255, 1));
        this.registerSetting(renderColorB = new SliderSetting("RenderColor-B", 0, 0, 255, 1));
        this.registerSetting(renderAlpha = new SliderSetting("RenderAlpha", 100, 0, 255, 1));
    }

    @Override
    public void onEnable() {
        if (Utils.Player.isPlayerInGame()) {
            yaw = mc.thePlayer.rotationYaw;
            pitch = mc.thePlayer.rotationPitch;
        }
        targetBlock = null;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        targetBlock = null;
        if (mc.gameSettings != null && mc.gameSettings.keyBindSneak != null) {
            net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
        }
        super.onDisable();
    }

    @Subscribe
    public void gameLoopEvent(GameLoopEvent e) {
        if (!Utils.Player.isPlayerInGame()) return;

        updateBlockCount();

        BlockPos under = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 1.0D, mc.thePlayer.posZ);

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
                targetBlock = under; // Store target block for rendering
                float[] rots = getRotations(neighbor, side);
                float maxStep = (float) rps.getInput();
                if (maxStep > 0f && maxStep < 180f) {
                    float yawDiff = MathHelper.wrapAngleTo180_float(rots[0] - yaw);
                    float pitchDiff = MathHelper.wrapAngleTo180_float(rots[1] - pitch);
                    yaw += MathHelper.clamp_float(yawDiff, -maxStep, maxStep);
                    pitch = MathHelper.clamp_float(pitch + MathHelper.clamp_float(pitchDiff, -maxStep, maxStep), -90f, 90f);
                } else {
                    yaw = rots[0];
                    pitch = MathHelper.clamp_float(rots[1], -90f, 90f);
                }

                int blockSlot = Utils.Player.getBlockSlot();
                if (blockSlot != -1) {
                    int prevSlot = mc.thePlayer.inventory.currentItem;
                    mc.thePlayer.inventory.currentItem = blockSlot;

                    if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), neighbor, side, new net.minecraft.util.Vec3(neighbor.getX() + 0.5, neighbor.getY() + 0.5, neighbor.getZ() + 0.5))) {
                        mc.thePlayer.swingItem();
                    }

                    mc.thePlayer.inventory.currentItem = prevSlot;
                }
            } else {
                targetBlock = null; // No valid block to place
            }
        } else {
            yaw = mc.thePlayer.rotationYaw;
            pitch = mc.thePlayer.rotationPitch;
            targetBlock = null; // No block needed
        }

        if (eagle.isToggled()) {
            if (Utils.Player.playerOverAir()) {
                net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
            } else {
                net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
            }
        }
    }

    @Subscribe
    public void updateEvent(UpdateEvent e) {
        e.setPitch(pitch);
        e.setYaw(yaw);
    }

    @Subscribe
    public void lookEvent(LookEvent e) {
        e.setPitch(pitch);
        e.setYaw(yaw);
    }

    @Subscribe
    public void moveEvent(MoveInputEvent e) {
        e.setYaw(yaw);
    }

    @Subscribe
    public void onRender2D(Render2DEvent e) {
        if (!Utils.Player.isPlayerInGame()) return;
        ScaledResolution sr = new ScaledResolution(mc);
        FontUtil.normal.drawCenteredSmoothString(blockCount + " blocks", (int) (sr.getScaledWidth() / 2f + 8), (int) (sr.getScaledHeight() / 2f - 4), blockCount <= 16 ? 0xff0000 : -1);
    }

    @Subscribe
    public void onRenderWorld(ForgeEvent fe) {
        if (!(fe.getEvent() instanceof RenderWorldLastEvent)) return;
        if (!render.isToggled() || targetBlock == null) return;
        if (!Utils.Player.isPlayerInGame()) return;

        RenderWorldLastEvent event = (RenderWorldLastEvent) fe.getEvent();
        // Render target block
        renderBlock(targetBlock, event.partialTicks);
    }

    private void renderBlock(BlockPos pos, float partialTicks) {
        double x = pos.getX() - mc.getRenderManager().viewerPosX;
        double y = pos.getY() - mc.getRenderManager().viewerPosY;
        double z = pos.getZ() - mc.getRenderManager().viewerPosZ;

        float r = (float) renderColorR.getInput() / 255.0f;
        float g = (float) renderColorG.getInput() / 255.0f;
        float b = (float) renderColorB.getInput() / 255.0f;
        float a = (float) renderAlpha.getInput() / 255.0f;

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(r, g, b, a);

        // Render filled box
        renderFilledBox(0, 0, 0, 1, 1, 1);

        // Render outline
        GL11.glColor4f(r, g, b, 1.0f);
        renderOutlineBox(0, 0, 0, 1, 1, 1);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    private void renderFilledBox(double x, double y, double z, double width, double height, double depth) {
        GL11.glBegin(GL11.GL_QUADS);

        // Bottom
        GL11.glVertex3d(x, y, z);
        GL11.glVertex3d(x + width, y, z);
        GL11.glVertex3d(x + width, y, z + depth);
        GL11.glVertex3d(x, y, z + depth);

        // Top
        GL11.glVertex3d(x, y + height, z);
        GL11.glVertex3d(x, y + height, z + depth);
        GL11.glVertex3d(x + width, y + height, z + depth);
        GL11.glVertex3d(x + width, y + height, z);

        // Front
        GL11.glVertex3d(x, y, z);
        GL11.glVertex3d(x, y + height, z);
        GL11.glVertex3d(x + width, y + height, z);
        GL11.glVertex3d(x + width, y, z);

        // Back
        GL11.glVertex3d(x, y, z + depth);
        GL11.glVertex3d(x + width, y, z + depth);
        GL11.glVertex3d(x + width, y + height, z + depth);
        GL11.glVertex3d(x, y + height, z + depth);

        // Left
        GL11.glVertex3d(x, y, z);
        GL11.glVertex3d(x, y, z + depth);
        GL11.glVertex3d(x, y + height, z + depth);
        GL11.glVertex3d(x, y + height, z);

        // Right
        GL11.glVertex3d(x + width, y, z);
        GL11.glVertex3d(x + width, y + height, z);
        GL11.glVertex3d(x + width, y + height, z + depth);
        GL11.glVertex3d(x + width, y, z + depth);

        GL11.glEnd();
    }

    private void renderOutlineBox(double x, double y, double z, double width, double height, double depth) {
        GL11.glLineWidth(2.0f);
        GL11.glBegin(GL11.GL_LINES);

        // Bottom edges
        GL11.glVertex3d(x, y, z);
        GL11.glVertex3d(x + width, y, z);

        GL11.glVertex3d(x + width, y, z);
        GL11.glVertex3d(x + width, y, z + depth);

        GL11.glVertex3d(x + width, y, z + depth);
        GL11.glVertex3d(x, y, z + depth);

        GL11.glVertex3d(x, y, z + depth);
        GL11.glVertex3d(x, y, z);

        // Top edges
        GL11.glVertex3d(x, y + height, z);
        GL11.glVertex3d(x + width, y + height, z);

        GL11.glVertex3d(x + width, y + height, z);
        GL11.glVertex3d(x + width, y + height, z + depth);

        GL11.glVertex3d(x + width, y + height, z + depth);
        GL11.glVertex3d(x, y + height, z + depth);

        GL11.glVertex3d(x, y + height, z + depth);
        GL11.glVertex3d(x, y + height, z);

        // Vertical edges
        GL11.glVertex3d(x, y, z);
        GL11.glVertex3d(x, y + height, z);

        GL11.glVertex3d(x + width, y, z);
        GL11.glVertex3d(x + width, y + height, z);

        GL11.glVertex3d(x + width, y, z + depth);
        GL11.glVertex3d(x + width, y + height, z + depth);

        GL11.glVertex3d(x, y, z + depth);
        GL11.glVertex3d(x, y + height, z + depth);

        GL11.glEnd();
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
            net.minecraft.item.ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof net.minecraft.item.ItemBlock) {
                blockCount += stack.stackSize;
            }
        }
    }
}
