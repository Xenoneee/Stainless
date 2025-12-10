package xenon.addon.stainless.modules.autopearlthrow;

public class PearlThrowState {
    private boolean pendingThrow = false;
    private boolean jumpedThisCycle = false;
    private long scheduleAt = 0L;
    private long lastThrowAt = 0L;

    public void scheduleThrow(long atMillis) {
        pendingThrow = true;
        jumpedThisCycle = false;
        scheduleAt = atMillis;
    }

    public void reset() {
        pendingThrow = false;
        jumpedThisCycle = false;
        scheduleAt = 0L;
    }

    public void recordThrow(long timestamp) {
        lastThrowAt = timestamp;
    }

    public void markJumped() {
        jumpedThisCycle = true;
    }

    public void delayThrow(long newScheduleTime) {
        scheduleAt = newScheduleTime;
    }

    public boolean isPendingThrow() {
        return pendingThrow;
    }

    public boolean hasJumped() {
        return jumpedThisCycle;
    }

    public boolean isTimeToThrow(long currentTime) {
        return currentTime >= scheduleAt;
    }

    public boolean isOnCooldown(long currentTime, int cooldownMs) {
        return currentTime - lastThrowAt < cooldownMs;
    }

    public long getLastThrowTime() {
        return lastThrowAt;
    }
}
