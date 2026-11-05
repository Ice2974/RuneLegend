package com.runelegend.network;

import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationPacketListenerImpl;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import static com.runelegend.network.RuneLegendPayloads.*;

final class ProtocolHandshake {
    private static final ConfigurationTask.Type TASK = new ConfigurationTask.Type("runelegend:protocol");
    private static final ConcurrentHashMap<ServerConfigurationPacketListenerImpl, Long> PENDING = new ConcurrentHashMap<>();

    private ProtocolHandshake() {}

    static void register() {
        ServerConfigurationConnectionEvents.CONFIGURE.register((listener, server) -> {
            if (!ServerConfigurationNetworking.canSend(listener, Hello.TYPE)) {
                listener.disconnect(Component.translatableWithFallback("runelegend.network.required", "This server requires the RuneLegend mod."));
                return;
            }
            long nonce = ThreadLocalRandom.current().nextLong();
            PENDING.put(listener, nonce);
            ((FabricServerConfigurationPacketListenerImpl) listener).addTask(new HandshakeTask(nonce));
        });
        ServerConfigurationConnectionEvents.DISCONNECT.register((listener, server) -> PENDING.remove(listener));
        ServerConfigurationNetworking.registerGlobalReceiver(HelloAck.TYPE, (payload, context) -> {
            var listener = context.packetListener();
            var nonce = PENDING.remove(listener);
            if (nonce == null) return;
            if (payload.version() != PROTOCOL || payload.nonce() != nonce) {
                listener.disconnect(Component.translatableWithFallback("runelegend.network.protocol", "RuneLegend protocol mismatch. Install the server's mod version."));
                return;
            }
            ((FabricServerConfigurationPacketListenerImpl) listener).completeTask(TASK);
        });
    }

    private record HandshakeTask(long nonce) implements ConfigurationTask {
        @Override public void start(Consumer<Packet<?>> send) {
            send.accept(ServerConfigurationNetworking.createClientboundPacket(new Hello(PROTOCOL, nonce)));
        }
        @Override public Type type() { return TASK; }
    }
}
