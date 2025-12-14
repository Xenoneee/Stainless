package xenon.addon.stainless.modules.autopearlthrow;

public class PearlThrowState {

    // ---- State Variables
    private boolean pendingThrow;
    private boolean jumpedThisCycle;
    private long scheduleAt;
    private long lastThrowAt;

    // ---- Constructor
    public PearlThrowState() {
        reset();
    }

    // ---- State Management
    /**
     * Schedules a pearl throw for a specific time
     * @param atMillis Timestamp when throw should execute
     */
    public void scheduleThrow(long atMillis) {
        this.pendingThrow = true;
        this.jumpedThisCycle = false;
        this.scheduleAt = atMillis;
    }

    /**
     * Resets all state flags (cancels pending throw)
     */
    public void reset() {
        this.pendingThrow = false;
        this.jumpedThisCycle = false;
        this.scheduleAt = 0L;
    }

    /**
     * Records when a throw was executed
     * @param timestamp Timestamp of throw execution
     */
    public void recordThrow(long timestamp) {
        this.lastThrowAt = timestamp;
    }

    /**
     * Marks that jump has been performed this cycle
     */
    public void markJumped() {
        this.jumpedThisCycle = true;
    }

    /**
     * Updates the scheduled throw time (used for delaying after jump)
     * @param newScheduleTime New timestamp for throw
     */
    public void delayThrow(long newScheduleTime) {
        this.scheduleAt = newScheduleTime;
    }

    // ---- State Queries
    /**
     * Checks if a throw is currently pending
     * @return true if throw is scheduled
     */
    public boolean isPendingThrow() {
        return pendingThrow;
    }

    /**
     * Checks if player has jumped during this throw cycle
     * @return true if jumped
     */
    public boolean hasJumped() {
        return jumpedThisCycle;
    }

    /**
     * Checks if it's time to execute the scheduled throw
     * @param currentTime Current timestamp
     * @return true if current time >= scheduled time
     */
    public boolean isTimeToThrow(long currentTime) {
        return currentTime >= scheduleAt;
    }

    /**
     * Checks if still on cooldown from last throw
     * @param currentTime Current timestamp
     * @param cooldownMs Cooldown duration in milliseconds
     * @return true if on cooldown
     */
    public boolean isOnCooldown(long currentTime, int cooldownMs) {
        return (currentTime - lastThrowAt) < cooldownMs;
    }

    /**
     * Gets the timestamp of the last throw
     * @return Last throw timestamp
     */
    public long getLastThrowTime() {
        return lastThrowAt;
    }

    /**
     * Gets the scheduled throw timestamp
     * @return Scheduled throw timestamp
     */
    public long getScheduledTime() {
        return scheduleAt;
    }
}
