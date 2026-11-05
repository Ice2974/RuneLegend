package com.runelegend.token;

import net.minecraft.resources.Identifier;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class OwnedEffects {
    record Key(UUID player, Identifier token, String effect) {}
    private final Map<Key, Runnable> cleanup = new HashMap<>();

    void track(Key key, Runnable remove) {
        if (cleanup.putIfAbsent(key, remove) != null) throw new IllegalStateException("Effect already exists: " + key);
    }

    void remove(Key key) {
        var action = cleanup.remove(key);
        if (action != null) action.run();
    }

    void removePlayer(UUID player) {
        for (var key : cleanup.keySet().stream().filter(key -> key.player().equals(player)).toList()) remove(key);
    }

    void clear() {
        for (var key : java.util.List.copyOf(cleanup.keySet())) remove(key);
    }
}
