package com.runelegend.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.runelegend.item.ItemLockPolicy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Inventory.class)
abstract class InventoryItemLockMixin {
    @Shadow @Final public Player player;

    @WrapOperation(method = "fillStackedContents(Lnet/minecraft/world/entity/player/StackedItemContents;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/StackedItemContents;accountSimpleStack(Lnet/minecraft/world/item/ItemStack;)V"),
            require = 1, allow = 1)
    private void runelegend$excludeLockedIngredients(StackedItemContents contents, ItemStack stack,
                                                    Operation<Void> original) {
        if (!ItemLockPolicy.isProtected(player, stack)) original.call(contents, stack);
    }

    @WrapOperation(method = "findSlotMatchingCraftingIngredient(Lnet/minecraft/core/Holder;Lnet/minecraft/world/item/ItemStack;)I",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;isUsableForCrafting(Lnet/minecraft/world/item/ItemStack;)Z"),
            require = 1, allow = 1)
    private boolean runelegend$skipLockedIngredient(ItemStack stack, Operation<Boolean> original) {
        // Keep the vanilla search running so a later, ordinary stack can still satisfy the recipe.
        return !ItemLockPolicy.isProtected(player, stack) && original.call(stack);
    }
}
