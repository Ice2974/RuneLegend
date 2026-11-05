package com.runelegend.combat;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.UseEffects;

public final class GuardItems {
    private GuardItems() {}

    public static boolean canBlock(ItemStack stack) {
        return !stack.isEmpty() && stack.has(DataComponents.BLOCKS_ATTACKS);
    }

    public static boolean using(LivingEntity entity) {
        return entity.isUsingItem() && canBlock(entity.getUseItem());
    }

    public static boolean canSprint(ItemStack stack) {
        return stack.getOrDefault(DataComponents.USE_EFFECTS, UseEffects.DEFAULT).canSprint();
    }
}
