package com.runelegend.combat;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

public final class GuardDirectionResolver {
    private static final double EPSILON = 1e-6;

    private GuardDirectionResolver() {}

    public static boolean canBlock(LivingEntity defender, DamageSource damage, Vec3 explosionCenter) {
        Vec3 direction;
        Entity direct = damage.getDirectEntity();
        if (explosionCenter != null || damage.is(DamageTypeTags.IS_EXPLOSION)) {
            var center = explosionCenter != null ? explosionCenter : damage.sourcePositionRaw();
            if (center == null) return false;
            direction = center.subtract(defender.position());
        } else if (direct instanceof Projectile projectile) {
            direction = projectile.getDeltaMovement().scale(-1);
        } else {
            if (damage.is(DamageTypeTags.IS_PROJECTILE) || !(direct instanceof LivingEntity)) return false;
            direction = direct.position().subtract(defender.position());
        }
        return inFront(defender.getYRot(), direction.x, direction.z);
    }

    public static boolean inFront(float yaw, double x, double z) {
        double length = Math.hypot(x, z);
        if (!Double.isFinite(length) || length <= EPSILON) return false;
        double radians = Math.toRadians(yaw);
        return (-Math.sin(radians) * x + Math.cos(radians) * z) / length > EPSILON;
    }
}
