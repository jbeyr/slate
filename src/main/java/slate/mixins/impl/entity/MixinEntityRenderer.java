package slate.mixins.impl.entity;


import net.minecraft.client.renderer.EntityRenderer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slate.module.ModuleManager;
import slate.module.impl.render.CustomFOV;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {

    /**
     * @author ub
     * @reason basically reimplementation of optifine  "Dynamic FOV:off" setting
     */
    @Inject(method = "getFOVModifier", at = @At("RETURN"), cancellable = true)
    public void onGetFOVModifier(@NotNull CallbackInfoReturnable<Float> cir) {
        CustomFOV cf = ModuleManager.customFOV;

        if (cf != null && cf.isEnabled() && cf.isUseStaticFov()) {
            cir.setReturnValue(cf.getActiveFov());
        }
    }
}
