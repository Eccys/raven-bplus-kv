package keystrokesmod.client.module.modules.render;

import com.google.common.eventbus.Subscribe;
import keystrokesmod.client.event.impl.ForgeEvent;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.modules.world.AntiBot;
import keystrokesmod.client.module.setting.impl.TickSetting;
import net.minecraftforge.client.event.RenderPlayerEvent.Post;
import net.minecraftforge.client.event.RenderPlayerEvent.Pre;
import org.lwjgl.opengl.GL11;

public class Chams extends Module {
    public static TickSetting ignoreBots;
    private boolean offsetActive = false;

    public Chams() {
        super("Chams", ModuleCategory.render);
        this.registerSetting(ignoreBots = new TickSetting("Ignore bots", true));
    }

    @Override
    public void onDisable() {
        offsetActive = false;
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        if (fe == null || fe.getEvent() == null) {
            return;
        }
        if (fe.getEvent() instanceof Pre) {
            Pre pre = (Pre) fe.getEvent();
            if (pre.entity != null && pre.entity != mc.thePlayer && (!ignoreBots.isToggled() || !AntiBot.bot(pre.entity))) {
                GL11.glEnable(32823);
                GL11.glPolygonOffset(1.0F, -1100000.0F);
                offsetActive = true;
            }
        } else if (fe.getEvent() instanceof Post) {
            if (offsetActive) {
                GL11.glDisable(32823);
                GL11.glPolygonOffset(1.0F, 1100000.0F);
                offsetActive = false;
            }
        }
    }
}