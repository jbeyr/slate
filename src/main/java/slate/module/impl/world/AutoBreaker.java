package slate.module.impl.world;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import slate.module.Module;
import slate.module.setting.impl.ButtonSetting;
import slate.module.setting.impl.SliderSetting;
import slate.utility.slate.Interpolate;
import slate.utility.slate.MouseManager;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.StreamSupport;

// FIXME bannable lol, this aint gonna work with aim mechanism

public class AutoBreaker extends Module {

    // --- Settings ---
    private final ButtonSetting interactCake = new ButtonSetting("Interact w/ Cake", true);
    private final ButtonSetting interactDragonEgg = new ButtonSetting("Interact w/ Dragon Egg", true);
    private final ButtonSetting breakBed = new ButtonSetting("Break Bed", false);

    private final SliderSetting range = new SliderSetting("Range", 4.5, 1.0, 6.0, 0.1);
    private final ButtonSetting aim = new ButtonSetting("Aim", true);
    private final SliderSetting aimStrength = new SliderSetting("Aim Strength", 0.25, 0.0, 0.5, 0.01, aim::isToggled);
    private final SliderSetting rightClickDelay = new SliderSetting("Right Click Delay", 4, 0, 20, 1);
    private final SliderSetting postBreakDelay = new SliderSetting("Post-Break Delay", 5, 0, 20, 1, breakBed::isToggled);
    private final ButtonSetting swingArm = new ButtonSetting("Swing Arm", true);
    private final ButtonSetting requireEmptyHand = new ButtonSetting("Require Empty Hand (RC)", true);

    // --- State Management ---
    private static final float DEADZONE_ANGLE = 0.5f;
    private enum TargetType { CAKE, DRAGON_EGG, BED }

    @Getter @AllArgsConstructor
    private static class TargetInfo {
        private final BlockPos pos;
        private final TargetType type;
        private final Block blockInstance;
        private final Vec3 lockedAimVector; // Stored aim point to prevent stuttering
        @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; TargetInfo that = (TargetInfo) o; return Objects.equals(pos, that.pos) && type == that.type; }
        @Override public int hashCode() { return Objects.hash(pos, type); }
    }

    private Optional<TargetInfo> currentTarget = Optional.empty();
    private int rightClickTimer = 0;
    private int postBreakCooldown = 0;
    private int dragonEggCooldown = 0; // New cooldown specifically for dragon eggs
    private float accumulatedMouseDX = 0.0f, accumulatedMouseDY = 0.0f;

    public AutoBreaker() {
        super("AutoBreaker", category.world);
        this.registerSetting(interactCake, interactDragonEgg, breakBed, range, aim, aimStrength, rightClickDelay, postBreakDelay, swingArm, requireEmptyHand);
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        if (currentTarget.isPresent() && currentTarget.get().getType() == TargetType.BED) {
            mc.playerController.resetBlockRemoving();
        }
        resetState();
    }

    private void resetState() {
        currentTarget = Optional.empty();
        rightClickTimer = 0;
        postBreakCooldown = 0;
        dragonEggCooldown = 0;
        accumulatedMouseDX = 0.0f;
        accumulatedMouseDY = 0.0f;
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) return;

        // Handle cooldowns first to halt all other logic
        if (postBreakCooldown > 0) {
            postBreakCooldown--;
            return;
        }
        if (dragonEggCooldown > 0) {
            dragonEggCooldown--;
            return;
        }

        if (rightClickTimer > 0) rightClickTimer--;

        Optional<TargetInfo> previousTarget = currentTarget;
        currentTarget = currentTarget.filter(this::isTargetStillValid);
        if (!currentTarget.isPresent()) {
            currentTarget = findNewTarget();
        }

        if (previousTarget.isPresent() && previousTarget.get().getType() == TargetType.BED && !currentTarget.equals(previousTarget)) {
            mc.playerController.resetBlockRemoving();
        }

        currentTarget.ifPresent(target -> {
            if (aim.isToggled()) performAim(target, event.renderTickTime);
            performInteraction(target);
        });

        if (!aim.isToggled() || !currentTarget.isPresent()) {
            accumulatedMouseDX *= 0.85f;
            accumulatedMouseDY *= 0.85f;
        }
    }

    private void performAim(TargetInfo target, float partialTicks) {
        Vec3 aimVec = target.getLockedAimVector(); // Use the pre-calculated, locked aim vector
        MouseManager.Priority priority = (target.getType() == TargetType.BED) ? MouseManager.Priority.LOW : MouseManager.Priority.HIGH;
        Vec3 playerEyePos = Interpolate.interpolatedPosEyes(partialTicks);

        double dx = aimVec.xCoord - playerEyePos.xCoord;
        double dy = aimVec.yCoord - playerEyePos.yCoord;
        double dz = aimVec.zCoord - playerEyePos.zCoord;

        double distHoriz = MathHelper.sqrt_double(dx * dx + dz * dz);
        float targetYaw = (float) (MathHelper.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float targetPitch = (float) -(MathHelper.atan2(dy, distHoriz) * 180.0D / Math.PI);

        float playerYaw = mc.thePlayer.prevRotationYaw + (mc.thePlayer.rotationYaw - mc.thePlayer.prevRotationYaw) * partialTicks;
        float playerPitch = mc.thePlayer.prevRotationPitch + (mc.thePlayer.rotationPitch - mc.thePlayer.prevRotationPitch) * partialTicks;

        float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - playerYaw);
        float pitchDiff = targetPitch - playerPitch;

        if (Math.abs(yawDiff) < DEADZONE_ANGLE && Math.abs(pitchDiff) < DEADZONE_ANGLE) {
            accumulatedMouseDX = 0;
            accumulatedMouseDY = 0;
            return;
        }

        float frameStrength = (float) aimStrength.getInput();
        float yawApply = yawDiff * frameStrength, pitchApply = pitchDiff * frameStrength;
        float sens = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        float factor = sens * sens * sens * 8.0F * 0.15F;

        if (factor > 0.0001f) {
            accumulatedMouseDX += yawApply / factor;
            accumulatedMouseDY += -pitchApply / factor;
        }

        int assistDX = 0, assistDY = 0;
        if (Math.abs(accumulatedMouseDX) >= 0.5f) { assistDX = Math.round(accumulatedMouseDX); accumulatedMouseDX -= assistDX; }
        if (Math.abs(accumulatedMouseDY) >= 0.5f) { assistDY = Math.round(accumulatedMouseDY); accumulatedMouseDY -= assistDY; }

        if (assistDX != 0 || assistDY != 0) MouseManager.offer(assistDX, assistDY, priority);
    }

    private void performInteraction(TargetInfo target) {
        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || !mop.getBlockPos().equals(target.getPos())) return;

        switch (target.getType()) {
            case CAKE:
            case DRAGON_EGG:
                if (rightClickTimer <= 0) {
                    if (requireEmptyHand.isToggled() && mc.thePlayer.getHeldItem() != null) return;
                    mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), target.getPos(), mop.sideHit, mop.hitVec);
                    if (swingArm.isToggled()) mc.thePlayer.swingItem();
                    rightClickTimer = (int) rightClickDelay.getInput();

                    if (target.getType() == TargetType.DRAGON_EGG) {
                        currentTarget = Optional.empty();
                        dragonEggCooldown = 20; // Set 20 tick (1s) cooldown after clicking egg
                    }
                }
                break;
            case BED:
                if (mc.playerController.onPlayerDamageBlock(target.getPos(), mop.sideHit)) {
                    if (swingArm.isToggled()) mc.thePlayer.swingItem();
                    this.postBreakCooldown = (int) postBreakDelay.getInput();
                    this.currentTarget = Optional.empty();
                }
                break;
        }
    }

    private Optional<TargetInfo> findNewTarget() {
        int r = (int) Math.ceil(range.getInput());
        BlockPos p = mc.thePlayer.getPosition();
        return StreamSupport.stream(BlockPos.getAllInBox(p.add(-r,-r,-r), p.add(r,r,r)).spliterator(), false)
                .filter(pos -> mc.thePlayer.getDistanceSq(pos) <= range.getInput() * range.getInput())
                .map(this::getTargetInfoIfValid)
                .filter(Optional::isPresent).map(Optional::get)
                .min(Comparator.comparingDouble(t -> mc.thePlayer.getDistanceSq(t.getPos())));
    }

    private Optional<TargetInfo> getTargetInfoIfValid(BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        TargetType type = null;

        if (interactCake.isToggled() && block == Blocks.cake) type = TargetType.CAKE;
        else if (interactDragonEgg.isToggled() && block == Blocks.dragon_egg) type = TargetType.DRAGON_EGG;
        else if (breakBed.isToggled() && block == Blocks.bed) type = TargetType.BED;

        if (type != null) {
            // Calculate the aim vector ONCE here during target acquisition.
            Optional<Vec3> aimVec = getAimVectorForTarget(pos, block, type);
            if (aimVec.isPresent() && hasLineOfSight(pos, aimVec.get())) {
                return Optional.of(new TargetInfo(pos, type, block, aimVec.get()));
            }
        }
        return Optional.empty();
    }

    private boolean isTargetStillValid(TargetInfo target) {
        return mc.theWorld.getBlockState(target.getPos()).getBlock() == target.getBlockInstance()
                && mc.thePlayer.getDistanceSq(target.getPos()) <= range.getInput() * range.getInput()
                && hasLineOfSight(target.getPos(), target.getLockedAimVector());
    }

    private boolean hasLineOfSight(BlockPos pos, Vec3 aimPoint) {
        MovingObjectPosition result = mc.theWorld.rayTraceBlocks(mc.thePlayer.getPositionEyes(1.0f), aimPoint, false, true, false);
        return result == null || (result.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && result.getBlockPos().equals(pos));
    }

    private Optional<Vec3> getAimVectorForTarget(BlockPos pos, Block block, TargetType type) {
        AxisAlignedBB aabb = block.getCollisionBoundingBox(mc.theWorld, pos, mc.theWorld.getBlockState(pos));
        if (aabb == null) return Optional.empty();

        if (type == TargetType.BED) {
            return Optional.of(getClosestPointOnAABB(mc.thePlayer.getPositionEyes(1.0f), aabb));
        } else {
            return Optional.of(new Vec3(
                    aabb.minX + (aabb.maxX - aabb.minX) / 2.0,
                    aabb.minY + (aabb.maxY - aabb.minY) / 2.0,
                    aabb.minZ + (aabb.maxZ - aabb.minZ) / 2.0
            ));
        }
    }

    private Vec3 getClosestPointOnAABB(Vec3 p, AxisAlignedBB aabb) {
        double x = MathHelper.clamp_double(p.xCoord, aabb.minX, aabb.maxX);
        double y = MathHelper.clamp_double(p.yCoord, aabb.minY, aabb.maxY);
        double z = MathHelper.clamp_double(p.zCoord, aabb.minZ, aabb.maxZ);
        return new Vec3(x, y, z);
    }
}