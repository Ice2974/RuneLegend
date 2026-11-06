package com.runelegend.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.runelegend.item.ItemLockMenuAccess;
import com.runelegend.item.ItemLockPolicy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(AbstractContainerMenu.class)
abstract class ContainerItemLockMixin implements ItemLockMenuAccess {
    @Shadow @Final private Set<Slot> quickcraftSlots;
    @Shadow protected abstract void resetQuickCraft();

    @Override public Set<Slot> runelegend$quickCraftSlots() { return quickcraftSlots; }
    @Override public void runelegend$resetQuickCraft() { resetQuickCraft(); }

    @Inject(method = "doClick(IILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void runelegend$guardInput(int slot, int button, ContainerInput input, Player player, CallbackInfo ci) {
        if (!ItemLockPolicy.allowContainerInput(player, (AbstractContainerMenu) (Object) this, slot, button, input)) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "doClick(IILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;quickMoveStack(Lnet/minecraft/world/entity/player/Player;I)Lnet/minecraft/world/item/ItemStack;"),
            require = 2, allow = 2)
    private ItemStack runelegend$guardRepeatedTransfer(AbstractContainerMenu menu, Player player, int slot,
                                                       Operation<ItemStack> original) {
        return ItemLockPolicy.isProtected(player, menu.getSlot(slot).getItem())
                ? ItemStack.EMPTY : original.call(menu, player, slot);
    }

    @WrapOperation(method = "doClick(IILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;safeTake(IILnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/item/ItemStack;"),
            require = 3, allow = 3)
    private ItemStack runelegend$guardRepeatedTake(Slot slot, int amount, int maximum, Player player,
                                                  Operation<ItemStack> original) {
        return ItemLockPolicy.isProtected(player, slot.getItem())
                ? ItemStack.EMPTY : original.call(slot, amount, maximum, player);
    }
}
