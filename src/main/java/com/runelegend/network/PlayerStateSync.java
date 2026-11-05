package com.runelegend.network;

import com.runelegend.RuneLegendServer;
import com.runelegend.combat.StaminaManager;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static com.runelegend.network.RuneLegendPayloads.*;

public final class PlayerStateSync {
    private static final class Peer {
        long revision;
        long staminaSent;
        long resyncAt = -20;
        View last;
    }

    private final RuneLegendServer server;
    private final Map<UUID, Peer> peers = new HashMap<>();
    private long poseRevision;

    public PlayerStateSync(RuneLegendServer server) { this.server = server; }

    public void join(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, PlayerSnapshot.TYPE)) return;
        peers.put(player.getUUID(), new Peer());
        rules(player);
        snapshot(player);
    }

    public void leave(ServerPlayer player) { peers.remove(player.getUUID()); }

    public void rules(ServerPlayer player) {
        if (peers.containsKey(player.getUUID())) ServerPlayNetworking.send(player, new Rules(server.game().getRules()));
    }

    public void resync(ServerPlayer player) {
        var peer = peers.get(player.getUUID());
        if (peer == null || server.tick() - peer.resyncAt < 20) return;
        peer.resyncAt = server.tick();
        rules(player);
        snapshot(player);
    }

    private void snapshot(ServerPlayer player) {
        var peer = peers.get(player.getUUID());
        if (peer == null) return;
        peer.last = current(player);
        peer.staminaSent = server.tick();
        ServerPlayNetworking.send(player, new PlayerSnapshot(++peer.revision, server.tick(), peer.last));
    }

    public void update(ServerPlayer player) {
        var peer = peers.get(player.getUUID());
        if (peer == null || peer.last == null) return;
        var before = peer.last;
        var after = current(player);
        int mask = 0;
        if (!before.tokens().equals(after.tokens())) mask |= TOKENS;
        if (before.guarding() != after.guarding() || before.input() != after.input()) mask |= GUARD;
        if (before.stamina() != after.stamina() && (server.tick() - peer.staminaSent >= 4
                || before.guarding() != after.guarding() || after.stamina() == 0 || after.stamina() == StaminaManager.MAX)) {
            mask |= STAMINA;
            peer.staminaSent = server.tick();
        }
        if (mask == 0) return;
        long base = peer.revision;
        peer.last = merge(before, after, mask);
        ServerPlayNetworking.send(player, new PlayerDelta(base, ++peer.revision, server.tick(), mask, after));
    }

    private View current(ServerPlayer player) {
        var guard = server.guards().get(player);
        return new View(server.tokens().views(player), guard == null ? StaminaManager.MAX : guard.stamina(),
                guard != null && guard.guarding(), guard == null ? -1 : guard.lastInput());
    }

    public void poseChanged(ServerPlayer player) {
        long revision = ++poseRevision;
        var guard = server.guards().get(player);
        var pose = new GuardPose(player.getId(), player.getUUID(), revision, guard != null && guard.guarding());
        for (var viewer : PlayerLookup.tracking(player)) if (viewer != player) sendPose(viewer, pose);
        update(player);
    }

    public void tracking(ServerPlayer target, ServerPlayer viewer, boolean tracking) {
        var runtime = server.guards().get(target);
        sendPose(viewer, new GuardPose(target.getId(), target.getUUID(), ++poseRevision, tracking && runtime != null && runtime.guarding()));
    }

    private void sendPose(ServerPlayer viewer, GuardPose pose) {
        if (ServerPlayNetworking.canSend(viewer, GuardPose.TYPE)) ServerPlayNetworking.send(viewer, pose);
    }
}
