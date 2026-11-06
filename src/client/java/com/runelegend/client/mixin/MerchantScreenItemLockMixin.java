package com.runelegend.client.mixin;

import com.runelegend.item.ItemLockPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantScreen.class)
abstract class MerchantScreenItemLockMixin {
    @Shadow private int shopItem;
    @Unique private int runelegend$acceptedShopItem;

    @Inject(method = "postButtonClick()V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void runelegend$checkTradeSelection(CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        var menu = ((MerchantScreen) (Object) this).getMenu();
        if (player != null && ItemLockPolicy.blocksTradeSelection(player, menu)) {
            // The offer button updates its highlight before it calls postButtonClick.
            shopItem = runelegend$acceptedShopItem;
            ci.cancel();
        } else {
            runelegend$acceptedShopItem = shopItem;
        }
    }
}
