package com.runelegend.combat;

import net.minecraft.server.level.ServerPlayer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class GuardStaminaService {
    private final Map<UUID, PlayerCombatRuntime> players = new HashMap<>();
    private final LongSupplier clock;

    public GuardStaminaService(LongSupplier clock) { this.clock = clock; }

    public void join(ServerPlayer player) { players.put(player.getUUID(), new PlayerCombatRuntime()); }

    public void leave(ServerPlayer player) { players.remove(player.getUUID()); }

    public int stamina(ServerPlayer player) {
        var runtime = players.get(player.getUUID());
        return runtime == null ? StaminaManager.MAX : runtime.stamina;
    }

    public boolean tryStart(ServerPlayer player) {
        var runtime = players.get(player.getUUID());
        if (runtime == null || !player.isAlive() || player.isSpectator() || runtime.releaseRequired) return false;
        runtime.releaseRequired = true;
        if (!StaminaManager.canStart(runtime.stamina)) return false;
        runtime.startedAt = clock.getAsLong();
        return true;
    }

    public void released(ServerPlayer player) {
        var runtime = players.get(player.getUUID());
        if (runtime != null) runtime.releaseRequired = false;
    }

    public void stopped(ServerPlayer player) {
        var runtime = players.get(player.getUUID());
        if (runtime != null && runtime.startedAt != PlayerCombatRuntime.NOT_USING) {
            runtime.startedAt = PlayerCombatRuntime.NOT_USING;
            runtime.stoppedAt = clock.getAsLong();
        }
    }

    public void resetRound(ServerPlayer player) {
        var runtime = players.get(player.getUUID());
        if (runtime != null) runtime.stamina = StaminaManager.MAX;
    }

    // A command or another mod can replace the active stack between vanilla entity ticks.
    public void validateUse(ServerPlayer player) {
        var runtime = players.get(player.getUUID());
        if (runtime == null) return;
        boolean using = GuardItems.using(player);
        if (using && (runtime.startedAt == PlayerCombatRuntime.NOT_USING || !player.isAlive() || player.isSpectator()
                || player.getUseItem() != player.getItemInHand(player.getUsedItemHand()))) {
            player.stopUsingItem();
        } else if (!using) {
            stopped(player);
        }
    }

    public void tick(ServerPlayer player) {
        var runtime = players.get(player.getUUID());
        if (runtime == null) return;
        validateUse(player);
        if (GuardItems.using(player)) {
            runtime.stamina = StaminaManager.drain(runtime.stamina);
            if (runtime.stamina == 0) player.stopUsingItem();
        } else if (player.isAlive() && runtime.stamina < StaminaManager.MAX) {
            runtime.stamina = StaminaManager.recover(runtime.stamina, clock.getAsLong(), runtime.stoppedAt);
        }
    }
}
