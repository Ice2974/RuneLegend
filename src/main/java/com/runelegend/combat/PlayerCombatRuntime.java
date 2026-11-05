package com.runelegend.combat;

final class PlayerCombatRuntime {
    static final long NOT_USING = -1;
    int stamina = StaminaManager.MAX;
    long startedAt = NOT_USING;
    long stoppedAt = -StaminaManager.REGEN_DELAY - 1L;
    boolean releaseRequired;
}
