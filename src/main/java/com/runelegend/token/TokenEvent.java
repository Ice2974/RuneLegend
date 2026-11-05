package com.runelegend.token;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

public record TokenEvent(Kind kind, ServerPlayer player, LivingEntity target, ServerLevel level,
                         DamageSource source, float incomingDamage, float effectiveDamage, float blockedDamage) {
    public enum Kind { MELEE, HURT, KILL, ARROW, GUARD_SUCCESS, HEALTH }
}
