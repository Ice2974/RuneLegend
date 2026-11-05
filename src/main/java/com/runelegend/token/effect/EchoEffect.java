package com.runelegend.token.effect;

import com.runelegend.token.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

public final class EchoEffect implements TokenEffectHandler {
    private static final double RADIUS = 5;

    @Override public void onEvent(TokenEngine.Context context, TokenRuntimeState state, TokenEvent event) {
        if (!TokenTargeting.enemy(event.target()) || !context.roll(0.50)) return;
        var center = event.target().position();
        var level = event.level();
        long start = context.tick();
        context.replaceEffect("echo", () -> {});
        state.flash(start);
        state.activeUntil = start + 160;
        for (int elapsed = 20; elapsed <= 160; elapsed += 20) {
            context.at("echo", start + elapsed, () -> {
                var area = new AABB(center, center).inflate(RADIUS);
                for (var enemy : level.getEntitiesOfClass(LivingEntity.class, area,
                        mob -> mob.isAlive() && TokenTargeting.enemy(mob) && mob.distanceToSqr(center) <= RADIUS * RADIUS)) {
                    context.damage(level, enemy, 2);
                }
            });
        }
        context.at("echo", state.activeUntil, () -> context.finish("echo"));
    }
}
