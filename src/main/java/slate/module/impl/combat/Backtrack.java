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
import net.minecraft.util.MathHelper;
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
    private final SliderSetting baseDelay = new SliderSetting("Base Delay (ms)", 0, 0, 200, 1);
    private final SliderSetting maxDynamicDelay = new SliderSetting("Max Dynamic Delay (ms)", 0, 0, 100, 1);
    private final SliderSetting maxOutboundHoldTime = new SliderSetting("Max Outbound Hold (ms)", 300, 50, 1000, 5);
    private final SliderSetting minRange = new SliderSetting("Min Range", 1.35, 0, 6.0, 0.02);
    private final SliderSetting maxRange = new SliderSetting("Max Range", 3.7, 3.0, 6.0, 0.02);
    private final SliderSetting maxTargetRange = new SliderSetting("Max Target Range", 5, 3.0, 10.0, 0.05);
    private final ButtonSetting disableIfHit = new ButtonSetting("Disable if hit", true);
    private final ButtonSetting allPackets = new ButtonSetting("All Packets", true);
    private final ButtonSetting forMovePackets = new ButtonSetting("Move Packets", true, () -> !allPackets.isToggled());
    private final ButtonSetting forAttackPackets = new ButtonSetting("Attack Packets", false, () -> !allPackets.isToggled());
    private final ButtonSetting showHitboxButton = new ButtonSetting("Show Hitbox", false);
    private final ButtonSetting showGhostButton = new ButtonSetting("Show Ghost", true);

    private final SliderSetting hitboxRed = new SliderSetting("Hitbox: red", 1, 0, 1, 0.01, showHitboxButton::isToggled);
    private final SliderSetting hitboxGreen = new SliderSetting("Hitbox: green", 1, 0, 1, 0.01, showHitboxButton::isToggled);
    private final SliderSetting hitboxBlue = new SliderSetting("Hitbox: blue", 1, 0, 1, 0.01, showHitboxButton::isToggled);
    private final SliderSetting hitboxAlpha = new SliderSetting("Hitbox: alpha", 1, 0, 1, 0.01, showHitboxButton::isToggled);
    private final SliderSetting hitboxFillMult = new SliderSetting("Hitbox: fill mult.", 0.25, 0, 1, 0.01, showHitboxButton::isToggled);

    private final SliderSetting ghostRed = new SliderSetting("Ghost: red", 1, 0, 1, 0.01, showGhostButton::isToggled);
    private final SliderSetting ghostGreen = new SliderSetting("Ghost: green", 1, 0, 1, 0.01, showGhostButton::isToggled);
    private final SliderSetting ghostBlue = new SliderSetting("Ghost: blue", 1, 0, 1, 0.01, showGhostButton::isToggled);
    private final SliderSetting ghostAlpha = new SliderSetting("Ghost: alpha", 1, 0, 1, 0.01, showGhostButton::isToggled);
    // endregion

    private double maxTargetRangeSq;

    // region State
    private final Queue<Packet<?>> heldPackets = new ConcurrentLinkedQueue<>();
    private Optional<Vec3> lastSentPosition = Optional.empty();
    private boolean isActivelyHolding = false;
    private long holdStartTime = 0;
    private Optional<EntityPlayer> currentTarget = Optional.empty();
    private Optional<Vec3> truePosition = Optional.empty();
    private Optional<Vec3> lastTruePosition = Optional.empty();
    private Optional<Vec3> visualPosition = Optional.empty();
    // endregion

    public Backtrack() {
        super("Backtrack", category.combat);
        this.registerSetting(new DescriptionSetting("Delays packets to gain an advantage."));
        this.registerSetting(baseDelay, maxDynamicDelay, maxOutboundHoldTime, minRange, maxRange, maxTargetRange, disableIfHit, allPackets, forMovePackets, forAttackPackets, showHitboxButton, showGhostButton,
                hitboxRed,hitboxBlue,hitboxGreen,hitboxAlpha,hitboxFillMult,
                ghostRed,ghostBlue,ghostGreen,ghostAlpha);
    }

    @Override
    public void guiUpdate() throws Throwable {
        super.guiUpdate();
        Utils.correctValue(minRange, maxRange);
        Utils.correctValue(maxRange, maxTargetRange);
        maxTargetRangeSq = Math.pow(maxTargetRange.getInput(), 2);
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
        if (!this.isEnabled() || !(mc.getNetHandler() instanceof NetHandlerPlayClient)) return;
        if (event.entityPlayer == mc.thePlayer && event.target instanceof EntityPlayer) {
            setTarget((EntityPlayer) event.target);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Utils.nullCheckPasses()) return;
        if (!this.isEnabled()) {
            if (isActivelyHolding) flushHeldPackets();
            if (currentTarget.isPresent()) setTarget(null);
            return;
        }
        validateCurrentTarget();

        // ** THE FIX: Calculate total delay by adding base and dynamic components **
        double dynamicDelay = calculateDynamicDelay();
        double totalDelay = baseDelay.getInput() + dynamicDelay;
        PacketInterceptionManager.processInboundQueue(totalDelay);

        // the maxOutboundHoldTime setting is for the outbound packet hold time
        if (isActivelyHolding && System.currentTimeMillis() - holdStartTime > maxOutboundHoldTime.getInput()) {
            flushHeldPackets();
        }
    }

    @SubscribeEvent
    public void onPacketSend(PacketEvent.Send event) {
        if (!this.isEnabled() || !(mc.getNetHandler() instanceof NetHandlerPlayClient)) {
            if (!heldPackets.isEmpty()) flushHeldPackets();
            return;
        }
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

    // region Rendering Logic
    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (mc.theWorld == null || !this.isEnabled() || (!showHitboxButton.isToggled() && !showGhostButton.isToggled())) {
            visualPosition = Optional.empty();
            return;
        }
        Optional<EntityPlayer> targetOpt = this.currentTarget;
        if (!targetOpt.isPresent()) {
            visualPosition = Optional.empty();
            return;
        }
        EntityPlayer target = targetOpt.get();
        if (mc.thePlayer.getDistanceToEntity(target) < minRange.getInput()) {
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
            double renderX = newVisualPos.xCoord - renderManager.getRenderPosX();
            double renderY = newVisualPos.yCoord - renderManager.getRenderPosY();
            double renderZ = newVisualPos.zCoord - renderManager.getRenderPosZ();

            if (showHitboxButton.isToggled()) {
                SlantRenderUtils.drawBbox3d(new Vec3(renderX, renderY, renderZ), (float) hitboxRed.getInput(), (float) hitboxGreen.getInput(), (float) hitboxBlue.getInput(), (float) hitboxAlpha.getInput(), (float) hitboxFillMult.getInput(), false);
            }
            if (showGhostButton.isToggled()) {
                renderGhost(target, renderX, renderY, renderZ, event.partialTicks);
            }
        } else {
            visualPosition = Optional.empty();
        }
    }

    private void renderGhost(EntityPlayer entity, double x, double y, double z, float partialTicks) {
        RenderManager renderManager = mc.getRenderManager();
        int originalHurtTime = entity.hurtTime;
        entity.hurtTime = 0;
        try {
            GlStateManager.pushMatrix();
            GlStateManager.pushAttrib();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.disableLighting();
            GlStateManager.depthMask(false);
            GlStateManager.enableDepth();
            GlStateManager.enablePolygonOffset();
            GlStateManager.doPolygonOffset(1.0f, -1000000.0f);
            GlStateManager.color((float) ghostRed.getInput(), (float) ghostGreen.getInput(), (float) ghostBlue.getInput(), (float) ghostAlpha.getInput());
            renderManager.renderEntityWithPosYaw(entity, x, y, z, entity.rotationYaw, partialTicks);
            GlStateManager.disablePolygonOffset();
            GlStateManager.enableLighting();
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
            GlStateManager.popAttrib();
            GlStateManager.popMatrix();
        } finally {
            entity.hurtTime = originalHurtTime;
        }
    }

    private double lerp(double start, double end, double percent) {
        return start + (end - start) * percent;
    }
    // endregion

    // region Core Logic
    private double calculateDynamicDelay() {
        if (!truePosition.isPresent()) return 0;
        double distance = mc.thePlayer.getDistance(truePosition.get().xCoord, truePosition.get().yCoord, truePosition.get().zCoord);
        double min = minRange.getInput();
        double max = maxRange.getInput();
        if (max <= min) return 0;
        double progress = (distance - min) / (max - min);
        progress = MathHelper.clamp_double(progress, 0.0, 1.0);
        return maxDynamicDelay.getInput() * progress;
    }

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
            if (!p.isEntityAlive() || mc.thePlayer.getDistanceSqToEntity(p) > maxTargetRange.getInput() * maxTargetRange.getInput()) {
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