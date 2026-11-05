package com.runelegend.token.effect;

import com.runelegend.token.*;

public final class SharpenedTipEffect implements TokenEffectHandler {
    @Override public float addMeleeDamage(TokenEngine.Context context, TokenRuntimeState state) {
        return context.roll(0.50) ? 2 : 0;
    }
}
