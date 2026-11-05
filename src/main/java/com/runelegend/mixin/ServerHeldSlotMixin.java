package com.runelegend.mixin;

import com.runelegend.RuneLegendServer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerHeldSlotMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleSetCarriedItem", at = @At("RETURN"))
    private void runelegend$recheckAfterSlotSwitch(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        var server = RuneLegendServer.find(player.level().getServer());
        if (server != null) server.guards().active(player);
    }
}
