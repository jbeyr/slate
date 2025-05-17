package slate.mixins.impl.client;

import net.minecraft.util.MouseHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slate.module.Module;
import slate.module.ModuleManager;
import slate.module.impl.combat.AimAssist;
import slate.module.impl.combat.aimassist.NormalAimAssist;

@Mixin(MouseHelper.class)
public abstract class MixinMouseHelper {

    @Shadow public int deltaX;
    @Shadow public int deltaY;

    @Inject(method = "mouseXYChange", at = @At("RETURN"))
    private void onMouseXYChangeReturn(CallbackInfo ci) {
        NormalAimAssist naa = ModuleManager.aimAssist.getNormalAimAssist();
        if (naa.isAssistEnabledAndActive()) {
            int assistDX = naa.getAssistDX_toApplyThisFrame();
            int assistDY = naa.getAssistDY_toApplyThisFrame();

            this.deltaX += assistDX;
            this.deltaY += assistDY;
        }
    }
}