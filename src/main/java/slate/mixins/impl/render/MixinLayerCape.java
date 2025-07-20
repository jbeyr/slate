package slate.mixins.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerCape;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import slate.module.impl.render.CustomCape;

@Mixin(priority = 1001, value = LayerCape.class)
public abstract class MixinLayerCape {
    @Mutable
    @Final
    @Shadow
    private final RenderPlayer playerRenderer;

    public MixinLayerCape(RenderPlayer playerRendererIn) {
        this.playerRenderer = playerRendererIn;
    }

    @Redirect(method = "doRenderLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/AbstractClientPlayer;isWearing(Lnet/minecraft/entity/player/EnumPlayerModelParts;)Z"))
    private boolean modifyIsWearing(AbstractClientPlayer player, EnumPlayerModelParts part) {
        if (player.equals(Minecraft.getMinecraft().thePlayer) && CustomCape.cape.getInput() > 0) {
            return true;
        }
        return player.isWearing(part);
    }

    @Redirect(method = "doRenderLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/AbstractClientPlayer;getLocationCape()Lnet/minecraft/util/ResourceLocation;"))
    private ResourceLocation modifyGetLocationCape(AbstractClientPlayer player) {
        if (player.equals(Minecraft.getMinecraft().thePlayer) && CustomCape.cape.getInput() > 0) {
            return CustomCape.LOADED_CAPES.get((int) (CustomCape.cape.getInput()));
        }
        return player.getLocationCape();
    }
}