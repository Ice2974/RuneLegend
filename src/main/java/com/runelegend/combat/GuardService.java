package com.runelegend.combat;

import com.runelegend.RuneLegendMod;
import com.runelegend.game.GameController;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

public final class GuardService {
    private static final AttributeModifier SLOW = new AttributeModifier(RuneLegendMod.id("guard_speed"),
            GuardPolicy.SPEED_MODIFIER, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    private final Map<UUID, PlayerCombatRuntime> players = new HashMap<>();
    private final Map<UUID, ServerPlayer> activePlayers = new HashMap<>();
    private final GameController game;
    private final LongSupplier clock;
    private final Consumer<ServerPlayer> poseChanged;

    public GuardService(GameController game, LongSupplier clock, Consumer<ServerPlayer> poseChanged) {
        this.game = game;
        this.clock = clock;
        this.poseChanged = poseChanged;
    }

    public void join(ServerPlayer player) {
        var oldPlayer = activePlayers.get(player.getUUID());
        if (oldPlayer != null) forceStop(oldPlayer);
        players.put(player.getUUID(), new PlayerCombatRuntime());
    }
    public PlayerCombatRuntime get(ServerPlayer player) { return players.get(player.getUUID()); }

    public void leave(ServerPlayer player) {
        forceStop(player);
        players.remove(player.getUUID());
    }

    public boolean start(ServerPlayer player, long sequence, long revision, int slot) {
        var runtime = get(player);
        if (!acceptInput(runtime, sequence)) return false;
        var rules = game.getRules();
        long now = clock.getAsLong();
        boolean allowed = runtime.lastStartTick != now && !runtime.releaseRequired && !runtime.guarding
                && rules.canGuard() && revision == rules.revision() && slot == player.getInventory().getSelectedSlot()
                && runtime.stamina >= StaminaManager.MIN_TO_START && eligible(player);
        runtime.lastStartTick = now;
        runtime.releaseRequired = true;
        if (allowed) {
            runtime.guarding = true;
            activePlayers.put(player.getUUID(), player);
            runtime.slot = slot;
            runtime.weapon = player.getMainHandItem();
            runtime.level = player.level();
            runtime.startedAt = now;
            player.setSprinting(false);
            var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) {
                speed.removeModifier(SLOW.id());
                speed.addTransientModifier(SLOW);
            }
            poseChanged.accept(player);
        }
        return true;
    }

    public boolean end(ServerPlayer player, long sequence, boolean released) {
        var runtime = get(player);
        if (!acceptInput(runtime, sequence)) return false;
        stop(player, runtime);
        runtime.releaseRequired = !released;
        return true;
    }

    private boolean acceptInput(PlayerCombatRuntime runtime, long sequence) {
        if (runtime == null || sequence < 0 || sequence <= runtime.lastInput) return false;
        runtime.lastInput = sequence;
        return true;
    }

    public boolean active(ServerPlayer player) {
        var runtime = get(player);
        if (runtime == null || !runtime.guarding) return false;
        if (!game.getRules().canGuard() || !eligible(player) || player.level() != runtime.level
                || player.getInventory().getSelectedSlot() != runtime.slot || player.getMainHandItem() != runtime.weapon) {
            stop(player, runtime);
            return false;
        }
        return true;
    }

    private boolean eligible(ServerPlayer player) {
        return player.isAlive() && player.connection != null && player.connection.hasClientLoaded() && !player.isSpectator() && !player.isPassenger() && !player.isSleeping()
                && !player.isUsingItem() && GuardPolicy.sword(player.getMainHandItem());
    }

    public void forceStop(ServerPlayer player) {
        var runtime = get(player);
        if (runtime != null) stop(player, runtime);
    }

    public void forceStopAll() {
        java.util.List.copyOf(activePlayers.values()).forEach(this::forceStop);
    }

    public void resetRound(ServerPlayer player) {
        var runtime = get(player);
        if (runtime == null) return;
        stop(player, runtime);
        runtime.stamina = StaminaManager.MAX;
    }

    public void tick(ServerPlayer player) {
        var runtime = get(player);
        if (runtime == null) return;
        long now = clock.getAsLong();
        if (active(player)) {
            if (player.isSprinting()) player.setSprinting(false);
            if (now > runtime.startedAt) {
                runtime.stamina = StaminaManager.drain(runtime.stamina);
                if (runtime.stamina == 0) stop(player, runtime);
            }
        } else if (player.isAlive() && runtime.stamina < StaminaManager.MAX) {
            runtime.stamina = StaminaManager.recover(runtime.stamina, now, runtime.stoppedAt);
        }
    }

    private void stop(ServerPlayer player, PlayerCombatRuntime runtime) {
        if (!runtime.guarding) return;
        runtime.guarding = false;
        activePlayers.remove(player.getUUID());
        runtime.slot = -1;
        runtime.weapon = ItemStack.EMPTY;
        runtime.level = null;
        runtime.stoppedAt = clock.getAsLong();
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SLOW.id());
        poseChanged.accept(player);
    }
}
