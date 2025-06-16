package slate.mixins.impl.client;

import net.minecraft.util.MouseHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slate.utility.slate.MouseManager;

@Mixin(MouseHelper.class)
public abstract class MixinMouseHelper {

    @Shadow public int deltaX;
    @Shadow public int deltaY;

    @Inject(method = "mouseXYChange", at = @At("RETURN"))
    private void onMouseXYChangeReturn(CallbackInfo ci) {
        int[] add = MouseManager.consume();
        this.deltaX += add[0];
        this.deltaY += add[1];
    }
}