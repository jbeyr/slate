package slate.module.impl.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.commons.lang3.RandomUtils;
import org.lwjgl.input.Keyboard;
import slate.event.custom.PacketEvent;
import slate.module.Module;
import slate.module.setting.impl.DescriptionSetting;
import slate.module.setting.impl.SliderSetting;
import slate.utility.Utils;

import java.util.Random;

// public class JumpReset extends Module {
//
//     private final SliderSetting chance = new SliderSetting("Chance", 1, 0, 1, 0.01);
//     private final Random rng = new Random();
//
//     public JumpReset() {
//         super("Jump Reset", category.movement);
//     }
//
//     public boolean shouldActivate() {
//         return rng.nextDouble() < chance.getInput();
//     }
//
//
//     public void legitJump() {
//         int key = Minecraft.getMinecraft().gameSettings.keyBindJump.getKeyCode();
//         KeyBinding.setKeyBindState(key, true);
//         KeyBinding.onTick(key);
//     }
// }


public class JumpReset extends Module {

    public static SliderSetting delayMin = new SliderSetting("Delay Min (ms)", 10.0D, 0.0D, 40.0D, 1.0D);
    public static SliderSetting delayMax = new SliderSetting("Delay Max (ms)", 20.0D, 0.0D, 40.0D, 1.0D);
    public static SliderSetting chance = new SliderSetting("Chance", 100.0D, 0.0D, 100.0D, 1.0D);

    public JumpReset() {
        super("Jump Reset", category.movement);
        this.registerSetting(delayMin, delayMax, chance);
    }

    @Override
    public void guiUpdate() throws Throwable {
        super.guiUpdate();
        Utils.correctValue(delayMin, delayMax);
    }

    @SubscribeEvent
    public void onPacket(PacketEvent.Receive e) {
        if(!Utils.nullCheckPasses()) return;
        if (e.getPacket() instanceof S12PacketEntityVelocity) {
            if (chance.getInput() != 100.0D) {
                double ch = Math.random() * 100;
                if (ch >= chance.getInput()) {
                    return;
                }
            }

            Entity entity = mc.theWorld.getEntityByID(((S12PacketEntityVelocity) e.getPacket()).getEntityID());
            int key = mc.gameSettings.keyBindJump.getKeyCode();
            if (entity == mc.thePlayer && mc.thePlayer.onGround && !Keyboard.isKeyDown(key)) {
                KeyBinding.setKeyBindState(key, true);
                KeyBinding.onTick(key);
                javax.swing.Timer timer = new javax.swing.Timer(RandomUtils.nextInt((int)delayMin.getInput(), (int)delayMax.getInput()), actionevent -> KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false));
                timer.setRepeats(false);
                timer.start();
            }
        }
    }
}