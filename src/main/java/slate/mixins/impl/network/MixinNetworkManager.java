package slate.mixins.impl.network;

import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slate.event.custom.PacketEvent;
import slate.utility.slate.manager.PacketInterceptionManager;

@Mixin(NetworkManager.class)
public abstract class MixinNetworkManager {

    /**
     * Injects into the head of the inbound packet handling method.
     * Fires our custom PacketEvent.Receive for modules to listen to.
     * The Backtrack module now handles all logic, so no manager call is needed here.
     */
    @Inject(method = "channelRead0", at = @At("HEAD"), cancellable = true)
    private void onChannelRead(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        PacketEvent.Receive event = new PacketEvent.Receive(packet);
        if (MinecraftForge.EVENT_BUS.post(event)) {
            ci.cancel();
        }
    }

    /**
     * Injects into the head of the outbound packet sending method.
     * Fires PacketEvent.Send, but ONLY if the packet is not being intentionally
     * sent by our own manager, which prevents an infinite loop.
     */
    @Inject(method = "sendPacket(Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
        // check the manager's flag to prevent infinite loops
        if (PacketInterceptionManager.isManagerSending()) {
            return;
        }

        PacketEvent.Send event = new PacketEvent.Send(packet);
        if (MinecraftForge.EVENT_BUS.post(event)) {
            ci.cancel();
        }
    }
}