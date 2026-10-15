package keystrokesmod.client.module.modules;

import java.awt.Color;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import org.lwjgl.opengl.GL11;

import com.google.common.eventbus.Subscribe;

import keystrokesmod.client.event.impl.Render2DEvent;
import keystrokesmod.client.main.Raven;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.modules.client.FakeHud;
import keystrokesmod.client.module.setting.Setting;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.module.setting.impl.DescriptionSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.RenderUtils;
import keystrokesmod.client.utils.Utils;
import keystrokesmod.client.utils.font.FontUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.config.GuiButtonExt;

public class HUD extends Module {
    public static TickSetting editPosition, dropShadow, logo, watermark, keybinds, arrayListBackground, arrayListBorder;
    public static ComboSetting logoMode;
    public static SliderSetting colourMode, logoScaleh, logoScalew, arrayListOpacity;
    public static DescriptionSetting colourModeDesc, logoDesc1, logoDesc2;
    public static int hudX = 5;
    public static int hudY = 70;
    public static int watermarkX = 5;
    public static int watermarkY = 5;
    public static int keybindsX = 5;
    public static int keybindsY = 50;
    public static int targetHUDX = 100;
    public static int targetHUDY = 100;
    private double logoHeight;
    public static boolean e;
    
    private InputStream inputStream;
    private ResourceLocation ravenLogo;

    public static Utils.HUD.PositionMode positionMode;
    public static boolean showedError;
    public static final String HUDX_prefix = "HUDX~ ";
    public static final String HUDY_prefix = "HUDY~ ";

    public enum lmv {
        l1, l2, l3, l4, l5, l6, l7, CD
    }

    public HUD() {
        super("HUD", ModuleCategory.render);
        this.registerSetting(editPosition = new TickSetting("Edit position", false));
        this.registerSetting(dropShadow = new TickSetting("Drop shadow", true));
        this.registerSetting(logo = new TickSetting("Logo", true));
        this.registerSetting(watermark = new TickSetting("Watermark", true));
        this.registerSetting(keybinds = new TickSetting("Keybinds", true));
        this.registerSetting(arrayListBackground = new TickSetting("Array List Background", false));
        this.registerSetting(arrayListBorder = new TickSetting("Array List Border", true));
        this.registerSetting(colourMode = new SliderSetting("Value: ", 1, 1, 7, 1));
        this.registerSetting(colourModeDesc = new DescriptionSetting("Mode: RAVEN"));
        this.registerSetting(logoScaleh = new SliderSetting("Logo Scale height ", 1, 0, 10, 0.01));
        this.registerSetting(logoScalew = new SliderSetting("Logo Scale width ", 2, 0, 10, 0.01));
        this.registerSetting(arrayListOpacity = new SliderSetting("Array List Opacity", 0.8, 0.1, 1.0, 0.1));
        this.registerSetting(logoMode = new ComboSetting("Logo Mode:", lmv.l7));
        this.registerSetting(logoDesc1 = new DescriptionSetting("cd logomode put an image logo.png"));
        this.registerSetting(logoDesc2 = new DescriptionSetting("in the keystrokes folder"));
        showedError = false;
        showInHud = false;
    }

    private void setUpLogo() {
        ResourceLocation loaded = RenderUtils.getResourcePath("/assets/keystrokes/logohud/" + logoMode.getMode().toString() + ".png");
        ravenLogo = (loaded != null && !"null".equals(loaded.getResourcePath())) ? loaded : null;
    }

    @Override
	public void postApplyConfig() {
        setUpLogo();
    }

    @Override
	public void guiButtonToggled(Setting b) {
        if (b == logoMode)
            setUpLogo();
        else if (b == editPosition) {
            editPosition.disable();
            mc.displayGuiScreen(new HUDEditor.HUDEditorScreen());
        }
    }

    public boolean logoLoaded() {
        return (ravenLogo != null) && logo.isToggled();
    }

    @Override
	public void guiUpdate() {
        colourModeDesc.setDesc(Utils.md + ColourModes.values()[(int) colourMode.getInput() - 1]);
    }

    @Override
	public void onEnable() {
        Raven.moduleManager.sort();
        loadHUDPositions();
    }

    @Subscribe
    public void onRender2D(Render2DEvent ev) {
        if (Utils.Player.isPlayerInGame()) {
            if ((mc.currentScreen != null) || mc.gameSettings.showDebugInfo)
				return;
            
            drawWatermark();
            drawKeybinds();

            Module fakeHudMod = Raven.moduleManager.getModuleByName("Fake Hud");
            boolean fhe = fakeHudMod != null && fakeHudMod.isEnabled();
            ScaledResolution sr = new ScaledResolution(mc);
            if (!e) {
                positionMode = Utils.HUD.getPostitionMode(hudX, hudY, sr.getScaledWidth(), sr.getScaledHeight());
                if ((positionMode == Utils.HUD.PositionMode.UPLEFT) || (positionMode == Utils.HUD.PositionMode.UPRIGHT)) {
                    if (!fhe)
						Raven.moduleManager.sortShortLong();
					else
						FakeHud.sortShortLong();
                } else if ((positionMode == Utils.HUD.PositionMode.DOWNLEFT)
                        || (positionMode == Utils.HUD.PositionMode.DOWNRIGHT))
					if (!fhe)
						Raven.moduleManager.sortLongShort();
					else
						FakeHud.sortLongShort();
                e = true;
            }
            int margin = 2;
            int y = hudY;
            int del = 0;

            List<Module> en = fhe ? FakeHud.getModules() : new ArrayList<>(Raven.moduleManager.getModules());
            if (en.isEmpty())
                return;

            int textBoxWidth = Raven.moduleManager.getLongestActiveModule(mc.fontRendererObj);
            int textBoxHeight = Raven.moduleManager.getBoxHeight(mc.fontRendererObj, margin);

            if (hudX < 0)
				hudX = margin;
            if (hudY < 0)
				hudY = margin;

            int maxHudX = Math.max(margin, sr.getScaledWidth() - textBoxWidth - margin);
            int maxHudY = Math.max(margin, sr.getScaledHeight() - textBoxHeight - margin);
            if (hudX > maxHudX)
                hudX = maxHudX;
            if (hudY > maxHudY)
                hudY = maxHudY;

            drawLogo(textBoxWidth);
            y += logoHeight;
            
            // Calculate array list dimensions for background
            int arrayListHeight = 0;
            int maxModuleWidth = 0;
            List<Module> activeModules = new ArrayList<>();
            
            for (Module m : en) {
                if (m.isEnabled() && m.showInHud()) {
                    activeModules.add(m);
                    int moduleWidth = mc.fontRendererObj.getStringWidth(m.getName());
                    if (moduleWidth > maxModuleWidth) {
                        maxModuleWidth = moduleWidth;
                    }
                    arrayListHeight += mc.fontRendererObj.FONT_HEIGHT + margin;
                }
            }
            
            // Draw array list background if enabled
            if (arrayListBackground.isToggled() && !activeModules.isEmpty()) {
                int bgOpacity = (int) (arrayListOpacity.getInput() * 255);
                int bgColor = (bgOpacity << 24) | 0x101010;
                int borderColor = (bgOpacity << 24) | 0x303030;
                
                if (arrayListBorder.isToggled()) {
                    RenderUtils.drawBorderedRoundedRect(hudX - 2, y - 2, hudX + maxModuleWidth + 4, y + arrayListHeight + 2, 4, 1, 0x00000000, 0xFFFF8C00);
                } else {
                    RenderUtils.drawRoundedRect(hudX - 2, y - 2, hudX + maxModuleWidth + 4, y + arrayListHeight + 2, 4, bgColor);
                }
            }
            
            for (Module m : activeModules)
				if ((positionMode == Utils.HUD.PositionMode.DOWNRIGHT)
                            || (positionMode == Utils.HUD.PositionMode.UPRIGHT)) {
                        int color = 0;
                        if (ColourModes.values()[(int) colourMode.getInput() - 1] == ColourModes.RAVEN) {
                            color = Utils.Client.rainbowDraw(2L, del);
                        } else if (ColourModes.values()[(int) colourMode.getInput() - 1] == ColourModes.RAVEN2) {
                            color = Utils.Client.rainbowDraw(2L, del);
                        } else if (ColourModes.values()[(int) colourMode.getInput() - 1] == ColourModes.ASTOLFO) {
                            color = Utils.Client.astolfoColorsDraw(10, 14);
                        } else if (ColourModes.values()[(int) colourMode.getInput() - 1] == ColourModes.ASTOLFO2) {
                            color = Utils.Client.astolfoColorsDraw(10, del);
                        } else if (ColourModes.values()[(int) colourMode.getInput() - 1] == ColourModes.ASTOLFO3) {
                            color = Utils.Client.astolfoColorsDraw(10, del);
                        } else if (ColourModes.values()[(int) colourMode.getInput() - 1] == ColourModes.KV) {
                            color = Utils.Client.customDraw(del);
                        } else if (ColourModes.values()[(int) colourMode.getInput() - 1] == ColourModes.ORANGE) {
                            color = 0xFFFF8C00;
                        }

                        if (color != 0) {
                            if (ColourModes.values()[(int) colourMode.getInput() - 1] == ColourModes.KV) {
                                FontUtil.two.drawString(m.getName(),
                                        (double) hudX + (maxModuleWidth - mc.fontRendererObj.getStringWidth(m.getName())), y,
                                        color, dropShadow.isToggled(), 10);
                            } else {
                                mc.fontRendererObj.drawString(m.getName(),
                                        (float) hudX + (maxModuleWidth - mc.fontRendererObj.getStringWidth(m.getName())),
                                        (float) y, color, dropShadow.isToggled());
                            }
                            y += mc.fontRendererObj.FONT_HEIGHT + margin;
                            if (ColourModes.values()[(int) colourMode.getInput() - 1] == ColourModes.RAVEN) del -= 120;
                            else del -= 10;
                        }
                    } else {
                        int color = 0;
                        if (ColourModes.values()[(int) colourMode.getInput() - 1] == ColourModes.RAVEN) {
                            color = Utils.Client.rainbowDraw(2L, del);
                        } else if (ColourModes.values()[(int) colourMode.getInput() - 1] == ColourModes.RAVEN2) {
                            color = Utils.Client.rainbowDraw(2L, del);
                        } else if (ColourModes.values()[(int) colourMode.getInput() - 1] == ColourModes.ASTOLFO) {
                            color = Utils.Client.astolfoColorsDraw(10, 14);
                        } else if (ColourModes.values()[(int) colourMode.getInput() - 1] == ColourModes.ASTOLFO2) {
                            color = Utils.Client.astolfoColorsDraw(10, del);
                        } else if (ColourModes.values()[(int) colourMode.getInput() - 1] == ColourModes.ASTOLFO3) {
                            color = Utils.Client.astolfoColorsDraw(10, del);
                        } else if (ColourModes.values()[(int) colourMode.getInput() - 1] == ColourModes.KV) {
                            color = Utils.Client.customDraw(del);
                        } else if (ColourModes.values()[(int) colourMode.getInput() - 1] == ColourModes.ORANGE) {
                            color = 0xFFFF8C00;
                        }

                        if (color != 0) {
                            if (ColourModes.values()[(int) colourMode.getInput() - 1] == ColourModes.KV) {
                                FontUtil.two.drawString(m.getName(), (float) hudX, (float) y, color);
                                y += mc.fontRendererObj.FONT_HEIGHT - 2;
                            } else {
                                mc.fontRendererObj.drawString(m.getName(), (float) hudX, (float) y,
                                        color, dropShadow.isToggled());
                                y += mc.fontRendererObj.FONT_HEIGHT + margin;
                            }
                            if (ColourModes.values()[(int) colourMode.getInput() - 1] == ColourModes.RAVEN) del -= 120;
                            else del -= 10;
                        }
                    }
        }

    }

    private void drawWatermark() {
        if (!watermark.isToggled()) return;
        String text = "Raven B++ | " + mc.getSession().getUsername() + " | " + Minecraft.getDebugFPS() + " FPS";
        int color = (ColourModes.values()[(int) colourMode.getInput() - 1] == ColourModes.ORANGE) ? 0xFFFF8C00 : -1;
        
        int width = mc.fontRendererObj.getStringWidth(text) + 6;
        int height = mc.fontRendererObj.FONT_HEIGHT + 6;
        
        RenderUtils.drawBorderedRoundedRect(watermarkX, watermarkY, watermarkX + width, watermarkY + height, 4, 1, 0xFFFF8C00, 0x90101010);
        mc.fontRendererObj.drawStringWithShadow(text, watermarkX + 3, watermarkY + 4, color);
    }

    private void drawKeybinds() {
        if (!keybinds.isToggled()) return;
        List<Module> binds = new ArrayList<>();
        for (Module m : Raven.moduleManager.getModules()) {
            if (m.getKeycode() != 0) binds.add(m);
        }
        
        if (binds.isEmpty()) return;
        
        int x = keybindsX;
        int y = keybindsY;
        int maxWidth = 80;
        int totalHeight = 15;
        for (Module m : binds) {
            String bindName = org.lwjgl.input.Keyboard.getKeyName(m.getKeycode());
            String text = m.getName() + " [" + bindName + "]";
            int w = mc.fontRendererObj.getStringWidth(text) + 6;
            if (w > maxWidth) maxWidth = w;
            totalHeight += mc.fontRendererObj.FONT_HEIGHT + 2;
        }
        totalHeight += 4;
        
        RenderUtils.drawBorderedRoundedRect(x, y, x + maxWidth, y + totalHeight, 4, 1, 0xFFFF8C00, 0x90101010);
        mc.fontRendererObj.drawStringWithShadow("Keybinds", x + (maxWidth/2f) - (mc.fontRendererObj.getStringWidth("Keybinds")/2f), y + 4, -1);
        
        int yOffset = 18;
        for (Module m : binds) {
            String bindName = org.lwjgl.input.Keyboard.getKeyName(m.getKeycode());
            String text = m.getName() + " [" + bindName + "]";
            int color = m.isEnabled() ? ((ColourModes.values()[(int) colourMode.getInput() - 1] == ColourModes.ORANGE) ? 0xFFFF8C00 : 0xFF00FF00) : 0xFFFFFFFF;
            mc.fontRendererObj.drawStringWithShadow(text, x + 3, y + yOffset, color);
            yOffset += mc.fontRendererObj.FONT_HEIGHT + 2;
        }
    }

    
    private void drawLogo(int e) {
        ScaledResolution sr = new ScaledResolution(mc);
        logoHeight = (sr.getScaledHeight() * logoScaleh.getInput()) / 10;
        if (logoLoaded()) {
            if ((positionMode == Utils.HUD.PositionMode.DOWNRIGHT) || (positionMode == Utils.HUD.PositionMode.UPRIGHT)) {
                double logoWidth = (sr.getScaledWidth() * logoScalew.getInput()) / 8;
                Minecraft.getMinecraft().getTextureManager().bindTexture(ravenLogo);
                GL11.glColor4f(1, 1, 1, 1);
				Gui.drawModalRectWithCustomSizedTexture((int) ((hudX + e) - logoWidth), hudY, 0, 0, (int) logoWidth,
						(int) logoHeight, (int) logoWidth, (int) logoHeight);
            } else {
                double logoWidth = (sr.getScaledWidth() * logoScalew.getInput()) / 8;
                Minecraft.getMinecraft().getTextureManager().bindTexture(ravenLogo);
                GL11.glColor4f(1, 1, 1, 1);
                Gui.drawModalRectWithCustomSizedTexture(hudX, hudY, 0, 0, (int) logoWidth, (int) logoHeight,
                        (int) logoWidth, (int) logoHeight);
            }
        } else
			logoHeight = 0;
    }

    static class EditHudPositionScreen extends GuiScreen {
        final String hudTextExample = "This is an-Example-HUD";
        GuiButtonExt resetPosButton;
        boolean mouseDown;
        int textBoxStartX;
        int textBoxStartY;
        ScaledResolution sr;
        int textBoxEndX;
        int textBoxEndY;
        int marginX = 5;
        int marginY = 70;
        int lastMousePosX;
        int lastMousePosY;
        int sessionMousePosX;
        int sessionMousePosY;

        @Override
		public void initGui() {
            super.initGui();
            this.buttonList
                    .add(this.resetPosButton = new GuiButtonExt(1, this.width - 90, 5, 85, 20, "Reset position"));
            this.marginX = hudX;
            this.marginY = hudY;
            sr = new ScaledResolution(mc);
            positionMode = Utils.HUD.getPostitionMode(marginX, marginY, sr.getScaledWidth(), sr.getScaledHeight());
            e = false;
        }

        @Override
		public void drawScreen(int mX, int mY, float pt) {
            drawRect(0, 0, this.width, this.height, -1308622848);
            drawRect(0, this.height / 2, this.width, (this.height / 2) + 1, 0x9936393f);
            drawRect(this.width / 2, 0, (this.width / 2) + 1, this.height, 0x9936393f);
            int textBoxStartX = this.marginX;
            int textBoxStartY = this.marginY;
            int textBoxEndX = textBoxStartX + 50;
            int textBoxEndY = textBoxStartY + 32;
            this.drawArrayList(this.mc.fontRendererObj, this.hudTextExample);
            this.textBoxStartX = textBoxStartX;
            this.textBoxStartY = textBoxStartY;
            this.textBoxEndX = textBoxEndX;
            this.textBoxEndY = textBoxEndY;
            hudX = textBoxStartX;
            hudY = textBoxStartY;
            ScaledResolution res = new ScaledResolution(this.mc);
            int descriptionOffsetX = (res.getScaledWidth() / 2) - 84;
            int descriptionOffsetY = (res.getScaledHeight() / 2) - 20;
            Utils.HUD.drawColouredText("Edit the HUD position by dragging.", '-', descriptionOffsetX,
                    descriptionOffsetY, 2L, 0L, true, this.mc.fontRendererObj);

            try {
                this.handleInput();
            } catch (IOException var12) {
            }

            super.drawScreen(mX, mY, pt);
        }

        private void drawArrayList(FontRenderer fr, String t) {
            int x = this.textBoxStartX;
            int gap = this.textBoxEndX - this.textBoxStartX;
            int y = this.textBoxStartY;
            double marginY = fr.FONT_HEIGHT + 2;
            String[] var4 = t.split("-");
            ArrayList<String> var5 = Utils.Java.toArrayList(var4);
            if ((positionMode == Utils.HUD.PositionMode.UPLEFT) || (positionMode == Utils.HUD.PositionMode.UPRIGHT))
				var5.sort((o1, o2) -> Utils.mc.fontRendererObj.getStringWidth(o2)
                        - Utils.mc.fontRendererObj.getStringWidth(o1));
			else if ((positionMode == Utils.HUD.PositionMode.DOWNLEFT)
                    || (positionMode == Utils.HUD.PositionMode.DOWNRIGHT))
				var5.sort(Comparator.comparingInt(o2 -> Utils.mc.fontRendererObj.getStringWidth(o2)));

            if ((positionMode == Utils.HUD.PositionMode.DOWNRIGHT) || (positionMode == Utils.HUD.PositionMode.UPRIGHT))
				for (String s : var5) {
                    fr.drawString(s, (float) x + (gap - fr.getStringWidth(s)), (float) y, Color.white.getRGB(),
                            dropShadow.isToggled());
                    y += marginY;
                }
			else
				for (String s : var5) {
                    fr.drawString(s, (float) x, (float) y, Color.white.getRGB(), dropShadow.isToggled());
                    y += marginY;
                }
        }

        @Override
		protected void mouseClickMove(int mousePosX, int mousePosY, int clickedMouseButton, long timeSinceLastClick) {
            super.mouseClickMove(mousePosX, mousePosY, clickedMouseButton, timeSinceLastClick);
            if (clickedMouseButton == 0)
				if (this.mouseDown) {
                    this.marginX = this.lastMousePosX + (mousePosX - this.sessionMousePosX);
                    this.marginY = this.lastMousePosY + (mousePosY - this.sessionMousePosY);
                    sr = new ScaledResolution(mc);
                    positionMode = Utils.HUD.getPostitionMode(marginX, marginY, sr.getScaledWidth(),
                            sr.getScaledHeight());

                    // in the else if statement, we check if the mouse is clicked AND inside the
                    // "text box"
                } else if ((mousePosX > this.textBoxStartX) && (mousePosX < this.textBoxEndX)
                        && (mousePosY > this.textBoxStartY) && (mousePosY < this.textBoxEndY)) {
                    this.mouseDown = true;
                    this.sessionMousePosX = mousePosX;
                    this.sessionMousePosY = mousePosY;
                    this.lastMousePosX = this.marginX;
                    this.lastMousePosY = this.marginY;
                }
        }

        @Override
		protected void mouseReleased(int mX, int mY, int state) {
            super.mouseReleased(mX, mY, state);
            if (state == 0)
				this.mouseDown = false;

        }

        @Override
		public void actionPerformed(GuiButton b) {
            if (b == this.resetPosButton) {
                this.marginX = hudX = 5;
                this.marginY = hudY = 70;
            }

        }

        @Override
		public boolean doesGuiPauseGame() {
            return false;
        }
    }

    public enum ColourModes {
        RAVEN, RAVEN2, ASTOLFO, ASTOLFO2, ASTOLFO3, KV, ORANGE
    }

    public static int getHudX() {
        return hudX;
    }

    public static int getHudY() {
        return hudY;
    }

    public static void setHudX(int hudX) {
        HUD.hudX = hudX;
    }

    public static void setHudY(int hudY) {
        HUD.hudY = hudY;
    }
    
    public static void setTargetHUDPosition(int x, int y) {
        targetHUDX = x;
        targetHUDY = y;
    }
    
    public static void resetTargetHUDPosition() {
        targetHUDX = 100;
        targetHUDY = 100;
    }
    
        
    private static File getHudPositionsFile() {
        return new File(Minecraft.getMinecraft().mcDataDir + File.separator + "keystrokes" + File.separator + "hudpos.json");
    }

    private static void ensureDirExists(File f) {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }

    @Override
    public void onDisable() {
        saveHUDPositions();
        super.onDisable();
    }

    public static String buildHudDataString() {
        StringBuilder hudData = new StringBuilder();
        hudData.append("hudX,").append(hudX).append(",").append(hudY).append(";");
        hudData.append("watermarkX,").append(watermarkX).append(",").append(watermarkY).append(";");
        hudData.append("keybindsX,").append(keybindsX).append(",").append(keybindsY).append(";");
        hudData.append("targetHUDX,").append(targetHUDX).append(",").append(targetHUDY).append(";");
        hudData.append("radarX,").append(keystrokesmod.client.module.modules.render.Radar.radarX).append(",").append(keystrokesmod.client.module.modules.render.Radar.radarY).append(";");
        if (positionMode != null) {
            hudData.append("positionMode,").append(positionMode.name()).append(";");
        }
        return hudData.toString();
    }

    public static void parseHudDataString(String hudData) {
        if (hudData == null || hudData.isEmpty()) return;

        String[] positions = hudData.split(";");
        for (String pos : positions) {
            String[] parts = pos.split(",");
            if (parts.length >= 2 && "positionMode".equals(parts[0].trim())) {
                try {
                    positionMode = Utils.HUD.PositionMode.valueOf(parts[1].trim());
                } catch (IllegalArgumentException ignored) {
                }
                continue;
            }
            if (parts.length == 3) {
                String element = parts[0].trim();
                int x = Integer.parseInt(parts[1].trim());
                int y = Integer.parseInt(parts[2].trim());

                switch (element) {
                    case "hudX":
                        hudX = x;
                        hudY = y;
                        break;
                    case "hudY":
                        hudY = y;
                        break;
                    case "watermarkX":
                        watermarkX = x;
                        watermarkY = y;
                        break;
                    case "watermarkY":
                        watermarkY = y;
                        break;
                    case "keybindsX":
                        keybindsX = x;
                        keybindsY = y;
                        break;
                    case "keybindsY":
                        keybindsY = y;
                        break;
                    case "targetHUDX":
                        targetHUDX = x;
                        targetHUDY = y;
                        break;
                    case "targetHUDY":
                        targetHUDY = y;
                        break;
                    case "radarX":
                        keystrokesmod.client.module.modules.render.Radar.radarX = x;
                        keystrokesmod.client.module.modules.render.Radar.radarY = y;
                        break;
                    case "radarY":
                        keystrokesmod.client.module.modules.render.Radar.radarY = y;
                        break;
                }
            }
        }
        e = false;
    }

    private void loadHUDPositions() {
        File hudFile = getHudPositionsFile();
        if (!hudFile.exists()) return;

        try (FileReader reader = new FileReader(hudFile)) {
            JsonParser jsonParser = new JsonParser();
            JsonObject data = jsonParser.parse(reader).getAsJsonObject();
            if (data == null || !data.has("hud_positions") || data.get("hud_positions").isJsonNull()) return;

            String hudData = data.get("hud_positions").getAsString();
            parseHudDataString(hudData);
        } catch (JsonSyntaxException | ClassCastException | NumberFormatException | IllegalStateException | IOException ex) {
            resetAllPositions();
        }
    }

    private static void writeHudPositions(String hudData) {
        File hudFile = getHudPositionsFile();
        ensureDirExists(hudFile);

        try {
            JsonObject data;
            if (hudFile.exists()) {
                try (FileReader reader = new FileReader(hudFile)) {
                    JsonParser jsonParser = new JsonParser();
                    data = jsonParser.parse(reader).getAsJsonObject();
                } catch (Exception e) {
                    data = new JsonObject();
                }
            } else {
                data = new JsonObject();
            }

            data.addProperty("hud_positions", hudData);
            try (PrintWriter out = new PrintWriter(new FileWriter(hudFile))) {
                out.write(data.toString());
            }
        } catch (Exception ignored) {}
    }

    private void saveHUDPositions() {
        writeHudPositions(buildHudDataString());
    }

    public static void savePositions() {
        writeHudPositions(buildHudDataString());
    }

    private void resetAllPositions() {
        hudX = 5;
        hudY = 70;
        watermarkX = 5;
        watermarkY = 5;
        keybindsX = 5;
        keybindsY = 50;
        targetHUDX = 100;
        targetHUDY = 100;
    }
}
