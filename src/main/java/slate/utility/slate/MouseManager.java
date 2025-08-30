package slate.utility.slate;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.Optional;

/**
 * @return mouse delta chosen by priority; last write wins on equal priority
 */
public final class MouseManager {

    public static void init() { }

    public enum Priority {
        MANUAL_INPUT(0),
        LOW(1),
        NORMAL(2),
        HIGH(3),
        CRITICAL(4);
        public final int value;
        Priority(int v) { this.value = v; }
    }

    private static volatile Request best = null;

    /**
     * @param dx delta x
     * @param dy delta y
     * @param prio priority
     */
    public static void offer(int dx, int dy, Priority prio) {
        Request incoming = new Request(dx, dy, prio);
        Request cur = best;
        if (cur == null || prio.value >= cur.priority.value) best = incoming;
    }

    /**
     * @return dx,dy
     */
    public static int[] consume() {
        Request chosen = best;
        best = null;
        return Optional.ofNullable(chosen).map(r -> new int[]{r.dx, r.dy}).orElseGet(() -> new int[]{0, 0});
    }

    @AllArgsConstructor @Getter @ToString
    private static class Request {
        final int dx, dy;
        final Priority priority;
    }
}