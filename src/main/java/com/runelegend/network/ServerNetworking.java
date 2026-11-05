package com.runelegend.network;

import com.runelegend.RuneLegendServer;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import static com.runelegend.network.RuneLegendPayloads.*;

public final class ServerNetworking {
    private ServerNetworking() {}

    public static void register() {
        ProtocolHandshake.register();
        ServerPlayConnectionEvents.JOIN.register((listener, sender, minecraft) -> {
            var player = listener.player;
            if (!ServerPlayNetworking.canSend(player, PlayerSnapshot.TYPE) || !ServerPlayNetworking.canSend(player, PlayerDelta.TYPE)
                    || !ServerPlayNetworking.canSend(player, Rules.TYPE) || !ServerPlayNetworking.canSend(player, GuardPose.TYPE)) {
                listener.disconnect(Component.translatableWithFallback("runelegend.network.required", "This server requires the RuneLegend mod."));
                return;
            }
            var server = RuneLegendServer.find(minecraft);
            if (server != null) server.join(player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((listener, minecraft) -> {
            var server = RuneLegendServer.find(minecraft);
            if (server != null) server.leave(listener.player);
        });
        ServerPlayNetworking.registerGlobalReceiver(GuardStart.TYPE, (request, context) -> {
            var server = RuneLegendServer.find(context.server());
            if (server != null && server.isCurrent(context.player())) server.guards().start(context.player(), request.input(), request.rulesRevision(), request.slot());
        });
        ServerPlayNetworking.registerGlobalReceiver(GuardEnd.TYPE, (request, context) -> {
            var server = RuneLegendServer.find(context.server());
            if (server != null && server.isCurrent(context.player())) server.guards().end(context.player(), request.input(), request.released());
        });
        ServerPlayNetworking.registerGlobalReceiver(StateResync.TYPE, (request, context) -> {
            var server = RuneLegendServer.find(context.server());
            if (server != null && server.isCurrent(context.player())) server.sync().resync(context.player());
        });
        EntityTrackingEvents.START_TRACKING.register((tracked, viewer) -> {
            var server = RuneLegendServer.find(viewer.level().getServer());
            if (server != null && tracked instanceof ServerPlayer player && player != viewer) server.sync().tracking(player, viewer, true);
        });
        EntityTrackingEvents.STOP_TRACKING.register((tracked, viewer) -> {
            var server = RuneLegendServer.find(viewer.level().getServer());
            if (server != null && tracked instanceof ServerPlayer player && player != viewer) server.sync().tracking(player, viewer, false);
        });
    }
}
