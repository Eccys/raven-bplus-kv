package keystrokesmod.client.clickgui.raven;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import keystrokesmod.client.main.Raven;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.Module.ModuleCategory;
import keystrokesmod.client.module.modules.client.GuiModule;
import keystrokesmod.client.module.setting.Setting;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.RenderUtils;
import keystrokesmod.client.utils.Utils;
import keystrokesmod.client.utils.ParticleSystem;
import keystrokesmod.client.utils.font.FontUtil;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;

public class ModernClickGui extends GuiScreen {
    private final int width = 600;
    private final int height = 450;
    private int x, y;
    private ModuleCategory selectedCategory = ModuleCategory.combat;
    private final List<CategoryTab> tabs = new ArrayList<>();
    private final List<ModuleButton> moduleButtons = new ArrayList<>();
    private double scrollAmount = 0;
    private int totalModuleHeight = 0;
    private float openAnimation = 0f;
    private long openStartTime = 0;
    private float searchAnimation = 0f;
    private boolean searchMode = false;
    private String searchText = "";
    private int hoveredTab = -1;
    private float tabAnimation[] = new float[ModuleCategory.values().length];
    private ParticleSystem particleSystem;

    public ModernClickGui() {
        for (ModuleCategory category : ModuleCategory.values()) {
            if (category != ModuleCategory.category && category != ModuleCategory.sumo) {
                tabs.add(new CategoryTab(category));
                tabAnimation[category.ordinal()] = 0f;
            }
        }
        this.openStartTime = System.currentTimeMillis();
        this.particleSystem = new ParticleSystem(100);
    }

    @Override
    public void initGui() {
        ScaledResolution sr = new ScaledResolution(mc);
        this.x = (sr.getScaledWidth() - width) / 2;
        this.y = (sr.getScaledHeight() - height) / 2;
        refreshModules();
    }

    private void refreshModules() {
        moduleButtons.clear();
        List<Module> modules = Raven.moduleManager.getModulesInCategory(selectedCategory);
        for (Module m : modules) {
            moduleButtons.add(new ModuleButton(m));
        }
        updateTotalHeight();
    }

    private void updateTotalHeight() {
        totalModuleHeight = 0;
        for (ModuleButton btn : moduleButtons) {
            totalModuleHeight += btn.getHeight() + 5;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long currentTime = System.currentTimeMillis();
        
        // Calculate opening animation
        float deltaTime = (currentTime - openStartTime) / 1000f;
        openAnimation = Math.min(1f, deltaTime * 4f);
        
        // Background with blur
        drawBackground(mouseX, mouseY, openAnimation);
        
        // Calculate window position with animation
        float scale = 0.9f + (1f - 0.9f) * openAnimation;
        float alpha = openAnimation;
        
        int animatedX = x + (int) ((width * (1 - scale)) / 2);
        int animatedY = y + (int) ((height * (1 - scale)) / 2);
        int animatedWidth = (int) (width * scale);
        int animatedHeight = (int) (height * scale);
        
        // Main window
        drawMainWindow(animatedX, animatedY, animatedWidth, animatedHeight, alpha);
        
        // Draw top navigation tabs
        drawTopTabs(animatedX, animatedY, animatedWidth, mouseX, mouseY, alpha);
        
        // Search bar
        drawSearchBar(animatedX, animatedY, animatedWidth, mouseX, mouseY, alpha);
        
        // Module grid
        drawModuleGrid(animatedX, animatedY, animatedWidth, animatedHeight, mouseX, mouseY, alpha);
        
        // Watermark
        drawWatermark(animatedX, animatedY, animatedWidth, animatedHeight, alpha);
    }
    
    private void drawBackground(int mouseX, int mouseY, float alpha) {
        // Modern gradient background
        for (int i = 0; i < 3; i++) {
            int bgAlpha = (int) ((15 - i * 3) * alpha);
            drawRect(0, 0, this.width, this.height, (bgAlpha << 24));
        }
        ScaledResolution sr = new ScaledResolution(mc);
        particleSystem.render(mouseX, mouseY, sr.getScaledWidth(), sr.getScaledHeight(), alpha);
    }
    
    private void drawMainWindow(int x, int y, int width, int height, float alpha) {
        // Main window with subtle shadow
        int shadowColor = (int) (alpha * 0x20) << 24 | 0x000000;
        RenderUtils.drawRoundedRect(x + 4, y + 4, x + width + 4, y + height + 4, 12, shadowColor);
        
        // Main background
        int bgColor = (int) (alpha * 0xF0) << 24 | 0x1A1A1A;
        RenderUtils.drawRoundedRect(x, y, x + width, y + height, 12, bgColor);
        
        // Subtle border
        int borderColor = (int) (alpha * 0xFF) << 24 | 0x2A2A2A;
        RenderUtils.drawRoundedOutline(x, y, x + width, y + height, 12, 2, borderColor);
    }
    
    private void drawTopTabs(int x, int y, int width, int mouseX, int mouseY, float alpha) {
        int tabWidth = 80;
        int tabHeight = 30;
        int tabSpacing = 10;
        int totalTabsWidth = tabs.size() * (tabWidth + tabSpacing);
        int startX = x + (width - totalTabsWidth) / 2;
        
        hoveredTab = -1;
        int tabIndex = 0;
        
        for (CategoryTab tab : tabs) {
            int tabX = startX + tabIndex * (tabWidth + tabSpacing);
            int tabY = y + 5;
            boolean isSelected = tab.category == selectedCategory;
            boolean isHovered = mouseX >= tabX && mouseX <= tabX + tabWidth && 
                               mouseY >= tabY && mouseY <= tabY + tabHeight;
            
            if (isHovered) hoveredTab = tabIndex;
            
            // Animate tab
            long currentTime = System.currentTimeMillis();
            float deltaTime = (currentTime - tab.lastAnimationTime) / 1000f;
            float targetProgress = isSelected ? 1f : (isHovered ? 0.3f : 0f);
            tabAnimation[tab.category.ordinal()] += (targetProgress - tabAnimation[tab.category.ordinal()]) * deltaTime * 8f;
            tabAnimation[tab.category.ordinal()] = Math.max(0f, Math.min(1f, tabAnimation[tab.category.ordinal()]));
            
            float animProgress = tabAnimation[tab.category.ordinal()];
            
            // Tab background
            int tabBgColor;
            if (isSelected) {
                tabBgColor = (int) (animProgress * alpha * 0xFF) << 24 | 0xFF8C00;
            } else if (isHovered) {
                tabBgColor = (int) (animProgress * alpha * 0x80) << 24 | 0x404040;
            } else {
                tabBgColor = (int) (animProgress * alpha * 0x60) << 24 | 0x303030;
            }
            
            RenderUtils.drawRoundedRect(tabX, tabY, tabX + tabWidth, tabY + tabHeight, 6, tabBgColor);
            
            // Tab text
            int textColor = isSelected ? 0xFFFFFF : 0xCCCCCC;
            FontUtil.normal.drawCenteredSmoothString(tab.category.getName(), tabX + tabWidth/2, tabY + tabHeight/2 - 4, textColor);
            
            tabIndex++;
        }
    }
    
    private void drawSearchBar(int x, int y, int width, int mouseX, int mouseY, float alpha) {
        int searchBarY = y + 45;
        int searchBarWidth = 200;
        int searchBarX = x + (width - searchBarWidth) / 2;
        
        // Search bar background
        int bgColor = (int) (alpha * 0x20) << 24 | 0x2A2A2A;
        RenderUtils.drawRoundedRect(searchBarX, searchBarY, searchBarX + searchBarWidth, searchBarY + 25, 8, bgColor);
        
        // Search text
        int textColor = (int) (alpha * 0xFF) << 24 | 0xCCCCCC;
        String displayText = searchText.isEmpty() ? "Search modules..." : searchText;
        FontUtil.normal.drawSmoothString(displayText, searchBarX + 10, searchBarY + 8, textColor);
        
        // Search icon
        if (searchText.isEmpty()) {
            FontUtil.normal.drawSmoothString("🔍", searchBarX + searchBarWidth - 25, searchBarY + 6, textColor);
        }
    }
    
    private void drawModuleGrid(int x, int y, int width, int height, int mouseX, int mouseY, float alpha) {
        int gridStartY = y + 80;
        int gridStartX = x + 20;
        int gridWidth = width - 40;
        int gridHeight = height - 100;
        
        // Filter modules based on search
        List<ModuleButton> filteredModules = new ArrayList<>();
        for (ModuleButton btn : moduleButtons) {
            if (searchText.isEmpty() || 
                btn.module.getName().toLowerCase().contains(searchText.toLowerCase())) {
                filteredModules.add(btn);
            }
        }
        
        // Calculate grid layout
        int moduleWidth = 180;
        int moduleHeight = 35;
        int spacing = 10;
        int modulesPerRow = Math.max(1, (gridWidth + spacing) / (moduleWidth + spacing));
        
        int totalModuleHeight = ((filteredModules.size() + modulesPerRow - 1) / modulesPerRow) * (moduleHeight + spacing);
        
        // Enable scissor for module area
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        ScaledResolution sr = new ScaledResolution(mc);
        int factor = sr.getScaleFactor();
        GL11.glScissor(gridStartX * factor, (mc.displayHeight - (gridStartY + gridHeight)) * factor, 
                     gridWidth * factor, gridHeight * factor);
        
        // Calculate scroll
        int maxScroll = Math.max(0, totalModuleHeight - gridHeight);
        if (scrollAmount > maxScroll) scrollAmount = maxScroll;
        if (scrollAmount < 0) scrollAmount = 0;
        
        float smoothScroll = (float) (scrollAmount * 0.9);
        int currentY = gridStartY - (int) smoothScroll;
        
        // Draw modules in grid
        int moduleIndex = 0;
        for (int row = 0; row < 100; row++) {
            for (int col = 0; col < modulesPerRow; col++) {
                if (moduleIndex >= filteredModules.size()) break;
                
                ModuleButton btn = filteredModules.get(moduleIndex);
                int moduleX = gridStartX + col * (moduleWidth + spacing);
                int moduleY = currentY + row * (moduleHeight + spacing);
                
                if (moduleY >= gridStartY + gridHeight) break;
                
                btn.drawGrid(moduleX, moduleY, moduleWidth, moduleHeight, mouseX, mouseY, alpha);
                moduleIndex++;
            }
        }
        
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        
        // Draw scrollbar if needed
        if (totalModuleHeight > gridHeight) {
            drawScrollbar(gridStartX + gridWidth, gridStartY, gridHeight, totalModuleHeight, alpha);
        }
    }
    
    private void drawScrollbar(int x, int y, int height, int totalHeight, float alpha) {
        int scrollbarWidth = 6;
        int scrollbarHeight = Math.max(30, (int) (height * height / totalHeight));
        int scrollPercent = (int) ((-scrollAmount) / (float) (totalModuleHeight - height));
        int scrollbarY = y + (int) ((height - scrollbarHeight) * scrollPercent / 100f);
        
        // Scrollbar background
        int bgColor = (int) (alpha * 0x40) << 24 | 0x404040;
        RenderUtils.drawRoundedRect(x + 2, y, x + scrollbarWidth - 2, y + height, 3, bgColor);
        
        // Scrollbar thumb
        int thumbColor = (int) (alpha * 0xFF) << 24 | 0xFF8C00;
        RenderUtils.drawRoundedRect(x + 2, scrollbarY, x + scrollbarWidth - 2, scrollbarY + scrollbarHeight, 3, thumbColor);
    }
    
    private void drawWatermark(int x, int y, int width, int height, float alpha) {
        int watermarkColor = (int) (alpha * 0x80) << 24 | 0xFF8C00;
        FontUtil.normal.drawCenteredSmoothString("RAVEN B++", x + width/2, y + height - 20, watermarkColor);
    }
    
    private void drawEnhancedScrollbar(int x, int y, int width, int height, float alpha) {
        float scrollPercent = (float) (-scrollAmount) / (totalModuleHeight - (height - 20));
        int scrollbarHeight = Math.max(25, (int) ((height - 45) * (height - 20) / totalModuleHeight));
        int scrollbarY = y + 22 + (int) ((height - 45 - scrollbarHeight) * scrollPercent);
        int scrollbarX = x + width - 18;
        
        // Animated scrollbar with glow
        float pulse = (float) Math.sin(System.currentTimeMillis() * 0.004) * 0.3f + 0.7f;
        
        for (int i = 0; i < 3; i++) {
            int glowAlpha = (int) ((100 - i * 30) * alpha * pulse);
            int offset = i;
            RenderUtils.drawRoundedRect(
                    scrollbarX - offset, scrollbarY - offset, 
                    scrollbarX + 8 + offset, scrollbarY + scrollbarHeight + offset, 
                    4, (glowAlpha << 24) | (0xFF8C00 << 16) | (0x8C00 << 8) | 0x00);
        }
    }
    
    private void drawCornerDecorations(int x, int y, int width, int height, float alpha) {
        long currentTime = System.currentTimeMillis();
        
        // Animated corner decorations
        for (int corner = 0; corner < 4; corner++) {
            float cornerPulse = (float) Math.sin(currentTime * 0.002 + corner * Math.PI / 2) * 0.5f + 0.5f;
            int cornerAlpha = (int) (50 * alpha * cornerPulse);
            int cornerColor = (cornerAlpha << 24) | (0xFF8C00 << 16) | (0x8C00 << 8) | 0x00;
            
            int cornerX = (corner == 1 || corner == 2) ? x + width - 10 : x;
            int cornerY = (corner >= 2) ? y + height - 10 : y;
            
            RenderUtils.drawRoundedRect(cornerX, cornerY, cornerX + 10, cornerY + 10, 2, cornerColor);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        // Check for search bar click
        int searchBarY = y + 45;
        int searchBarWidth = 200;
        int searchBarX = x + (width - searchBarWidth) / 2;
        
        if (mouseX >= searchBarX && mouseX <= searchBarX + searchBarWidth && 
            mouseY >= searchBarY && mouseY <= searchBarY + 25) {
            // Toggle search mode
            searchMode = !searchMode;
            if (!searchMode) searchText = "";
            return;
        }
        
        // Check for tab clicks
        int tabWidth = 80;
        int tabSpacing = 10;
        int totalTabsWidth = tabs.size() * (tabWidth + tabSpacing);
        int startX = x + (width - totalTabsWidth) / 2;
        int tabY = y + 5;
        int tabIndex = 0;
        
        for (CategoryTab tab : tabs) {
            int tabX = startX + tabIndex * (tabWidth + tabSpacing);
            if (mouseX >= tabX && mouseX <= tabX + tabWidth && 
                mouseY >= tabY && mouseY <= tabY + 30) {
                selectedCategory = tab.category;
                scrollAmount = 0;
                refreshModules();
                return;
            }
            tabIndex++;
        }
        
        // Check for module grid clicks
        int gridStartY = y + 80;
        int gridStartX = x + 20;
        int gridWidth = width - 40;
        int gridHeight = height - 100;
        
        if (mouseX >= gridStartX && mouseX <= gridStartX + gridWidth && 
            mouseY >= gridStartY && mouseY <= gridStartY + gridHeight) {
            
            // Filter modules based on search
            List<ModuleButton> filteredModules = new ArrayList<>();
            for (ModuleButton btn : moduleButtons) {
                if (searchText.isEmpty() || 
                    btn.module.getName().toLowerCase().contains(searchText.toLowerCase())) {
                    filteredModules.add(btn);
                }
            }
            
            // Calculate grid layout
            int moduleWidth = 180;
            int moduleHeight = 35;
            int spacing = 10;
            int modulesPerRow = Math.max(1, (gridWidth + spacing) / (moduleWidth + spacing));
            float smoothScroll = (float) (scrollAmount * 0.9);
            int currentY = gridStartY - (int) smoothScroll;
            
            int moduleIndex = 0;
            for (int row = 0; row < 100; row++) {
                for (int col = 0; col < modulesPerRow; col++) {
                    if (moduleIndex >= filteredModules.size()) break;
                    
                    ModuleButton btn = filteredModules.get(moduleIndex);
                    int moduleX = gridStartX + col * (moduleWidth + spacing);
                    int moduleY = currentY + row * (moduleHeight + spacing);
                    
                    if (btn.isMouseOverGrid(mouseX, mouseY, moduleX, moduleY, moduleWidth, btn.getHeight())) {
                        btn.onClick(mouseX, mouseY, mouseButton, moduleX, moduleY);
                        updateTotalHeight();
                        return;
                    }
                    moduleIndex++;
                }
            }
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int mouseButton) {
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dw = Mouse.getEventDWheel();
        if (dw != 0) {
            scrollAmount += (dw > 0 ? -50 : 50);
            int maxScroll = Math.max(0, totalModuleHeight - (height - 100));
            if (scrollAmount < 0) scrollAmount = 0;
            if (scrollAmount > maxScroll) scrollAmount = maxScroll;
        }
    }

    private class CategoryTab {
        ModuleCategory category;
        private float animationProgress = 0f;
        private long lastAnimationTime = 0;

        CategoryTab(ModuleCategory category) {
            this.category = category;
            this.lastAnimationTime = System.currentTimeMillis();
        }

        void draw(int tx, int ty, int mx, int my, boolean selected, float globalAlpha) {
            boolean hover = mx >= tx && mx <= tx + 95 && my >= ty && my <= ty + 35;
            
            // Smooth animation
            long currentTime = System.currentTimeMillis();
            float deltaTime = (currentTime - lastAnimationTime) / 1000f;
            lastAnimationTime = currentTime;
            
            float targetProgress = selected ? 1f : (hover ? 0.5f : 0f);
            animationProgress += (targetProgress - animationProgress) * deltaTime * 8f;
            animationProgress = Math.max(0f, Math.min(1f, animationProgress));
            
            // Enhanced background with animation
            if (animationProgress > 0.01f) {
                int bgColor = (int) (animationProgress * 0x60 * globalAlpha) << 24 | 0xFF8C00;
                RenderUtils.drawRoundedRect(tx, ty, tx + 95, ty + 35, 8, bgColor);
                
                // Add glow effect for selected tabs
                if (selected) {
                    float glowPulse = (float) Math.sin(currentTime * 0.003) * 0.4f + 0.6f;
                    int glowAlpha = (int) (animationProgress * glowPulse * 40 * globalAlpha);
                    for (int i = 3; i >= 0; i--) {
                        int glowSize = i * 3;
                        int glowColor = (glowAlpha << 24) | (0xFF8C00 << 16) | (0x8C00 << 8) | 0x00;
                        RenderUtils.drawRoundedRect(tx - glowSize, ty - glowSize, tx + 95 + glowSize, ty + 35 + glowSize, 
                                8 + glowSize, glowColor);
                    }
                }
            }
            
            // Enhanced text with animation
            int color = selected ? 0xFFFF8C00 : (hover ? 0xFFE0E0E0 : 0xFFAAAAAA);
            color = (int) (globalAlpha * 255) << 24 | (color & 0xFFFFFF);
            float textScale = 1f + animationProgress * 0.15f;
            FontUtil.normal.drawCenteredSmoothString(category.getName(), tx + 47, 
                    ty + 18 - (int) (FontUtil.normal.getHeight() * (textScale - 1) / 2), color);
            
            // Add category icon indicator
            if (selected) {
                float indicatorPulse = (float) Math.sin(currentTime * 0.005) * 0.6f + 0.4f;
                int indicatorSize = (int) (4 + indicatorPulse * 3);
                int indicatorColor = (int) (globalAlpha * 255) << 24 | 0xFF8C00;
                RenderUtils.drawRoundedRect(tx + 88, ty + 16 - indicatorSize/2, tx + 93, ty + 19 + indicatorSize/2, 
                        3, indicatorColor);
            }
        }
    }

    private class ModuleButton {
        Module module;
        boolean expanded = false;
        private float expandAnimation = 0f;
        private long lastAnimationTime = 0;
        private float hoverAnimation = 0f;
        private boolean wasHovering = false;

        ModuleButton(Module module) {
            this.module = module;
            this.lastAnimationTime = System.currentTimeMillis();
        }

        int getHeight() {
            float targetHeight = expanded ? 40 + module.getSettings().size() * 22 : 40;
            return (int) (40 + (targetHeight - 40) * expandAnimation);
        }

        void drawGrid(int bx, int by, int width, int height, int mouseX, int mouseY, float alpha) {
            long currentTime = System.currentTimeMillis();
            float deltaTime = (currentTime - lastAnimationTime) / 1000f;
            lastAnimationTime = currentTime;
            
            boolean hover = mouseX >= bx && mouseX <= bx + width && 
                           mouseY >= by && mouseY <= by + height;
            
            // Hover animation
            float targetHover = hover ? 1f : 0f;
            hoverAnimation += (targetHover - hoverAnimation) * deltaTime * 8f;
            hoverAnimation = Math.max(0f, Math.min(1f, hoverAnimation));
            
            // Background with animation
            int bgColor;
            if (module.isEnabled()) {
                float enabledPulse = (float) Math.sin(currentTime * 0.002) * 0.3f + 0.7f;
                bgColor = (int) (0x50 * enabledPulse * alpha) << 24 | 0xFF8C00;
            } else {
                int hoverAlpha = (int) (0x30 + hoverAnimation * 0x30);
                bgColor = (int) (hoverAlpha * alpha) << 24 | 0x404040;
            }
            
            // Draw module background
            RenderUtils.drawRoundedRect(bx, by, bx + width, by + height, 6, bgColor);
            
            // Border for enabled modules
            if (module.isEnabled()) {
                int borderColor = (int) (alpha * 0xFF) << 24 | 0xFF8C00;
                RenderUtils.drawRoundedOutline(bx, by, bx + width, by + height, 6, 2, borderColor);
            }
            
            // Module name
            int nameColor = module.isEnabled() ? 0xFFFF8C00 : 0xFFFFFF;
            nameColor = (int) (alpha * 255) << 24 | (nameColor & 0xFFFFFF);
            FontUtil.normal.drawCenteredSmoothString(module.getName(), bx + width/2, by + 12, nameColor);
            
            // Toggle indicator
            float togglePulse = (float) Math.sin(currentTime * 0.004) * 0.4f + 0.6f;
            int toggleSize = (int) (8 + togglePulse * 2);
            int toggleColor = module.isEnabled() ? 0xFFFF8C00 : 0xFF666666;
            toggleColor = (int) (alpha * 255) << 24 | (toggleColor & 0xFFFFFF);
            RenderUtils.drawRoundedRect(bx + width - 20, by + 20, bx + width - 8, by + 32, 4, toggleColor);
            
            // Settings button indicator
            if (module.getSettings().size() > 0) {
                int settingsColor = (int) (alpha * 255) << 24 | 0x888888;
                FontUtil.normal.drawSmoothString("...", bx + width - 35, by + 18, settingsColor);
            }
            
            // Settings rendering
            if (expanded) {
                int sy = by + 45;
                for (Setting s : module.getSettings()) {
                    if (s instanceof TickSetting) {
                        TickSetting ts = (TickSetting) s;
                        FontUtil.normal.drawSmoothString(ts.getName(), bx + 10, sy + 6, (int)(alpha * 255) << 24 | 0xCCCCCC);
                        int boxColor = ts.isToggled() ? 0xFFFF8C00 : 0xFF404040;
                        RenderUtils.drawRoundedRect(bx + width - 30, sy + 4, bx + width - 10, sy + 16, 4, (int)(alpha * 255) << 24 | (boxColor & 0xFFFFFF));
                        if (ts.isToggled()) {
                            RenderUtils.drawRoundedRect(bx + width - 18, sy + 6, bx + width - 12, sy + 14, 3, (int)(alpha * 255) << 24 | 0xFFFFFF);
                        } else {
                            RenderUtils.drawRoundedRect(bx + width - 28, sy + 6, bx + width - 22, sy + 14, 3, (int)(alpha * 255) << 24 | 0x888888);
                        }
                    } else if (s instanceof SliderSetting) {
                        SliderSetting ss = (SliderSetting) s;
                        FontUtil.normal.drawSmoothString(ss.getName(), bx + 10, sy + 2, (int)(alpha * 255) << 24 | 0xCCCCCC);
                        String valStr = String.format("%.1f", ss.getInput());
                        FontUtil.normal.drawSmoothString(valStr, bx + width - 10 - FontUtil.normal.getStringWidth(valStr), sy + 2, (int)(alpha * 255) << 24 | 0xFFFF8C00);
                        
                        int sliderStartX = bx + 10;
                        int sliderEndX = bx + width - 10;
                        int sliderWidth = sliderEndX - sliderStartX;
                        
                        RenderUtils.drawRoundedRect(sliderStartX, sy + 14, sliderEndX, sy + 18, 2, (int)(alpha * 255) << 24 | 0x404040);
                        double percent = (ss.getInput() - ss.getMin()) / (ss.getMax() - ss.getMin());
                        int fillWidth = (int) (sliderWidth * percent);
                        RenderUtils.drawRoundedRect(sliderStartX, sy + 14, sliderStartX + fillWidth, sy + 18, 2, (int)(alpha * 255) << 24 | 0xFFFF8C00);
                        RenderUtils.drawRoundedRect(sliderStartX + fillWidth - 3, sy + 12, sliderStartX + fillWidth + 3, sy + 20, 3, (int)(alpha * 255) << 24 | 0xFFFFFF);
                    } else if (s instanceof ComboSetting) {
                        ComboSetting<?> cs = (ComboSetting<?>) s;
                        FontUtil.normal.drawSmoothString(cs.getName() + ":", bx + 10, sy + 6, (int)(alpha * 255) << 24 | 0xCCCCCC);
                        String mode = cs.getMode().toString();
                        FontUtil.normal.drawSmoothString(mode, bx + width - 10 - FontUtil.normal.getStringWidth(mode), sy + 6, (int)(alpha * 255) << 24 | 0xFFFF8C00);
                    }
                    sy += 22;
                }
            }
        }
        
        boolean isMouseOverGrid(int mx, int my, int bx, int by, int width, int height) {
            return mx >= bx && mx <= bx + width && my >= by && my <= by + height;
        }
        
        void onClick(int mx, int my, int button, int bx, int by) {
            if (my >= by && my <= by + 40) {
                if (button == 0) module.toggle();
                else if (button == 1) expanded = !expanded;
            } else if (expanded) {
                int sy = by + 45;
                for (Setting s : module.getSettings()) {
                    if (my >= sy && my <= sy + 22) {
                        if (s instanceof TickSetting) {
                            ((TickSetting) s).toggle();
                        } else if (s instanceof ComboSetting) {
                            if (button == 0) ((ComboSetting<?>) s).nextMode();
                            else if (button == 1) ((ComboSetting<?>) s).prevMode();
                        } else if (s instanceof SliderSetting) {
                            SliderSetting ss = (SliderSetting) s;
                            int sliderStartX = bx + 10;
                            int sliderEndX = bx + 180 - 10;
                            double percent = (double) (mx - sliderStartX) / (sliderEndX - sliderStartX);
                            if (percent < 0) percent = 0;
                            if (percent > 1) percent = 1;
                            double newValue = ss.getMin() + (percent * (ss.getMax() - ss.getMin()));
                            ss.setValue(newValue);
                        }
                    }
                    sy += 22;
                }
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
    
    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (searchMode) {
            if (keyCode == 28) { // Enter key
                searchMode = false;
                searchText = "";
                return;
            } else if (keyCode == 14) { // Backspace
                if (!searchText.isEmpty()) {
                    searchText = searchText.substring(0, searchText.length() - 1);
                }
            } else if (keyCode == 1) { // Escape key
                searchMode = false;
                searchText = "";
                return;
            } else if (typedChar != 167 && Character.isLetterOrDigit(typedChar)) {
                searchText += typedChar;
            }
        }
        try {
            super.keyTyped(typedChar, keyCode);
        } catch (Exception e) {
            // Ignore any exceptions from super method
        }
    }
}
