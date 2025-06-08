package slate.event.custom;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.network.Packet;
import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * The base class for our custom packet events.
 * This event is fired from our Mixins and is marked as @Cancelable,
 * which allows any module listening to it to prevent the packet from
 * being processed further by calling event.setCanceled(true).
 */
@Getter
@RequiredArgsConstructor
@Cancelable // This annotation is crucial for allowing the event to be cancelled.
public class PacketEvent extends Event {

    /**
     * The packet associated with this event.
     */
    private final Packet<?> packet;

    /**
     * A static nested class representing an event for outgoing packets.
     * Fired when a packet is being sent from the client to the server.
     * Subscribing to this specific class allows modules to only listen for Send events.
     */
    public static class Send extends PacketEvent {
        public Send(Packet<?> packet) {
            super(packet);
        }
    }

    /**
     * A static nested class representing an event for incoming packets.
     * Fired when a packet has been received from the server, but before it is processed.
     * Subscribing to this specific class allows modules to only listen for Receive events.
     */
    public static class Receive extends PacketEvent {
        public Receive(Packet<?> packet) {
            super(packet);
        }
    }
}