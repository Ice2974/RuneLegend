package com.runelegend.client.network;

import com.runelegend.api.RuleSnapshot;
import com.runelegend.network.RuneLegendPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import static com.runelegend.network.RuneLegendPayloads.*;

public final class ClientStateCache {
    public static final ClientStateCache INSTANCE = new ClientStateCache();
    private record Pose(ClientLevel level, GuardPose packet, long receivedAt) {}
    private final Map<Integer, Pose> poses = new HashMap<>();
    private RuleSnapshot rules;
    private View view;
    private long revision = -1;
    private long serverTick;
    private long localSampleTick;
    private long tick;
    private boolean desynced;
    private ClientLevel level;

    private ClientStateCache() {}

    public RuleSnapshot rules() { return rules; }
    public View view() { return desynced ? null : view; }
    public long tick() { return tick; }
    public long serverTick() { return serverTick + Math.max(0, tick - localSampleTick); }
    public boolean desynced() { return desynced; }

    public void tick(ClientLevel level) {
        tick++;
        if (this.level != level) {
            this.level = level;
            poses.entrySet().removeIf(entry -> entry.getValue().level != level);
        } else if (level != null && tick % 20 == 0) {
            poses.entrySet().removeIf(entry -> tick - entry.getValue().receivedAt > 20 && level.getEntity(entry.getKey()) == null);
        }
    }

    public void apply(Rules packet) {
        if (rules == null || packet.rules().revision() >= rules.revision()) rules = packet.rules();
    }

    public void apply(PlayerSnapshot packet) {
        if (packet.revision() < revision) return;
        view = packet.state();
        desynced = false;
        updateClock(packet.revision(), packet.tick());
    }

    public void apply(PlayerDelta packet) {
        if (packet.revision() <= revision) return;
        if (view == null || packet.base() != revision) {
            desynced = true;
            return;
        }
        view = RuneLegendPayloads.merge(view, packet.state(), packet.mask());
        updateClock(packet.revision(), packet.tick());
    }

    public void apply(GuardPose packet) {
        var level = Minecraft.getInstance().level;
        var old = poses.get(packet.entityId());
        if (old != null && old.level == level && packet.revision() < old.packet.revision()) return;
        if (level != null) poses.put(packet.entityId(), new Pose(level, packet, tick));
    }

    public boolean pose(Entity entity) {
        var pose = poses.get(entity.getId());
        return rules != null && rules.canGuard() && pose != null && pose.packet.guarding() && pose.level == entity.level()
                && pose.packet.uuid().equals(entity.getUUID());
    }

    private void updateClock(long newRevision, long sample) {
        revision = newRevision;
        serverTick = sample;
        localSampleTick = tick;
    }

    public void clear() {
        poses.clear();
        rules = null;
        view = null;
        revision = -1;
        serverTick = 0;
        localSampleTick = tick;
        desynced = false;
        level = null;
    }
}
