package keystrokesmod.client.module.modules.render;

import java.awt.Color;
import java.util.Iterator;

import org.lwjgl.opengl.GL11;

import com.google.common.eventbus.Subscribe;

import keystrokesmod.client.event.impl.ForgeEvent;
import keystrokesmod.client.main.Raven;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.Module.ModuleCategory;
import keystrokesmod.client.module.modules.world.AntiBot;
import keystrokesmod.client.module.setting.impl.DescriptionSetting;
import keystrokesmod.client.module.setting.impl.RGBSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.Utils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

public class EnhancedESP extends Module {
    public static DescriptionSetting desc;
    public static RGBSetting color;
    public static TickSetting rainbow, showInvis, redOnDamage, matchArmor, smoothHealth, roundedCorners, glowEffect;
    public static TickSetting box, healthBar, tracers, nameTags;
    public static SliderSetting boxWidth, healthWidth, tracerWidth, fadeDistance;
    public static SliderSetting boxOpacity, healthOpacity, tracerOpacity;
    
    private int rgbColor;

    public EnhancedESP() {
        super("EnhancedESP", ModuleCategory.render);
        this.registerSetting(color = new RGBSetting("Color", 255, 100, 0));
        this.registerSetting(rainbow = new TickSetting("Rainbow", false));
        this.registerSetting(desc = new DescriptionSetting("Enhanced ESP Features"));
        
        // ESP Types
        this.registerSetting(box = new TickSetting("Box", true));
        this.registerSetting(healthBar = new TickSetting("Health Bar", true));
        this.registerSetting(tracers = new TickSetting("Tracers", false));
        this.registerSetting(nameTags = new TickSetting("Name Tags", false));
        
        // Visual Settings
        this.registerSetting(roundedCorners = new TickSetting("Rounded Corners", true));
        this.registerSetting(smoothHealth = new TickSetting("Smooth Health", true));
        this.registerSetting(glowEffect = new TickSetting("Glow Effect", false));
        
        // Size Settings
        this.registerSetting(boxWidth = new SliderSetting("Box Width", 2.0, 0.5, 5.0, 0.5));
        this.registerSetting(healthWidth = new SliderSetting("Health Width", 4.0, 2.0, 8.0, 0.5));
        this.registerSetting(tracerWidth = new SliderSetting("Tracer Width", 1.5, 0.5, 5.0, 0.5));
        
        // Opacity Settings
        this.registerSetting(boxOpacity = new SliderSetting("Box Opacity", 0.8, 0.1, 1.0, 0.1));
        this.registerSetting(healthOpacity = new SliderSetting("Health Opacity", 0.9, 0.1, 1.0, 0.1));
        this.registerSetting(tracerOpacity = new SliderSetting("Tracer Opacity", 0.6, 0.1, 1.0, 0.1));
        
        // Other Settings
        this.registerSetting(fadeDistance = new SliderSetting("Fade Distance", 50.0, 20.0, 100.0, 5.0));
        this.registerSetting(showInvis = new TickSetting("Show Invisible", true));
        this.registerSetting(redOnDamage = new TickSetting("Red on Damage", true));
        this.registerSetting(matchArmor = new TickSetting("Match Armor Color", false));
        this.rgbColor = color.getRGB();
    }

    @Override
    public void guiUpdate() {
        this.rgbColor = color.getRGB();
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        if (fe.getEvent() instanceof RenderWorldLastEvent) {
            RenderWorldLastEvent event = (RenderWorldLastEvent) fe.getEvent();
            if (Utils.Player.isPlayerInGame()) {
                this.rgbColor = color.getRGB();
                int baseColor = rainbow.isToggled() ? Utils.Client.rainbowDraw(2L, 0L) : this.rgbColor;
                
                Iterator<EntityPlayer> iterator = mc.theWorld.playerEntities.iterator();
                while (iterator.hasNext()) {
                    EntityPlayer player = iterator.next();
                    
                    if (player == mc.thePlayer || player.deathTime != 0) continue;
                    if (!showInvis.isToggled() && player.isInvisible()) continue;
                    if (AntiBot.bot(player)) continue;
                    
                    int entityColor = getEntityColor(player, baseColor);
                    renderESP(player, entityColor, event.partialTicks);
                }
            }
        }
    }

    private int getEntityColor(EntityPlayer player, int defaultColor) {
        if (matchArmor.isToggled()) {
            ItemStack chestplate = player.getCurrentArmor(2);
            if (chestplate != null) {
                int armorColor = getArmorColor(chestplate);
                if (armorColor > 0) {
                    return new Color(armorColor).getRGB();
                }
            }
        }
        
        if (redOnDamage.isToggled() && player.hurtTime > 0) {
            return Color.RED.getRGB();
        }
        
        return defaultColor;
    }

    private int getArmorColor(ItemStack stack) {
        if (stack == null) return -1;
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt != null) {
            NBTTagCompound display = nbt.getCompoundTag("display");
            if (display != null && display.hasKey("color", 3)) {
                return display.getInteger("color");
            }
        }
        return -1;
    }

    private void renderESP(EntityPlayer player, int color, float partialTicks) {
        double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks - mc.getRenderManager().viewerPosX;
        double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks - mc.getRenderManager().viewerPosY;
        double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks - mc.getRenderManager().viewerPosZ;
        
        double distance = mc.thePlayer.getDistanceToEntity(player);
        float fadeAlpha = calculateFadeAlpha(distance);
        
        GlStateManager.pushMatrix();
        
        if (box.isToggled()) {
            renderModernBox(player, x, y, z, color, fadeAlpha);
        }
        
        if (healthBar.isToggled()) {
            renderSmoothHealthBar(player, x, y, z, fadeAlpha);
        }
        
        if (tracers.isToggled()) {
            renderTracers(x, y, z, color, fadeAlpha);
        }
        
        if (nameTags.isToggled()) {
            renderNameTag(player, x, y, z, color, fadeAlpha);
        }
        
        GlStateManager.popMatrix();
    }

    private float calculateFadeAlpha(double distance) {
        if (distance <= fadeDistance.getInput()) {
            return 1.0f;
        } else {
            double fadeRange = 20.0; // Additional 20 blocks for fading
            if (distance <= fadeDistance.getInput() + fadeRange) {
                return (float) (1.0 - (distance - fadeDistance.getInput()) / fadeRange);
            }
            return 0.0f;
        }
    }

    private void renderModernBox(EntityPlayer player, double x, double y, double z, int color, float fadeAlpha) {
        float opacity = (float) boxOpacity.getInput() * fadeAlpha;
        float width = (float) boxWidth.getInput();
        
        AxisAlignedBB bbox = player.getEntityBoundingBox().expand(0.1, 0.1, 0.1);
        AxisAlignedBB bb = new AxisAlignedBB(
            bbox.minX - player.posX + x,
            bbox.minY - player.posY + y,
            bbox.minZ - player.posZ + z,
            bbox.maxX - player.posX + x,
            bbox.maxY - player.posY + y,
            bbox.maxZ - player.posZ + z
        );
        
        // Enable blending for transparency
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        
        float[] rgb = getRGB(color);
        GL11.glColor4f(rgb[0], rgb[1], rgb[2], opacity);
        GL11.glLineWidth(width);
        
        if (roundedCorners.isToggled()) {
            drawRoundedBox(bb, rgb[0], rgb[1], rgb[2], opacity, width);
        } else {
            drawBox(bb, rgb[0], rgb[1], rgb[2], opacity, width);
        }
        
        if (glowEffect.isToggled()) {
            renderGlowEffect(bb, rgb[0], rgb[1], rgb[2], opacity * 0.3f);
        }
        
        // Restore state
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
    }

    private void drawBox(AxisAlignedBB bb, float r, float g, float b, float a, float width) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        
        worldRenderer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        
        // Draw box edges - use separate lines instead of line strip
        // Bottom face
        worldRenderer.pos(bb.minX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        
        worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        
        worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        
        worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.minX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        
        // Top face
        worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
        
        worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
        
        worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
        
        worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
        
        // Vertical edges
        worldRenderer.pos(bb.minX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
        
        worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).color(r, g, b, a).endVertex();
        
        worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
        
        worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).color(r, g, b, a).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).color(r, g, b, a).endVertex();
        
        tessellator.draw();
    }

    private void drawRoundedBox(AxisAlignedBB bb, float r, float g, float b, float a, float width) {
        // Simplified rounded box - draw regular box with corner highlights
        drawBox(bb, r, g, b, a, width);
        
        // Add corner highlights for rounded effect
        float hr = Math.min(1.0f, r * 1.2f);
        float hg = Math.min(1.0f, g * 1.2f);
        float hb = Math.min(1.0f, b * 1.2f);
        float ha = Math.min(1.0f, a * 0.8f);
        GL11.glLineWidth(width + 1.0f);
        GL11.glColor4f(hr, hg, hb, ha);
        
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        worldRenderer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        
        float corner = 0.1f;
        
        // Corner lines
        worldRenderer.pos(bb.minX, bb.minY + corner, bb.minZ).color(hr, hg, hb, ha).endVertex();
        worldRenderer.pos(bb.minX + corner, bb.minY, bb.minZ).color(hr, hg, hb, ha).endVertex();
        
        worldRenderer.pos(bb.maxX - corner, bb.minY, bb.minZ).color(hr, hg, hb, ha).endVertex();
        worldRenderer.pos(bb.maxX, bb.minY + corner, bb.minZ).color(hr, hg, hb, ha).endVertex();
        
        worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ - corner).color(hr, hg, hb, ha).endVertex();
        worldRenderer.pos(bb.maxX - corner, bb.minY, bb.maxZ).color(hr, hg, hb, ha).endVertex();
        
        worldRenderer.pos(bb.minX + corner, bb.minY, bb.maxZ).color(hr, hg, hb, ha).endVertex();
        worldRenderer.pos(bb.minX, bb.minY, bb.maxZ - corner).color(hr, hg, hb, ha).endVertex();
        
        tessellator.draw();
    }

    private void renderGlowEffect(AxisAlignedBB bb, float r, float g, float b, float a) {
        GL11.glLineWidth(4.0f);
        drawBox(bb.expand(0.05, 0.05, 0.05), r, g, b, a * 0.3f, 4.0f);
    }

    private void renderSmoothHealthBar(EntityPlayer player, double x, double y, double z, float fadeAlpha) {
        double health = player.getHealth();
        double maxHealth = Math.max(1.0, player.getMaxHealth());
        float healthPercent = MathHelper.clamp_float((float) (health / maxHealth), 0.0f, 1.0f);
        
        float opacity = (float) healthOpacity.getInput() * fadeAlpha;
        
        // Calculate health color with smooth gradient
        Color healthColor = getSmoothHealthColor(healthPercent);
        float[] rgb = {healthColor.getRed() / 255f, healthColor.getGreen() / 255f, healthColor.getBlue() / 255f};
        
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        
        // Position health bar next to the box (right side)
        double barY = y + player.height / 2; // Center with entity
        double barX = x + player.width / 2 + 0.3; // Right side of box
        double barWidth = 0.8;
        double barHeight = 0.05;
        
        // Draw background
        GL11.glColor4f(0.1f, 0.1f, 0.1f, opacity * 0.8f);
        drawFilledRect(barX - barWidth/2, barY, barX + barWidth/2, barY + barHeight, z);
        
        // Draw health with smooth animation
        if (smoothHealth.isToggled()) {
            // Animated health bar
            double animatedHealth = getAnimatedHealth(player, healthPercent);
            GL11.glColor4f(rgb[0], rgb[1], rgb[2], opacity);
            drawFilledRect(barX - barWidth/2, barY, barX - barWidth/2 + barWidth * animatedHealth, barY + barHeight, z);
        } else {
            // Static health bar
            GL11.glColor4f(rgb[0], rgb[1], rgb[2], opacity);
            drawFilledRect(barX - barWidth/2, barY, barX - barWidth/2 + barWidth * healthPercent, barY + barHeight, z);
        }
        
        // Draw border
        GL11.glLineWidth(1.0f);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, opacity * 0.5f);
        drawRectOutline(barX - barWidth/2, barY, barX + barWidth/2, barY + barHeight, z);
        
        // Restore state
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
    }

    private Color getSmoothHealthColor(float healthPercent) {
        healthPercent = MathHelper.clamp_float(healthPercent, 0.0f, 1.0f);
        if (healthPercent > 0.6f) {
            return new Color(0, 255, 0); // Green
        } else if (healthPercent > 0.3f) {
            // Smooth transition from green to yellow to orange
            float ratio = (healthPercent - 0.3f) / 0.3f;
            int red = MathHelper.clamp_int((int) (255 * (1 - ratio)), 0, 255);
            int green = 255;
            return new Color(red, green, 0);
        } else {
            // Smooth transition from orange to red
            float ratio = healthPercent / 0.3f;
            int red = 255;
            int green = MathHelper.clamp_int((int) (165 * ratio), 0, 255);
            return new Color(red, green, 0);
        }
    }

    private double getAnimatedHealth(EntityPlayer player, float healthPercent) {
        int id = player.getEntityId();
        double display = Utils.HUD.healthDisplay.getOrDefault(id, (double) healthPercent);
        if (Math.abs(display - healthPercent) > 0.005) {
            display += (healthPercent - display) * 0.12;
        } else {
            display = healthPercent;
        }
        Utils.HUD.healthDisplay.put(id, display);
        return display;
    }

    private void renderTracers(double x, double y, double z, int color, float fadeAlpha) {
        float opacity = (float) tracerOpacity.getInput() * fadeAlpha;
        float width = (float) tracerWidth.getInput();
        
        float[] rgb = getRGB(color);
        
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glLineWidth(width);
        
        // Draw line from player to entity
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        worldRenderer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        
        // Start at player position (feet)
        worldRenderer.pos(0, mc.thePlayer.getEyeHeight(), 0).color(rgb[0], rgb[1], rgb[2], opacity * 0.3f).endVertex();
        // End at entity position (head)
        worldRenderer.pos(x, y + 1.6, z).color(rgb[0], rgb[1], rgb[2], opacity).endVertex();
        
        tessellator.draw();
        
        // Restore state
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
    }

    private void renderNameTag(EntityPlayer player, double x, double y, double z, int color, float fadeAlpha) {
        // This would require 2D rendering - simplified for now
        // You could implement this using the existing name tag system
    }

    private float[] getRGB(int color) {
        return new float[]{
            (color >> 16 & 255) / 255.0f,
            (color >> 8 & 255) / 255.0f,
            (color & 255) / 255.0f
        };
    }

    private void drawFilledRect(double x1, double y1, double x2, double y2, double z) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        worldRenderer.pos(x1, y1, z).endVertex();
        worldRenderer.pos(x2, y1, z).endVertex();
        worldRenderer.pos(x2, y2, z).endVertex();
        worldRenderer.pos(x1, y2, z).endVertex();
        tessellator.draw();
    }

    private void drawRectOutline(double x1, double y1, double x2, double y2, double z) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        worldRenderer.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION);
        worldRenderer.pos(x1, y1, z).endVertex();
        worldRenderer.pos(x2, y1, z).endVertex();
        worldRenderer.pos(x2, y2, z).endVertex();
        worldRenderer.pos(x1, y2, z).endVertex();
        tessellator.draw();
    }
}
