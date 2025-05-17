package slate.mixins.impl.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slate.module.ModuleManager;
import slate.module.impl.player.AutoWeapon;
import slate.utility.ActionCoordinator;

@Mixin(EntityPlayer.class)
public class MixinEntityPlayer {
    @Inject(method = "attackTargetEntityWithCurrentItem", at = @At("HEAD"))
    private void swapToWeaponIfTargeting(Entity targetEntity, CallbackInfo ci) {
        AutoWeapon aw = ModuleManager.autoWeapon;
        if (!aw.isEnabled() || !ActionCoordinator.isHotbarSelectedSlotChangeAllowed() || !(targetEntity instanceof EntityLivingBase)) return;
        aw.swapToWeapon();
    }
}
