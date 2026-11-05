package com.runelegend.api;

public record RuleSnapshot(GameState state, boolean guardEnabled, long round, long revision) {
    public boolean running() { return state == GameState.RUNNING; }
    public boolean canGuard() { return running() && guardEnabled; }
}
