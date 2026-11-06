package com.runelegend.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.runelegend.item.ItemLockPolicy;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantMenu.class)
abstract class MerchantItemLockMixin {
    @Shadow @Final private Merchant trader;

    @Inject(method = "tryMoveItems(I)V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void runelegend$protectPayments(int offer, CallbackInfo ci) {
        var menu = (MerchantMenu) (Object) this;
        var player = trader.getTradingPlayer();
        // Check both payments before vanilla returns either one to the player's inventory.
        if (player != null && ItemLockPolicy.blocksTradeSelection(player, menu)) {
            if (!player.level().isClientSide()) menu.sendAllDataToRemote();
            ci.cancel();
        }
    }

    @WrapOperation(method = "moveFromInventoryToPaymentSlot(ILnet/minecraft/world/item/trading/ItemCost;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/trading/ItemCost;test(Lnet/minecraft/world/item/ItemStack;)Z"),
            require = 1, allow = 1)
    private boolean runelegend$skipLockedPayment(ItemCost cost, ItemStack candidate, Operation<Boolean> original) {
        var player = trader.getTradingPlayer();
        return (player == null || !ItemLockPolicy.isProtected(player, candidate)) && original.call(cost, candidate);
    }
}
