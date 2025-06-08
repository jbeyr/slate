package slate.module.impl.combat;

import lombok.Getter;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;
import slate.event.custom.PacketEvent;
import slate.mixins.impl.render.RenderManagerAccessor;
import slate.module.Module;
import slate.module.setting.impl.ButtonSetting;
import slate.module.setting.impl.DescriptionSetting;
import slate.module.setting.impl.SliderSetting;
import slate.utility.Utils;
import slate.utility.slate.SlantRenderUtils;
import slate.utility.slate.manager.PacketInterceptionManager;

import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Getter
public class Backtrack extends Module {

    // region Settings
    private final SliderSetting maxDelay = new SliderSetting("Max Delay (ms)", 50, 1, 500, 1);
    private final SliderSetting minRange = new SliderSetting("Min Range", 3.0, 0.1, 6.0, 0.1);
    private final SliderSetting maxRange = new SliderSetting("Max Range", 4.5, 3.0, 6.0, 0.1);
    private final ButtonSetting disableIfHit = new ButtonSetting("Disable if hit", true);

    private final ButtonSetting allPackets = new ButtonSetting("All Packets", true);
    private final ButtonSetting forMovePackets = new ButtonSetting("Move Packets", true, () -> !allPackets.isToggled());
    private final ButtonSetting forAttackPackets = new ButtonSetting("Attack Packets", false, () -> !allPackets.isToggled());

    // settings moved from NetworkHandler
    private final ButtonSetting showTruePositionsButton = new ButtonSetting("Show true positions", true);
    // endregion

    // region State
    private final Queue<Packet<?>> heldPackets = new ConcurrentLinkedQueue<>();
    private Optional<Vec3> lastSentPosition = Optional.empty();
    private boolean isActivelyHolding = false;
    private long holdStartTime = 0;

    private Optional<EntityPlayer> currentTarget = Optional.empty();
    private Optional<Vec3> truePosition = Optional.empty();
    private Optional<Vec3> lastTruePosition = Optional.empty();
    private Optional<Vec3> visualPosition = Optional.empty(); // for rendering
    // endregion

    public Backtrack() {
        super("Backtrack", category.combat);
        this.registerSetting(new DescriptionSetting("Delays packets to gain an advantage."));
        this.registerSetting(maxDelay, minRange, maxRange, disableIfHit, allPackets, forMovePackets, forAttackPackets, showTruePositionsButton);
    }

    @Override
    public void guiUpdate() throws Throwable {
        super.guiUpdate();
        Utils.correctValue(minRange, maxRange);
    }

    @Override
    public void onDisable() throws Throwable {
        super.onDisable();
        flushHeldPackets();
        PacketInterceptionManager.flushInboundQueue();
        setTarget(null);
    }

    // region Event Handling
    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (!this.isEnabled()) return;
        if (event.entityPlayer == mc.thePlayer && event.target instanceof EntityPlayer) {
            setTarget((EntityPlayer) event.target);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Utils.nullCheckPasses()) return;

        if (!this.isEnabled()) {
            if (isActivelyHolding) flushHeldPackets();
            if (currentTarget.isPresent()) setTarget(null); // clear target if disabled
            return;
        }

        validateCurrentTarget();
        PacketInterceptionManager.processInboundQueue(maxDelay.getInput());

        if (isActivelyHolding && System.currentTimeMillis() - holdStartTime > maxDelay.getInput()) {
            flushHeldPackets();
        }
    }

    @SubscribeEvent
    public void onPacketSend(PacketEvent.Send event) {
        if (!this.isEnabled() || !(mc.getNetHandler() instanceof NetHandlerPlayClient)) return;
        if (event.getPacket() instanceof C03PacketPlayer) {
            handlePlayerPacket((C03PacketPlayer) event.getPacket());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!this.isEnabled() || !(mc.getNetHandler() instanceof NetHandlerPlayClient)) return;

        updateTruePositionFromPacket(event.getPacket());

        if (isActivelyHolding && disableIfHit.isToggled() && event.getPacket() instanceof S12PacketEntityVelocity) {
            if (((S12PacketEntityVelocity) event.getPacket()).getEntityID() == mc.thePlayer.getEntityId()) {
                flushHeldPackets();
            }
        }

        boolean shouldDelay = false;
        if (allPackets.isToggled()) {
            shouldDelay = true;
        } else {
            if (forMovePackets.isToggled() && isMovementPacket(event.getPacket())) shouldDelay = true;
            if (forAttackPackets.isToggled() && isAttackPacket(event.getPacket())) shouldDelay = true;
        }
        if (shouldDelay) {
            PacketInterceptionManager.queueInboundPacket(event.getPacket());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onDisconnect(ClientDisconnectionFromServerEvent event) {
        flushHeldPackets();
        PacketInterceptionManager.flushInboundQueue();
        setTarget(null);
    }
    // endregion

    // region Rendering Logic (Moved from NetworkHandler)
    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (mc.theWorld == null || !this.isEnabled() || !showTruePositionsButton.isToggled()) {
            visualPosition = Optional.empty();
            return;
        }

        // render hitbox only when we have a target
        if (!currentTarget.isPresent()) {
            visualPosition = Optional.empty();
            return;
        }

        if (truePosition.isPresent()) {
            Vec3 targetPos = truePosition.get();
            if (!visualPosition.isPresent()) {
                visualPosition = Optional.of(targetPos);
            }
            Vec3 currentVisualPos = visualPosition.get();
            double smoothingFactor = 0.15;
            Vec3 newVisualPos = new Vec3(
                    lerp(currentVisualPos.xCoord, targetPos.xCoord, smoothingFactor),
                    lerp(currentVisualPos.yCoord, targetPos.yCoord, smoothingFactor),
                    lerp(currentVisualPos.zCoord, targetPos.zCoord, smoothingFactor)
            );
            visualPosition = Optional.of(newVisualPos);

            RenderManagerAccessor renderManager = (RenderManagerAccessor) mc.getRenderManager();
            Vec3 renderVec = new Vec3(
                    newVisualPos.xCoord - renderManager.getRenderPosX(),
                    newVisualPos.yCoord - renderManager.getRenderPosY(),
                    newVisualPos.zCoord - renderManager.getRenderPosZ()
            );
            SlantRenderUtils.drawBbox3d(renderVec, 0.0f, 1.0f, 0.0f, 0.7f, 0.25f, false);
        } else {
            visualPosition = Optional.empty();
        }
    }

    private double lerp(double start, double end, double percent) {
        return start + (end - start) * percent;
    }
    // endregion

    // region Core Logic
    private boolean isMovementPacket(Packet<?> packet) {
        return packet instanceof S14PacketEntity || packet instanceof S18PacketEntityTeleport || packet instanceof S12PacketEntityVelocity;
    }

    private boolean isAttackPacket(Packet<?> packet) {
        return packet instanceof S0BPacketAnimation;
    }

    private void handlePlayerPacket(C03PacketPlayer packet) {
        Optional<Vec3> targetPos = this.truePosition;
        Vec3 currentPos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        if (!lastSentPosition.isPresent()) lastSentPosition = Optional.of(currentPos);
        if (!targetPos.isPresent()) {
            flushAndSend(packet);
            return;
        }
        Vec3 packetPos = new Vec3(packet.getPositionX(), packet.getPositionY(), packet.getPositionZ());
        Vec3 nextPlayerPos = packet.isMoving() ? packetPos : currentPos;
        double nextDist = nextPlayerPos.distanceTo(targetPos.get());
        if (nextDist < maxRange.getInput() || lastSentPosition.get().distanceTo(targetPos.get()) < minRange.getInput()) {
            flushAndSend(packet);
        } else {
            holdPacket(packet);
        }
    }

    private void flushAndSend(C03PacketPlayer packet) {
        flushHeldPackets();
        sendPacketAndUpdate(packet);
    }

    private void holdPacket(Packet<?> packet) {
        if (!isActivelyHolding) {
            isActivelyHolding = true;
            holdStartTime = System.currentTimeMillis();
        }
        heldPackets.add(packet);
    }

    private void flushHeldPackets() {
        if (heldPackets.isEmpty() && !isActivelyHolding) return;
        isActivelyHolding = false;
        Packet<?> p;
        while ((p = heldPackets.poll()) != null) sendPacketAndUpdate((C03PacketPlayer) p);
        holdStartTime = 0;
    }

    private void sendPacketAndUpdate(C03PacketPlayer packet) {
        PacketInterceptionManager.sendPacketFromManager(packet);
        if (packet.isMoving()) {
            this.lastSentPosition = Optional.of(new Vec3(packet.getPositionX(), packet.getPositionY(), packet.getPositionZ()));
        }
    }

    private void setTarget(EntityPlayer newTarget) {
        Optional<EntityPlayer> newTargetOpt = Optional.ofNullable(newTarget);
        int currentId = currentTarget.map(Entity::getEntityId).orElse(-1);
        int newId = newTargetOpt.map(Entity::getEntityId).orElse(-2);
        if (newId != currentId) {
            this.currentTarget = newTargetOpt;
            this.truePosition = this.currentTarget.map(Entity::getPositionVector);
            this.lastTruePosition = this.truePosition;
            if (mc.thePlayer != null) this.lastSentPosition = Optional.of(new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ));
        }
    }

    private void validateCurrentTarget() {
        this.currentTarget.ifPresent(p -> {
            if (!p.isEntityAlive() || mc.thePlayer.getDistanceSqToEntity(p) > 20 * 20) {
                setTarget(null);
            }
        });
    }

    private void updateTruePositionFromPacket(Packet<?> packet) {
        if (!currentTarget.isPresent() || mc.theWorld == null) return;
        EntityPlayer target = currentTarget.get();
        if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport p = (S18PacketEntityTeleport) packet;
            if (p.getEntityId() == target.getEntityId()) setNewTruePosition(new Vec3(p.getX() / 32.0D, p.getY() / 32.0D, p.getZ() / 32.0D));
        }
        if (packet instanceof S14PacketEntity) {
            S14PacketEntity p = (S14PacketEntity) packet;
            Entity entity = p.getEntity(mc.theWorld);
            if (entity != null && entity.getEntityId() == target.getEntityId() && truePosition.isPresent()) {
                setNewTruePosition(truePosition.get().addVector(p.func_149062_c() / 32.0D, p.func_149061_d() / 32.0D, p.func_149064_e() / 32.0D));
            }
        }
    }

    private void setNewTruePosition(Vec3 newPos) {
        this.lastTruePosition = this.truePosition;
        this.truePosition = Optional.of(newPos);
    }
    // endregion
}