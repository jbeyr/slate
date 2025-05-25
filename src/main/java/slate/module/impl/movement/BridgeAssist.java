package slate.module.impl.movement;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovementInput;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import slate.module.Module;
import slate.module.setting.impl.ButtonSetting;
import slate.module.setting.impl.SliderSetting;
import slate.utility.ContainerUtils;
import slate.utility.Utils;
import slate.utility.slate.ActionCoordinator;

public class BridgeAssist extends Module {
    /**
     * Players bridge at a pitch above this value.
     */
    private static final float BRIDGING_PITCH = 68f;
    @Getter @Setter private MovementInput lastMovementInput;

    private final SliderSetting edgeDistance = new SliderSetting("Edge Distance", 0.2f, 0.01f, 0.5f, 0.01f);
    private final ButtonSetting disableIfNotBridgingPitch = new ButtonSetting("Disable if not bridging pitch", false);
    private final ButtonSetting disableIfMovingForwards = new ButtonSetting("Disable if moving forward", true);


    public BridgeAssist() {
        super("Bridge Assist", category.movement);
        this.registerSetting(edgeDistance, disableIfNotBridgingPitch);
    }

    @Override
    public void onDisable() throws Throwable {
        super.onDisable();
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (isEnabled()) {
            if (shouldSwitchBlocksNextFrame) {
                switchToMostBlocksSlot();
                shouldSwitchBlocksNextFrame = false;
            }
            handleBridgeAssist(event.partialTicks);
        }
    }

    private boolean shouldSwitchBlocksNextFrame = false;

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent ev) {
        if (!Utils.nullCheckPasses()) {
            return;
        }
        if (ev.phase == TickEvent.Phase.END) {
            if (mc.currentScreen != null) {
                return;
            }
            final ScaledResolution scaledResolution = new ScaledResolution(mc);
            int blocks = totalBlocks();
            String color = "§";
            if (blocks <= 5) {
                color += "c";
            } else if (blocks <= 15) {
                color += "6";
            } else if (blocks <= 25) {
                color += "e";
            } else {
                color = "";
            }
            mc.fontRendererObj.drawStringWithShadow(color + blocks + " §rblock" + (blocks == 1 ? "" : "s"), (float) scaledResolution.getScaledWidth() / 2 + 8, (float) scaledResolution.getScaledHeight() / 2 + 4, -1);
        }
    }
    public int totalBlocks() {
        if (!Utils.nullCheckPasses()) return 0;

        try {
            int totalBlocks = 0;
            for (int i = 0; i < 9; ++i) {
                final ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
                if (stack != null && stack.getItem() instanceof ItemBlock && ContainerUtils.canBePlaced((ItemBlock) stack.getItem()) && stack.stackSize > 0) {
                    totalBlocks += stack.stackSize;
                }
            }
            return totalBlocks;
        } catch (Throwable e) {
            return 0;
        }
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isEnabled()) return;
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;

        ItemStack heldItem = mc.thePlayer.getHeldItem();
        if (heldItem != null && heldItem.getItem() instanceof ItemBlock) {
            if (heldItem.stackSize <= 1) { // Will be empty after placement
                shouldSwitchBlocksNextFrame = true;
            }
        }
    }

    private void handleBridgeAssist(float partialTicks) {
        if (!ActionCoordinator.isSneakingAllowed()) return;

        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player.rotationPitch < BRIDGING_PITCH) {

            if(isEnabled() && disableIfNotBridgingPitch.isToggled()) toggle();
            return;
        }

        KeyBinding sneakKey = mc.gameSettings.keyBindSneak;
        boolean isSneaking = sneakKey.isKeyDown();

        ItemStack heldItem = player.getHeldItem();
        if (heldItem == null || !(heldItem.getItem() instanceof ItemBlock)) {
            KeyBinding.setKeyBindState(sneakKey.getKeyCode(), false);
            return;
        }

        double playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        float forward = lastMovementInput.moveForward;
        float strafe = lastMovementInput.moveStrafe;

        boolean isStrictlyBackwards = forward < 0 && strafe == 0;

        double xOffset = playerX - Math.floor(playerX);
        double zOffset = playerZ - Math.floor(playerZ);

        float yaw = player.rotationYaw % 360;
        if (yaw < 0) yaw += 360;
        boolean isDiagonalYaw = (Math.abs(yaw - 45) < 15 || Math.abs(yaw - 135) < 15 || Math.abs(yaw - 225) < 15 || Math.abs(yaw - 315) < 15);

        double moveAngle = Math.atan2(-strafe, forward) + Math.toRadians(yaw);
        double xMovement = -Math.sin(moveAngle);
        double zMovement = Math.cos(moveAngle);

        double futureX = playerX + xMovement * edgeDistance.getInput();
        double futureZ = playerZ + zMovement * edgeDistance.getInput();

        BlockPos futurePos = new BlockPos(futureX, playerY - 1, futureZ);
        boolean isAirLayingInWait = player.worldObj.isAirBlock(futurePos);
        boolean isXOriented = (yaw < 45 || yaw >= 315) || (yaw >= 135 && yaw < 225);


        // check if oriented within the central lane
        boolean isInCenterLane;
        double laneWidth = 0.1;

        // use diagonal lines parallel to respective axes
        if (isXOriented) {
            isInCenterLane = Math.abs(xOffset - zOffset) <= laneWidth ||
                    Math.abs(xOffset + zOffset - 1) <= laneWidth;
        } else {
            isInCenterLane = Math.abs(zOffset - xOffset) <= laneWidth ||
                    Math.abs(zOffset + xOffset - 1) <= laneWidth;
        }

        // check if position behind the player is still a solid block
        double behindX = playerX - xMovement * edgeDistance.getInput();
        double behindZ = playerZ - zMovement * edgeDistance.getInput();
        BlockPos behindPos = new BlockPos(behindX, playerY - 1, behindZ);
        boolean behindIsSolid = !player.worldObj.isAirBlock(behindPos);

        if (isStrictlyBackwards && isDiagonalYaw) { // diagonal bridging

            if (isSneaking) {
                if (isInCenterLane && behindIsSolid) { // uncrouch if the player is in the center lane and the block behind is solid
                    KeyBinding.setKeyBindState(sneakKey.getKeyCode(), false);
                }
            } else { // crouch if block behind is not solid
                KeyBinding.setKeyBindState(sneakKey.getKeyCode(), !behindIsSolid);
            }
        } else { // side bridging
            KeyBinding.setKeyBindState(sneakKey.getKeyCode(), isAirLayingInWait);
        }
    }

    private void switchToMostBlocksSlot() {
        ItemStack currentStack = mc.thePlayer.getHeldItem();
        if (currentStack != null && currentStack.stackSize > 0) return; // current slot still has blocks

        int bestSlot = -1;
        int mostBlocks = 0;

        // search hotbar for slot with most blocks
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemBlock) {
                // check if block is solid/full
                Block block = ((ItemBlock) stack.getItem()).getBlock();
                if (block.isFullBlock()) {
                    if (stack.stackSize > mostBlocks) {
                        mostBlocks = stack.stackSize;
                        bestSlot = i;
                    }
                }
            }
        }

        // switch to best slot if found
        if (bestSlot != -1 && ActionCoordinator.isHotbarSelectedSlotChangeAllowed()) {
            mc.thePlayer.inventory.currentItem = bestSlot;
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (isEnabled() && disableIfMovingForwards.isToggled() && mc.gameSettings.keyBindForward.isKeyDown()) toggle();
    }
}
