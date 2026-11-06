package com.runelegend.test.mixin;

import com.runelegend.test.ItemLockClientGameTest;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test-only observer: never cancels or changes packets. */
@Mixin(ClientCommonPacketListenerImpl.class)
abstract class ItemLockPacketObserverMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), require = 1, allow = 1)
    private void runelegend$observeItemLockRequests(Packet<?> packet, CallbackInfo ci) {
        ItemLockClientGameTest.observePacket(packet);
    }
}
