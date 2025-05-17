package slate.mixins.impl.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slate.module.ModuleManager;
import slate.module.impl.world.DelayRemover;

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

}
