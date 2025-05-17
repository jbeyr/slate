package slate.mixins.impl.entity;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slate.module.ModuleManager;
import slate.module.impl.player.AutoTool;
import slate.module.impl.world.DelayRemover;
import slate.utility.Utils;

@Mixin(PlayerControllerMP.class)
public class MixinPlayerControllerMP {

    @Shadow
    private boolean isHittingBlock;

    @Shadow
    private int blockHitDelay;

    @Inject(method = "onPlayerDamageBlock", at = @At("HEAD"))
    private void onPlayerDamageBlock(BlockPos posBlock, EnumFacing directionFacing, CallbackInfoReturnable<Boolean> cir) {
        DelayRemover dm = ModuleManager.delayRemover;
        if(dm.isNoMiningDelayEnabled()) blockHitDelay = 0;

        AutoTool at = ModuleManager.autoTool;

        Minecraft mc = Minecraft.getMinecraft();
        if (at.isEnabled()) {
            Block block = mc.theWorld.getBlockState(posBlock).getBlock();
            int slot = Utils.getTool(block);
            if (slot != mc.thePlayer.inventory.currentItem) {
                mc.thePlayer.inventory.currentItem = slot;
            }
        }
    }

    @Inject(method = "updateController", at = @At("TAIL"))
    private void afterUpdateController(CallbackInfo ci) {
        DelayRemover dm = ModuleManager.delayRemover;
        if (dm.isNoMiningDelayEnabled()) {
            blockHitDelay = 0;
        }
    }

    @ModifyConstant(method = "updateBlockRemoving", constant = @Constant(intValue = 5))
    private int zeroOutDelay(int original) {
        DelayRemover dm = ModuleManager.delayRemover;
        return dm.isNoMiningDelayEnabled() ? 0 : original;  // 0 or keep vanilla
    }
}
