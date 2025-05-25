package slate.module.impl.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
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
import java.util.concurrent.atomic.AtomicBoolean;

public class BlockHit extends Module {

    private final SliderSetting range = new SliderSetting("Range", 3, 1, 6, 0.02);
    private final SliderSetting chance = new SliderSetting("Chance", 1, 0.01, 1, 0.01);
    private final ButtonSetting onlyPlayers = new ButtonSetting("Only players", true);
    private final ButtonSetting onlyForward = new ButtonSetting("Only forward", true);

    private final SliderSetting waitMsMin = new SliderSetting("Wait Min (ms)", 37, 0, 100, 1);
    private final SliderSetting waitMsMax = new SliderSetting("Wait Max (ms)", 59, 0, 100, 1);

    private final SliderSetting actionMsMin = new SliderSetting("Action Min (ms)", 12, 1, 300, 1);
    private final SliderSetting actionMsMax = new SliderSetting("Action Max (ms)", 41, 1, 300, 1);

    private final SliderSetting hitPerMin = new SliderSetting("Hit Per Min", 1, 1, 10, 1);
    private final SliderSetting hitPerMax = new SliderSetting("Hit Per Max", 1, 1, 10, 1);

    private double rangeSqr;

    private boolean executingAction;
    private int hits, rHit;
    private final AtomicBoolean eventProcessing = new AtomicBoolean(false);
    private boolean tryStartCombo;
    private final CoolDown actionTimer = new CoolDown(0);
    private final CoolDown waitTimer = new CoolDown(0);

    public BlockHit() {
        super("Block Hit", category.combat);
        this.registerSetting(range, chance, onlyPlayers, onlyForward, waitMsMin, waitMsMax, actionMsMin, actionMsMax, hitPerMin, hitPerMax);
        rangeSqr = range.getInput() * range.getInput();
        generateNewHitTarget();
    }

    @Override
    public void onEnable() {
        executingAction = false;
        hits = 0;
        tryStartCombo = false;
        generateNewHitTarget();
    }

    @Override
    public void onDisable() {
        if (executingAction) {
            finishCombo();
        }
    }

    public void guiUpdate() {
        Utils.correctValue(waitMsMin, waitMsMax);
        Utils.correctValue(actionMsMin, actionMsMax);
        Utils.correctValue(hitPerMin, hitPerMax);

        rangeSqr = Math.pow(range.getInput(), 2);
    }

    private void generateNewHitTarget() {
        int min = (int) hitPerMin.getInput();
        int max = (int) hitPerMax.getInput();
        int range = max - min + 1;
        rHit = ThreadLocalRandom.current().nextInt(range) + min;
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        if (!isEnabled()) return;
        if (!ActionCoordinator.isActingOnPlayerBehalfAllowed()) return;

        if (tryStartCombo && waitTimer.hasFinished()) {
            tryStartCombo = false;
            startCombo();
        }

        if (actionTimer.hasFinished() && executingAction) {
            finishCombo();
        }
    }

    @SubscribeEvent
    public void onHit(AttackEntityEvent fe) {
        if (!isEnabled()) return;
        if (!ActionCoordinator.isActingOnPlayerBehalfAllowed()) return;

        // use atomic boolean to prevent duplicate processing
        if (!eventProcessing.compareAndSet(false, true)) {
            eventProcessing.set(false);
            return;
        }

        try {
            Minecraft mc = Minecraft.getMinecraft();
            EntityPlayer me = mc.thePlayer;
            hits++;

            if (hits > rHit) {
                hits = 1;
                generateNewHitTarget();
            }

            ItemStack heldItem = me.getCurrentEquippedItem();

            if ((!(fe.target instanceof EntityPlayer) && onlyPlayers.isToggled())
                    || !(Math.random() <= chance.getInput())
                    || !(heldItem != null && heldItem.getItem() instanceof ItemSword)
                    || mc.thePlayer.getDistanceSqToEntity(fe.target) > rangeSqr
                    || !(rHit == hits))
                return;

            tryStartCombo();
        } finally {
            eventProcessing.set(false);
        }
    }

    private void finishCombo() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!executingAction) return;

        executingAction = false;
        int key = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(key, false);
        Utils.setMouseButtonState(1, false);
    }

    private void startCombo() {
        Minecraft mc = Minecraft.getMinecraft();
        if (onlyForward.isToggled() && !Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode())) return;

        executingAction = true;
        int useItemKey = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(useItemKey, true);
        KeyBinding.onTick(useItemKey);
        Utils.setMouseButtonState(1, true);

        // Set action timer with random duration
        long actionDuration = (long) ThreadLocalRandom.current().nextDouble(waitMsMin.getInput(), waitMsMax.getInput() + 0.01);
        actionTimer.setCooldown(actionDuration);
        actionTimer.start();
    }

    public void tryStartCombo() {
        tryStartCombo = true;
        long waitDuration = (long) ThreadLocalRandom.current().nextDouble(actionMsMin.getInput(), actionMsMax.getInput() + 0.01);
        waitTimer.setCooldown(waitDuration);
        waitTimer.start();
    }
}