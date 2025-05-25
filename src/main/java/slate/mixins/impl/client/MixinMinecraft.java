package slate.mixins.impl.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slate.module.ModuleManager;
import slate.module.impl.render.Animations;
import slate.module.impl.world.DelayRemover;

import static slate.Main.mc;

@Mixin(value = Minecraft.class, priority = 1001)
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

    @Inject(method = "runTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;onStoppedUsingItem(Lnet/minecraft/entity/player/EntityPlayer;)V",
            shift = At.Shift.BY, by = 2
    ))
    private void onRunTick$usingWhileDigging(CallbackInfo ci) {
        if (ModuleManager.animations != null && ModuleManager.animations.isEnabled() && Animations.swingWhileDigging.isToggled()
                && mc.gameSettings.keyBindAttack.isKeyDown()) {
            if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                mc.thePlayer.swingItem();
            }
        }
    }
}
