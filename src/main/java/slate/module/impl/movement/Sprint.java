package slate.module.impl.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import slate.module.Module;
import slate.utility.slate.ActionCoordinator;

public class Sprint extends Module {

    public Sprint() {
        super("Sprint", category.movement);
    }

    @SubscribeEvent
    public void onTick(TickEvent e) { // note: raven xd impl listens to PlayerTickEvent
        if(!isEnabled()) return;
        if(!ActionCoordinator.isSprintingAllowed()) return;
        KeyBinding.setKeyBindState(Minecraft.getMinecraft().gameSettings.keyBindSprint.getKeyCode(), true);
    }
}