package com.runelegend.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.runelegend.combat.GuardItems;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.ArrayList;
import java.util.function.Consumer;

@Mixin(ServerEntity.class)
abstract class ServerEntityPairingMixin {
    @Shadow @Final private Entity entity;

    @WrapMethod(method = "sendPairingData(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V")
    private void runelegend$equipBeforeUseFlags(ServerPlayer viewer, Consumer<Packet<ClientGamePacketListener>> send,
                                              Operation<Void> original) {
        if (!(entity instanceof ServerPlayer player) || !GuardItems.using(player)) {
            original.call(viewer, send);
            return;
        }
        // A newly tracking client reads the active hand stack when it receives the use flag.
        // Deliver the existing equipment packet first so a mid-use spawn can enter the native pose.
        var pending = new ArrayList<Packet<ClientGamePacketListener>>(1);
        Consumer<Packet<ClientGamePacketListener>> pair = packet -> {
            if (packet instanceof ClientboundSetEntityDataPacket) {
                pending.add(packet);
            } else {
                send.accept(packet);
                if (packet instanceof ClientboundSetEquipmentPacket) {
                    pending.forEach(send);
                    pending.clear();
                }
            }
        };
        original.call(viewer, pair);
        pending.forEach(send);
    }
}
