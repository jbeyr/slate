package slate.module.impl.world;

import slate.event.RightClickEvent;
import slate.module.Module;
import slate.module.ModuleManager;
import slate.module.impl.other.SlotHandler;
import slate.module.setting.impl.ButtonSetting;
import slate.module.setting.impl.SliderSetting;
import slate.utility.Reflection;
import slate.utility.Utils;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent;
import org.jetbrains.annotations.NotNull;

public class FastPlace extends Module {
    public SliderSetting tickDelay;
    public ButtonSetting blocksOnly;

    public FastPlace() {
        super("Fast Place", Module.category.player, 0);
        this.registerSetting(tickDelay = new SliderSetting("Tick delay", 1, 0, 3, 1));
        this.registerSetting(blocksOnly = new ButtonSetting("Blocks only", true));
    }

    @SubscribeEvent
    public void a(@NotNull PlayerTickEvent e) {
        if (e.phase == Phase.END) {
            if (Utils.nullCheckPasses() && mc.inGameHasFocus && Reflection.rightClickDelayTimerField != null) {
                if (blocksOnly.isToggled()) {
                    ItemStack item = SlotHandler.getHeldItem();
                    if (item == null || !(item.getItem() instanceof ItemBlock)) {
                        return;
                    }
                }

                try {
                    int c = (int) tickDelay.getInput();
                    if (c == 0) {
                        Reflection.rightClickDelayTimerField.set(mc, 0);
                    } else {
                        if (c == 4) {
                            return;
                        }

                        int d = Reflection.rightClickDelayTimerField.getInt(mc);
                        if (d == 4) {
                            Reflection.rightClickDelayTimerField.set(mc, c);
                        }
                    }
                } catch (IllegalAccessException | IndexOutOfBoundsException ignored) {
                }
            }
        }
    }

    @SubscribeEvent
    public void onRightClick(RightClickEvent event) {
        try {
            int c = (int) tickDelay.getInput();
            if (c == 0) {
                Reflection.rightClickDelayTimerField.set(mc, 0);
            }
        } catch (IllegalAccessException | IndexOutOfBoundsException ignored) {
        }
    }
}
