package slate.module.impl.combat;

import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import slate.module.Module;
import slate.module.setting.impl.ButtonSetting;
import slate.module.setting.impl.SliderSetting;
import slate.utility.CoolDown;
import slate.utility.Utils;
import slate.utility.slate.ActionCoordinator;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

public class BlockHit extends Module {

    private final SliderSetting range = new SliderSetting("Range", 3.5, 1, 6, 0.1);
    private final SliderSetting chance = new SliderSetting("Chance", 1, 0.01, 1, 0.01);
    private final ButtonSetting onlyPlayers = new ButtonSetting("Only Players", true);
    private final ButtonSetting onlyForward = new ButtonSetting("Only Forward", false);

    private final SliderSetting delayMinMs = new SliderSetting("Delay Min (ms)", 20, 0, 250, 1);
    private final SliderSetting delayMaxMs = new SliderSetting("Delay Max (ms)", 40, 0, 250, 1);
    private final SliderSetting blockDurationMinMs = new SliderSetting("Block Duration Min (ms)", 60, 1, 500, 1);
    private final SliderSetting blockDurationMaxMs = new SliderSetting("Block Duration Max (ms)", 120, 1, 500, 1);

    private final SliderSetting hitPerMin = new SliderSetting("Hit Per Min", 1, 1, 10, 1);
    private final SliderSetting hitPerMax = new SliderSetting("Hit Per Max", 1, 1, 10, 1);

    private double rangeSqr;
    private boolean isBlockScheduled;
    private boolean isCurrentlyBlocking;

    private int attackCounter;
    private int nextBlockAttackCount;

    private final CoolDown delayTimer = new CoolDown(0);
    private final CoolDown blockDurationTimer = new CoolDown(0);

    public BlockHit() {
        super("Block Hit", category.combat);
        this.registerSetting(
                range, chance, onlyPlayers, onlyForward,
                delayMinMs, delayMaxMs, blockDurationMinMs, blockDurationMaxMs,
                hitPerMin, hitPerMax
        );
    }

    @Override
    public void onEnable() {
        resetState();
        generateNextBlockAttackTarget();
    }

    @Override
    public void onDisable() {
        if (isCurrentlyBlocking) {
            stopBlocking();
        }
        resetState();
    }

    @Override
    public void guiUpdate() {
        // ensure min <= max for sliders
        Utils.correctValue(delayMinMs, delayMaxMs);
        Utils.correctValue(blockDurationMinMs, blockDurationMaxMs);
        Utils.correctValue(hitPerMin, hitPerMax);

        rangeSqr = Math.pow(range.getInput(), 2);
    }

    private void resetState() {
        isBlockScheduled = false;
        isCurrentlyBlocking = false;
        attackCounter = 0;
        delayTimer.finish();
        blockDurationTimer.finish();
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent event) {
        if (mc.thePlayer == null || !isEnabled()) return;

        // this render-loop timer check drives our state machine
        if (isBlockScheduled && delayTimer.hasFinished()) {
            startBlocking();
        }

        if (isCurrentlyBlocking && blockDurationTimer.hasFinished()) {
            stopBlocking();
        }
    }

    @SubscribeEvent
    public void onPlayerAttack(AttackEntityEvent event) {
        if (!shouldActOnAttack(event.target)) {
            return;
        }

        // if we pass all checks, count this as a valid hit
        attackCounter++;
        if (attackCounter >= nextBlockAttackCount) {
            scheduleBlock();
            generateNextBlockAttackTarget(); // reset for the next sequence
        }
    }

    private void scheduleBlock() {
        if (isBlockScheduled || isCurrentlyBlocking) return;

        isBlockScheduled = true;
        long delay = (long) ThreadLocalRandom.current().nextDouble(delayMinMs.getInput(), delayMaxMs.getInput() + 0.01);
        delayTimer.setCooldown(delay);
        delayTimer.start();
    }

    private void startBlocking() {
        isBlockScheduled = false;

        if (onlyForward.isToggled() && !Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode())) return;

        isCurrentlyBlocking = true;

        int useItemKey = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(useItemKey, true);
        KeyBinding.onTick(useItemKey);
        Utils.setMouseButtonState(1, true); // backup for some servers

        long duration = (long) ThreadLocalRandom.current().nextDouble(blockDurationMinMs.getInput(), blockDurationMaxMs.getInput() + 0.01);
        blockDurationTimer.setCooldown(duration);
        blockDurationTimer.start();
    }

    private void stopBlocking() {
        if (!isCurrentlyBlocking) return;
        isCurrentlyBlocking = false;

        int useItemKey = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(useItemKey, false);
        Utils.setMouseButtonState(1, false);
    }

    private boolean shouldActOnAttack(Entity target) {
        if (mc.thePlayer == null || !isEnabled() || !mc.thePlayer.isEntityAlive() || mc.currentScreen != null) {
            return false;
        }
        if (!ActionCoordinator.isActingOnPlayerBehalfAllowed()) {
            return false;
        }

        ItemStack heldItem = mc.thePlayer.getCurrentEquippedItem();
        if (heldItem == null || !(heldItem.getItem() instanceof ItemSword)) {
            return false;
        }
        if (onlyPlayers.isToggled() && !(target instanceof EntityPlayer)) {
            return false;
        }
        if (mc.thePlayer.getDistanceSqToEntity(target) > rangeSqr) {
            return false;
        }
        if (Math.random() > chance.getInput()) {
            return false;
        }
        // all checks passed
        return true;
    }

    private void generateNextBlockAttackTarget() {
        attackCounter = 0;
        int min = (int) hitPerMin.getInput();
        int max = (int) hitPerMax.getInput();

        // ensure min is not greater than max before generating random
        if (min > max) {
            nextBlockAttackCount = min;
            return;
        }
        nextBlockAttackCount = ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}