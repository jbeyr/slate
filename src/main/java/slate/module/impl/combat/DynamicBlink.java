// package slate.module.impl.combat;
//
// import lombok.Getter;
// import net.minecraft.entity.Entity;
// import net.minecraft.entity.player.EntityPlayer;
// import net.minecraft.network.Packet;
// import net.minecraft.network.play.client.C03PacketPlayer;
// import net.minecraft.network.play.server.S12PacketEntityVelocity;
// import net.minecraft.network.play.server.S14PacketEntity;
// import net.minecraft.network.play.server.S18PacketEntityTeleport;
// import net.minecraft.util.Vec3;
// import net.minecraftforge.event.entity.player.AttackEntityEvent;
// import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
// import net.minecraftforge.fml.common.gameevent.TickEvent;
// import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;
// import slate.event.custom.PacketEvent;
// import slate.module.Module;
// import slate.module.setting.impl.ButtonSetting;
// import slate.module.setting.impl.SliderSetting;
// import slate.utility.Utils;
// import slate.utility.slate.manager.PacketInterceptionManager;
//
// import java.util.Optional;
// import java.util.Queue;
// import java.util.concurrent.ConcurrentLinkedQueue;
//
// @Getter
// public class DynamicBlink extends Module {
//
//     // region Settings
//     private final SliderSetting minRange = new SliderSetting("Min Range", 3.0, 0.1, 6.0, 0.1);
//     private final SliderSetting maxRange = new SliderSetting("Max Range", 4.5, 3.0, 6.0, 0.1);
//     private final SliderSetting maxDelay = new SliderSetting("Max Delay (ms)", 300, 50, 1000, 10);
//     private final ButtonSetting disableOnHit = new ButtonSetting("Disable on Hit", true);
//     // endregion
//
//     // region State
//     private final Queue<Packet<?>> heldPackets = new ConcurrentLinkedQueue<>();
//     @Getter private Optional<Vec3> lastSentPosition = Optional.empty(); // added @Getter
//     private boolean isActivelyHolding = false;
//     private long holdStartTime = 0;
//
//     private Optional<EntityPlayer> currentTarget = Optional.empty();
//     private Optional<Vec3> truePosition = Optional.empty();
//     private Optional<Vec3> lastTruePosition = Optional.empty();
//     // endregion
//
//     public DynamicBlink() {
//         super("Backtrack", category.combat);
//         this.registerSetting(minRange, maxRange, maxDelay, disableOnHit);
//     }
//
//     @Override
//     public void onDisable() throws Throwable {
//         super.onDisable();
//         flushHeldPackets();
//         setTarget(null);
//     }
//
//     @Override
//     public void guiUpdate() throws Throwable {
//         super.guiUpdate();
//         Utils.correctValue(minRange, maxRange);
//     }
//
//     // region Event Handling
//     @SubscribeEvent
//     public void onAttack(AttackEntityEvent event) {
//         if (event.entityPlayer == mc.thePlayer && event.target instanceof EntityPlayer) {
//             setTarget((EntityPlayer) event.target);
//         }
//     }
//
//     @SubscribeEvent
//     public void onClientTick(TickEvent.ClientTickEvent event) {
//         if (event.phase != TickEvent.Phase.END || !Utils.nullCheckPasses()) return;
//
//         Utils.correctValue(minRange, maxRange);
//         validateCurrentTarget();
//
//         // safety valve: if we hold packets for too long, flush them to prevent disconnect
//         if (isActivelyHolding && System.currentTimeMillis() - holdStartTime > maxDelay.getInput()) {
//             flushHeldPackets();
//         }
//     }
//
//     @SubscribeEvent
//     public void onPacketSend(PacketEvent.Send event) {
//         if (!this.isEnabled() || !(mc.getNetHandler() instanceof net.minecraft.client.network.NetHandlerPlayClient)) {
//             if (!heldPackets.isEmpty()) flushHeldPackets();
//             return;
//         }
//         if (event.getPacket() instanceof C03PacketPlayer) {
//             // we manually handle all C03 packets to decide whether to send or hold
//             handlePlayerPacket((C03PacketPlayer) event.getPacket());
//             event.setCanceled(true);
//         }
//     }
//
//     @SubscribeEvent
//     public void onPacketReceive(PacketEvent.Receive event) {
//         if (!(mc.getNetHandler() instanceof net.minecraft.client.network.NetHandlerPlayClient)) return;
//
//         updateTruePositionFromPacket(event.getPacket());
//
//         if (isActivelyHolding && disableOnHit.isToggled() && event.getPacket() instanceof S12PacketEntityVelocity) {
//             if (((S12PacketEntityVelocity) event.getPacket()).getEntityID() == mc.thePlayer.getEntityId()) {
//                 flushHeldPackets();
//             }
//         }
//     }
//
//     @SubscribeEvent
//     public void onDisconnect(ClientDisconnectionFromServerEvent event) {
//         flushHeldPackets();
//         setTarget(null);
//     }
//     // endregion
//
//     // region Internal Logic
//     /**
//      * The new core logic. It decides to hold a packet only when moving would
//      * place the player outside of the maximum reach from the target.
//      * @param packet The C03PacketPlayer to evaluate.
//      */
//     private void handlePlayerPacket(C03PacketPlayer packet) {
//         Optional<Vec3> targetPos = this.truePosition;
//         Vec3 currentPos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
//
//         // initialize our server-side position if we don't have one
//         if (!lastSentPosition.isPresent()) {
//             lastSentPosition = Optional.of(currentPos);
//         }
//
//         // if we have no target, just send everything and stay in sync
//         if (!targetPos.isPresent()) {
//             flushAndSend(packet);
//             return;
//         }
//
//         Vec3 packetPos = new Vec3(packet.getPositionX(), packet.getPositionY(), packet.getPositionZ());
//         Vec3 nextPlayerPos = packet.isMoving() ? packetPos : currentPos;
//         double nextDist = nextPlayerPos.distanceTo(targetPos.get());
//
//         // if moving is safe (we'll still be in range) OR we are currently too close, send packets
//         if (nextDist < maxRange.getInput() || lastSentPosition.get().distanceTo(targetPos.get()) < minRange.getInput()) {
//             flushAndSend(packet);
//         } else {
//             // otherwise, moving would take us out of reach. hold the packet.
//             holdPacket(packet);
//         }
//     }
//
//     private void flushAndSend(C03PacketPlayer packet) {
//         flushHeldPackets();
//         sendPacketAndUpdate(packet);
//     }
//
//     private void holdPacket(Packet<?> packet) {
//         if (!isActivelyHolding) {
//             isActivelyHolding = true;
//             holdStartTime = System.currentTimeMillis();
//         }
//         heldPackets.add(packet);
//     }
//
//     private void flushHeldPackets() {
//         if (heldPackets.isEmpty()) {
//             // ensure the flag is off even if the queue is empty
//             if (isActivelyHolding) isActivelyHolding = false;
//             return;
//         }
//
//         isActivelyHolding = false;
//         Packet<?> p;
//         while ((p = heldPackets.poll()) != null) {
//             sendPacketAndUpdate((C03PacketPlayer) p);
//         }
//         holdStartTime = 0;
//     }
//
//     private void sendPacketAndUpdate(C03PacketPlayer packet) {
//         PacketInterceptionManager.sendPacketFromManager(packet);
//         if (packet.isMoving()) {
//             this.lastSentPosition = Optional.of(new Vec3(packet.getPositionX(), packet.getPositionY(), packet.getPositionZ()));
//         }
//     }
//
//     private void setTarget(EntityPlayer newTarget) {
//         Optional<EntityPlayer> newTargetOpt = Optional.ofNullable(newTarget);
//         int currentId = currentTarget.map(Entity::getEntityId).orElse(-1);
//         int newId = newTargetOpt.map(Entity::getEntityId).orElse(-2);
//         if (newId != currentId) {
//             this.currentTarget = newTargetOpt;
//             this.truePosition = this.currentTarget.map(Entity::getPositionVector);
//             this.lastTruePosition = this.truePosition;
//             if (mc.thePlayer != null) {
//                 this.lastSentPosition = Optional.of(new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ));
//             }
//         }
//     }
//
//     private void validateCurrentTarget() {
//         this.currentTarget.ifPresent(p -> {
//             if (!p.isEntityAlive() || mc.thePlayer.getDistanceSqToEntity(p) > 20 * 20) {
//                 setTarget(null);
//             }
//         });
//     }
//
//     private void updateTruePositionFromPacket(Packet<?> packet) {
//         if (!currentTarget.isPresent() || mc.theWorld == null) return;
//         EntityPlayer target = currentTarget.get();
//         if (packet instanceof S18PacketEntityTeleport) {
//             S18PacketEntityTeleport p = (S18PacketEntityTeleport) packet;
//             if (p.getEntityId() == target.getEntityId()) setNewTruePosition(new Vec3(p.getX() / 32.0D, p.getY() / 32.0D, p.getZ() / 32.0D));
//         }
//         if (packet instanceof S14PacketEntity) {
//             S14PacketEntity p = (S14PacketEntity) packet;
//             Entity entity = p.getEntity(mc.theWorld);
//             if (entity != null && entity.getEntityId() == target.getEntityId() && truePosition.isPresent()) {
//                 setNewTruePosition(truePosition.get().addVector(p.func_149062_c() / 32.0D, p.func_149061_d() / 32.0D, p.func_149064_e() / 32.0D));
//             }
//         }
//     }
//
//     private void setNewTruePosition(Vec3 newPos) {
//         this.lastTruePosition = this.truePosition;
//         this.truePosition = Optional.of(newPos);
//     }
//     // endregion
// }