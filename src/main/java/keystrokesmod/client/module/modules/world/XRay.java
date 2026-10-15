package keystrokesmod.client.module.modules.world;

import java.util.HashSet;
import java.util.Set;

import org.lwjgl.opengl.GL11;

import com.google.common.eventbus.Subscribe;

import keystrokesmod.client.event.impl.ForgeEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.Module.ModuleCategory;
import keystrokesmod.client.module.setting.impl.DescriptionSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

public class XRay extends Module {
    public static DescriptionSetting desc;
    public static TickSetting showOres, showLava, showWater, showRedstone, showSpawners;
    public static TickSetting showChests, showDiamondsOnly, fadeDistant;
    public static SliderSetting renderDistance, opacity, lineWidth;
    
    private final Set<Block> visibleBlocks = new HashSet<>();
    private final Set<BlockPos> renderedBlocks = new HashSet<>();
    private long lastUpdateTime = 0;
    private static final int UPDATE_INTERVAL = 100; // Update every 100ms

    public XRay() {
        super("XRay", ModuleCategory.world);
        this.registerSetting(desc = new DescriptionSetting("See through walls"));
        
        // Block types to show
        this.registerSetting(showOres = new TickSetting("Show Ores", true));
        this.registerSetting(showLava = new TickSetting("Show Lava", false));
        this.registerSetting(showWater = new TickSetting("Show Water", false));
        this.registerSetting(showRedstone = new TickSetting("Show Redstone", false));
        this.registerSetting(showSpawners = new TickSetting("Show Spawners", true));
        this.registerSetting(showChests = new TickSetting("Show Chests", true));
        this.registerSetting(showDiamondsOnly = new TickSetting("Diamonds Only", false));
        
        // Visual settings
        this.registerSetting(fadeDistant = new TickSetting("Fade Distant", true));
        this.registerSetting(renderDistance = new SliderSetting("Render Distance", 50.0, 10.0, 100.0, 5.0));
        this.registerSetting(opacity = new SliderSetting("Opacity", 0.8, 0.1, 1.0, 0.1));
        this.registerSetting(lineWidth = new SliderSetting("Line Width", 2.0, 0.5, 5.0, 0.5));
    }

    @Override
    public void onEnable() {
        setupVisibleBlocks();
        renderedBlocks.clear();
    }

    @Override
    public void onDisable() {
        renderedBlocks.clear();
    }

    private void setupVisibleBlocks() {
        visibleBlocks.clear();
        
        if (showDiamondsOnly.isToggled()) {
            visibleBlocks.add(Blocks.diamond_ore);
            visibleBlocks.add(Blocks.diamond_block);
            visibleBlocks.add(Blocks.lit_redstone_ore); // Diamond redstone ore
        } else {
            if (showOres.isToggled()) {
                // All ores
                visibleBlocks.add(Blocks.iron_ore);
                visibleBlocks.add(Blocks.gold_ore);
                visibleBlocks.add(Blocks.diamond_ore);
                visibleBlocks.add(Blocks.emerald_ore);
                visibleBlocks.add(Blocks.coal_ore);
                visibleBlocks.add(Blocks.lapis_ore);
                visibleBlocks.add(Blocks.redstone_ore);
                visibleBlocks.add(Blocks.lit_redstone_ore);
                visibleBlocks.add(Blocks.quartz_ore);
                
                // Ore blocks
                visibleBlocks.add(Blocks.iron_block);
                visibleBlocks.add(Blocks.gold_block);
                visibleBlocks.add(Blocks.diamond_block);
                visibleBlocks.add(Blocks.emerald_block);
                visibleBlocks.add(Blocks.coal_block);
                visibleBlocks.add(Blocks.lapis_block);
                visibleBlocks.add(Blocks.quartz_block);
            }
            
            if (showLava.isToggled()) {
                visibleBlocks.add(Blocks.lava);
                visibleBlocks.add(Blocks.flowing_lava);
            }
            
            if (showWater.isToggled()) {
                visibleBlocks.add(Blocks.water);
                visibleBlocks.add(Blocks.flowing_water);
            }
            
            if (showRedstone.isToggled()) {
                visibleBlocks.add(Blocks.redstone_wire);
                visibleBlocks.add(Blocks.redstone_torch);
                visibleBlocks.add(Blocks.unlit_redstone_torch);
                visibleBlocks.add(Blocks.redstone_block);
                visibleBlocks.add(Blocks.stone_button);
                visibleBlocks.add(Blocks.wooden_button);
                visibleBlocks.add(Blocks.lever);
                visibleBlocks.add(Blocks.tripwire_hook);
                visibleBlocks.add(Blocks.tripwire);
            }
            
            if (showSpawners.isToggled()) {
                visibleBlocks.add(Blocks.mob_spawner);
            }
            
            if (showChests.isToggled()) {
                visibleBlocks.add(Blocks.chest);
                visibleBlocks.add(Blocks.trapped_chest);
                visibleBlocks.add(Blocks.ender_chest);
            }
        }
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        if (fe.getEvent() instanceof RenderWorldLastEvent) {
            RenderWorldLastEvent event = (RenderWorldLastEvent) fe.getEvent();
            if (Utils.Player.isPlayerInGame()) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastUpdateTime > UPDATE_INTERVAL) {
                    updateNearbyBlocks();
                    lastUpdateTime = currentTime;
                }
                renderXRayBlocks(Utils.Client.getTimer().renderPartialTicks);
            }
        }
    }

    private void updateNearbyBlocks() {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        
        renderedBlocks.clear();
        EntityPlayer player = mc.thePlayer;
        int range = (int) renderDistance.getInput();
        int chunkRadius = range / 16 + 1;
        
        for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
            for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
                int chunkX = (int) (player.posX / 16) + cx;
                int chunkZ = (int) (player.posZ / 16) + cz;
                
                if (!mc.theWorld.getChunkProvider().chunkExists(chunkX, chunkZ)) continue;
                
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < 256; y++) {
                            BlockPos pos = new BlockPos(
                                chunkX * 16 + x,
                                y,
                                chunkZ * 16 + z
                            );
                            
                            if (player.getDistance(pos.getX(), pos.getY(), pos.getZ()) > range) continue;
                            
                            IBlockState state = mc.theWorld.getBlockState(pos);
                            Block block = state.getBlock();
                            
                            if (visibleBlocks.contains(block)) {
                                renderedBlocks.add(pos);
                            }
                        }
                    }
                }
            }
        }
    }

    private void renderXRayBlocks(float partialTicks) {
        if (renderedBlocks.isEmpty()) return;
        
        EntityPlayer player = mc.thePlayer;
        double playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;
        
        GlStateManager.pushMatrix();
        
        // Setup rendering
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glLineWidth((float) lineWidth.getInput());
        
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        
        for (BlockPos pos : renderedBlocks) {
            double x = pos.getX() - playerX;
            double y = pos.getY() - playerY;
            double z = pos.getZ() - playerZ;
            
            double distance = Math.sqrt(x * x + y * y + z * z);
            float alpha = calculateAlpha(distance);
            
            if (alpha <= 0) continue;
            
            IBlockState state = mc.theWorld.getBlockState(pos);
            Block block = state.getBlock();
            float[] color = getBlockColor(block);
            
            GL11.glColor4f(color[0], color[1], color[2], alpha);
            
            // Render block outline
            renderBlockOutline(worldRenderer, x, y, z, color[0], color[1], color[2], alpha);
            
            // Render filled block if close enough
            if (distance < 20) {
                renderFilledBlock(worldRenderer, x, y, z, color[0], color[1], color[2], alpha * 0.3f);
            }
        }
        
        // Restore rendering state
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
        
        GlStateManager.popMatrix();
    }

    private float calculateAlpha(double distance) {
        if (!fadeDistant.isToggled()) {
            return (float) opacity.getInput();
        }
        
        double maxDistance = renderDistance.getInput();
        if (distance <= maxDistance * 0.5) {
            return (float) opacity.getInput();
        } else if (distance >= maxDistance) {
            return 0;
        } else {
            // Linear fade
            float fadeRange = (float) (maxDistance * 0.5);
            float fadeProgress = (float) ((distance - maxDistance * 0.5) / fadeRange);
            return (float) opacity.getInput() * (1.0f - fadeProgress);
        }
    }

    private float[] getBlockColor(Block block) {
        if (block == Blocks.diamond_ore || block == Blocks.diamond_block) {
            return new float[]{0.0f, 0.8f, 1.0f}; // Cyan
        } else if (block == Blocks.gold_ore || block == Blocks.gold_block) {
            return new float[]{1.0f, 0.8f, 0.0f}; // Gold
        } else if (block == Blocks.iron_ore || block == Blocks.iron_block) {
            return new float[]{0.7f, 0.7f, 0.7f}; // Iron
        } else if (block == Blocks.coal_ore || block == Blocks.coal_block) {
            return new float[]{0.2f, 0.2f, 0.2f}; // Coal
        } else if (block == Blocks.emerald_ore || block == Blocks.emerald_block) {
            return new float[]{0.0f, 1.0f, 0.0f}; // Emerald
        } else if (block == Blocks.lapis_ore || block == Blocks.lapis_block) {
            return new float[]{0.0f, 0.4f, 0.8f}; // Lapis
        } else if (block == Blocks.redstone_ore || block == Blocks.lit_redstone_ore || block == Blocks.redstone_block) {
            return new float[]{1.0f, 0.0f, 0.0f}; // Redstone
        } else if (block == Blocks.quartz_ore || block == Blocks.quartz_block) {
            return new float[]{0.9f, 0.9f, 0.9f}; // Quartz
        } else if (block == Blocks.lava || block == Blocks.flowing_lava) {
            return new float[]{1.0f, 0.4f, 0.0f}; // Lava orange
        } else if (block == Blocks.water || block == Blocks.flowing_water) {
            return new float[]{0.0f, 0.4f, 0.8f}; // Water blue
        } else if (block == Blocks.mob_spawner) {
            return new float[]{0.5f, 0.0f, 0.5f}; // Purple
        } else if (block == Blocks.chest || block == Blocks.trapped_chest) {
            return new float[]{0.8f, 0.6f, 0.4f}; // Chest brown
        } else if (block == Blocks.ender_chest) {
            return new float[]{0.4f, 0.0f, 0.6f}; // Ender chest purple
        } else {
            return new float[]{1.0f, 1.0f, 1.0f}; // White default
        }
    }

    private void renderBlockOutline(WorldRenderer worldRenderer, double x, double y, double z, float r, float g, float b, float a) {
        worldRenderer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        
        // Bottom face
        worldRenderer.pos(x, y, z).color(r, g, b, a).endVertex();
        worldRenderer.pos(x + 1, y, z).color(r, g, b, a).endVertex();
        
        worldRenderer.pos(x + 1, y, z).color(r, g, b, a).endVertex();
        worldRenderer.pos(x + 1, y, z + 1).color(r, g, b, a).endVertex();
        
        worldRenderer.pos(x + 1, y, z + 1).color(r, g, b, a).endVertex();
        worldRenderer.pos(x, y, z + 1).color(r, g, b, a).endVertex();
        
        worldRenderer.pos(x, y, z + 1).color(r, g, b, a).endVertex();
        worldRenderer.pos(x, y, z).color(r, g, b, a).endVertex();
        
        // Top face
        worldRenderer.pos(x, y + 1, z).color(r, g, b, a).endVertex();
        worldRenderer.pos(x + 1, y + 1, z).color(r, g, b, a).endVertex();
        
        worldRenderer.pos(x + 1, y + 1, z).color(r, g, b, a).endVertex();
        worldRenderer.pos(x + 1, y + 1, z + 1).color(r, g, b, a).endVertex();
        
        worldRenderer.pos(x + 1, y + 1, z + 1).color(r, g, b, a).endVertex();
        worldRenderer.pos(x, y + 1, z + 1).color(r, g, b, a).endVertex();
        
        worldRenderer.pos(x, y + 1, z + 1).color(r, g, b, a).endVertex();
        worldRenderer.pos(x, y + 1, z).color(r, g, b, a).endVertex();
        
        // Vertical edges
        worldRenderer.pos(x, y, z).color(r, g, b, a).endVertex();
        worldRenderer.pos(x, y + 1, z).color(r, g, b, a).endVertex();
        
        worldRenderer.pos(x + 1, y, z).color(r, g, b, a).endVertex();
        worldRenderer.pos(x + 1, y + 1, z).color(r, g, b, a).endVertex();
        
        worldRenderer.pos(x + 1, y, z + 1).color(r, g, b, a).endVertex();
        worldRenderer.pos(x + 1, y + 1, z + 1).color(r, g, b, a).endVertex();
        
        worldRenderer.pos(x, y, z + 1).color(r, g, b, a).endVertex();
        worldRenderer.pos(x, y + 1, z + 1).color(r, g, b, a).endVertex();
        
        Tessellator.getInstance().draw();
    }

    private void renderFilledBlock(WorldRenderer worldRenderer, double x, double y, double z, float r, float g, float b, float a) {
        worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        
        // All six faces
        // Bottom
        worldRenderer.pos(x, y, z).color(r, g, b, a).endVertex();
        worldRenderer.pos(x + 1, y, z).color(r, g, b, a).endVertex();
        worldRenderer.pos(x + 1, y, z + 1).color(r, g, b, a).endVertex();
        worldRenderer.pos(x, y, z + 1).color(r, g, b, a).endVertex();
        
        // Top
        worldRenderer.pos(x, y + 1, z).color(r, g, b, a).endVertex();
        worldRenderer.pos(x, y + 1, z + 1).color(r, g, b, a).endVertex();
        worldRenderer.pos(x + 1, y + 1, z + 1).color(r, g, b, a).endVertex();
        worldRenderer.pos(x + 1, y + 1, z).color(r, g, b, a).endVertex();
        
        // Front
        worldRenderer.pos(x, y, z).color(r, g, b, a).endVertex();
        worldRenderer.pos(x, y + 1, z).color(r, g, b, a).endVertex();
        worldRenderer.pos(x + 1, y + 1, z).color(r, g, b, a).endVertex();
        worldRenderer.pos(x + 1, y, z).color(r, g, b, a).endVertex();
        
        // Back
        worldRenderer.pos(x, y, z + 1).color(r, g, b, a).endVertex();
        worldRenderer.pos(x + 1, y, z + 1).color(r, g, b, a).endVertex();
        worldRenderer.pos(x + 1, y + 1, z + 1).color(r, g, b, a).endVertex();
        worldRenderer.pos(x, y + 1, z + 1).color(r, g, b, a).endVertex();
        
        // Left
        worldRenderer.pos(x, y, z).color(r, g, b, a).endVertex();
        worldRenderer.pos(x, y, z + 1).color(r, g, b, a).endVertex();
        worldRenderer.pos(x, y + 1, z + 1).color(r, g, b, a).endVertex();
        worldRenderer.pos(x, y + 1, z).color(r, g, b, a).endVertex();
        
        // Right
        worldRenderer.pos(x + 1, y, z).color(r, g, b, a).endVertex();
        worldRenderer.pos(x + 1, y + 1, z).color(r, g, b, a).endVertex();
        worldRenderer.pos(x + 1, y + 1, z + 1).color(r, g, b, a).endVertex();
        worldRenderer.pos(x + 1, y, z + 1).color(r, g, b, a).endVertex();
        
        Tessellator.getInstance().draw();
    }

    @Override
    public void guiUpdate() {
        setupVisibleBlocks();
    }
}
