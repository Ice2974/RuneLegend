package com.runelegend.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.runelegend.item.ItemLockPolicy;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerItemLockMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleContainerClick(Lnet/minecraft/network/protocol/game/ServerboundContainerClickPacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;suppressRemoteUpdates()V"),
            cancellable = true, require = 1, allow = 1)
    private void runelegend$guardContainer(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (!ItemLockPolicy.allowContainerInput(player, player.containerMenu, packet.slotNum(), packet.buttonNum(),
                packet.containerInput())) {
            // Do not accept the rejected request's predicted hashes or leave remote updates suppressed.
            player.containerMenu.sendAllDataToRemote();
            ci.cancel();
        }
    }

    @Inject(method = "handlePlayerAction(Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket;getAction()Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket$Action;"),
            cancellable = true, require = 1, allow = 1)
    private void runelegend$guardHands(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        boolean blocked = switch (packet.getAction()) {
            case DROP_ITEM, DROP_ALL_ITEMS -> ItemLockPolicy.isProtected(player, player.getMainHandItem());
            case SWAP_ITEM_WITH_OFFHAND -> ItemLockPolicy.blocksSwapHands(player);
            default -> false;
        };
        if (blocked) {
            var inventory = player.getInventory();
            player.connection.send(inventory.createInventoryUpdatePacket(inventory.getSelectedSlot()));
            if (packet.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND) {
                player.connection.send(inventory.createInventoryUpdatePacket(Inventory.SLOT_OFFHAND));
            }
            ci.cancel();
        }
    }

    @Inject(method = "handlePlaceRecipe(Lnet/minecraft/network/protocol/game/ServerboundPlaceRecipePacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/RecipeBookMenu;handlePlacement(ZZLnet/minecraft/world/item/crafting/RecipeHolder;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/player/Inventory;)Lnet/minecraft/world/inventory/RecipeBookMenu$PostPlaceAction;"),
            cancellable = true, require = 1, allow = 1)
    private void runelegend$guardRecipe(ServerboundPlaceRecipePacket packet, CallbackInfo ci) {
        if (ItemLockPolicy.blocksRecipePlacement(player, player.containerMenu)) {
            player.containerMenu.sendAllDataToRemote();
            ci.cancel();
        }
    }

    @Inject(method = "handleSelectTrade(Lnet/minecraft/network/protocol/game/ServerboundSelectTradePacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/MerchantMenu;setSelectionHint(I)V"),
            cancellable = true, require = 1, allow = 1)
    private void runelegend$guardTrade(ServerboundSelectTradePacket packet, CallbackInfo ci) {
        if (ItemLockPolicy.blocksTradeSelection(player, player.containerMenu)) {
            player.containerMenu.sendAllDataToRemote();
            ci.cancel();
        }
    }

    @WrapOperation(method = "tryPickItem(Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;pickSlot(I)V"),
            require = 1, allow = 1)
    private void runelegend$guardPick(Inventory inventory, int source, Operation<Void> original) {
        int target = inventory.getSuitableHotbarSlot();
        if (ItemLockPolicy.isProtected(player, inventory.getItem(source))
                || ItemLockPolicy.isProtected(player, inventory.getItem(target))) {
            player.connection.send(inventory.createInventoryUpdatePacket(source));
            player.connection.send(inventory.createInventoryUpdatePacket(target));
            // Vanilla still sends the authoritative selected slot after this call.
            return;
        }
        original.call(inventory, source);
    }
}
