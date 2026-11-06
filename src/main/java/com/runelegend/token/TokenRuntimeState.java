package com.runelegend.token;

public final class TokenRuntimeState {
    public int charge;
    public long cooldownUntil;
    public long triggeredUntil;
    public long activeUntil;

    public void flash(long tick) { triggeredUntil = tick + 12; }
    /** Cooldown survives death and disconnect; all other round-local state is discarded. */
    public void clearEffects() { charge = 0; triggeredUntil = 0; activeUntil = 0; }
}
