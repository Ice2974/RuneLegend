package com.runelegend.player;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.runelegend.RuneLegendMod;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerTokenSavedData extends SavedData {
    private static final int VERSION = 1;
    private static final String NAME = "player_tokens";
    private static final Codec<Integer> VERSION_CODEC = Codec.INT.validate(version -> version == VERSION
            ? DataResult.success(version) : DataResult.error(() -> "Unsupported RuneLegend schema " + version));
    private static final Codec<PlayerTokenSavedData> CODEC = RecordCodecBuilder.<PlayerTokenSavedData>create(instance -> instance.group(
            VERSION_CODEC.fieldOf("schemaVersion").forGetter(data -> VERSION),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, PlayerTokenData.CODEC).fieldOf("players")
                    .forGetter(data -> data.players)
    ).apply(instance, (version, players) -> new PlayerTokenSavedData(players, true)))
            .mapResult(new Codec.ResultFunction<>() {
                @Override
                public <T> DataResult<Pair<PlayerTokenSavedData, T>> apply(DynamicOps<T> ops, T input,
                                                                          DataResult<Pair<PlayerTokenSavedData, T>> decoded) {
                    // Vanilla accepts a partial result; never allow a partial save to overwrite missing records.
                    return decoded.error().<DataResult<Pair<PlayerTokenSavedData, T>>>map(error ->
                            DataResult.error(error.messageSupplier())).orElse(decoded);
                }

                @Override
                public <T> DataResult<T> coApply(DynamicOps<T> ops, PlayerTokenSavedData input, DataResult<T> encoded) {
                    return encoded;
                }
            });
    private static final SavedDataType<PlayerTokenSavedData> TYPE = new SavedDataType<>(
            RuneLegendMod.id(NAME), () -> new PlayerTokenSavedData(Map.of(), true), CODEC, null);

    private final Map<UUID, PlayerTokenData> players;
    private final boolean writable;

    private PlayerTokenSavedData(Map<UUID, PlayerTokenData> players, boolean writable) {
        this.players = new HashMap<>(players);
        this.writable = writable;
    }

    public static PlayerTokenSavedData load(MinecraftServer server) {
        var storage = server.overworld().getDataStorage();
        var root = server.getWorldPath(LevelResource.ROOT);
        var dimension = DimensionType.getStorageFolder(server.overworld().dimension(), root);
        var file = RuneLegendMod.id(NAME + ".dat").resolveAgainst(dimension.resolve("data"));
        if (!Files.notExists(file)) {
            var existing = storage.get(TYPE);
            if (existing != null) return existing;
            RuneLegendMod.LOGGER.error("Cannot read RuneLegend player data {}; equipment writes are disabled", file);
            return new PlayerTokenSavedData(Map.of(), false);
        }
        return storage.computeIfAbsent(TYPE);
    }

    public boolean writable() { return writable; }
    public PlayerTokenData get(UUID uuid) { return players.getOrDefault(uuid, PlayerTokenData.EMPTY); }

    void set(UUID uuid, PlayerTokenData data) {
        if (!writable) throw new IllegalStateException("RuneLegend data is read-only");
        if (data.equipped().isEmpty() && data.longTerm().isEmpty()) players.remove(uuid);
        else players.put(uuid, data);
        setDirty();
    }
}
