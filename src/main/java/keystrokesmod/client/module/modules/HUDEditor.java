package keystrokesmod.client.module.modules;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;

import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.modules.render.Radar;
import keystrokesmod.client.module.setting.impl.DescriptionSetting;
import keystrokesmod.client.utils.RenderUtils;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.fml.client.config.GuiButtonExt;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.gui.Gui;

public class HUDEditor extends Module {
    
    public HUDEditor() {
        super("HUDEditor", ModuleCategory.config);
        this.registerSetting(new DescriptionSetting("Click to open HUD editor"));
        showInHud = false;
    }
    
    @Override
    public void onEnable() {
        if (mc.thePlayer != null) {
            mc.displayGuiScreen(new HUDEditorScreen());
        }
        this.disable();
    }
    
    public static class HUDEditorScreen extends GuiScreen {
        private List<HUDElement> elements = new ArrayList<>();
        private HUDElement selectedElement = null;
        private boolean dragging = false;
        private int dragOffsetX = 0;
        private int dragOffsetY = 0;
        private GuiButtonExt resetAllButton;
        private GuiButtonExt saveButton;
        
        public HUDEditorScreen() {
            initializeElements();
        }
        
        private void initializeElements() {
            elements.clear();
            
            // Array List element
            elements.add(new HUDElement("Array List", HUD.hudX, HUD.hudY, 100, 50) {
                @Override
                public void render(int mouseX, int mouseY) {
                    FontRenderer fr = mc.fontRendererObj;
                    String[] exampleModules = {"KillAura", "Fly", "Speed", "NoFall"};
                    
                    int width = 0;
                    for (String module : exampleModules) {
                        int moduleWidth = fr.getStringWidth(module);
                        if (moduleWidth > width) width = moduleWidth;
                    }
                    width += 10;
                    
                    int height = exampleModules.length * (fr.FONT_HEIGHT + 2) + 5;
                    
                    RenderUtils.drawRoundedRect(x, y, x + width, y + height, 4, 0x90101010);
                    RenderUtils.drawBorderedRoundedRect(x, y, x + width, y + height, 4, 1, 0x00000000, 0xFFFF8C00);
                    
                    int currentY = y + 3;
                    for (String module : exampleModules) {
                        fr.drawString(module, x + width - fr.getStringWidth(module) - 5, currentY, 0xFFFFFF, true);
                        currentY += fr.FONT_HEIGHT + 2;
                    }
                    
                    this.width = width;
                    this.height = height;
                }
                
                @Override
                public void updatePosition(int newX, int newY) {
                    HUD.hudX = newX;
                    HUD.hudY = newY;
                    HUD.e = false;
                }
            });
            
            // Watermark element
            elements.add(new HUDElement("Watermark", HUD.watermarkX, HUD.watermarkY, 200, 20) {
                @Override
                public void render(int mouseX, int mouseY) {
                    String text = "Raven B++ | Player | 144 FPS";
                    FontRenderer fr = mc.fontRendererObj;
                    int width = fr.getStringWidth(text) + 6;
                    int height = fr.FONT_HEIGHT + 6;
                    
                    RenderUtils.drawRoundedRect(x, y, x + width, y + height, 4, 0x90101010);
                    RenderUtils.drawBorderedRoundedRect(x, y, x + width, y + height, 4, 1, 0x00000000, 0xFFFF8C00);
                    fr.drawStringWithShadow(text, x + 3, y + 4, 0xFFFF8C00);
                    
                    this.width = width;
                    this.height = height;
                }
                
                @Override
                public void updatePosition(int newX, int newY) {
                    HUD.watermarkX = newX;
                    HUD.watermarkY = newY;
                }
            });
            
            // Keybinds element
            elements.add(new HUDElement("Keybinds", HUD.keybindsX, HUD.keybindsY, 80, 60) {
                @Override
                public void render(int mouseX, int mouseY) {
                    int width = 80;
                    int height = 60;
                    
                    RenderUtils.drawRoundedRect(x, y, x + width, y + height, 4, 0x90101010);
                    RenderUtils.drawBorderedRoundedRect(x, y, x + width, y + height, 4, 1, 0x00000000, 0xFFFF8C00);
                    mc.fontRendererObj.drawStringWithShadow("Keybinds", x + (width/2f) - (mc.fontRendererObj.getStringWidth("Keybinds")/2f), y + 4, -1);
                    
                    mc.fontRendererObj.drawStringWithShadow("KillAura [R]", x + 2, y + 20, 0xFFFF8C00);
                    mc.fontRendererObj.drawStringWithShadow("Fly [F]", x + 2, y + 32, 0xFFFFFFFF);
                    mc.fontRendererObj.drawStringWithShadow("Speed [G]", x + 2, y + 44, 0xFFFFFFFF);
                    
                    this.width = width;
                    this.height = height;
                }
                
                @Override
                public void updatePosition(int newX, int newY) {
                    HUD.keybindsX = newX;
                    HUD.keybindsY = newY;
                }
            });
            
            // TargetHUD element
            elements.add(new HUDElement("TargetHUD", HUD.targetHUDX, HUD.targetHUDY, 150, 50) {
                @Override
                public void render(int mouseX, int mouseY) {
                    int width = 150;
                    int height = 50;
                    
                    int bgColor = 0x801A1A1A;
                    RenderUtils.drawRoundedRect(x, y, x + width, y + height, 8, bgColor);
                    
                    int borderColor = 0xFFFF8C00;
                    RenderUtils.drawBorderedRoundedRect(x, y, x + width, y + height, 8, 2, borderColor, 0x00000000);
                    
                    RenderUtils.drawRoundedRect(x + 10, y + 15, x + 25, y + 30, 3, 0x606060);
                    mc.fontRendererObj.drawStringWithShadow("Face", x + 11, y + 20, 0xFFFFFF);
                    
                    mc.fontRendererObj.drawStringWithShadow("TargetPlayer", x + 35, y + 8, 0xFFFFFF);
                    mc.fontRendererObj.drawStringWithShadow("20.0/20.0", x + 35, y + 15, 0xCCCCCC);
                    
                    int healthBarY = y + 25;
                    int healthBarWidth = width - 45;
                    RenderUtils.drawRoundedRect(x + 35, healthBarY, x + 35 + healthBarWidth, healthBarY + 8, 4, 0x404040);
                    RenderUtils.drawRoundedRect(x + 35, healthBarY, x + 35 + (int)(healthBarWidth * 0.8), healthBarY + 8, 4, 0xFF00FF00);
                    
                    int armorBarY = healthBarY + 10;
                    RenderUtils.drawRoundedRect(x + 35, armorBarY, x + 35 + healthBarWidth, armorBarY + 4, 2, 0x404040);
                    RenderUtils.drawRoundedRect(x + 35, armorBarY, x + 35 + (int)(healthBarWidth * 0.5), armorBarY + 4, 2, 0xFF00BFFF);
                    
                    this.width = width;
                    this.height = height;
                }
                
                @Override
                public void updatePosition(int newX, int newY) {
                    HUD.setTargetHUDPosition(newX, newY);
                }
            });

            // Radar element
            elements.add(new HUDElement("Radar", Radar.radarX, Radar.radarY, 100, 100) {
                @Override
                public void render(int mouseX, int mouseY) {
                    int s = 100;
                    int halfS = s / 2;
                    int cx = x + halfS;
                    int cy = y + halfS;
                    
                    RenderUtils.drawRoundedRect(x, y, x + s, y + s, 8, 0x801A1A1A);
                    RenderUtils.drawBorderedRoundedRect(x, y, x + s, y + s, 8, 2, 0xFFFF8C00, 0x00000000);
                    
                    Gui.drawRect(cx - halfS, cy - 1, cx + halfS, cy + 1, 0x20FFFFFF);
                    Gui.drawRect(cx - 1, cy - halfS, cx + 1, cy + halfS, 0x20FFFFFF);
                    
                    mc.fontRendererObj.drawString("Radar", x + 5, y + 5, 0xFFFFFFFF);
                    RenderUtils.drawRoundedRect(cx - 2, cy - 2, cx + 2, cy + 2, 2, 0xFFFF8C00);

                    RenderUtils.drawRoundedRect(cx + 15, cy - 10, cx + 19, cy - 6, 2, 0xFFFF0000);
                    RenderUtils.drawRoundedRect(cx - 20, cy + 15, cx - 16, cy + 19, 2, 0xFF00FF00);

                    this.width = s;
                    this.height = s;
                }

                @Override
                public void updatePosition(int newX, int newY) {
                    Radar.radarX = newX;
                    Radar.radarY = newY;
                }
            });
        }
        
        @Override
        public void initGui() {
            super.initGui();
            this.buttonList.add(resetAllButton = new GuiButtonExt(1, this.width - 100, 5, 95, 20, "Reset All"));
            this.buttonList.add(saveButton = new GuiButtonExt(2, this.width - 100, 30, 95, 20, "Save & Exit"));
        }
        
        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            // Draw modern dark background
            drawRect(0, 0, this.width, this.height, 0x80000000);
            
            // Draw grid lines
            ScaledResolution sr = new ScaledResolution(mc);
            GL11.glEnable(GL11.GL_BLEND);
            for (int i = 0; i < sr.getScaledWidth(); i += 25) {
                drawVerticalLine(i, 0, sr.getScaledHeight(), 0x1AFFFFFF);
            }
            for (int i = 0; i < sr.getScaledHeight(); i += 25) {
                drawHorizontalLine(0, sr.getScaledWidth(), i, 0x1AFFFFFF);
            }
            
            // Draw center lines
            drawVerticalLine(sr.getScaledWidth() / 2, 0, sr.getScaledHeight(), 0x40FF8C00);
            drawHorizontalLine(0, sr.getScaledWidth(), sr.getScaledHeight() / 2, 0x40FF8C00);
            GL11.glDisable(GL11.GL_BLEND);
            
            // Render all elements
            for (HUDElement element : elements) {
                boolean isSelected = element == selectedElement;
                boolean isHovered = element.isHovered(mouseX, mouseY);
                
                element.render(mouseX, mouseY);
                
                // Modern selection border
                if (isSelected) {
                    RenderUtils.drawBorderedRoundedRect(element.x - 2, element.y - 2, element.x + element.width + 2, element.y + element.height + 2, 4, 1, 0x00000000, 0xFFFF8C00);
                } else if (isHovered) {
                    RenderUtils.drawBorderedRoundedRect(element.x - 2, element.y - 2, element.x + element.width + 2, element.y + element.height + 2, 4, 1, 0x00000000, 0x80FFFFFF);
                }
            }
            
            // Modern header
            RenderUtils.drawRoundedRect(5, 5, 220, 50, 6, 0x901A1A1A);
            RenderUtils.drawBorderedRoundedRect(5, 5, 220, 50, 6, 2, 0x00000000, 0xFFFF8C00);
            mc.fontRendererObj.drawStringWithShadow("\u00a76HUD Editor", 12, 12, -1);
            mc.fontRendererObj.drawStringWithShadow("\u00a77Click and drag elements", 12, 25, -1);
            mc.fontRendererObj.drawStringWithShadow("\u00a77ESC to exit", 12, 35, -1);
            
            super.drawScreen(mouseX, mouseY, partialTicks);
        }
        
        @Override
        protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            
            if (mouseButton == 0) {
                selectedElement = null;
                for (int i = elements.size() - 1; i >= 0; i--) {
                    HUDElement element = elements.get(i);
                    if (element.isHovered(mouseX, mouseY)) {
                        selectedElement = element;
                        dragging = true;
                        dragOffsetX = mouseX - element.x;
                        dragOffsetY = mouseY - element.y;
                        
                        elements.remove(i);
                        elements.add(element);
                        break;
                    }
                }
            }
        }
        
        @Override
        protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
            super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
            
            if (dragging && selectedElement != null && clickedMouseButton == 0) {
                int newX = mouseX - dragOffsetX;
                int newY = mouseY - dragOffsetY;
                
                newX = (newX / 5) * 5;
                newY = (newY / 5) * 5;
                
                ScaledResolution sr = new ScaledResolution(mc);
                newX = Math.max(0, Math.min(newX, sr.getScaledWidth() - selectedElement.width));
                newY = Math.max(0, Math.min(newY, sr.getScaledHeight() - selectedElement.height));
                
                selectedElement.x = newX;
                selectedElement.y = newY;
                selectedElement.updatePosition(newX, newY);
            }
        }
        
        @Override
        protected void mouseReleased(int mouseX, int mouseY, int state) {
            super.mouseReleased(mouseX, mouseY, state);
            dragging = false;
        }
        
        @Override
        protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                HUD.savePositions();
                mc.displayGuiScreen(null);
                return;
            }
            super.keyTyped(typedChar, keyCode);
        }
        
        @Override
        public void actionPerformed(GuiButton button) {
            if (button == resetAllButton) {
                HUD.hudX = 5;
                HUD.hudY = 70;
                HUD.e = false;
                HUD.watermarkX = 5;
                HUD.watermarkY = 5;
                HUD.keybindsX = 5;
                HUD.keybindsY = 50;
                HUD.resetTargetHUDPosition();
                Radar.radarX = 10;
                Radar.radarY = 120;
                initializeElements();
            } else if (button == saveButton) {
                HUD.savePositions();
                mc.displayGuiScreen(null);
            }
        }
        
        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }
    }
    
    public static abstract class HUDElement {
        public String name;
        public int x, y, width, height;
        
        public HUDElement(String name, int x, int y, int width, int height) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
        
        public boolean isHovered(int mouseX, int mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
        
        public abstract void render(int mouseX, int mouseY);
        public abstract void updatePosition(int newX, int newY);
    }
}
