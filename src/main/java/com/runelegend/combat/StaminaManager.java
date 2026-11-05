package com.runelegend.combat;

/** All values are integral; timing follows server ticks. */
public final class StaminaManager {
    public static final int MAX = 20_000;
    public static final int MIN_TO_START = 2_000;
    public static final int DRAIN_PER_TICK = 125;
    public static final int REGEN_PER_TICK = 100;
    public static final int REGEN_DELAY = 10;

    private StaminaManager() {}

    public static int drain(int value) { return Math.max(0, value - DRAIN_PER_TICK); }
    public static int recover(int value, long tick, long stoppedAt) {
        return tick - stoppedAt > REGEN_DELAY ? Math.min(MAX, value + REGEN_PER_TICK) : value;
    }
}
