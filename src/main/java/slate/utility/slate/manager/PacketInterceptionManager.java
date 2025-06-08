package slate.utility.slate.manager;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import slate.utility.slate.DelayedPacket;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A simplified utility for queueing and processing inbound packets.
 * Includes a flag to prevent packet re-injection loops.
 */
public class PacketInterceptionManager {

    // region Fields
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Queue<DelayedPacket> inboundQueue = new ConcurrentLinkedQueue<>();
    @Getter private static boolean isManagerSending = false;
    // endregion

    // region Public API
    /**
     * Queues an inbound packet for delayed processing.
     * @param packet The packet to queue.
     */
    public static void queueInboundPacket(Packet<?> packet) {
        inboundQueue.add(new DelayedPacket(packet, System.currentTimeMillis()));
    }

    /**
     * Processes the inbound packet queue based on a given delay.
     * @param delay The delay in milliseconds to wait before processing.
     */
    public static void processInboundQueue(double delay) {
        while (inboundQueue.peek() != null) {
            DelayedPacket delayedPacket = inboundQueue.peek();
            if (System.currentTimeMillis() >= delayedPacket.getTimestamp() + delay) {
                inboundQueue.poll();
                processInboundPacket(delayedPacket.getPacket());
            } else {
                break;
            }
        }
    }

    /**
     * Flushes all packets in the inbound queue immediately.
     */
    public static void flushInboundQueue() {
        DelayedPacket p;
        while ((p = inboundQueue.poll()) != null) processInboundPacket(p.getPacket());
    }

    /**
     * The safe way for modules to send packets from their queues.
     * Sets a flag to prevent our own Mixins from re-intercepting it.
     */
    public static void sendPacketFromManager(Packet<?> packet) {
        if (mc.getNetHandler() == null || isManagerSending) return;
        isManagerSending = true;
        try {
            mc.getNetHandler().addToSendQueue(packet);
        } finally {
            isManagerSending = false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void processInboundPacket(Packet packet) {
        if (mc.getNetHandler() != null) {
            try {
                packet.processPacket(mc.getNetHandler());
            } catch (Exception e) {
                System.err.println("Error processing INBOUND packet: " + packet.getClass().getSimpleName());
            }
        }
    }
    // endregion
}