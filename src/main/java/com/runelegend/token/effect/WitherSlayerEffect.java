package com.runelegend.token.effect;

import com.runelegend.token.*;
import net.minecraft.server.level.ServerPlayer;

public final class WitherSlayerEffect implements TokenEffectHandler {
    private static final int HITS = 5;
    private static final int DURATION = 120;
    private static final int WITHER_II_INTERVAL = 20;

    @Override public boolean ready(ServerPlayer player, TokenRuntimeState state, long tick) { return state.charge >= HITS; }

    @Override public void onEvent(TokenEngine.Context context, TokenRuntimeState state, TokenEvent event) {
        if (event.kind() == TokenEvent.Kind.HURT) {
            var attacker = event.source().getEntity();
            if (attacker != event.player() && attacker != null && (attacker instanceof ServerPlayer || TokenTargeting.enemy(attacker))) {
                state.charge = Math.min(HITS, state.charge + 1);
            }
            return;
        }
        if (state.charge < HITS) return;
        state.charge = 0;
        long tick = context.tick();
        state.flash(tick);
        var target = event.target();
        if (!target.isAlive()) return;
        String effect = "wither:" + target.getUUID();
        context.replaceEffect(effect, () -> {});
        // Vanilla applies Wither II when its remaining duration is a multiple of 20.
        for (int elapsed = 0; elapsed < DURATION; elapsed += WITHER_II_INTERVAL) {
            context.at(effect, tick + elapsed + 1, () -> context.damage(event.level(), target, 1));
        }
        context.at(effect, tick + DURATION, () -> context.finish(effect));
    }
}
