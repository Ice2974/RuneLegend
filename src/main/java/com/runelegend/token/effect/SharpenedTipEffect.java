package com.runelegend.token.effect;

import com.runelegend.token.TokenConfig;
import com.runelegend.token.*;

public final class SharpenedTipEffect implements TokenEffectHandler {
    private final TokenConfig.SharpenedTipSettings settings;

    public SharpenedTipEffect(TokenConfig.SharpenedTipSettings settings) {
        this.settings = settings;
    }

    @Override public float addMeleeDamage(TokenEngine.Context context, TokenRuntimeState state) {
        return context.roll(settings.chance()) ? settings.bonusDamage() : 0;
    }

    @Override public Object[] descriptionArguments() {
        return new Object[]{TokenConfig.percent(settings.chance()), settings.bonusDamage()};
    }
}
