package slate.utility.slate;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.*;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import slate.Main;
import slate.module.ModuleManager;
import slate.module.impl.combat.Backtrack;
import slate.module.impl.other.PingSpoofer;
import slate.module.impl.world.targeting.TargetManager;
import slate.utility.Utils;

import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class PacketManager {

    public static Minecraft mc = Main.mc;

    private static final AtomicBoolean hardFlushing = new AtomicBoolean(false);
    // TODO public static state for packet management probably isn't a good idea
    public static ConcurrentLinkedQueue<DelayedPacket<? extends INetHandler>> inboundPacketsQueue = new ConcurrentLinkedQueue<>();
    public static ConcurrentLinkedQueue<DelayedPacket<? extends INetHandler>> outboundPacketsQueue = new ConcurrentLinkedQueue<>();

    // TODO refactor target to use Optional
    @Getter private static EntityPlayer target;
    @Getter private static Vec3 currTickTargetPos = null;
    @Getter private static Vec3 prevTickTargetPos = null;

    @Getter private static long prevTickTargetTime;
    @Getter private static long currTickTargetTime;


    public static Optional<AxisAlignedBB> getTargetBox() {
        if (currTickTargetPos == null) return Optional.empty();
        return Optional.of(new AxisAlignedBB(currTickTargetPos.xCoord - 0.3, currTickTargetPos.yCoord, currTickTargetPos.zCoord - 0.3,
                currTickTargetPos.xCoord + 0.3, currTickTargetPos.yCoord + 1.8, currTickTargetPos.zCoord + 0.3));
    }

    public static void setTargetPos(Vec3 v) {
        long timestamp = v == null ? 0 : System.currentTimeMillis();

        PacketManager.prevTickTargetPos = currTickTargetPos;
        PacketManager.prevTickTargetTime = currTickTargetTime;
        PacketManager.currTickTargetPos = v;
        PacketManager.currTickTargetTime = timestamp;
    }

    /**
     * Converts a fixed-point coordinate from a Minecraft packet to a floating-point coordinate.
     *
     * @param fixedPointCoordinate The coordinate value from the packet
     * @return The actual in-game coordinate
     */
    private static double packetToWorldCoord(int fixedPointCoordinate) {
        return fixedPointCoordinate / 32D;
    }

    public static boolean shouldSpoofInboundPackets(Packet p) {
        Backtrack bt = ModuleManager.backtrack;
        PingSpoofer ps = ModuleManager.pingSpoofer;

        if (hardFlushing.get()) {
            return false;
        }

        if (p instanceof S00PacketKeepAlive) {
            return false; // this is so we don't disconnect while in a hud menu
        }

        if (isPlayClientPacket(p) && Utils.nullCheckPasses()) {
            if (bt.isEnabled()) {
                if (p instanceof S18PacketEntityTeleport) {
                    if (((S18PacketEntityTeleport) p).getEntityId() == mc.thePlayer.getEntityId()) {
                        processWholePacketQueue(); // if the user is teleported, process inbound packets
                    } else if (target != null && ((S18PacketEntityTeleport) p).getEntityId() == target.getEntityId()) {
                        try {
                            setTargetPos(new Vec3(
                                    packetToWorldCoord(((S18PacketEntityTeleport) p).getX()),
                                    packetToWorldCoord(((S18PacketEntityTeleport) p).getY()),
                                    packetToWorldCoord(((S18PacketEntityTeleport) p).getZ())
                            ));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                } else if (p instanceof S14PacketEntity) {
                    if (((S14PacketEntity) p).getEntity(mc.theWorld) != null) {
                        if (target != null && ((S14PacketEntity) p).getEntity(mc.theWorld).getEntityId() == target.getEntityId()) {
                            try {
                                setTargetPos(new Vec3(
                                        currTickTargetPos.xCoord + packetToWorldCoord(((S14PacketEntity) p).func_149062_c()),
                                        currTickTargetPos.yCoord + packetToWorldCoord(((S14PacketEntity) p).func_149061_d()),
                                        currTickTargetPos.zCoord + packetToWorldCoord(((S14PacketEntity) p).func_149064_e())));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }

            if (bt.isEnabled() && bt.isShouldSpoof()) {
                return true;
            }

            // only run ping spoofer when backtrack isn't flushing
            if (!bt.isShouldSpoof() && ps.isEnabled()) {
                return true;
            }

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

        for (DelayedPacket packet : inboundPacketsQueue) {
            if (!ps.isEnabled() || System.currentTimeMillis() > packet.getTime() + ps.getDelayMs()) {
                try {
                    Packet p = packet.getPacket();
                    p.processPacket(mc.thePlayer.sendQueue.getNetworkManager().getNetHandler());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                inboundPacketsQueue.remove(packet);
            }
        }
    }

    /**
     * Flush everything in the inbound queue immediately, IGNORING PingSpoofer.
     * Used by Backtrack when it stops spoofing so the client won't hitch.
     */
    public static void forceFlushInboundQueue() {
        // setAndGet makes the write visible *before* we continue
        if (!hardFlushing.compareAndSet(false, true)) return; // already flushing

        try {
            for (;;) {
                DelayedPacket dp = inboundPacketsQueue.poll();  // atomic poll
                if (dp == null) break;                             // queue empty
                try {
                    dp.getPacket().processPacket(mc.thePlayer.sendQueue.getNetworkManager().getNetHandler());
                } catch (Exception e) { e.printStackTrace(); }
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

        // return packet instanceof INetHandlerPlayClient;

        return packet instanceof S0EPacketSpawnObject || packet instanceof S11PacketSpawnExperienceOrb || packet instanceof S2CPacketSpawnGlobalEntity || packet instanceof
                S0FPacketSpawnMob || packet instanceof S3BPacketScoreboardObjective || packet instanceof S10PacketSpawnPainting || packet instanceof S0CPacketSpawnPlayer || packet instanceof S0BPacketAnimation || packet instanceof
                S37PacketStatistics || packet instanceof S25PacketBlockBreakAnim || packet instanceof S36PacketSignEditorOpen || packet instanceof S35PacketUpdateTileEntity || packet instanceof S24PacketBlockAction || packet instanceof
                S23PacketBlockChange || packet instanceof S02PacketChat || packet instanceof S3APacketTabComplete || packet instanceof S22PacketMultiBlockChange || packet instanceof S34PacketMaps || packet instanceof S32PacketConfirmTransaction || packet instanceof
                S2EPacketCloseWindow || packet instanceof S30PacketWindowItems || packet instanceof S2DPacketOpenWindow || packet instanceof S31PacketWindowProperty || packet instanceof S2FPacketSetSlot || packet instanceof S3FPacketCustomPayload || packet instanceof S0APacketUseBed || packet instanceof S19PacketEntityStatus || packet instanceof S1BPacketEntityAttach || packet instanceof S27PacketExplosion || packet instanceof S2BPacketChangeGameState || packet instanceof
                S00PacketKeepAlive || packet instanceof S21PacketChunkData || packet instanceof S26PacketMapChunkBulk || packet instanceof S28PacketEffect || packet instanceof S14PacketEntity || packet instanceof S08PacketPlayerPosLook || packet instanceof
                S2APacketParticles || packet instanceof S39PacketPlayerAbilities || packet instanceof S38PacketPlayerListItem || packet instanceof S13PacketDestroyEntities || packet instanceof S1EPacketRemoveEntityEffect || packet instanceof S07PacketRespawn || packet instanceof
                S19PacketEntityHeadLook || packet instanceof S09PacketHeldItemChange || packet instanceof S3DPacketDisplayScoreboard || packet instanceof S1CPacketEntityMetadata || packet instanceof S12PacketEntityVelocity || packet instanceof S04PacketEntityEquipment || packet instanceof
                S1FPacketSetExperience || packet instanceof S06PacketUpdateHealth || packet instanceof S3EPacketTeams || packet instanceof S3CPacketUpdateScore || packet instanceof S05PacketSpawnPosition || packet instanceof S03PacketTimeUpdate || packet instanceof S33PacketUpdateSign || packet instanceof
                S29PacketSoundEffect || packet instanceof S0DPacketCollectItem || packet instanceof S18PacketEntityTeleport || packet instanceof S20PacketEntityProperties || packet instanceof S1DPacketEntityEffect || packet instanceof S42PacketCombatEvent || packet instanceof
                S41PacketServerDifficulty || packet instanceof S43PacketCamera || packet instanceof S44PacketWorldBorder || packet instanceof S45PacketTitle || packet instanceof S46PacketSetCompressionLevel || packet instanceof S47PacketPlayerListHeaderFooter || packet instanceof
                S48PacketResourcePackSend || packet instanceof S49PacketUpdateEntityNBT;
    }

    public static void setTarget(Optional<EntityPlayer> t) {
        setNewTarget(Optional.ofNullable(getTarget()), t);
    }

    public static void setNewTarget(Optional<EntityPlayer> oldTarget, Optional<EntityPlayer> newTarget) {
        Backtrack bt = ModuleManager.backtrack;
        PingSpoofer ps = ModuleManager.pingSpoofer;

        if (!Utils.nullCheckPasses() || (!bt.isEnabled() && !ps.isEnabled())) return;

        target = newTarget.orElse(null);
        setTargetPos(target == null ? null : new Vec3(target.posX, target.posY, target.posZ));
    }

    public static Vec3 getInterpolatedTargetPos(float partialTicks) {
        if (currTickTargetPos == null) return null;
        if (prevTickTargetPos == null) return currTickTargetPos;

        double ix = prevTickTargetPos.xCoord +
                (currTickTargetPos.xCoord - prevTickTargetPos.xCoord) * partialTicks;
        double iy = prevTickTargetPos.yCoord +
                (currTickTargetPos.yCoord - prevTickTargetPos.yCoord) * partialTicks;
        double iz = prevTickTargetPos.zCoord +
                (currTickTargetPos.zCoord - prevTickTargetPos.zCoord) * partialTicks;

        return new Vec3(ix, iy, iz);
    }



    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (Utils.nullCheckPasses() && e.phase == TickEvent.Phase.START) {

            Backtrack   bt = ModuleManager.backtrack;
            PingSpoofer ps = ModuleManager.pingSpoofer;

            if (bt.isEnabled()) {
                bt.tickCheck();

                long now = System.currentTimeMillis();
                for (DelayedPacket dp : PacketManager.inboundPacketsQueue) {

                    long effectiveDelay = 0;
                    if (bt.isShouldSpoof())         // add Back-track delay
                        effectiveDelay += bt.getDelayMs();

                    if (ps.isEnabled())             // add Ping-Spoofer delay
                        effectiveDelay += ps.getDelayMs();

                    if (now > dp.getTime() + effectiveDelay) {
                        try {
                            dp.getPacket().processPacket(mc.thePlayer.sendQueue.getNetworkManager().getNetHandler());
                        } catch (Exception ex) { ex.printStackTrace(); }
                        PacketManager.inboundPacketsQueue.remove(dp);
                    }
                }
            }

            /* PingSpoofer tick still runs only when Back-track is not spoofing */
            if (ps.isEnabled() && !bt.isShouldSpoof()) {
                ps.tickCheck();
            }
        }
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

    // PacketManager.java  (same class that already subscribes to ClientTickEvent)

    @SubscribeEvent
    public void onRenderTick(net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent e) {
        if (e.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END)
            return;

        processWholePacketQueue();   // inbound
        sendWholeOutboundQueue();    // outbound
    }
}