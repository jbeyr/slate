package slate.mixins.impl.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slate.module.ModuleManager;
import slate.module.impl.world.DelayRemover;

@Mixin({Minecraft.class})
public abstract class MixinMinecraft {
    @Shadow
    private int leftClickCounter;

    @Inject(method = "clickMouse", at = @At("HEAD"))
    private void onClickMouse(CallbackInfo ci) {
        DelayRemover dm = ModuleManager.delayRemover;
        if (dm.isNoHitDelayEnabled()) leftClickCounter = 0;
    }

    @Inject(method = "sendClickBlockToController", at = @At("HEAD"))
    private void zeroCounterEveryTick(boolean holding, CallbackInfo ci) {
        DelayRemover dm = ModuleManager.delayRemover;
        if (dm.isNoHitDelayEnabled()) leftClickCounter = 0;
    }
}
