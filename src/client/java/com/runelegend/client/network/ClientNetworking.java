package com.runelegend.client.network;

import com.runelegend.client.input.GuardInputController;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import static com.runelegend.network.RuneLegendPayloads.*;

public final class ClientNetworking {
    private ClientNetworking() {}

    public static void register() {
        var cache = ClientStateCache.INSTANCE;
        ClientConfigurationNetworking.registerGlobalReceiver(Hello.TYPE,
                (packet, context) -> context.responseSender().sendPacket(new HelloAck(PROTOCOL, packet.nonce())));
        ClientPlayConnectionEvents.INIT.register((listener, minecraft) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((listener, minecraft) -> reset());
        ClientPlayNetworking.registerGlobalReceiver(Rules.TYPE, (packet, context) -> cache.apply(packet));
        ClientPlayNetworking.registerGlobalReceiver(PlayerSnapshot.TYPE, (packet, context) -> cache.apply(packet));
        ClientPlayNetworking.registerGlobalReceiver(PlayerDelta.TYPE, (packet, context) -> cache.apply(packet));
        ClientPlayNetworking.registerGlobalReceiver(GuardPose.TYPE, (packet, context) -> cache.apply(packet));
    }

    private static void reset() {
        ClientStateCache.INSTANCE.clear();
        GuardInputController.INSTANCE.clear();
    }
}
