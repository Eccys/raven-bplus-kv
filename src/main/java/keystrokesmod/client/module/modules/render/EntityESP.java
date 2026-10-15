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
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.module.setting.impl.DescriptionSetting;
import keystrokesmod.client.module.setting.impl.RGBSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.Utils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderWorldLastEvent;

public class EntityESP extends Module {
    
    public static DescriptionSetting desc;
    public static RGBSetting playerColor, mobColor, animalColor, itemColor, projectileColor;
    public static ComboSetting mode;
    public static TickSetting rainbow, showPlayers, showMobs, showAnimals, showItems, showProjectiles;
    public static TickSetting outline, filled, glow, showHealth, showDistance;
    public static TickSetting onlyHostile, onlyPassive, filterInvisible, showNames;
    public static SliderSetting renderDistance, lineWidth, opacity, fadeDistance;
    
    private int playerRgb, mobRgb, animalRgb, itemRgb, projectileRgb;
    
    public enum ESPMode {
        BOX,           // Box around entity
        OUTLINE,       // Outline only
        GLOW,          // Glow effect
        CORNER,        // Corner boxes
        TRACER,        // Tracers to entities
        CHAMS,         // Chams (colored model)
        COMBO          // Combination of multiple
    }

    public EntityESP() {
        super("EntityESP", ModuleCategory.render);
        this.registerSetting(desc = new DescriptionSetting("See all entities through walls"));
        
        // Colors
        this.registerSetting(playerColor = new RGBSetting("Player Color", 255, 0, 0));
        this.registerSetting(mobColor = new RGBSetting("Mob Color", 255, 255, 0));
        this.registerSetting(animalColor = new RGBSetting("Animal Color", 0, 255, 0));
        this.registerSetting(itemColor = new RGBSetting("Item Color", 0, 255, 255));
        this.registerSetting(projectileColor = new RGBSetting("Projectile Color", 255, 0, 255));
        this.registerSetting(rainbow = new TickSetting("Rainbow", false));
        this.registerSetting(mode = new ComboSetting("Mode", ESPMode.BOX));
        
        // Entity types
        this.registerSetting(showPlayers = new TickSetting("Show Players", true));
        this.registerSetting(showMobs = new TickSetting("Show Mobs", true));
        this.registerSetting(showAnimals = new TickSetting("Show Animals", false));
        this.registerSetting(showItems = new TickSetting("Show Items", false));
        this.registerSetting(showProjectiles = new TickSetting("Show Projectiles", false));
        
        // Filters
        this.registerSetting(onlyHostile = new TickSetting("Only Hostile", false));
        this.registerSetting(onlyPassive = new TickSetting("Only Passive", false));
        this.registerSetting(filterInvisible = new TickSetting("Filter Invisible", true));
        this.registerSetting(showNames = new TickSetting("Show Names", true));
        this.registerSetting(showHealth = new TickSetting("Show Health", true));
        this.registerSetting(showDistance = new TickSetting("Show Distance", true));
        
        // Visual settings
        this.registerSetting(outline = new TickSetting("Outline", true));
        this.registerSetting(filled = new TickSetting("Filled", false));
        this.registerSetting(glow = new TickSetting("Glow", false));
        this.registerSetting(renderDistance = new SliderSetting("Render Distance", 100.0, 20.0, 200.0, 5.0));
        this.registerSetting(lineWidth = new SliderSetting("Line Width", 2.0, 0.5, 5.0, 0.5));
        this.registerSetting(opacity = new SliderSetting("Opacity", 0.8, 0.1, 1.0, 0.1));
        this.registerSetting(fadeDistance = new SliderSetting("Fade Distance", 50.0, 20.0, 100.0, 5.0));
        guiUpdate();
    }

    @Override
    public void guiUpdate() {
        this.playerRgb = playerColor.getRGB();
        this.mobRgb = mobColor.getRGB();
        this.animalRgb = animalColor.getRGB();
        this.itemRgb = itemColor.getRGB();
        this.projectileRgb = projectileColor.getRGB();
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        if (fe.getEvent() instanceof RenderWorldLastEvent) {
            if (Utils.Player.isPlayerInGame()) {
                guiUpdate();
                renderEntities();
            }
        }
    }

    private void renderEntities() {
        Iterator<Entity> iterator = mc.theWorld.loadedEntityList.iterator();
        
        while (iterator.hasNext()) {
            Entity entity = iterator.next();
            
            if (!shouldRenderEntity(entity)) continue;
            
            double distance = mc.thePlayer.getDistanceToEntity(entity);
            if (distance > renderDistance.getInput()) continue;
            
            int color = getEntityColor(entity);
            float alpha = calculateAlpha(distance);
            if (alpha <= 0) continue;
            
            renderEntityESP(entity, color, alpha, distance);
        }
    }

    private boolean shouldRenderEntity(Entity entity) {
        if (entity == mc.thePlayer) return false;
        
        // Filter invisible entities
        if (filterInvisible.isToggled() && entity.isInvisible()) return false;
        
        // Filter bots
        if (entity instanceof EntityPlayer && AntiBot.bot((EntityPlayer) entity)) return false;
        
        // Check entity type filters
        if (entity instanceof EntityPlayer && !showPlayers.isToggled()) return false;
        if (entity instanceof EntityMob && !showMobs.isToggled()) return false;
        if (entity instanceof EntityAnimal && !showAnimals.isToggled()) return false;
        if (entity instanceof EntityItem && !showItems.isToggled()) return false;
        if (entity instanceof EntityArrow || entity instanceof EntityFireball) {
            if (!showProjectiles.isToggled()) return false;
        }
        
        // Hostile/Passive filters
        if (onlyHostile.isToggled() && !(entity instanceof EntityMob)) return false;
        if (onlyPassive.isToggled() && !(entity instanceof EntityAnimal)) return false;
        
        return true;
    }

    private int getEntityColor(Entity entity) {
        if (rainbow.isToggled()) {
            return Utils.Client.rainbowDraw(2L, 0L);
        }
        
        if (entity instanceof EntityPlayer) {
            return playerRgb;
        } else if (entity instanceof EntityMob) {
            return mobRgb;
        } else if (entity instanceof EntityAnimal) {
            return animalRgb;
        } else if (entity instanceof EntityItem) {
            return itemRgb;
        } else if (entity instanceof EntityArrow || entity instanceof EntityFireball) {
            return projectileRgb;
        }
        
        return 0xFFFFFF; // Default white
    }

    private void renderEntityESP(Entity entity, int color, float alpha, double distance) {
        ESPMode currentMode = (ESPMode) mode.getMode();
        
        switch (currentMode) {
            case BOX:
                renderBoxESP(entity, color, alpha);
                break;
            case OUTLINE:
                renderOutlineESP(entity, color, alpha);
                break;
            case GLOW:
                renderGlowESP(entity, color, alpha);
                break;
            case CORNER:
                renderCornerESP(entity, color, alpha);
                break;
            case TRACER:
                renderTracerESP(entity, color, alpha);
                break;
            case CHAMS:
                renderChamsESP(entity, color, alpha);
                break;
            case COMBO:
                renderComboESP(entity, color, alpha);
                break;
        }
        
        // Render additional info
        if (showNames.isToggled()) {
            renderEntityName(entity, color, alpha, distance);
        }
        
        if (showHealth.isToggled() && entity instanceof EntityLivingBase) {
            renderEntityHealth((EntityLivingBase) entity, color, alpha, distance);
        }
    }

    private void renderBoxESP(Entity entity, int color, float alpha) {
        float[] rgb = getRGB(color);
        
        // Get entity bounding box
        AxisAlignedBB bb = entity.getEntityBoundingBox();
        
        // Move to player position
        double x = bb.minX - mc.getRenderManager().viewerPosX;
        double y = bb.minY - mc.getRenderManager().viewerPosY;
        double z = bb.minZ - mc.getRenderManager().viewerPosZ;
        
        AxisAlignedBB bb2 = new AxisAlignedBB(x, y, z, 
                                           x + (bb.maxX - bb.minX), 
                                           y + (bb.maxY - bb.minY), 
                                           z + (bb.maxZ - bb.minZ));
        
        setupRendering();
        GL11.glColor4f(rgb[0], rgb[1], rgb[2], alpha * (float) opacity.getInput());
        GL11.glLineWidth((float) lineWidth.getInput());
        
        if (filled.isToggled()) {
            drawFilledBox(bb2, rgb[0], rgb[1], rgb[2], alpha * (float) opacity.getInput() * 0.3f);
        }
        
        if (outline.isToggled()) {
            drawBoxOutline(bb2, rgb[0], rgb[1], rgb[2], alpha * (float) opacity.getInput());
        }
        
        restoreRendering();
    }

    private void renderOutlineESP(Entity entity, int color, float alpha) {
        float[] rgb = getRGB(color);
        
        AxisAlignedBB bb = entity.getEntityBoundingBox();
        double x = bb.minX - mc.getRenderManager().viewerPosX;
        double y = bb.minY - mc.getRenderManager().viewerPosY;
        double z = bb.minZ - mc.getRenderManager().viewerPosZ;
        
        AxisAlignedBB bb2 = new AxisAlignedBB(x, y, z, 
                                           x + (bb.maxX - bb.minX), 
                                           y + (bb.maxY - bb.minY), 
                                           z + (bb.maxZ - bb.minZ));
        
        setupRendering();
        GL11.glColor4f(rgb[0], rgb[1], rgb[2], alpha * (float) opacity.getInput());
        GL11.glLineWidth((float) lineWidth.getInput());
        
        drawBoxOutline(bb2, rgb[0], rgb[1], rgb[2], alpha * (float) opacity.getInput());
        restoreRendering();
    }

    private void renderGlowESP(Entity entity, int color, float alpha) {
        float[] rgb = getRGB(color);
        
        AxisAlignedBB bb = entity.getEntityBoundingBox();
        double x = bb.minX - mc.getRenderManager().viewerPosX;
        double y = bb.minY - mc.getRenderManager().viewerPosY;
        double z = bb.minZ - mc.getRenderManager().viewerPosZ;
        
        setupRendering();
        
        // Draw multiple expanding boxes for glow effect
        for (int i = 0; i < 3; i++) {
            double expand = 0.1 + (i * 0.05);
            float glowAlpha = alpha * (float) opacity.getInput() * (1.0f - (i * 0.3f));
            
            AxisAlignedBB bb2 = new AxisAlignedBB(x - expand, y - expand, z - expand, 
                                               x + (bb.maxX - bb.minX) + expand, 
                                               y + (bb.maxY - bb.minY) + expand, 
                                               z + (bb.maxZ - bb.minZ) + expand);
            
            GL11.glColor4f(rgb[0], rgb[1], rgb[2], glowAlpha);
            GL11.glLineWidth((float) lineWidth.getInput() * (1.0f - i * 0.2f));
            
            drawBoxOutline(bb2, rgb[0], rgb[1], rgb[2], glowAlpha);
        }
        
        restoreRendering();
    }

    private void renderCornerESP(Entity entity, int color, float alpha) {
        float[] rgb = getRGB(color);
        
        AxisAlignedBB bb = entity.getEntityBoundingBox();
        double x = bb.minX - mc.getRenderManager().viewerPosX;
        double y = bb.minY - mc.getRenderManager().viewerPosY;
        double z = bb.minZ - mc.getRenderManager().viewerPosZ;
        
        double width = bb.maxX - bb.minX;
        double height = bb.maxY - bb.minY;
        double depth = bb.maxZ - bb.minZ;
        
        setupRendering();
        GL11.glColor4f(rgb[0], rgb[1], rgb[2], alpha * (float) opacity.getInput());
        GL11.glLineWidth((float) lineWidth.getInput());
        
        double cornerSize = 0.1;
        
        // Draw corners
        drawCornerBox(x, y, z, cornerSize, rgb[0], rgb[1], rgb[2], alpha * (float) opacity.getInput());
        drawCornerBox(x + width - cornerSize, y, z, cornerSize, rgb[0], rgb[1], rgb[2], alpha * (float) opacity.getInput());
        drawCornerBox(x, y, z + depth - cornerSize, cornerSize, rgb[0], rgb[1], rgb[2], alpha * (float) opacity.getInput());
        drawCornerBox(x + width - cornerSize, y, z + depth - cornerSize, cornerSize, rgb[0], rgb[1], rgb[2], alpha * (float) opacity.getInput());
        
        drawCornerBox(x, y + height - cornerSize, z, cornerSize, rgb[0], rgb[1], rgb[2], alpha * (float) opacity.getInput());
        drawCornerBox(x + width - cornerSize, y + height - cornerSize, z, cornerSize, rgb[0], rgb[1], rgb[2], alpha * (float) opacity.getInput());
        drawCornerBox(x, y + height - cornerSize, z + depth - cornerSize, cornerSize, rgb[0], rgb[1], rgb[2], alpha * (float) opacity.getInput());
        drawCornerBox(x + width - cornerSize, y + height - cornerSize, z + depth - cornerSize, cornerSize, rgb[0], rgb[1], rgb[2], alpha * (float) opacity.getInput());
        
        restoreRendering();
    }

    private void renderTracerESP(Entity entity, int color, float alpha) {
        float[] rgb = getRGB(color);
        
        // Calculate entity position
        double entityX = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * Utils.Client.getTimer().renderPartialTicks - mc.getRenderManager().viewerPosX;
        double entityY = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * Utils.Client.getTimer().renderPartialTicks - mc.getRenderManager().viewerPosY;
        double entityZ = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * Utils.Client.getTimer().renderPartialTicks - mc.getRenderManager().viewerPosZ;
        
        // Player position (feet)
        double playerX = 0;
        double playerY = mc.thePlayer.getEyeHeight();
        double playerZ = 0;
        
        setupRendering();
        GL11.glColor4f(rgb[0], rgb[1], rgb[2], alpha * (float) opacity.getInput());
        GL11.glLineWidth((float) lineWidth.getInput());
        
        drawLine(playerX, playerY, playerZ, entityX, entityY, entityZ, rgb[0], rgb[1], rgb[2], alpha * (float) opacity.getInput());
        restoreRendering();
    }

    private void renderChamsESP(Entity entity, int color, float alpha) {
        // Chams would require modifying entity rendering
        // This is a simplified implementation
        renderBoxESP(entity, color, alpha);
    }

    private void renderComboESP(Entity entity, int color, float alpha) {
        // Combine multiple ESP modes
        renderBoxESP(entity, color, alpha);
        renderTracerESP(entity, color, alpha * 0.5f);
    }

    private void renderEntityName(Entity entity, int color, float alpha, double distance) {
        // This would require 2D rendering - simplified for now
        // You could implement text rendering using the existing font system
    }

    private void renderEntityHealth(EntityLivingBase entity, int color, float alpha, double distance) {
        // This would require 2D rendering - simplified for now
        // You could implement health bar rendering
    }

    private void setupRendering() {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
    }

    private void restoreRendering() {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
    }

    private void drawBoxOutline(AxisAlignedBB bb, float r, float g, float b, float a) {
        // Draw box edges
        drawLine(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.minZ, r, g, b, a);
        drawLine(bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.maxZ, r, g, b, a);
        drawLine(bb.maxX, bb.minY, bb.maxZ, bb.minX, bb.minY, bb.maxZ, r, g, b, a);
        drawLine(bb.minX, bb.minY, bb.maxZ, bb.minX, bb.minY, bb.minZ, r, g, b, a);
        
        drawLine(bb.minX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.minZ, r, g, b, a);
        drawLine(bb.maxX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ, r, g, b, a);
        drawLine(bb.maxX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ, r, g, b, a);
        drawLine(bb.minX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.minZ, r, g, b, a);
        
        drawLine(bb.minX, bb.minY, bb.minZ, bb.minX, bb.maxY, bb.minZ, r, g, b, a);
        drawLine(bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.minZ, r, g, b, a);
        drawLine(bb.maxX, bb.minY, bb.maxZ, bb.maxX, bb.maxY, bb.maxZ, r, g, b, a);
        drawLine(bb.minX, bb.minY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ, r, g, b, a);
    }

    private void drawFilledBox(AxisAlignedBB bb, float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, a);
        Utils.HUD.dbb(bb, r, g, b);
    }

    private void drawCornerBox(double x, double y, double z, double size, float r, float g, float b, float a) {
        AxisAlignedBB bb = new AxisAlignedBB(x, y, z, x + size, y + size, z + size);
        drawBoxOutline(bb, r, g, b, a);
    }

    private void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x2, y2, z2);
        GL11.glEnd();
    }

    private float[] getRGB(int color) {
        return new float[]{
            (color >> 16 & 255) / 255.0f,
            (color >> 8 & 255) / 255.0f,
            (color & 255) / 255.0f
        };
    }

    private float calculateAlpha(double distance) {
        double maxDistance = renderDistance.getInput();
        double fadeDist = fadeDistance.getInput();
        if (distance <= fadeDist || maxDistance <= fadeDist) {
            return distance <= maxDistance ? 1.0f : 0.0f;
        } else if (distance >= maxDistance) {
            return 0.0f;
        } else {
            return (float) (1.0 - (distance - fadeDist) / (maxDistance - fadeDist));
        }
    }
}
