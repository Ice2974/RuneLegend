package com.runelegend.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.runelegend.RuneLegendMod;
import com.runelegend.api.GameState;
import com.runelegend.api.RuleSnapshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Persists the map-level game gate so integrated-server saves keep an active round. */
public final class GameRulesSavedData extends SavedData {
    private static final int VERSION = 1;
    private static final Codec<GameState> STATE_CODEC = Codec.STRING.xmap(GameState::valueOf, Enum::name);
    static final Codec<GameRulesSavedData> CODEC = RecordCodecBuilder.<GameRulesSavedData>create(instance -> instance.group(
            Codec.INT.fieldOf("schemaVersion").forGetter(data -> VERSION),
            STATE_CODEC.fieldOf("state").forGetter(data -> data.rules.state()),
            Codec.LONG.fieldOf("round").forGetter(data -> data.rules.round()),
            Codec.LONG.fieldOf("revision").forGetter(data -> data.rules.revision())
    ).apply(instance, GameRulesSavedData::new));
    private static final SavedDataType<GameRulesSavedData> TYPE = new SavedDataType<>(
            RuneLegendMod.id("game_rules"), GameRulesSavedData::new, CODEC, null);

    private RuleSnapshot rules = new RuleSnapshot(GameState.WAITING, 0, 0);

    public static GameRulesSavedData load(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    GameRulesSavedData() {}

    GameRulesSavedData(int version, GameState state, long round, long revision) {
        if (version != VERSION) throw new IllegalArgumentException("Unsupported RuneLegend game rules schema " + version);
        this.rules = new RuleSnapshot(state, round, revision);
    }

    public RuleSnapshot rules() { return rules; }

    public void update(RuleSnapshot rules) {
        this.rules = rules;
        setDirty();
    }
}
