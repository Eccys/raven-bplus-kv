package keystrokesmod.client.module.setting.impl;

import java.awt.Color;

import com.google.gson.JsonObject;

import keystrokesmod.client.clickgui.kv.KvComponent;
import keystrokesmod.client.clickgui.kv.components.KvRgbComponent;
import keystrokesmod.client.clickgui.raven.Component;
import keystrokesmod.client.clickgui.raven.components.ModuleComponent;
import keystrokesmod.client.clickgui.raven.components.RGBComponent;
import keystrokesmod.client.clickgui.raven.components.SettingComponent;
import keystrokesmod.client.module.setting.Setting;
import keystrokesmod.client.utils.ColorM;
import keystrokesmod.client.utils.Utils;
import keystrokesmod.client.utils.Utils.Client;

public class RGBSetting extends Setting {

    private final String name;
    private int[] colour = new int[2];
    private int[] defaultColour = new int[2];
    private int colorRGB;

    public RGBSetting(String name, int defaultRed, int defaultGreen, int defaultBlue) {
        super(name);
        this.name = name;
        int r = clampChannel(defaultRed);
        int g = clampChannel(defaultGreen);
        int b = clampChannel(defaultBlue);
        this.defaultColour = new int[] { r, g, b };
        this.colour = new int[] { r, g, b };
        this.colorRGB = new Color(r, g, b).getRGB();
    }

    private static int clampChannel(int value) {
        return value > 255 ? 255 : (value < 0 ? 0 : value);
    }

    @Override
    public void resetToDefaults() {
        for (int i = 0; i < this.colour.length && i < this.defaultColour.length; i++) {
            this.colour[i] = this.defaultColour[i];
        }
        this.colorRGB = new Color(this.colour[0], this.colour[1], this.colour[2]).getRGB();
    }

    @Override
    public JsonObject getConfigAsJson() {
        JsonObject data = new JsonObject();
        data.addProperty("type", getSettingType());
        data.addProperty("red", getRed());
        data.addProperty("green", getGreen());
        data.addProperty("blue", getBlue());
        return data;
    }

    @Override
    public String getSettingType() {
        return "rgbsetting";
    }

    @Override
    public void applyConfigFromJson(JsonObject data) {
        if (data == null || !data.has("type") || data.get("type").isJsonNull())
            return;
        if (!data.get("type").getAsString().equals(getSettingType()))
            return;

        if (data.has("red") && !data.get("red").isJsonNull())
            setRed(data.get("red").getAsInt());
        if (data.has("green") && !data.get("green").isJsonNull())
            setGreen(data.get("green").getAsInt());
        if (data.has("blue") && !data.get("blue").isJsonNull())
            setBlue(data.get("blue").getAsInt());
    }

    @Override
    public Component createComponent(ModuleComponent moduleComponent) {
        return null;
    }

    public int getRed() {
        return this.colour[0];
    }

    public int getGreen() {
        return this.colour[1];
    }

    public int getBlue() {
        return this.colour[2];
    }

    public int[] getColors() {
        return this.colour;
    }

    public int getColor(int colour) {
        if (colour < 0 || colour >= this.colour.length) {
            return 0;
        }
        return this.colour[colour];
    }

    public int getRGB() {
        return colorRGB;
    }

    public void setRed(int red) {
        setColor(0, red);
    }

    public void setGreen(int green) {
        setColor(1, green);
    }

    public void setBlue(int blue) {
        setColor(2, blue);
    }

    public void setColor(int colour, int value) {
        if (colour < 0 || colour >= this.colour.length) {
            return;
        }
        value = clampChannel(value);
        this.colour[colour] = value;
        this.colorRGB = new Color(this.colour[0], this.colour[1], this.colour[2]).getRGB();
    }

    public void setColors(int[] colour) {
        if (colour == null || colour.length < 3) {
            return;
        }
        this.colour = new int[] {
            clampChannel(colour[0]),
            clampChannel(colour[1]),
            clampChannel(colour[2])
        };
        this.colorRGB = new Color(this.colour[0], this.colour[1], this.colour[2]).getRGB();
    }


	@Override
	public Class<? extends KvComponent> getComponentType() {
		return KvRgbComponent.class;
	}

    @Override
    public Class<? extends SettingComponent> getRavenComponentType() {
        return RGBComponent.class;
    }

	public enum NSColor {
	    Staic(d -> 0xFFFFFFFF), //Placeholder
	    Rainbow(d -> Utils.Client.rainbowDraw(1, d)),
	    Astolfo(d -> Utils.Client.astolfoColorsDraw(10, 14, d)),
	    Kv(Client::customDraw);
	    //Hurttime(-> (float) player.hurtTime),
	    //Fov(fovToEntity);

	    private final ColorM c;

	    private NSColor(ColorM c) {
	        this.c = c;
	    }

	    public int getColor() {
	        return c.color(0);
	    }

	    public int getColor(int delay) {
            return c.color(delay);
        }

        private static NSColor[] vals = values();
        public NSColor next() {
            return vals[(this.ordinal()+1) % vals.length];
        }
	}
}
