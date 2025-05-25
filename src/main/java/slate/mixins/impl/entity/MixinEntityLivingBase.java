package slate.mixins.impl.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slate.event.SwingAnimationEvent;
import slate.module.ModuleManager;
import slate.module.impl.world.DelayRemover;
import slate.utility.Utils;

@Mixin(priority = 995, value = EntityLivingBase.class)
public abstract class MixinEntityLivingBase extends Entity {

    @Shadow
    private int jumpTicks;

    public MixinEntityLivingBase(World worldIn) {
        super(worldIn);
    }


    @Inject(method = "onLivingUpdate", at = @At("HEAD"))
    public void onLivingUpdate(CallbackInfo ci) {
        DelayRemover dm = ModuleManager.delayRemover;
        if (dm.isNoJumpDelayEnabled()) {
            this.jumpTicks = 0;
        }
    }

    /**
     * @author xia__mc
     * @reason for Animations module
     */
    @Inject(method = "getArmSwingAnimationEnd", at = @At("RETURN"), cancellable = true)
    private void onGetArmSwingAnimationEnd(@NotNull CallbackInfoReturnable<Integer> cir) {
        SwingAnimationEvent swingAnimationEvent = new SwingAnimationEvent(cir.getReturnValue());
        MinecraftForge.EVENT_BUS.post(swingAnimationEvent);

        cir.setReturnValue((int) (swingAnimationEvent.getAnimationEnd() * Utils.getTimer().timerSpeed));
    }

    @Inject(method = "isPotionActive(Lnet/minecraft/potion/Potion;)Z", at = @At("HEAD"), cancellable = true)
    private void isPotionActive(Potion p_isPotionActive_1_, final CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
        if (ModuleManager.potions != null && ModuleManager.potions.isEnabled() && ((p_isPotionActive_1_ == Potion.confusion && ModuleManager.potions.removeNausea.isToggled()) || (p_isPotionActive_1_ == Potion.blindness && ModuleManager.potions.removeBlindness.isToggled()))) {
            callbackInfoReturnable.setReturnValue(false);
        }
    }

}
