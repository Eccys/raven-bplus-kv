package keystrokesmod.client.module.modules.render;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;
import org.lwjgl.opengl.GL11;
import com.google.common.eventbus.Subscribe;
import keystrokesmod.client.event.impl.ForgeEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.Module.ModuleCategory;
import keystrokesmod.client.module.setting.impl.RGBSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.Utils;
import keystrokesmod.client.utils.RenderUtils;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;

public class BedESP extends Module {
    
    public static RGBSetting color;
    public static TickSetting rainbow;
    public static SliderSetting range;
    private int rgbColor;
    private final Set<BlockPos> cachedBeds = new HashSet<>();
    private long lastScanTime = 0L;
    private static final long SCAN_INTERVAL_MS = 500L;

    public BedESP() {
        super("BedESP", ModuleCategory.render);
        this.registerSetting(color = new RGBSetting("Color", 255, 0, 255));
        this.registerSetting(rainbow = new TickSetting("Rainbow", false));
        this.registerSetting(range = new SliderSetting("Range", 50.0, 10.0, 100.0, 5.0));
        this.rgbColor = color.getRGB();
    }

    @Override
    public void onEnable() {
        cachedBeds.clear();
        lastScanTime = 0L;
    }

    @Override
    public void onDisable() {
        cachedBeds.clear();
    }

    @Override
    public void guiUpdate() {
        this.rgbColor = color.getRGB();
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        if (fe.getEvent() instanceof RenderWorldLastEvent) {
            if (Utils.Player.isPlayerInGame()) {
                renderBeds();
            }
        }
    }

    private void updateBedCache() {
        cachedBeds.clear();
        int searchRadius = (int) range.getInput();
        int maxDistSq = searchRadius * searchRadius;
        BlockPos playerPos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        int minY = Math.max(0, playerPos.getY() - 32);
        int maxY = Math.min(255, playerPos.getY() + 32);

        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int z = -searchRadius; z <= searchRadius; z++) {
                if (x * x + z * z > maxDistSq) continue;
                int bx = playerPos.getX() + x;
                int bz = playerPos.getZ() + z;
                if (!mc.theWorld.isBlockLoaded(new BlockPos(bx, playerPos.getY(), bz))) continue;
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(bx, y, bz);
                    IBlockState state = mc.theWorld.getBlockState(pos);
                    if (state != null && state.getBlock() instanceof BlockBed) {
                        cachedBeds.add(pos);
                    }
                }
            }
        }
    }

    private void renderBeds() {
        this.rgbColor = color.getRGB();
        int renderColor = rainbow.isToggled() ? Utils.Client.rainbowDraw(1, 0) : this.rgbColor;

        long now = System.currentTimeMillis();
        if (now - lastScanTime >= SCAN_INTERVAL_MS) {
            updateBedCache();
            lastScanTime = now;
        }

        double maxDistSq = range.getInput() * range.getInput();
        for (BlockPos pos : cachedBeds) {
            if (mc.thePlayer.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= maxDistSq) {
                renderBedESP(pos, renderColor);
            }
        }
    }

    private void renderBedESP(BlockPos pos, int color) {
        // Setup rendering
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        
        // Calculate position relative to player
        double x = pos.getX() - mc.getRenderManager().viewerPosX;
        double y = pos.getY() - mc.getRenderManager().viewerPosY;
        double z = pos.getZ() - mc.getRenderManager().viewerPosZ;
        
        // Draw bed outline (bed is 0.5625 blocks high)
        drawBoundingBox(x, y, z, x + 1, y + 0.5625, z + 1, 2.0f, color);
        
        // Restore rendering state
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
    }
    
    private void drawBoundingBox(double x, double y, double z, double x2, double y2, double z2, float lineWidth, int color) {
        int alphaBits = (color >> 24) & 255;
        float alpha = alphaBits == 0 ? 1.0f : (alphaBits / 255.0f);
        float[] rgb = new float[]{
            (color >> 16 & 255) / 255.0f,
            (color >> 8 & 255) / 255.0f,
            (color & 255) / 255.0f,
            alpha
        };
        
        GL11.glColor4f(rgb[0], rgb[1], rgb[2], rgb[3]);
        GL11.glLineWidth(lineWidth);
        
        // Draw box edges
        GL11.glBegin(GL11.GL_LINES);
        
        // Bottom edges
        GL11.glVertex3d(x, y, z);
        GL11.glVertex3d(x2, y, z);
        
        GL11.glVertex3d(x2, y, z);
        GL11.glVertex3d(x2, y, z2);
        
        GL11.glVertex3d(x2, y, z2);
        GL11.glVertex3d(x, y, z2);
        
        GL11.glVertex3d(x, y, z2);
        GL11.glVertex3d(x, y, z);
        
        // Top edges
        GL11.glVertex3d(x, y2, z);
        GL11.glVertex3d(x2, y2, z);
        
        GL11.glVertex3d(x2, y2, z);
        GL11.glVertex3d(x2, y2, z2);
        
        GL11.glVertex3d(x2, y2, z2);
        GL11.glVertex3d(x, y2, z2);
        
        GL11.glVertex3d(x, y2, z2);
        GL11.glVertex3d(x, y2, z);
        
        // Vertical edges
        GL11.glVertex3d(x, y, z);
        GL11.glVertex3d(x, y2, z);
        
        GL11.glVertex3d(x2, y, z);
        GL11.glVertex3d(x2, y2, z);
        
        GL11.glVertex3d(x2, y, z2);
        GL11.glVertex3d(x2, y2, z2);
        
        GL11.glVertex3d(x, y, z2);
        GL11.glVertex3d(x, y2, z2);
        
        GL11.glEnd();
    }
}
