package slate.module.impl.combat;

import lombok.Getter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import slate.module.Module;
import slate.module.ModuleManager;
import slate.module.impl.world.targeting.TargetManager;
import slate.module.setting.impl.ButtonSetting;
import slate.module.setting.impl.SliderSetting;
import slate.utility.Utils;
import slate.utility.slate.PacketManager;
import slate.utility.slate.SlantRenderUtils;

import java.util.Optional;

public class Backtrack extends Module {

    private static final double CLEANUP_DIST_SQ = 36d;

    @Getter private boolean shouldSpoof = false;
    @Getter private float delayMs;
    @Getter private float minRangeSq;
    @Getter private float maxRangeSq;

    private final SliderSetting delayMsSlider = new SliderSetting("Delay (ms)", 1, 1, 100, 1);
    private final SliderSetting minRange = new SliderSetting("Min Range", 3.5, 0, 4, 0.02);
    private final SliderSetting maxRange = new SliderSetting("Max Range", 8, 0, 8, 0.02);
    private final ButtonSetting startWhenAttacked = new ButtonSetting("Start if target hits first", false);
    private final ButtonSetting renderOriginalPosition = new ButtonSetting("Render Original Position", true);
    private final ButtonSetting smartDisable = new ButtonSetting("Smart Disable", true, "Disables backtrack if the target is moving toward you so that if they get a combo they aren't out of range as a result of the backtrack lag");
    private final SliderSetting rushThreshold = new SliderSetting("Rush Threshold", -0.7, -1, 0, 0.01, "How directly the target must rush you to disable backtrack (dot product of current and past velocity)", smartDisable::isToggled);

    public Backtrack() {
        super("Backtrack", category.combat);
        this.registerSetting(delayMsSlider, minRange, maxRange, startWhenAttacked, renderOriginalPosition, smartDisable, rushThreshold);
    }

    @SubscribeEvent
    public void onCombat(AttackEntityEvent e) {
        boolean imAttacked = e.target == mc.thePlayer;
        boolean imAttacking = e.entity == mc.thePlayer;
        if(!imAttacked && !imAttacking) return;

        TargetManager tm = ModuleManager.targetManager;
        Entity other = e.target == mc.thePlayer ? e.entity : e.target;

        if(!(other instanceof EntityPlayer)) return;
        if(!tm.isRecommendedTarget(other)) return;

        if (imAttacked && startWhenAttacked.isToggled()) {
            PacketManager.setTarget(Optional.of((EntityPlayer) other));
        }

        if (imAttacking) {
            PacketManager.setTarget(Optional.of((EntityPlayer) other));
        }
    }

    @Override
    public void guiUpdate() throws Throwable {
        super.guiUpdate();

        Utils.correctValue(minRange, maxRange);
        delayMs = (float) delayMsSlider.getInput();
        minRangeSq = (float) Math.pow(minRange.getInput(), 2);
        maxRangeSq = (float) Math.pow(maxRange.getInput(), 2);
    }

    public void tickCheck() {
        EntityPlayer target = PacketManager.getTarget();

        if (target == null) {
            shouldSpoof = false;
            return;
        }

        double dSq = mc.thePlayer.getDistanceSqToEntity(target);

        // remove backtrack target if unreasonably far
        if (dSq > CLEANUP_DIST_SQ) {
            PacketManager.setTarget(Optional.empty());
            shouldSpoof = false;
            return;
        }

        if (dSq < minRangeSq || dSq > maxRangeSq) {
            shouldSpoof = false;
            PacketManager.forceFlushInboundQueue();
            return;
        }

        // if target is moving to us, backtracking would make them appear further away
        // assuming they hit us, we won't be able to hit back, so don't backtrack here
        if (smartDisable.isToggled()) {
            if (getDotProductBetwMyLookVecAndTargetDirection(target) < rushThreshold.getInput()) {
                shouldSpoof = false;
                PacketManager.forceFlushInboundQueue();
                return;
            }
        }

        // passed all checks; desirable to backtrack
        shouldSpoof = true;
    }

    private static double getDotProductBetwMyLookVecAndTargetDirection(EntityPlayer target) {
        Vec3 myLookVec = mc.thePlayer.getLookVec();
        Vec3 targetVelocityVec = new Vec3(
                target.posX - target.prevPosX,
                0,
                target.posZ - target.prevPosZ
        );

        Vec3 targetDirectionVec = targetVelocityVec.normalize();

        // if approaching -1 => target is moving directly to us
        // if approaching 0 => target is strafing perpendicular to us
        // if approaching 1 => target is moving away from us
        return myLookVec.dotProduct(targetDirectionVec);
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        // This guard clause is now the *only* thing that matters for rendering.
        // If the module is on, the setting is on, and we are actively spoofing, we proceed.
        if (!renderOriginalPosition.isToggled() || !isEnabled() || !shouldSpoof) {
            return;
        }

        // We still need a target to get position data from.
        // This check is mostly redundant because shouldSpoof would be false without a target,
        // but it's good practice for preventing NullPointerExceptions.
        EntityPlayer target = PacketManager.getTarget();
        if (target == null) {
            return;
        }

        // Get the interpolated "real" position from the packet history.
        Vec3 interpolatedPos = PacketManager.getInterpolatedTargetPos(event.partialTicks);

        // This should rarely, if ever, be null when shouldSpoof is true, but it's a safe check.
        if (interpolatedPos == null) {
            return;
        }

        // Always render the box in a single, consistent color (white).
        SlantRenderUtils.drawBboxAtWorldPos(
                interpolatedPos,
                1.0f, 1.0f, 1.0f, // Static white color
                0.6f,             // Overall opacity (for the outline)
                0.25f,            // Opacity multiplier for the filled part
                false             // Don't respect depth
        );
    }
}