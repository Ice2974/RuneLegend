package com.runelegend.combat;

import com.runelegend.RuneLegendMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.ItemStack;

public final class GuardPolicy {
    public static final TagKey<DamageType> GUARDABLE = TagKey.create(Registries.DAMAGE_TYPE, RuneLegendMod.id("sword_guardable"));
    public static final float DAMAGE_MULTIPLIER = 0.5F;
    public static final double SPEED_MODIFIER = -0.8;

    private GuardPolicy() {}

    public static boolean sword(ItemStack stack) { return stack.is(ItemTags.SWORDS); }

    public static boolean guardable(DamageSource source) {
        return source.is(GUARDABLE) && !source.is(DamageTypeTags.BYPASSES_ARMOR)
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }
}
