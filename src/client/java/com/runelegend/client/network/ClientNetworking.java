package com.runelegend.client.network;

import com.runelegend.client.input.GuardInputController;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import static com.runelegend.network.RuneLegendPayloads.*;

public final class ClientNetworking {
    private static long resyncAt;

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
    }

    public static boolean active() { return ClientStateCache.INSTANCE.rules() != null; }

    public static void tick() {
        var cache = ClientStateCache.INSTANCE;
        cache.advanceTick();
        if (cache.desynced() && cache.tick() >= resyncAt && ClientPlayNetworking.canSend(StateResync.TYPE)) {
            resyncAt = cache.tick() + 20;
            ClientPlayNetworking.send(new StateResync());
        }
    }

    private static void reset() {
        resyncAt = 0;
        ClientStateCache.INSTANCE.clear();
        GuardInputController.INSTANCE.clear();
    }
}
