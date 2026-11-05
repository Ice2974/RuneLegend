package com.runelegend.player;

import com.runelegend.game.GameController;
import com.runelegend.token.TokenRegistry;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlayerTokenService {
    public enum Result { CHANGED, UNCHANGED, RUNNING, READ_ONLY, UNKNOWN, DUPLICATE, FULL, NOT_EQUIPPED }

    private final GameController game;
    private final PlayerTokenSavedData data;
    private final TokenRegistry registry;

    public PlayerTokenService(GameController game, PlayerTokenSavedData data, TokenRegistry registry) {
        this.game = game;
        this.data = data;
        this.registry = registry;
    }

    public PlayerTokenData get(UUID player) { return data.get(player); }

    public Result equip(UUID player, Identifier id) {
        var rejection = rejectWrite();
        if (rejection != null) return rejection;
        if (registry.get(id) == null) return Result.UNKNOWN;
        var old = get(player);
        var slots = new ArrayList<>(old.equipped());
        if (slots.contains(id)) return Result.DUPLICATE;
        if (slots.size() == PlayerTokenData.MAX_SLOTS) return Result.FULL;
        slots.add(id);
        data.set(player, new PlayerTokenData(slots, old.longTerm()));
        return Result.CHANGED;
    }

    public Result unequip(UUID player, Identifier id) {
        var rejection = rejectWrite();
        if (rejection != null) return rejection;
        var old = get(player);
        var slots = new ArrayList<>(old.equipped());
        if (!slots.remove(id)) return Result.NOT_EQUIPPED;
        data.set(player, new PlayerTokenData(slots, old.longTerm()));
        return Result.CHANGED;
    }

    public Result clear(UUID player) {
        var rejection = rejectWrite();
        if (rejection != null) return rejection;
        var old = get(player);
        if (old.equipped().isEmpty()) return Result.UNCHANGED;
        data.set(player, new PlayerTokenData(List.of(), old.longTerm()));
        return Result.CHANGED;
    }

    private Result rejectWrite() {
        if (game.getRules().running()) return Result.RUNNING;
        return data.writable() ? null : Result.READ_ONLY;
    }
}
