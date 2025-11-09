package xenon.stainless.mixin.mixinhelper;

/** Thread-local gate that decides whether the next ModelPart.render() should draw. */
public final class CrystalPartGate {
    private static final ThreadLocal<State> TL = ThreadLocal.withInitial(State::new);
    private CrystalPartGate() {}

    /** Call at the start of EndCrystalEntityRenderer#render. */
    public static void begin(boolean vanillaShowsBottom,
                             boolean showOuter, boolean showInner, boolean showCore, boolean showBottom) {
        State s = TL.get();
        s.active = true;
        s.idx = 0;
        s.vanillaShowsBottom = vanillaShowsBottom;
        s.showOuter = showOuter;
        s.showInner = showInner;
        s.showCore  = showCore;
        s.showBottom = showBottom;
    }

    /** Call at the end of EndCrystalEntityRenderer#render. */
    public static void end() {
        TL.get().active = false;
    }

    /** Returns true if the *next* ModelPart.render() should draw (also advances the step). */
    public static boolean nextAllowed() {
        State s = TL.get();
        if (!s.active) return true;

        int step = s.idx++;
        if (s.vanillaShowsBottom) {
            // order: base, outer glass, inner glass, core
            return switch (step) {
                case 0 -> s.showBottom;
                case 1 -> s.showOuter;
                case 2 -> s.showInner;
                case 3 -> s.showCore;
                default -> true; // any extra renders, just let through
            };
        } else {
            // order: outer glass, inner glass, core
            return switch (step) {
                case 0 -> s.showOuter;
                case 1 -> s.showInner;
                case 2 -> s.showCore;
                default -> true;
            };
        }
    }

    private static final class State {
        boolean active;
        boolean vanillaShowsBottom;
        boolean showOuter, showInner, showCore, showBottom;
        int idx;
    }
}
