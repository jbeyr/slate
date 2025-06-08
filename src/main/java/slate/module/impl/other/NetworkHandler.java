// package slate.module.impl.other;
//
// import lombok.Getter;
// import net.minecraft.client.gui.ScaledResolution;
// import net.minecraft.entity.player.EntityPlayer;
// import net.minecraft.util.Vec3;
// import net.minecraftforge.client.event.RenderWorldLastEvent;
// import net.minecraftforge.common.MinecraftForge;
// import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
// import net.minecraftforge.fml.common.gameevent.TickEvent;
// import slate.mixins.impl.render.RenderManagerAccessor;
// import slate.module.Module;
// import slate.module.ModuleManager;
// import slate.module.impl.combat.DynamicBlink;
// import slate.module.setting.impl.ButtonSetting;
// import slate.module.setting.impl.DescriptionSetting;
// import slate.utility.Utils;
// import slate.utility.slate.Interpolate;
// import slate.utility.slate.SlantRenderUtils;
//
// import java.util.Optional;
//
// @Getter
// public class NetworkHandler extends Module {
//
//     private final ButtonSetting showTruePositionsButton = new ButtonSetting("Show true positions", true);
//
//     private Optional<Vec3> visualPosition = Optional.empty();
//
//     public NetworkHandler() {
//         super("Network Handler", category.other);
//         this.registerSetting(new DescriptionSetting("Config for network handling. Always applied."));
//         this.registerSetting(showTruePositionsButton);
//         this.canBeEnabled = false;
//         MinecraftForge.EVENT_BUS.register(this);
//     }
//
//     @SubscribeEvent
//     public void onRenderWorldLast(RenderWorldLastEvent event) {
//         if (mc.theWorld == null || !showTruePositionsButton.isToggled()) {
//             visualPosition = Optional.empty();
//             return;
//         }
//
//         DynamicBlink backtrack = ModuleManager.backtrack;
//         if (backtrack == null) return;
//
//         // render the green "true target position" hitbox
//         renderTrueTargetHitbox(backtrack);
//
//         // render the blue "my server position" hitbox
//         renderServerPositionHitbox(backtrack);
//     }
//
//     private void renderTrueTargetHitbox(DynamicBlink backtrack) {
//         Optional<EntityPlayer> targetOpt = backtrack.getCurrentTarget();
//         if (!targetOpt.isPresent()) {
//             visualPosition = Optional.empty();
//             return;
//         }
//
//         Optional<Vec3> truePosition = backtrack.getTruePosition();
//         if (truePosition.isPresent()) {
//             Vec3 targetPos = truePosition.get();
//             if (!visualPosition.isPresent()) {
//                 visualPosition = Optional.of(targetPos);
//             }
//             Vec3 currentVisualPos = visualPosition.get();
//             double smoothingFactor = 0.15;
//             Vec3 newVisualPos = new Vec3(
//                     lerp(currentVisualPos.xCoord, targetPos.xCoord, smoothingFactor),
//                     lerp(currentVisualPos.yCoord, targetPos.yCoord, smoothingFactor),
//                     lerp(currentVisualPos.zCoord, targetPos.zCoord, smoothingFactor)
//             );
//             visualPosition = Optional.of(newVisualPos);
//
//             RenderManagerAccessor renderManager = (RenderManagerAccessor) mc.getRenderManager();
//             Vec3 renderVec = new Vec3(
//                     newVisualPos.xCoord - renderManager.getRenderPosX(),
//                     newVisualPos.yCoord - renderManager.getRenderPosY(),
//                     newVisualPos.zCoord - renderManager.getRenderPosZ()
//             );
//             // Draw a GREEN box for the target's true position
//             SlantRenderUtils.drawBbox3d(renderVec, 0.0f, 1.0f, 0.0f, 0.7f, 0.25f, false);
//         } else {
//             visualPosition = Optional.empty();
//         }
//     }
//
//     private void renderServerPositionHitbox(DynamicBlink backtrack) {
//         // render this box only when backtrack is actively holding packets
//         if (!backtrack.isActivelyHolding()) {
//             return;
//         }
//
//         Optional<Vec3> serverPosOpt = backtrack.getLastSentPosition();
//         serverPosOpt.ifPresent(serverPos -> {
//             RenderManagerAccessor renderManager = (RenderManagerAccessor) mc.getRenderManager();
//             Vec3 renderVec = new Vec3(
//                     serverPos.xCoord - renderManager.getRenderPosX(),
//                     serverPos.yCoord - renderManager.getRenderPosY(),
//                     serverPos.zCoord - renderManager.getRenderPosZ()
//             );
//             // Draw a BLUE box for our own server-side position
//             SlantRenderUtils.drawBbox3d(renderVec, 0.2f, 0.5f, 1.0f, 0.7f, 0.25f, false);
//         });
//     }
//
//     private double lerp(double start, double end, double percent) {
//         return start + (end - start) * percent;
//     }
//
//     @SubscribeEvent
//     public void onRenderTick(TickEvent.RenderTickEvent ev) {
//         if (!Utils.nullCheckPasses()) return;
//         if (ev.phase == TickEvent.Phase.END) {
//             if (mc.currentScreen != null) return;
//             float partialTicks = ev.renderTickTime;
//
//             DynamicBlink backtrack = ModuleManager.backtrack;
//             Optional<EntityPlayer> targetOpt = backtrack.getCurrentTarget();
//
//             // render distance tag if a target exists
//             targetOpt.ifPresent(target -> renderDistanceTag(target, partialTicks));
//         }
//     }
//
//
//     public void renderDistanceTag(EntityPlayer entity, float partialTicks) {
//         if (!Utils.nullCheckPasses()) return;
//         if (mc.currentScreen != null) return;
//
//         double distanceSq = Interpolate.interpolatedDistanceSqToEntity(mc.thePlayer, entity, partialTicks);
//         double distance = Math.sqrt(distanceSq);
//         String distanceText = String.format("%.2fm", distance); // e.g., "4.21m"
//
//         final ScaledResolution scaledResolution = new ScaledResolution(mc);
//         mc.fontRendererObj.drawStringWithShadow((distance > 4 ? "§c" : "") + distanceText, (float) scaledResolution.getScaledWidth() / 2 + 8, (float) scaledResolution.getScaledHeight() / 2 + 4, -1);
//     }
// }