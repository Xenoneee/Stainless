package xenon.addon.stainless.util;

/**
 * Thread-local gate that tracks crystal part rendering.
 */
public final class CrystalPartGate {
    private static final ThreadLocal<State> TL = ThreadLocal.withInitial(State::new);
    private CrystalPartGate() {}

    public static void begin(boolean vanillaShowsBottom,
                             boolean showOuter, boolean showInner, boolean showCore, boolean showBottom) {
        State s = TL.get();
        s.active = true;
        s.attemptCount = 0;
        s.showOuter = showOuter;
        s.showInner = showInner;
        s.showCore  = showCore;
        s.showBottom = showBottom;
        s.maxAttempts = 5;

        System.out.println("[Crystal] BEGIN - Bottom:" + showBottom + " Outer:" + showOuter + " Inner:" + showInner + " Core:" + showCore);
    }
    // TODO: remove console log debug info
    public static void end() {
        State s = TL.get();
        System.out.println("[Crystal] END - Total attempts: " + s.attemptCount);
        s.active = false;
        s.attemptCount = 0;
    }

    public static boolean shouldRender() {
        State s = TL.get();
        if (!s.active) {
            return true;
        }

        int attempt = s.attemptCount++;

        if (attempt >= s.maxAttempts) {
            s.active = false;
            return true;
        }

        boolean result = switch (attempt) {
            case 0 -> s.showBottom;
            case 1 -> s.showOuter;
            case 2 -> s.showInner;
            case 3 -> s.showCore;
            case 4 -> true;
            default -> true;
        };

        String partName = switch (attempt) {
            case 0 -> "BOTTOM";
            case 1 -> "OUTER";
            case 2 -> "INNER";
            case 3 -> "CORE";
            case 4 -> "UNKNOWN";
            default -> "EXTRA";
        };

        System.out.println("[Crystal] Attempt " + attempt + " (" + partName + ") -> " + (result ? "RENDER" : "SKIP"));

        return result;
    }

    private static final class State {
        boolean active;
        boolean showOuter, showInner, showCore, showBottom;
        int attemptCount;
        int maxAttempts;
    }
}
