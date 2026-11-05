package com.runelegend.token.effect;

import com.runelegend.token.*;
import net.minecraft.server.level.ServerPlayer;

public final class ImmediateCounterEffect implements TokenEffectHandler {
    @Override public boolean ready(ServerPlayer player, TokenRuntimeState state, long tick) {
        return tick >= state.cooldownUntil && player.getHealth() < player.getMaxHealth() / 2;
    }

    @Override public float addMeleeDamage(TokenEngine.Context context, TokenRuntimeState state) {
        return context.tick() < state.activeUntil ? 3 : 0;
    }

    @Override public void onEvent(TokenEngine.Context context, TokenRuntimeState state, TokenEvent event) {
        if (event.kind() != TokenEvent.Kind.GUARD_SUCCESS || !ready(event.player(), state, context.tick())) return;
        state.flash(context.tick());
        state.activeUntil = context.tick() + 160;
        state.cooldownUntil = state.activeUntil + 240;
    }
}
