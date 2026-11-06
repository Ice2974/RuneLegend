package com.runelegend.mixin;

import com.runelegend.item.ItemLockEquipment;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ArmorStand.class)
abstract class ArmorStandItemLockMixin {
    @Inject(method = "swapItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Z",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void runelegend$protectStandEquipment(Player player, EquipmentSlot slot, ItemStack stack,
                                                 InteractionHand hand, CallbackInfoReturnable<Boolean> cir) {
        if (ItemLockEquipment.denyTransfer(player, (ArmorStand) (Object) this, slot, stack)) cir.setReturnValue(false);
    }
}
