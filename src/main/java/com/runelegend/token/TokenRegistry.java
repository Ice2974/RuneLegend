package com.runelegend.token;

import com.runelegend.RuneLegendMod;
import com.runelegend.token.effect.*;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.runelegend.token.TokenEvent.Kind.*;

public final class TokenRegistry {
    private final Map<Identifier, TokenDefinition> definitions;

    private TokenRegistry(Map<Identifier, TokenDefinition> definitions) {
        this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }

    public static TokenRegistry builtIn() {
        return new Builder()
                .add("sharpened_tip", new SharpenedTipEffect(), MELEE)
                .add("wither_slayer", new WitherSlayerEffect(), HURT, MELEE)
                .add("soul_siphon", new SoulSiphonEffect(), KILL)
                .add("echo", new EchoEffect(), KILL)
                .add("immediate_counter", new ImmediateCounterEffect(), GUARD_SUCCESS, MELEE)
                .build();
    }

    public TokenDefinition get(Identifier id) { return definitions.get(id); }
    public Collection<TokenDefinition> all() { return definitions.values(); }

    public static final class Builder {
        private final Map<Identifier, TokenDefinition> entries = new LinkedHashMap<>();
        private boolean built;

        public Builder register(TokenDefinition definition) {
            if (built) throw new IllegalStateException("Token registry is frozen");
            if (entries.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalArgumentException("Duplicate token: " + definition.id());
            }
            return this;
        }

        private Builder add(String id, TokenEffectHandler effect, TokenEvent.Kind... events) {
            return register(new TokenDefinition(RuneLegendMod.id(id), Set.of(events), effect));
        }

        public TokenRegistry build() {
            if (built) throw new IllegalStateException("Token registry is frozen");
            built = true;
            return new TokenRegistry(entries);
        }
    }
}
