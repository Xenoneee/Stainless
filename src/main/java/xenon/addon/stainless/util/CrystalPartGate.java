package xenon.addon.stainless.util;

/**
 * Thread-local gate that controls individual crystal part rendering.
 *
 * This utility allows selective rendering of end crystal parts by tracking
 * render attempts and controlling which parts should be visible.
 *
 * Usage:
 * 1. Call begin() before crystal rendering starts
 * 2. shouldRender() is called for each crystal part in sequence
 * 3. Call end() after crystal rendering completes
 */
public final class CrystalPartGate {

    // ---- Constants
    private static final int BOTTOM_PART_INDEX = 0;
    private static final int OUTER_PART_INDEX = 1;
    private static final int INNER_PART_INDEX = 2;
    private static final int CORE_PART_INDEX = 3;
    private static final int UNKNOWN_PART_INDEX = 4;
    private static final int MAX_RENDER_ATTEMPTS = 5;

    // ---- Thread-Local State
    private static final ThreadLocal<State> THREAD_STATE = ThreadLocal.withInitial(State::new);

    // ---- Constructor (Private - Utility Class)
    private CrystalPartGate() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ---- Public API
    /**
     * Begins a new crystal rendering cycle with specified part visibility
     *
     * @param vanillaShowsBottom Whether vanilla rendering shows bottom (unused currently)
     * @param showOuter Whether to render outer crystal part
     * @param showInner Whether to render inner crystal part
     * @param showCore Whether to render core crystal part
     * @param showBottom Whether to render bottom crystal part
     */
    public static void begin(boolean vanillaShowsBottom,
                             boolean showOuter,
                             boolean showInner,
                             boolean showCore,
                             boolean showBottom) {
        State state = THREAD_STATE.get();
        state.activate(showOuter, showInner, showCore, showBottom);
    }

    /**
     * Ends the current crystal rendering cycle
     */
    public static void end() {
        State state = THREAD_STATE.get();
        state.deactivate();
    }

    /**
     * Determines whether the current render attempt should proceed
     *
     * @return true if part should render, false if it should be skipped
     */
    public static boolean shouldRender() {
        State state = THREAD_STATE.get();

        if (!state.isActive()) {
            return true; // Not in controlled rendering mode
        }

        int currentAttempt = state.getCurrentAttempt();
        state.incrementAttempt();

        // Safety: If we exceed expected attempts, deactivate and allow rendering
        if (currentAttempt >= MAX_RENDER_ATTEMPTS) {
            state.deactivate();
            return true;
        }

        return state.shouldRenderPart(currentAttempt);
    }

    // ---- State Management
    private static final class State {
        // Control flags
        private boolean active;

        // Part visibility flags
        private boolean showOuter;
        private boolean showInner;
        private boolean showCore;
        private boolean showBottom;

        // Tracking
        private int attemptCount;

        /**
         * Activates controlled rendering with specified part visibility
         */
        void activate(boolean showOuter, boolean showInner, boolean showCore, boolean showBottom) {
            this.active = true;
            this.attemptCount = 0;
            this.showOuter = showOuter;
            this.showInner = showInner;
            this.showCore = showCore;
            this.showBottom = showBottom;
        }

        /**
         * Deactivates controlled rendering
         */
        void deactivate() {
            this.active = false;
            this.attemptCount = 0;
        }

        /**
         * Checks if controlled rendering is active
         */
        boolean isActive() {
            return active;
        }

        /**
         * Gets the current attempt index
         */
        int getCurrentAttempt() {
            return attemptCount;
        }

        /**
         * Increments the attempt counter
         */
        void incrementAttempt() {
            attemptCount++;
        }

        /**
         * Determines if a specific part should render based on attempt index
         *
         * @param attempt The attempt index (0-4)
         * @return true if part should render
         */
        boolean shouldRenderPart(int attempt) {
            return switch (attempt) {
                case BOTTOM_PART_INDEX -> showBottom;
                case OUTER_PART_INDEX -> showOuter;
                case INNER_PART_INDEX -> showInner;
                case CORE_PART_INDEX -> showCore;
                case UNKNOWN_PART_INDEX -> true; // Always render unknown parts
                default -> true; // Safety: render anything beyond expected
            };
        }
    }
}
