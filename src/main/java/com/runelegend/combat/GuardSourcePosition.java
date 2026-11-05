package com.runelegend.combat;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import java.util.function.BooleanSupplier;

/** Supplies missing origin coordinates. Vanilla still compares the angle and resolves damage. */
public final class GuardSourcePosition {
    private record Explosion(Entity victim, DamageSource source, Vec3 center) {}
    private static final ThreadLocal<Explosion> EXPLOSION = new ThreadLocal<>();
    private static final double MIN_HORIZONTAL = 1e-6;

    private GuardSourcePosition() {}

    public static boolean withExplosion(Entity victim, DamageSource source, Vec3 center, BooleanSupplier damage) {
        var previous = EXPLOSION.get();
        EXPLOSION.set(new Explosion(victim, source, center));
        try {
            return damage.getAsBoolean();
        } finally {
            if (previous == null) EXPLOSION.remove();
            else EXPLOSION.set(previous);
        }
    }

    public static Vec3 forSword(Entity victim, DamageSource source, Vec3 vanilla) {
        var origin = victim.position();
        var explosion = EXPLOSION.get();
        if (explosion != null && explosion.victim == victim && explosion.source == source) {
            return validPosition(origin, explosion.center);
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return validPosition(origin, source.sourcePositionRaw());
        if (source.getDirectEntity() instanceof Projectile projectile) {
            var velocity = projectile.getDeltaMovement();
            double length = Math.hypot(velocity.x, velocity.z);
            if (!Double.isFinite(length) || length <= MIN_HORIZONTAL) return null;
            return origin.add(-velocity.x / length, 0, -velocity.z / length);
        }
        return vanilla;
    }

    private static Vec3 validPosition(Vec3 origin, Vec3 source) {
        if (source == null) return null;
        double length = Math.hypot(source.x - origin.x, source.z - origin.z);
        return Double.isFinite(length) && length > MIN_HORIZONTAL ? source : null;
    }
}
