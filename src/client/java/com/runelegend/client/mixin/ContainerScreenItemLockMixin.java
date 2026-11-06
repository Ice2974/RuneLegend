package com.runelegend.client.mixin;

import com.runelegend.item.ItemLockPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(AbstractContainerScreen.class)
abstract class ContainerScreenItemLockMixin {
    @Shadow @Final protected AbstractContainerMenu menu;
    @Shadow @Final protected Set<Slot> quickCraftSlots;
    @Shadow protected boolean isQuickCrafting;
    @Shadow private int quickCraftingRemainder;
    @Shadow private void recalculateQuickCraftRemaining() { throw new AssertionError(); }

    @Inject(method = "shouldAddSlotToQuickCraft(Lnet/minecraft/world/inventory/Slot;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void runelegend$checkDragCandidate(Slot slot, ItemStack carried, CallbackInfoReturnable<Boolean> cir) {
        var player = Minecraft.getInstance().player;
        if (player != null && (ItemLockPolicy.isProtected(player, carried)
                || slot != null && ItemLockPolicy.isProtected(player, slot.getItem()))) cir.setReturnValue(false);
    }

    @Inject(method = "extractContents(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"),
            require = 1, allow = 1)
    private void runelegend$refreshDragPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
                                             CallbackInfo ci) {
        if (isQuickCrafting) runelegend$removeLockedDragSlots();
    }

    @Inject(method = "quickCraftToSlots()V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void runelegend$checkDragCompletion(CallbackInfo ci) {
        // Server updates may have changed a candidate or the cursor since mouse-down.
        runelegend$removeLockedDragSlots();
        if (quickCraftSlots.isEmpty()) {
            isQuickCrafting = false;
            ci.cancel();
        }
    }

    @Unique
    private void runelegend$removeLockedDragSlots() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        if (ItemLockPolicy.isProtected(player, menu.getCarried())) {
            quickCraftSlots.clear();
            isQuickCrafting = false;
            quickCraftingRemainder = menu.getCarried().getCount();
        } else if (quickCraftSlots.removeIf(slot -> ItemLockPolicy.isProtected(player, slot.getItem()))) {
            recalculateQuickCraftRemaining();
        }
    }
}
