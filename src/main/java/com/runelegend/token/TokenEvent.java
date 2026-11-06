package com.runelegend.token;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * A token trigger, not a damage-statistics record. damageBeforeMitigation is the amount entering
 * vanilla actuallyHurt after blocking and damage-cooldown adjustment, before armor, enchantments,
 * Resistance and absorption; it excludes the separate melee token bonus.
 * HURT is delivered only to surviving players, and token-created direct hits do not trigger tokens.
 */
public record TokenEvent(Kind kind, ServerPlayer player, LivingEntity target, ServerLevel level,
                         DamageSource source, float incomingDamage, float damageBeforeMitigation, float blockedDamage) {
    /** @deprecated This value has never represented final damage or health lost. */
    @Deprecated
    public float effectiveDamage() { return damageBeforeMitigation; }

    public enum Kind { MELEE, HURT, KILL, ARROW, GUARD_SUCCESS, HEALTH }
}
