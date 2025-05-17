package slate.module.impl.combat;

import slate.module.Module;
import slate.module.ModuleManager;
import slate.module.setting.impl.ButtonSetting;
import slate.module.setting.impl.ModeSetting;
import slate.module.setting.impl.SliderSetting;
import slate.utility.CoolDown;
import slate.utility.Utils;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.concurrent.ThreadLocalRandom;

public class BlockHit extends Module {
    public static SliderSetting range, chance, waitMsMin, waitMsMax, hitPerMin, hitPerMax, postDelayMin, postDelayMax;
    public static ModeSetting eventType;
    public static ButtonSetting onlyPlayers, onRightMBHold, onlyWhenMovingForward;
    public static boolean executingAction, hitCoolDown, alreadyHit, safeGuard;
    public static int hitTimeout, hitsWaited;
    private final String[] MODES = new String[]{"PRE", "POST"};
    private final CoolDown actionTimer = new CoolDown(0);
    private final CoolDown postDelayTimer = new CoolDown(0);
    private boolean waitingForPostDelay;

    public BlockHit() {
        super("BlockHit", category.combat, "Automatically blockHit");
        this.registerSetting(onlyPlayers = new ButtonSetting("Only players", true));
        this.registerSetting(onlyWhenMovingForward = new ButtonSetting("When moving forward", false));
        this.registerSetting(onRightMBHold = new ButtonSetting("When holding down rmb", false));
        // range => 3f
        // chanec => 1f
        // waitmsmin, max => 37f, 59f
        // actionmsmin, max => 12f, 41f
        // hitpermin, max => 1, 1
        this.registerSetting(waitMsMin = new SliderSetting("Action Time Min (MS)", 12, 1, 500, 1));
        this.registerSetting(waitMsMax = new SliderSetting("Action Time Max (MS)", 41, 1, 500, 1));
        this.registerSetting(hitPerMin = new SliderSetting("Once every Min hits", 1, 1, 10, 1));
        this.registerSetting(hitPerMax = new SliderSetting("Once every Max hits", 1, 1, 10, 1));
        this.registerSetting(postDelayMin = new SliderSetting("Post Delay Min (MS)", 37, 0, 500, 1));
        this.registerSetting(postDelayMax = new SliderSetting("Post Delay Max (MS)", 59, 0, 500, 1));
        this.registerSetting(chance = new SliderSetting("Chance %", 100, 0, 100, 1));
        this.registerSetting(range = new SliderSetting("Range: ", 3, 1, 6, 0.05));
        this.registerSetting(eventType = new ModeSetting("Value: ", MODES, 1));
    }

    private static void finishCombo() {
        int key = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(key, false);
        Utils.setMouseButtonState(1, false);
    }

    private static void startCombo() {
        if (!onlyWhenMovingForward.isToggled() || Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode())) {
            int key = mc.gameSettings.keyBindUseItem.getKeyCode();
            KeyBinding.setKeyBindState(key, true);
            KeyBinding.onTick(key);
            Utils.setMouseButtonState(1, true);
        }
    }

    public void guiUpdate() {
        Utils.correctValue(waitMsMin, waitMsMax);
        Utils.correctValue(hitPerMin, hitPerMax);
        Utils.correctValue(postDelayMin, postDelayMax);
    }

    @SubscribeEvent
    public void onTick(TickEvent.RenderTickEvent e) {
        if (!Utils.nullCheck())
            return;

        if (onRightMBHold.isToggled() && !Utils.tryingToCombo()) {
            if (!safeGuard || Utils.holdingWeapon() && Mouse.isButtonDown(0)) {
                safeGuard = true;
                finishCombo();
            }
            return;
        }
        if (waitingForPostDelay) {
            if (postDelayTimer.hasFinished()) {
                executingAction = true;
                startCombo();
                waitingForPostDelay = false;
                if (safeGuard) safeGuard = false;
                actionTimer.start();
            }
            return;
        }

        if (executingAction) {
            if (actionTimer.hasFinished()) {
                executingAction = false;
                finishCombo();
                return;
            } else {
                return;
            }
        }

        if (onRightMBHold.isToggled() && Utils.tryingToCombo()) {
            if (mc.objectMouseOver == null || mc.objectMouseOver.entityHit == null) {
                if (!safeGuard || Utils.holdingWeapon() && Mouse.isButtonDown(0)) {
                    safeGuard = true;
                    finishCombo();
                }
                return;
            } else {
                Entity target = mc.objectMouseOver.entityHit;
                if (target.isDead) {
                    if (!safeGuard || Utils.holdingWeapon() && Mouse.isButtonDown(0)) {
                        safeGuard = true;
                        finishCombo();
                    }
                    return;
                }
            }
        }

        if (mc.objectMouseOver != null && mc.objectMouseOver.entityHit instanceof Entity && Mouse.isButtonDown(0)) {
            Entity target = mc.objectMouseOver.entityHit;
            if (target.isDead) {
                if (onRightMBHold.isToggled() && Mouse.isButtonDown(1) && Mouse.isButtonDown(0)) {
                    if (!safeGuard || Utils.holdingWeapon() && Mouse.isButtonDown(0)) {
                        safeGuard = true;
                        finishCombo();
                    }
                }
                return;
            }

            if (mc.thePlayer.getDistanceToEntity(target) <= range.getInput()) {
                if ((target.hurtResistantTime >= 10 && MODES[(int) eventType.getInput()] == MODES[1]) || (target.hurtResistantTime <= 10 && MODES[(int) eventType.getInput()] == MODES[0])) {

                    if (onlyPlayers.isToggled()) {
                        if (!(target instanceof EntityPlayer)) {
                            return;
                        }
                    }

                    if (!ModuleManager.targetManager.isRecommendedTarget(target)) {
                        return;
                    }


                    if (hitCoolDown && !alreadyHit) {
                        hitsWaited++;
                        if (hitsWaited >= hitTimeout) {
                            hitCoolDown = false;
                            hitsWaited = 0;
                        } else {
                            alreadyHit = true;
                            return;
                        }
                    }

                    if (!(chance.getInput() == 100 || Math.random() <= chance.getInput() / 100))
                        return;

                    if (!alreadyHit) {
                        guiUpdate();
                        if (hitPerMin.getInput() == hitPerMax.getInput()) {
                            hitTimeout = (int) hitPerMin.getInput();
                        } else {

                            hitTimeout = ThreadLocalRandom.current().nextInt((int) hitPerMin.getInput(), (int) hitPerMax.getInput());
                        }
                        hitCoolDown = true;
                        hitsWaited = 0;

                        actionTimer.setCooldown((long) ThreadLocalRandom.current().nextDouble(waitMsMin.getInput(), waitMsMax.getInput() + 0.01));
                        if (postDelayMax.getInput() != 0) {
                            postDelayTimer.setCooldown((long) ThreadLocalRandom.current().nextDouble(postDelayMin.getInput(), postDelayMax.getInput() + 0.01));
                            postDelayTimer.start();
                            waitingForPostDelay = true;
                        } else {
                            executingAction = true;
                            startCombo();
                            actionTimer.start();
                            alreadyHit = true;
                            if (safeGuard) safeGuard = false;
                        }
                        alreadyHit = true;
                    }
                } else {
                    if (alreadyHit) {
                        alreadyHit = false;
                    }

                    if (safeGuard) safeGuard = false;
                }
            }
        }
    }
}