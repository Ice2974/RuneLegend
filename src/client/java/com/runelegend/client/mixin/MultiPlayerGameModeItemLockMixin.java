package com.runelegend.client.mixin;

import com.runelegend.item.ItemLockPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeItemLockMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "handleContainerInput(IIILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void runelegend$checkContainerInput(int containerId, int slotId, int button, ContainerInput input,
                                               Player player, CallbackInfo ci) {
        var menu = player.containerMenu;
        if (menu.containerId == containerId && !ItemLockPolicy.allowContainerInput(player, menu, slotId, button, input)) {
            ci.cancel();
        }
    }

    @Inject(method = "dropItem(Lnet/minecraft/client/player/LocalPlayer;Z)V", at = @At("HEAD"),
            cancellable = true, require = 1, allow = 1)
    private void runelegend$checkDrop(LocalPlayer player, boolean wholeStack, CallbackInfo ci) {
        // Vanilla removes the selected stack before sending the drop packet.
        if (ItemLockPolicy.isProtected(player, player.getMainHandItem())) ci.cancel();
    }

    @Inject(method = "handlePlaceRecipe(ILnet/minecraft/world/item/crafting/display/RecipeDisplayId;Z)V",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void runelegend$checkRecipe(int containerId, RecipeDisplayId recipe, boolean shiftDown, CallbackInfo ci) {
        var player = minecraft.player;
        if (player != null && player.containerMenu.containerId == containerId
                && ItemLockPolicy.blocksRecipePlacement(player, player.containerMenu)) ci.cancel();
    }
}
