package com.runelegend.mixin;

import com.runelegend.item.ItemLockEquipment;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Equippable.class)
abstract class EquippableItemLockMixin {
    @Shadow public abstract EquipmentSlot slot();

    @Inject(method = "swapWithEquipmentSlot(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/InteractionResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;level()Lnet/minecraft/world/level/Level;"),
            cancellable = true, require = 1, allow = 1)
    private void runelegend$protectOwnEquipment(ItemStack stack, Player player,
                                               CallbackInfoReturnable<InteractionResult> cir) {
        if (ItemLockEquipment.denyTransfer(player, player, slot(), stack)) cir.setReturnValue(InteractionResult.FAIL);
    }

    @Inject(method = "equipOnTarget(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/InteractionResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;level()Lnet/minecraft/world/level/Level;"),
            cancellable = true, require = 1, allow = 1)
    private void runelegend$protectTargetEquipment(Player player, LivingEntity target, ItemStack stack,
                                                  CallbackInfoReturnable<InteractionResult> cir) {
        if (ItemLockEquipment.denyTransfer(player, target, slot(), stack)) cir.setReturnValue(InteractionResult.FAIL);
    }
}
