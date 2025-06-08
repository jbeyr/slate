package slate.utility._unused.clicks;

import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CPSCalculator {
    private static final List<Long> leftClicks = new ArrayList<>();
    private static final List<Long> rightClicks = new ArrayList<>();
    public static long lastLeftClick = 0L;
    public static long lastRightClick = 0L;

    @SubscribeEvent
    public void onMouseUpdate(@NotNull MouseEvent event) {
        if (event.buttonstate) {
            if (event.button == 0) {
                addLeftClick();
            } else if (event.button == 1) {
                addRightClick();
            }
        }
    }

    public static void addLeftClick() {
        leftClicks.add(lastLeftClick = System.currentTimeMillis());
    }

    public static void addRightClick() {
        rightClicks.add(lastRightClick = System.currentTimeMillis());
    }

    public static int getLeftCPS() {
        leftClicks.removeIf(timestamp -> (Long) timestamp < System.currentTimeMillis() - 1000L);
        return leftClicks.size();
    }

    public static int getRightCPS() {
        rightClicks.removeIf(timestamp -> (Long) timestamp < System.currentTimeMillis() - 1000L);
        return rightClicks.size();
    }
}