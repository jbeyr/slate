package slate.mixins.impl.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slate.module.ModuleManager;
import slate.module.impl.movement.JumpReset;
import slate.utility.Utils;

import javax.swing.*;

import static slate.Main.mc;


@Mixin(value = NetHandlerPlayClient.class)
public class MixinNetHandlerPlayClient {

    public MixinNetHandlerPlayClient() {
    }

    // TODO remove old jump reset
    // @Inject(method = "handleEntityVelocity", at = {@At("HEAD")})
    // public void handleEntityVelocity(S12PacketEntityVelocity velocityPacket, CallbackInfo ci) {
    //     JumpReset jr = ModuleManager.jumpReset;
    //     if (!jr.isEnabled()) return;
    //     if (velocityPacket.getEntityID() != mc.thePlayer.getEntityId()) return;
    //
    //     if (mc.thePlayer == null || !mc.thePlayer.isEntityAlive()) return;
    //     if (mc.currentScreen != null) return;
    //     if (!Utils.nullCheckPasses()) return;
    //     if (!jr.shouldActivate()) return;
    //
    //     // prevent jumping after taking fall damage
    //     if (velocityPacket.getMotionY() <= 0) return;
    //
    //     boolean wasHeld = Minecraft.getMinecraft().gameSettings.keyBindJump.isKeyDown();
    //     jr.legitJump();
    //
    //     // release the jump key
    //     Timer timer = new Timer(20, (actionevent) -> {
    //         if (!wasHeld) KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
    //     });
    //     timer.setRepeats(false);
    //     timer.start();
    // }
}