package slate.mixins.impl.entity;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slate.event.PreMotionEvent;
import slate.module.Module;
import slate.module.ModuleManager;
import slate.module.impl.player.AutoDiamondUpgrade;

@Mixin(EntityPlayerSP.class)
public class MixinEntityPlayerSP {

    @Inject(method = "onUpdateWalkingPlayer", at = @At("HEAD"))
    private void onUpdateWalkingPlayer(CallbackInfo ci) {
        EntityPlayerSP me = (EntityPlayerSP) (Object) this;
        ModuleManager.bridgeAssist.setLastMovementInput(me.movementInput);
        ModuleManager.autoDiamondUpgrade.onPreMotion();
    }


}