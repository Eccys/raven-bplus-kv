package keystrokesmod.client.clickgui.kv;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import keystrokesmod.client.clickgui.kv.components.KvModuleSection;
import keystrokesmod.client.main.Raven;
import keystrokesmod.client.module.modules.client.GuiModule;
import keystrokesmod.client.utils.RenderUtils;
import keystrokesmod.client.utils.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;

public class KvCompactGui extends GuiScreen {

    private KvSection currentSection;
    private List<KvSection> sections;

    public static int containerX, containerY, containerWidth, containerHeight, horizontalBoarderY;
    private int a;
    private boolean open;


    public KvCompactGui() {
        sections = new ArrayList<KvSection>();
        sections.add(currentSection = new KvModuleSection());
        sections.add(new KvSection("terminal"));
    }

    @Override
    public void initGui() {
        containerWidth = (int) (this.width/1.5);
        containerHeight = (int) (this.height/1.5);
        containerX = (this.width/2) - (containerWidth/2);
        containerY = (this.height/2) - (containerHeight/2);
        horizontalBoarderY = containerY + (containerHeight/6);
        KvModuleSection.initGui(containerX, containerY, containerWidth, containerHeight);
        currentSection.refresh();
        int xOffSet = 0;
        for (KvSection section : sections) {
            section.setSectionCoords(containerX + (containerWidth/3) + xOffSet, containerY + ((containerHeight/6/2 ) - (section.getHeight()/2)));
            xOffSet += section.getWidth() + 5;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long currentTime = System.currentTimeMillis();
        
        // Enhanced animated background
        drawRect(0, 0, this.width, this.height, 0x90000000);
        
        // Add subtle gradient overlay
        for (int i = 0; i < 4; i++) {
            int alpha = (int) (15 * (1 - i * 0.25));
            int gradientColor = (alpha << 24) | (0x15 << 16) | (0x15 << 8) | 0x15;
            drawRect(0, i * (this.height / 4), this.width, (i + 1) * (this.height / 4), gradientColor);
        }
        
        // Enhanced container with glow effect
        float pulse = (float) Math.sin(currentTime * 0.003) * 0.5f + 0.5f;
        int glowAlpha = (int) (30 + pulse * 15);
        
        // Draw glow effect
        for (int i = 3; i >= 0; i--) {
            int glowSize = i * 4;
            int glowColor = (glowAlpha << 24) | Utils.Client.rainbowDraw(1, 0);
            RenderUtils.drawBorderedRoundedRect(
                    containerX - glowSize,
                    containerY - glowSize,
                    containerX + containerWidth + glowSize,
                    containerY + containerHeight + glowSize,
                    7 + glowSize,
                    3,
                    glowColor, 0x00000000);
        }
        
        // Main container with enhanced border
        int borderColor = Utils.Client.rainbowDraw(1, 0);
        RenderUtils.drawBorderedRoundedRect(
                containerX,
                containerY,
                containerX + containerWidth,
                containerY + containerHeight,
                7,
                3,
                borderColor, 0x90000000);

        // Enhanced raven icon with animation
        Minecraft.getMinecraft().getTextureManager().bindTexture(Raven.mResourceLocation);
        GL11.glEnable(GL11.GL_BLEND);
        
        // Add glow behind icon
        float iconPulse = (float) Math.sin(currentTime * 0.004) * 0.3f + 0.7f;
        int iconGlowAlpha = (int) (iconPulse * 40);
        for (int i = 2; i >= 0; i--) {
            int glowSize = i * 3;
            int glowColor = (iconGlowAlpha << 24) | (0xFF8C00 << 16) | (0x8C00 << 8) | 0x00;
            RenderUtils.drawRoundedRect(
                    containerX + 1 - glowSize,
                    containerY + 1 - glowSize,
                    containerX + (horizontalBoarderY - containerY) + 1 + glowSize,
                    containerY + (horizontalBoarderY - containerY) + 1 + glowSize,
                    5 + glowSize, glowColor);
        }
        
        GL11.glColor4f(1f, 1f, 1f, 0.9f);
        Gui.drawModalRectWithCustomSizedTexture(
                containerX + 1,
                containerY + 1,
                0,
                0,
                (horizontalBoarderY - containerY),
                (horizontalBoarderY - containerY),
                (horizontalBoarderY - containerY),
                (horizontalBoarderY - containerY));
        GL11.glDisable(GL11.GL_BLEND);

        // Enhanced horizontal border with animation
        float borderPulse = (float) Math.sin(currentTime * 0.005) * 0.4f + 0.6f;
        int borderAlpha = (int) (255 * borderPulse);
        int animatedBorderColor = (borderAlpha << 24) | Utils.Client.rainbowDraw(1, 0);
        
        // Draw main border
        Gui.drawRect(
                containerX,
                horizontalBoarderY,
                containerX + containerWidth,
                horizontalBoarderY + 1,
                animatedBorderColor);
        
        // Add glow effect to border
        for (int i = 1; i >= 0; i--) {
            int borderGlowAlpha = (int) (borderPulse * 30 * (1 - i * 0.5));
            int glowColor = (borderGlowAlpha << 24) | Utils.Client.rainbowDraw(1, 0);
            Gui.drawRect(
                    containerX,
                    horizontalBoarderY - i,
                    containerX + containerWidth,
                    horizontalBoarderY + 1 + i,
                    glowColor);
        }

        // Enhanced sections with animations
        for (KvSection section : sections) {
            section.drawSection(mouseX, mouseY, partialTicks);
        }

        currentSection.drawScreen(mouseX, mouseY, partialTicks);
        
        // Add subtle particle effects or additional visual enhancements
        drawFloatingParticles(currentTime);
    }
    
    private void drawFloatingParticles(long currentTime) {
        // Add subtle floating particles for visual enhancement
        GL11.glEnable(GL11.GL_BLEND);
        for (int i = 0; i < 5; i++) {
            float particleX = containerX + (float) (Math.sin(currentTime * 0.001 + i) * containerWidth * 0.8);
            float particleY = containerY + (float) (Math.cos(currentTime * 0.0015 + i * 2) * containerHeight * 0.8);
            float particleSize = 2 + (float) Math.sin(currentTime * 0.003 + i) * 1;
            int particleAlpha = (int) (30 + Math.sin(currentTime * 0.002 + i * 3) * 20);
            int particleColor = (particleAlpha << 24) | (0xFF8C00 << 16) | (0x8C00 << 8) | 0x00;
            
            drawRect((int) (particleX - particleSize/2), (int) (particleY - particleSize/2), 
                    (int) (particleX + particleSize/2), (int) (particleY + particleSize/2), particleColor);
        }
        GL11.glDisable(GL11.GL_BLEND);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        for (KvSection section : sections) {
            if (section.mouseClicked(mouseX, mouseY, mouseButton)) {
                return;
            }
        }
        if (currentSection != null) {
            currentSection.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public void mouseReleased(int x, int y, int button) {
        currentSection.mouseReleased(x, y, button);
    }

    public KvSection getCurrentSection() {
        return currentSection;
    }

    public void setCurrentSection(KvSection section) {
        currentSection = section;
    }

    @Override
    public void keyTyped(char t, int k) throws IOException {
        super.keyTyped(t, k);
        currentSection.keyTyped(t, k);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int i = Mouse.getEventDWheel();
        i = Integer.compare(i, 0);
        currentSection.scroll(i * 5f);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }


    @Override
    public void onGuiClosed() {
        Raven.configManager.save();
        Raven.clientConfig.saveConfig();
        Raven.mc.gameSettings.guiScale = GuiModule.guiScale;
    }

}
