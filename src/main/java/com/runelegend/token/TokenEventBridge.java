package com.runelegend.token;

import com.runelegend.RuneLegendServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import java.util.function.BooleanSupplier;

/** Scoped to the original call, so nested damage never inherits an unrelated hit's metadata. */
public final class TokenEventBridge {
    public static final class Hit {
        private final LivingEntity victim;
        private final DamageSource source;
        private final float incoming;
        private final boolean aliveBefore;
        private float effective;
        private float blocked;

        private Hit(LivingEntity victim, DamageSource source, float incoming) {
            this.victim = victim;
            this.source = source;
            this.incoming = incoming;
            this.aliveBefore = victim.isAlive();
        }
    }

    private record Melee(ServerPlayer player, Entity victim, DamageSource source) {}
    private record Explosion(Entity victim, DamageSource source, Vec3 center) {}
    private static final ThreadLocal<Hit> HITS = new ThreadLocal<>();
    private static final ThreadLocal<Melee> MELEE = new ThreadLocal<>();
    private static final ThreadLocal<Explosion> EXPLOSIONS = new ThreadLocal<>();

    private TokenEventBridge() {}

    public static boolean trackHit(RuneLegendServer server, LivingEntity victim, ServerLevel level,
                                   DamageSource source, float amount, BooleanSupplier original) {
        if (source.is(TokenEngine.TOKEN_DAMAGE)) return original.getAsBoolean();
        var previous = HITS.get();
        var hit = new Hit(victim, source, amount);
        HITS.set(hit);
        try {
            boolean accepted = original.getAsBoolean();
            if (accepted && hit.aliveBefore && hit.effective > 0) dispatch(server, hit, level);
            return accepted;
        } finally {
            restore(HITS, previous);
        }
    }

    public static void applied(LivingEntity victim, DamageSource source, float effective, float blocked) {
        var hit = HITS.get();
        if (hit != null && hit.victim == victim && hit.source == source) {
            hit.effective += effective;
            hit.blocked += blocked;
        }
    }

    public static boolean melee(ServerPlayer attacker, Entity victim, DamageSource source, BooleanSupplier original) {
        var previous = MELEE.get();
        MELEE.set(new Melee(attacker, victim, source));
        try {
            return original.getAsBoolean();
        } finally {
            restore(MELEE, previous);
        }
    }

    public static boolean explosion(Entity victim, DamageSource source, Vec3 center, BooleanSupplier original) {
        var previous = EXPLOSIONS.get();
        EXPLOSIONS.set(new Explosion(victim, source, center));
        try {
            return original.getAsBoolean();
        } finally {
            restore(EXPLOSIONS, previous);
        }
    }

    public static Vec3 explosionCenter(Entity victim, DamageSource source) {
        var explosion = EXPLOSIONS.get();
        return explosion != null && explosion.victim == victim && explosion.source == source ? explosion.center : null;
    }

    private static void dispatch(RuneLegendServer server, Hit hit, ServerLevel level) {
        var tokens = server.tokens();
        var victim = hit.victim;
        var source = hit.source;
        if (victim instanceof ServerPlayer player && player.isAlive()) {
            tokens.dispatch(event(TokenEvent.Kind.HURT, player, victim, level, hit));
            if (hit.blocked > 0) tokens.dispatch(event(TokenEvent.Kind.GUARD_SUCCESS, player, victim, level, hit));
        }
        if (!(source.getEntity() instanceof ServerPlayer player) || player == victim) return;
        var melee = MELEE.get();
        if (melee != null && melee.player == player && melee.victim == victim && melee.source == source) {
            tokens.dispatch(event(TokenEvent.Kind.MELEE, player, victim, level, hit));
        } else if (source.getDirectEntity() instanceof AbstractArrow) {
            tokens.dispatch(event(TokenEvent.Kind.ARROW, player, victim, level, hit));
        }
        if (!victim.isAlive()) tokens.dispatch(event(TokenEvent.Kind.KILL, player, victim, level, hit));
    }

    private static TokenEvent event(TokenEvent.Kind kind, ServerPlayer player, LivingEntity victim, ServerLevel level, Hit hit) {
        return new TokenEvent(kind, player, victim, level, hit.source, hit.incoming, hit.effective, hit.blocked);
    }

    private static <T> void restore(ThreadLocal<T> local, T previous) {
        if (previous == null) local.remove();
        else local.set(previous);
    }
}
