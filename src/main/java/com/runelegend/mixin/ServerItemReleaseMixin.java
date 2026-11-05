package com.runelegend.mixin;

import com.runelegend.RuneLegendServer;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerItemReleaseMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handlePlayerAction(Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;releaseUsingItem()V"), allow = 1)
    private void runelegend$onRelease(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        var server = RuneLegendServer.find(player.level().getServer());
        if (server != null) server.stamina().released(player);
    }
}
