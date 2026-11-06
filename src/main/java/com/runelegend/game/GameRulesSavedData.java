package com.runelegend.game;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.runelegend.RuneLegendMod;
import com.runelegend.api.GameState;
import com.runelegend.api.RuleSnapshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.nio.file.Files;
import java.nio.file.Path;

/** Persists the map-level game gate so integrated-server saves keep an active round. */
public final class GameRulesSavedData extends SavedData {
    private static final int VERSION = 1;
    private static final Codec<Integer> VERSION_CODEC = Codec.INT.validate(version -> version == VERSION
            ? DataResult.success(version) : DataResult.error(() -> "Unsupported RuneLegend game rules schema " + version));
    private static final Codec<Long> COUNTER_CODEC = Codec.LONG.validate(value -> value >= 0
            ? DataResult.success(value) : DataResult.error(() -> "Negative RuneLegend game counter " + value));
    private static final Codec<GameState> STATE_CODEC = Codec.STRING.comapFlatMap(value -> {
        try {
            return DataResult.success(GameState.valueOf(value));
        } catch (IllegalArgumentException error) {
            return DataResult.error(() -> "Unknown RuneLegend game state " + value);
        }
    }, Enum::name);
    static final Codec<GameRulesSavedData> CODEC = RecordCodecBuilder.<GameRulesSavedData>create(instance -> instance.group(
            VERSION_CODEC.fieldOf("schemaVersion").forGetter(data -> VERSION),
            STATE_CODEC.fieldOf("state").forGetter(data -> data.rules.state()),
            COUNTER_CODEC.fieldOf("round").forGetter(data -> data.rules.round()),
            COUNTER_CODEC.fieldOf("revision").forGetter(data -> data.rules.revision())
    ).apply(instance, GameRulesSavedData::new)).mapResult(new Codec.ResultFunction<>() {
        @Override
        public <T> DataResult<Pair<GameRulesSavedData, T>> apply(DynamicOps<T> ops, T input,
                                                               DataResult<Pair<GameRulesSavedData, T>> decoded) {
            // SavedDataStorage accepts partial results; only a fully valid record is safe to load.
            return decoded.error().<DataResult<Pair<GameRulesSavedData, T>>>map(error ->
                    DataResult.error(error.messageSupplier())).orElse(decoded);
        }

        @Override
        public <T> DataResult<T> coApply(DynamicOps<T> ops, GameRulesSavedData input, DataResult<T> encoded) {
            return encoded;
        }
    });
    private static final SavedDataType<GameRulesSavedData> TYPE = new SavedDataType<>(
            RuneLegendMod.id("game_rules"), GameRulesSavedData::new, CODEC, null);

    private RuleSnapshot rules = new RuleSnapshot(GameState.WAITING, 0, 0);

    public static GameRulesSavedData load(MinecraftServer server) {
        var dimension = DimensionType.getStorageFolder(server.overworld().dimension(), server.getWorldPath(LevelResource.ROOT));
        return load(server.overworld().getDataStorage(), dimension.resolve("data"));
    }

    static GameRulesSavedData load(SavedDataStorage storage, Path directory) {
        var file = TYPE.id().withSuffix(".dat").resolveAgainst(directory);
        if (Files.notExists(file)) return storage.computeIfAbsent(TYPE);
        var existing = storage.get(TYPE);
        if (existing != null) return existing;
        // Never put a default, dirty record in the storage cache after a failed read.
        throw new IllegalStateException("Cannot read RuneLegend game rules " + file
                + "; startup aborted to preserve the existing save. Restore or repair the file before restarting.");
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
