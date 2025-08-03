package slate.utility.slate;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import slate.Main;
import slate.module.ModuleManager;
import slate.module.impl.combat.Backtrack;
import slate.module.impl.other.PingSpoofer;
import slate.utility.Utils;

import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class PacketManager {

    private static final AtomicBoolean hardFlushing = new AtomicBoolean(false);
    public static Minecraft mc = Main.mc;
    public static ConcurrentLinkedQueue<DelayedPacket<? extends INetHandler>> inboundPacketsQueue = new ConcurrentLinkedQueue<>();
    public static ConcurrentLinkedQueue<DelayedPacket<? extends INetHandler>> outboundPacketsQueue = new ConcurrentLinkedQueue<>();

    @Getter
    private static EntityPlayer target;

    // The positions for the start (prev) and end (curr) of the current game tick.
    @Getter
    private static Vec3 currTickTargetPos = null;
    @Getter
    private static Vec3 prevTickTargetPos = null;

    // A volatile variable to safely receive the latest position from the network thread.
    private static volatile Vec3 nextTickTargetPos = null;

    public static Optional<AxisAlignedBB> getTargetBox() {
        if (currTickTargetPos == null) return Optional.empty();
        return Optional.of(new AxisAlignedBB(currTickTargetPos.xCoord - 0.3, currTickTargetPos.yCoord, currTickTargetPos.zCoord - 0.3,
                currTickTargetPos.xCoord + 0.3, currTickTargetPos.yCoord + 1.8, currTickTargetPos.zCoord + 0.3));
    }

    private static void cacheNewTargetPosition(Vec3 v) {
        nextTickTargetPos = v;
    }

    private static double packetToWorldCoord(int fixedPointCoordinate) {
        return fixedPointCoordinate / 32D;
    }

    public static boolean shouldSpoofInboundPackets(Packet p) {
        Backtrack bt = ModuleManager.backtrack;
        PingSpoofer ps = ModuleManager.pingSpoofer;

        if (hardFlushing.get()) return false;
        if (p instanceof S00PacketKeepAlive) return false;

        if (isPlayClientPacket(p) && Utils.nullCheckPasses()) {
            if (bt.isEnabled() && target != null) {
                if (p instanceof S18PacketEntityTeleport) {
                    if (((S18PacketEntityTeleport) p).getEntityId() == mc.thePlayer.getEntityId()) {
                        processWholePacketQueue();
                    } else if (((S18PacketEntityTeleport) p).getEntityId() == target.getEntityId()) {
                        cacheNewTargetPosition(new Vec3(
                                packetToWorldCoord(((S18PacketEntityTeleport) p).getX()),
                                packetToWorldCoord(((S18PacketEntityTeleport) p).getY()),
                                packetToWorldCoord(((S18PacketEntityTeleport) p).getZ())
                        ));
                    }
                } else if (p instanceof S14PacketEntity) {
                    if (((S14PacketEntity) p).getEntity(mc.theWorld) != null && ((S14PacketEntity) p).getEntity(mc.theWorld).getEntityId() == target.getEntityId()) {
                        // Use the most recent position (either from this tick or a pending one) as the base for the relative update.
                        Vec3 lastPos = nextTickTargetPos != null ? nextTickTargetPos : currTickTargetPos;
                        if (lastPos != null) {
                            cacheNewTargetPosition(new Vec3(
                                    lastPos.xCoord + packetToWorldCoord(((S14PacketEntity) p).func_149062_c()),
                                    lastPos.yCoord + packetToWorldCoord(((S14PacketEntity) p).func_149061_d()),
                                    lastPos.zCoord + packetToWorldCoord(((S14PacketEntity) p).func_149064_e())));
                        }
                    }
                }
            }

            if (bt.isEnabled() && bt.isShouldSpoof()) return true;
            if (!bt.isShouldSpoof() && ps.isEnabled()) return true;

            processWholePacketQueue();
        }

        return false;
    }

    public static <T extends INetHandler> void enqueueSpoofedInboundPacket(Packet<T> p) {
        inboundPacketsQueue.add(new DelayedPacket<>(p, System.currentTimeMillis()));
    }

    public static <T extends INetHandler> void enqueueSpoofedOutboundPacket(Packet<T> p) {
        outboundPacketsQueue.add(new DelayedPacket<>(p, System.currentTimeMillis()));
    }

    public static void processWholePacketQueue() {
        PingSpoofer ps = ModuleManager.pingSpoofer;
        // for (DelayedPacket packet : inboundPacketsQueue) {
        //     if (!ps.isEnabled() || System.currentTimeMillis() > packet.getTime() + ps.getDelayMs()) {
        //         try {
        //             Packet p = packet.getPacket();
        //             p.processPacket(mc.thePlayer.sendQueue.getNetworkManager().getNetHandler());
        //         } catch (Exception e) {
        //             e.printStackTrace();
        //         }
        //         inboundPacketsQueue.remove(packet);
        //     }
        // }
        Iterator<DelayedPacket<? extends INetHandler>> it = inboundPacketsQueue.iterator();

        while (it.hasNext()) {
            DelayedPacket dp = it.next();
            if(!ps.isEnabled() || System.currentTimeMillis() > dp.getTime() + ps.getDelayMs()) {
                try {
                    Packet p = dp.getPacket();
                    p.processPacket(mc.thePlayer.sendQueue.getNetworkManager().getNetHandler());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                it.remove();
            }
        }
    }

    public static void forceFlushInboundQueue() {
        if (!hardFlushing.compareAndSet(false, true)) return;
        try {
            for (; ; ) {
                DelayedPacket dp = inboundPacketsQueue.poll();
                if (dp == null) break;
                try {
                    dp.getPacket().processPacket(mc.thePlayer.sendQueue.getNetworkManager().getNetHandler());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } finally {
            hardFlushing.set(false);
        }
    }

    public static void sendWholeOutboundQueue() {
        for (DelayedPacket packet : outboundPacketsQueue) {
            try {
                Packet p = packet.getPacket();
                mc.thePlayer.sendQueue.getNetworkManager().sendPacket(p);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
            outboundPacketsQueue.remove(packet);
        }
    }

    public static boolean isPlayClientPacket(Packet<? extends INetHandler> packet) {
        return packet instanceof INetHandlerPlayClient;
    }

    public static void setTarget(Optional<EntityPlayer> t) {
        setNewTarget(Optional.ofNullable(getTarget()), t);
    }

    public static void setNewTarget(Optional<EntityPlayer> oldTarget, Optional<EntityPlayer> newTarget) {
        target = newTarget.orElse(null);
        if (target == null) {
            currTickTargetPos = null;
            prevTickTargetPos = null;
            nextTickTargetPos = null;
        } else {
            Vec3 initialPos = new Vec3(target.posX, target.posY, target.posZ);
            currTickTargetPos = initialPos;
            prevTickTargetPos = initialPos;
            nextTickTargetPos = null;
        }
    }

    public static Vec3 getInterpolatedTargetPos(float partialTicks) {
        if (currTickTargetPos == null) return null;
        if (prevTickTargetPos == null) return currTickTargetPos;
        double ix = prevTickTargetPos.xCoord + (currTickTargetPos.xCoord - prevTickTargetPos.xCoord) * partialTicks;
        double iy = prevTickTargetPos.yCoord + (currTickTargetPos.yCoord - prevTickTargetPos.yCoord) * partialTicks;
        double iz = prevTickTargetPos.zCoord + (currTickTargetPos.zCoord - prevTickTargetPos.zCoord) * partialTicks;
        return new Vec3(ix, iy, iz);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (Utils.nullCheckPasses() && e.phase == TickEvent.Phase.START) {

            // --- Correct Interpolation Logic ---
            if (target != null) {
                // This is the correct, Minecraft-style way to handle interpolation.
                // At the start of every tick, the "current" position from the last tick becomes the "previous" position for this tick.
                // This happens regardless of whether a new packet has arrived.
                prevTickTargetPos = currTickTargetPos;

                // Now, we check if the network thread has provided a new position.
                if (nextTickTargetPos != null) {
                    // If so, update the "current" position to this new value.
                    currTickTargetPos = nextTickTargetPos;
                    // And clear the cached value so we don't process it again.
                    nextTickTargetPos = null;
                }
            }

            Backtrack bt = ModuleManager.backtrack;
            PingSpoofer ps = ModuleManager.pingSpoofer;

            if (bt.isEnabled()) {
                bt.tickCheck();
                long now = System.currentTimeMillis();
                Iterator<DelayedPacket<? extends INetHandler>> it = inboundPacketsQueue.iterator();
                while(it.hasNext()) {
                    DelayedPacket dp = it.next();
                    long effectiveDelay = 0;
                    if(bt.isShouldSpoof()) effectiveDelay += (long) bt.getDelayMs();
                    if(ps.isEnabled()) effectiveDelay += (long) ps.getDelayMs();
                    if(now > dp.getTime() + effectiveDelay) {
                        try {
                            dp.getPacket().processPacket(mc.thePlayer.sendQueue.getNetworkManager().getNetHandler());
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                        it.remove();
                    }
                }

                // for (DelayedPacket dp : inboundPacketsQueue) {
                //     long effectiveDelay = 0;
                //     if (bt.isShouldSpoof()) effectiveDelay += bt.getDelayMs();
                //     if (ps.isEnabled()) effectiveDelay += ps.getDelayMs();
                //     if (now > dp.getTime() + effectiveDelay) {
                //         try {
                //             dp.getPacket().processPacket(mc.thePlayer.sendQueue.getNetworkManager().getNetHandler());
                //         } catch (Exception ex) {
                //             ex.printStackTrace();
                //         }
                //         inboundPacketsQueue.remove(dp);
                //     }
                // }
            }

            if (ps.isEnabled() && !bt.isShouldSpoof()) {
                ps.tickCheck();
            }
        }
    }

    @SubscribeEvent
    public void onRenderTick(net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent e) {
        if (e.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END) return;
        processWholePacketQueue();
        sendWholeOutboundQueue();
    }

    public static class InboundHandlerTuplePacketListener<T extends INetHandler> {
        public final Packet<T> packet;
        public final GenericFutureListener<? extends Future<? super Void>>[] futureListeners;

        @SafeVarargs
        public InboundHandlerTuplePacketListener(Packet<T> p_i45146_1_, GenericFutureListener<? extends Future<? super Void>>... p_i45146_2_) {
            this.packet = p_i45146_1_;
            this.futureListeners = p_i45146_2_;
        }
    }
}