package com.runelegend.api;

public record RuleSnapshot(GameState state, long round, long revision) {
    public boolean running() { return state == GameState.RUNNING; }
}
