package com.runelegend.token.effect;

import com.runelegend.RuneLegendMod;
import com.runelegend.token.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class SoulSiphonEffect implements TokenEffectHandler {
    private static final net.minecraft.resources.Identifier MODIFIER = RuneLegendMod.id("soul_siphon");

    @Override public void onEvent(TokenEngine.Context context, TokenRuntimeState state, TokenEvent event) {
        if (!TokenTargeting.enemy(event.target()) || !context.roll(0.09)) return;
        var player = context.player();
        var attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) return;
        float health = player.getHealth();
        context.replaceEffect("max_health", () -> {
            attribute.removeModifier(MODIFIER);
            if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
        });
        attribute.addTransientModifier(new AttributeModifier(MODIFIER, 2, AttributeModifier.Operation.ADD_VALUE));
        if (player.getHealth() < health) player.setHealth(health);
        state.flash(context.tick());
        state.activeUntil = context.tick() + 3600;
        context.at("max_health", state.activeUntil, () -> context.finish("max_health"));
    }
}
