package keystrokesmod.client.module.modules.render;

import com.google.common.eventbus.Subscribe;
import keystrokesmod.client.event.impl.ForgeEvent;
import keystrokesmod.client.event.impl.Render2DEvent;
import keystrokesmod.client.event.impl.UpdateEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.modules.HUD;
import keystrokesmod.client.module.setting.impl.ComboSetting;
import keystrokesmod.client.module.setting.impl.SliderSetting;
import keystrokesmod.client.module.setting.impl.TickSetting;
import keystrokesmod.client.utils.RenderUtils;
import keystrokesmod.client.utils.font.FontUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StringUtils;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class TargetHUD extends Module {
    public static ComboSetting mode;
    public TickSetting editPosition;
    public SliderSetting fadeTime;

    public int height, width;
    public FontRenderer fr;
    private AbstractClientPlayer target;
    private long lastTargetTime = 0;
    private Map<EntityPlayer, Long> recentTargets = new HashMap<>();
    ScaledResolution sr;

    // Animation variables
    private float animatedHealth = 0f;
    private float animatedArmor = 0f;
    private float animatedScale = 0f;
    private long damageFlashTime = 0;
    private float lastHealth = 0f;

    public TargetHUD() {
        super("Target HUD", ModuleCategory.render);
        fr = mc != null ? mc.fontRendererObj : null;

        this.registerSetting(mode = new ComboSetting("Mode", TargetHUDMode.Face));
        this.registerSetting(fadeTime = new SliderSetting("Fade Time (seconds)", 3.0, 1.0, 10.0, 0.5));
        this.registerSetting(editPosition = new TickSetting("Edit Position", false));
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        if (fe.getEvent() instanceof AttackEntityEvent) {
            AttackEntityEvent e = (AttackEntityEvent) fe.getEvent();
            if (e.target instanceof AbstractClientPlayer) {
                AbstractClientPlayer targetPlayer = (AbstractClientPlayer) e.target;
                target = targetPlayer;
                lastTargetTime = System.currentTimeMillis();
                recentTargets.put(targetPlayer, lastTargetTime);
            }
        }
    }

    @Subscribe
    public void onUpdate(UpdateEvent e) {
        if (target != null) {
            if (target.getHealth() < lastHealth) {
                damageFlashTime = System.currentTimeMillis();
            }
            lastHealth = target.getHealth();
        }
    }

    @Subscribe
    public void onRender2d(Render2DEvent e) {
        if (editPosition.isToggled()) {
            if (mc.thePlayer == null) return;
            target = mc.thePlayer;
            drawTargetHUD(target, 0, 10000000, 1.0f);
            return;
        }

        long currentTime = System.currentTimeMillis();
        long fadeTimeMs = (long) (fadeTime.getInput() * 1000);

        recentTargets.entrySet().removeIf(entry -> currentTime - entry.getValue() > fadeTimeMs);

        if (target != null && currentTime - lastTargetTime < fadeTimeMs) {
            animatedScale = interpolate(animatedScale, 1.0f, 0.1f);
            drawTargetHUD(target, currentTime - lastTargetTime, fadeTimeMs, animatedScale);
        } else if (!recentTargets.isEmpty()) {
            EntityPlayer mostRecent = null;
            long mostRecentTime = 0;
            for (Map.Entry<EntityPlayer, Long> entry : recentTargets.entrySet()) {
                if (entry.getValue() > mostRecentTime) {
                    mostRecentTime = entry.getValue();
                    mostRecent = entry.getKey();
                }
            }
            if (mostRecent instanceof AbstractClientPlayer && currentTime - mostRecentTime < fadeTimeMs) {
                animatedScale = interpolate(animatedScale, 1.0f, 0.1f);
                drawTargetHUD((AbstractClientPlayer) mostRecent, currentTime - mostRecentTime, fadeTimeMs, animatedScale);
            }
        } else {
            if (animatedScale > 0.01f) {
                animatedScale = interpolate(animatedScale, 0.0f, 0.15f);
                if (target != null) {
                    drawTargetHUD(target, 0, fadeTimeMs, animatedScale);
                }
            } else {
                target = null;
            }
        }
    }

    private void drawTargetHUD(AbstractClientPlayer target, long timeSinceTarget, long fadeTime, float scale) {
        if (target == null || scale <= 0.01f) return;

        switch ((TargetHUDMode) mode.getMode()) {
            case ThreeD:
                draw3DMode(target, timeSinceTarget, fadeTime, scale);
                break;
            case Simple:
                drawSimpleMode(target, timeSinceTarget, fadeTime, scale);
                break;
            default:
                drawFaceMode(target, timeSinceTarget, fadeTime, scale);
                break;
        }
    }

    private void drawFaceMode(AbstractClientPlayer target, long timeSinceTarget, long fadeTime, float scale) {
        float alpha = Math.min(1f, scale);
        int baseX = HUD.targetHUDX;
        int baseY = HUD.targetHUDY;

        int hudWidth = 150;
        int hudHeight = 50;

        GL11.glPushMatrix();
        GL11.glTranslated(baseX + hudWidth / 2.0, baseY + hudHeight / 2.0, 0);
        GL11.glScalef(scale, scale, 1.0f);
        GL11.glTranslated(-(baseX + hudWidth / 2.0), -(baseY + hudHeight / 2.0), 0);

        int x = baseX;
        int y = baseY;

        long timeSinceDamage = System.currentTimeMillis() - damageFlashTime;
        float flashAlpha = 0f;
        if (timeSinceDamage < 300) {
            flashAlpha = 1f - (timeSinceDamage / 300f);
        }

        int bgBase = new Color(26, 26, 26, (int)(alpha * 128)).getRGB();
        if (flashAlpha > 0) {
            int r = (int) (26 + (255 - 26) * flashAlpha);
            int g = (int) (26 * (1 - flashAlpha));
            int b = (int) (26 * (1 - flashAlpha));
            bgBase = new Color(r, g, b, (int)(alpha * 128)).getRGB();
        }
        RenderUtils.drawRoundedRect(x, y, x + hudWidth, y + hudHeight, 8, bgBase);

        int borderColor = (int) (alpha * 0xFF) << 24 | 0xFF8C00;
        if (flashAlpha > 0) {
            borderColor = (int) (alpha * 0xFF) << 24 | 0xFF0000;
        }
        RenderUtils.drawBorderedRoundedRect(x, y, x + hudWidth, y + hudHeight, 8, 2, borderColor, 0x00000000);

        GL11.glColor4f(1, 1, 1, alpha);
        mc.getTextureManager().bindTexture(target.getLocationSkin());
        Gui.drawScaledCustomSizeModalRect(x + 5, y + 5, 8.0F, 8.0F, 8, 8, 30, 30, 64.0F, 64.0F);

        String targetName = target.getName();
        int nameColor = (int) (alpha * 0xFF) << 24 | 0xFFFFFF;
        FontUtil.normal.drawSmoothString(StringUtils.stripControlCodes(targetName), x + 40, y + 8, nameColor);

        float health = target.getHealth();
        float maxHealth = Math.max(1f, target.getMaxHealth());

        if (animatedHealth == 0 || target == mc.thePlayer) animatedHealth = health;
        animatedHealth = interpolate(animatedHealth, health, 0.1f);
        float healthPercent = Math.max(0f, Math.min(1f, animatedHealth / maxHealth));

        int healthBarY = y + 25;
        int healthBarWidth = hudWidth - 45;

        RenderUtils.drawRoundedRect(x + 40, healthBarY, x + 40 + healthBarWidth, healthBarY + 8, 4,
                (int) (alpha * 0x40) << 24 | 0x404040);

        int healthColor = healthPercent > 0.5f ? 0x00FF00 : (healthPercent > 0.25f ? 0xFFFF00 : 0xFF0000);
        int healthFillColor = (int) (alpha * 0xFF) << 24 | healthColor;
        RenderUtils.drawRoundedRect(x + 40, healthBarY, x + 40 + (int) (healthBarWidth * healthPercent), healthBarY + 8, 4,
                healthFillColor);

        int armor = target.getTotalArmorValue();
        if (animatedArmor == 0 || target == mc.thePlayer) animatedArmor = armor;
        animatedArmor = interpolate(animatedArmor, armor, 0.1f);
        float armorPercent = Math.max(0f, Math.min(1f, animatedArmor / 20f));

        int armorBarY = healthBarY + 10;
        RenderUtils.drawRoundedRect(x + 40, armorBarY, x + 40 + healthBarWidth, armorBarY + 4, 2,
                (int) (alpha * 0x40) << 24 | 0x404040);

        int armorFillColor = (int) (alpha * 0xFF) << 24 | 0x00BFFF;
        RenderUtils.drawRoundedRect(x + 40, armorBarY, x + 40 + (int) (healthBarWidth * armorPercent), armorBarY + 4, 2,
                armorFillColor);

        String healthText = String.format("%.1f/%.1f", animatedHealth, maxHealth);
        int healthTextColor = (int) (alpha * 0xFF) << 24 | 0xCCCCCC;
        FontUtil.two.drawString(healthText, x + 40, y + 15, healthTextColor);

        GL11.glPopMatrix();
    }

    private void draw3DMode(AbstractClientPlayer target, long timeSinceTarget, long fadeTime, float scale) {
        float alpha = Math.min(1f, scale);
        int baseX = HUD.targetHUDX;
        int baseY = HUD.targetHUDY;

        int hudWidth = 150;
        int hudHeight = 50;

        GL11.glPushMatrix();
        GL11.glTranslated(baseX + hudWidth / 2.0, baseY + hudHeight / 2.0, 0);
        GL11.glScalef(scale, scale, 1.0f);
        GL11.glTranslated(-(baseX + hudWidth / 2.0), -(baseY + hudHeight / 2.0), 0);

        int x = baseX;
        int y = baseY;

        long timeSinceDamage = System.currentTimeMillis() - damageFlashTime;
        float flashAlpha = 0f;
        if (timeSinceDamage < 300) {
            flashAlpha = 1f - (timeSinceDamage / 300f);
        }

        int bgBase = new Color(26, 26, 26, (int)(alpha * 128)).getRGB();
        if (flashAlpha > 0) {
            int r = (int) (26 + (255 - 26) * flashAlpha);
            int g = (int) (26 * (1 - flashAlpha));
            int b = (int) (26 * (1 - flashAlpha));
            bgBase = new Color(r, g, b, (int)(alpha * 128)).getRGB();
        }
        RenderUtils.drawRoundedRect(x, y, x + hudWidth, y + hudHeight, 8, bgBase);

        int borderColor = (int) (alpha * 0xFF) << 24 | 0xFF8C00;
        if (flashAlpha > 0) {
            borderColor = (int) (alpha * 0xFF) << 24 | 0xFF0000;
        }
        RenderUtils.drawBorderedRoundedRect(x, y, x + hudWidth, y + hudHeight, 8, 2, borderColor, 0x00000000);

        if (scale > 0.5f) {
            try {
                GL11.glColor4f(1f, 1f, 1f, scale);
                GuiInventory.drawEntityOnScreen(x + 20, y + 34, 14, 0, 0, target);
            } catch (Exception e) {}
        }

        String targetName = target.getName();
        int nameColor = (int) (alpha * 0xFF) << 24 | 0xFFFFFF;
        FontUtil.normal.drawSmoothString(StringUtils.stripControlCodes(targetName), x + 40, y + 8, nameColor);

        float health = target.getHealth();
        float maxHealth = Math.max(1f, target.getMaxHealth());

        if (animatedHealth == 0 || target == mc.thePlayer) animatedHealth = health;
        animatedHealth = interpolate(animatedHealth, health, 0.1f);
        float healthPercent = Math.max(0f, Math.min(1f, animatedHealth / maxHealth));

        int healthBarY = y + 25;
        int healthBarWidth = hudWidth - 45;

        RenderUtils.drawRoundedRect(x + 40, healthBarY, x + 40 + healthBarWidth, healthBarY + 8, 4,
                (int) (alpha * 0x40) << 24 | 0x404040);

        int healthColor = healthPercent > 0.5f ? 0x00FF00 : (healthPercent > 0.25f ? 0xFFFF00 : 0xFF0000);
        int healthFillColor = (int) (alpha * 0xFF) << 24 | healthColor;
        RenderUtils.drawRoundedRect(x + 40, healthBarY, x + 40 + (int) (healthBarWidth * healthPercent), healthBarY + 8, 4,
                healthFillColor);

        int armor = target.getTotalArmorValue();
        if (animatedArmor == 0 || target == mc.thePlayer) animatedArmor = armor;
        animatedArmor = interpolate(animatedArmor, armor, 0.1f);
        float armorPercent = Math.max(0f, Math.min(1f, animatedArmor / 20f));

        int armorBarY = healthBarY + 10;
        RenderUtils.drawRoundedRect(x + 40, armorBarY, x + 40 + healthBarWidth, armorBarY + 4, 2,
                (int) (alpha * 0x40) << 24 | 0x404040);

        int armorFillColor = (int) (alpha * 0xFF) << 24 | 0x00BFFF;
        RenderUtils.drawRoundedRect(x + 40, armorBarY, x + 40 + (int) (healthBarWidth * armorPercent), armorBarY + 4, 2,
                armorFillColor);

        String healthText = String.format("%.1f/%.1f", animatedHealth, maxHealth);
        int healthTextColor = (int) (alpha * 0xFF) << 24 | 0xCCCCCC;
        FontUtil.two.drawString(healthText, x + 40, y + 15, healthTextColor);

        GL11.glPopMatrix();
    }

    private void drawSimpleMode(AbstractClientPlayer target, long timeSinceTarget, long fadeTime, float scale) {
        float alpha = Math.min(1f, scale);
        int baseX = HUD.targetHUDX;
        int baseY = HUD.targetHUDY;

        int hudWidth = 130;
        int hudHeight = 32;

        GL11.glPushMatrix();
        GL11.glTranslated(baseX + hudWidth / 2.0, baseY + hudHeight / 2.0, 0);
        GL11.glScalef(scale, scale, 1.0f);
        GL11.glTranslated(-(baseX + hudWidth / 2.0), -(baseY + hudHeight / 2.0), 0);

        int x = baseX;
        int y = baseY;

        long timeSinceDamage = System.currentTimeMillis() - damageFlashTime;
        float flashAlpha = 0f;
        if (timeSinceDamage < 300) {
            flashAlpha = 1f - (timeSinceDamage / 300f);
        }

        int bgBase = new Color(26, 26, 26, (int)(alpha * 128)).getRGB();
        if (flashAlpha > 0) {
            int r = (int) (26 + (255 - 26) * flashAlpha);
            int g = (int) (26 * (1 - flashAlpha));
            int b = (int) (26 * (1 - flashAlpha));
            bgBase = new Color(r, g, b, (int)(alpha * 128)).getRGB();
        }
        RenderUtils.drawRoundedRect(x, y, x + hudWidth, y + hudHeight, 8, bgBase);

        int borderColor = (int) (alpha * 0xFF) << 24 | 0xFF8C00;
        if (flashAlpha > 0) {
            borderColor = (int) (alpha * 0xFF) << 24 | 0xFF0000;
        }
        RenderUtils.drawBorderedRoundedRect(x, y, x + hudWidth, y + hudHeight, 8, 2, borderColor, 0x00000000);

        String targetName = target.getName();
        int nameColor = (int) (alpha * 0xFF) << 24 | 0xFFFFFF;
        FontUtil.normal.drawSmoothString(StringUtils.stripControlCodes(targetName), x + 5, y + 5, nameColor);

        float health = target.getHealth();
        float maxHealth = Math.max(1f, target.getMaxHealth());

        if (animatedHealth == 0 || target == mc.thePlayer) animatedHealth = health;
        animatedHealth = interpolate(animatedHealth, health, 0.1f);
        float healthPercent = Math.max(0f, Math.min(1f, animatedHealth / maxHealth));

        int healthBarY = y + 19;
        int healthBarWidth = hudWidth - 10;

        RenderUtils.drawRoundedRect(x + 5, healthBarY, x + 5 + healthBarWidth, healthBarY + 8, 4,
                (int) (alpha * 0x40) << 24 | 0x404040);

        int healthColor = healthPercent > 0.5f ? 0x00FF00 : (healthPercent > 0.25f ? 0xFFFF00 : 0xFF0000);
        int healthFillColor = (int) (alpha * 0xFF) << 24 | healthColor;
        RenderUtils.drawRoundedRect(x + 5, healthBarY, x + 5 + (int) (healthBarWidth * healthPercent), healthBarY + 8, 4,
                healthFillColor);

        int actualHealthInt = Math.round(animatedHealth);
        String healthText = String.format("%d/%d", actualHealthInt, (int) maxHealth);
        int healthTextColor = (int) (alpha * 0xFF) << 24 | 0xCCCCCC;
        double healthTextWidth = FontUtil.normal.getStringWidth(healthText);
        FontUtil.normal.drawSmoothString(healthText, (int)(x + hudWidth - 5 - healthTextWidth), y + 5, healthTextColor);

        GL11.glPopMatrix();
    }

    @Override
    public void onEnable() {
        target = null;
        animatedScale = 0f;
        recentTargets.clear();
    }

    @Override
    public void onDisable() {
        target = null;
        animatedScale = 0f;
        recentTargets.clear();
    }

    private float interpolate(float current, float target, float speed) {
        return current + (target - current) * speed;
    }

    public enum TargetHUDMode {
        Face("Face"), ThreeD("3D"), Simple("Simple");
        private final String display;
        TargetHUDMode(String display) { this.display = display; }
        @Override public String toString() { return display; }
    }
}
