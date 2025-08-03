package slate.module.impl.combat;

import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;
import slate.event.custom.AutoclickerAttackEvent;
import slate.module.Module;
import slate.module.ModuleManager;
import slate.module.setting.impl.ButtonSetting;
import slate.module.setting.impl.SliderSetting;
import slate.utility.CoolDown;
import slate.utility.Utils;
import slate.utility.slate.ActionCoordinator;

import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

public class BlockHit extends Module {

    private final SliderSetting range = new SliderSetting("Range", 3.5, 1, 6, 0.1);
    private final ButtonSetting onlyPlayers = new ButtonSetting("Only Players", true);
    private final ButtonSetting syncWithAutoclicker = new ButtonSetting("Sync with autoclicker", true);
    private final SliderSetting unblockBufferMs = new SliderSetting("Unblock Buffer (ms)", 15, 1, 50, 1, "How early to unblock before the next hit.", syncWithAutoclicker::isToggled);

    private double rangeSqr;
    private boolean isCurrentlyBlocking;
    private final CoolDown unblockTimer = new CoolDown(0);

    public BlockHit() {
        super("Block Hit", category.combat);
        this.registerSetting(range, onlyPlayers, syncWithAutoclicker, unblockBufferMs);
    }

    @Override
    public void onEnable() {
        isCurrentlyBlocking = false;
        unblockTimer.finish();
    }

    @Override
    public void onDisable() {
        if (isCurrentlyBlocking) {
            stopBlocking();
        }
    }

    @Override
    public void guiUpdate() {
        rangeSqr = Math.pow(range.getInput(), 2);
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent event) {
        if (!isEnabled()) return;

        if (isCurrentlyBlocking && unblockTimer.hasFinished()) {
            stopBlocking();
            return;
        }

        // unblock if autoclicker is disabled
        if (isCurrentlyBlocking) {
            AutoClicker ac = ModuleManager.autoClicker;
            if (ac == null || !ac.isEnabled()) {
                stopBlocking();
            }
        }
    }

    @SubscribeEvent
    public void onAutoclickerAttack(AutoclickerAttackEvent event) {
        if (!this.isEnabled() || !syncWithAutoclicker.isToggled() || !shouldActOnAttack(event.getAttacked())) {
            return;
        }

        startBlocking();
        long clickerDelay = AutoClicker.getRemainingClickDelay();

        if (clickerDelay > 0) {
            long unblockIn = clickerDelay - (long) unblockBufferMs.getInput();
            unblockTimer.setCooldown(Math.max(0, unblockIn));
            unblockTimer.start();
        } else {
            // prevents us from blocking forever (ex: when autoclicker is disabled, it'll return -1 as the delay).
            unblockTimer.setCooldown(50);
            unblockTimer.start();
            Utils.sendMessage("autoclicker returned -1 remaining click delay; defaulting to 50ms block");
        }
    }

    private void startBlocking() {
        if (isCurrentlyBlocking) return;
        isCurrentlyBlocking = true;
        int useItemKey = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(useItemKey, true);
        KeyBinding.onTick(useItemKey);
        Utils.setMouseButtonState(1, true);
    }

    private void stopBlocking() {
        if (!isCurrentlyBlocking) return;
        isCurrentlyBlocking = false;
        unblockTimer.finish(); // Ensure timer is reset
        int useItemKey = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(useItemKey, false);
        Utils.setMouseButtonState(1, false);
    }

    private boolean shouldActOnAttack(Entity target) {
        if (!ActionCoordinator.isSwordBlockAllowed()) return false;
        if (ModuleManager.autoClicker == null || !ModuleManager.autoClicker.isEnabled()) return false;

        ItemStack heldItem = mc.thePlayer.getCurrentEquippedItem();
        if (heldItem == null || !(heldItem.getItem() instanceof ItemSword)) return false;

        if (onlyPlayers.isToggled() && !(target instanceof EntityPlayer)) return false;
        if (mc.thePlayer.getDistanceSqToEntity(target) > rangeSqr) return false;

        return true;
    }
}