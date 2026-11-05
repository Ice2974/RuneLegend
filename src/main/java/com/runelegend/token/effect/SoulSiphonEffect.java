package com.runelegend.token.effect;

import com.runelegend.RuneLegendMod;
import com.runelegend.token.TokenEffectHandler;
import com.runelegend.token.TokenEvent;
import com.runelegend.token.TokenEngine;
import com.runelegend.token.TokenRuntimeState;
import com.runelegend.token.TokenTargeting;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class SoulSiphonEffect implements TokenEffectHandler {
    private static final net.minecraft.resources.Identifier MODIFIER = RuneLegendMod.id("soul_siphon");

    @Override public void onEvent(TokenEngine.Context context, TokenRuntimeState state, TokenEvent event) {
        if (!TokenTargeting.enemy(event.target()) || !context.roll(0.05)) return;
        var player = context.player();
        var attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) return;
        float health = player.getHealth();
        // A permanent modifier is saved with the player and is not owned by one game round.
        attribute.removeModifier(MODIFIER);
        attribute.addPermanentModifier(new AttributeModifier(MODIFIER, 2, AttributeModifier.Operation.ADD_VALUE));
        if (player.getHealth() < health) player.setHealth(health);
        state.flash(context.tick());
    }
}
