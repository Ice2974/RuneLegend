package com.runelegend.client.network;

import com.runelegend.api.RuleSnapshot;
import com.runelegend.network.RuneLegendPayloads;
import static com.runelegend.network.RuneLegendPayloads.*;

public final class ClientStateCache {
    public static final ClientStateCache INSTANCE = new ClientStateCache();
    private RuleSnapshot rules;
    private View view;
    private long revision = -1;
    private long serverTick;
    private long localSampleTick;
    private long tick;
    private boolean desynced;

    private ClientStateCache() {}

    public RuleSnapshot rules() { return rules; }
    public View view() { return desynced ? null : view; }
    public long tick() { return tick; }
    public long serverTick() { return serverTick + Math.max(0, tick - localSampleTick); }
    public boolean desynced() { return desynced; }

    public void advanceTick() { tick++; }

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

    private void updateClock(long newRevision, long sample) {
        revision = newRevision;
        serverTick = sample;
        localSampleTick = tick;
    }

    public void clear() {
        rules = null;
        view = null;
        revision = -1;
        serverTick = 0;
        localSampleTick = tick;
        desynced = false;
    }
}
