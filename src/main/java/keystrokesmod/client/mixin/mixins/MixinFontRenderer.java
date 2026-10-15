package keystrokesmod.client.mixin.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import keystrokesmod.client.main.Raven;
import keystrokesmod.client.module.Module;
import keystrokesmod.client.module.modules.other.NameHider;
import net.minecraft.client.gui.FontRenderer;

@Mixin(priority = 1005, value = FontRenderer.class)
public class MixinFontRenderer {

    @ModifyVariable(method = "renderStringAtPos", at = @At("HEAD"), argsOnly = true)
    private String renderStringAtPos(String p_renderStringAtPos_1_) {
        if (p_renderStringAtPos_1_ == null || Raven.moduleManager == null) {
            return p_renderStringAtPos_1_;
        }
        Module nameHider = Raven.moduleManager.getModuleByClazz(NameHider.class);
        if ((nameHider != null) && nameHider.isEnabled()) {
            return NameHider.format(p_renderStringAtPos_1_);
        }
        return p_renderStringAtPos_1_;
    }

    @ModifyVariable(method = "getStringWidth", at = @At("HEAD"), argsOnly = true)
    public String getStringWidth(String p_getStringWidth_1_) {
        if (p_getStringWidth_1_ == null || Raven.moduleManager == null) {
            return p_getStringWidth_1_;
        }
        Module nameHider = Raven.moduleManager.getModuleByClazz(NameHider.class);
        if ((nameHider != null) && nameHider.isEnabled()) {
            return NameHider.format(p_getStringWidth_1_);
        }
        return p_getStringWidth_1_;
    }

}
