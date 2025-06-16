package slate.utility.slate;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Message bus that manages mouse movement requests and prioritizes one on each frame.
 */
public final class MouseManager {

    /** Call this once in your Loader / ClientProxy */
    public static void init() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(HOLDER);
    }


    // region public api

    /** Priority buckets – extend / reorder whenever you like. */
    public enum Priority {
        MANUAL_INPUT(0), // vanilla mouse                        (not used here, just a reminder)
        LOW(1), // cosmetic stuff
        NORMAL(2), // AimAssist, ReachDisplay, …
        HIGH(3), // Auto-Block
        CRITICAL(4); // Auto-Clutch  (you REALLY don’t want to miss)
        public final int value;
        Priority(int v) { this.value = v; }
    }

    /**
     * Queue a delta for the *current* client tick.
     * You can call this as many times as you want – only the highest-priority
     * request will be kept.
     */
    public static void offer(int dx, int dy, Priority prio) {
        REQUESTS.add(new Request(dx, dy, prio));
    }

    /** Consume & clear. Called by the MouseHelper Mixin. */
    public static int[] consume() {
        Optional<Request> chosen = resolve();
        reset();
        return chosen.map(request -> new int[]{request.dx, request.dy}).orElseGet(() -> new int[]{0, 0});
    }


    // endregion


    // region internals
    private static final Holder HOLDER = new Holder();  // event-bus holder
    private static final List<Request> REQUESTS = new ArrayList<>();

    @AllArgsConstructor @Getter @ToString
    private static class Request {
        final int dx, dy;
        final Priority priority;
    }

    /** Pick request with highest priority (first come first serve if equal). */
    private static Optional<Request> resolve() {
        return REQUESTS.stream().max(Comparator.comparingInt(r -> r.priority.value));
    }

    /** Clear at the start of *every* client tick so stale data never persists. */
    private static void reset() {
        REQUESTS.clear();
    }

    private static class Holder {
        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase == TickEvent.Phase.START) {
                reset();
            }
        }
    }
    // endregion
}