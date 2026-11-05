package com.runelegend.combat;

import net.minecraft.world.item.ItemStack;

public final class PlayerCombatRuntime {
    int stamina = StaminaManager.MAX;
    long stoppedAt = -StaminaManager.REGEN_DELAY - 1L;
    boolean guarding;
    boolean releaseRequired;
    long lastInput = -1;
    long lastStartTick = Long.MIN_VALUE;
    int slot = -1;
    ItemStack weapon = ItemStack.EMPTY;
    long startedAt;
    net.minecraft.server.level.ServerLevel level;

    public int stamina() { return stamina; }
    public boolean guarding() { return guarding; }
    public long lastInput() { return lastInput; }
}
