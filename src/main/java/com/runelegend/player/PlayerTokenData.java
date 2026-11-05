package com.runelegend.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Ordered slots are independent of which token definitions are installed. */
public record PlayerTokenData(List<Identifier> equipped, Map<Identifier, CompoundTag> longTerm) {
    public static final int MAX_SLOTS = 3;
    public static final PlayerTokenData EMPTY = new PlayerTokenData(List.of(), Map.of());
    private static final Codec<List<Identifier>> SLOTS = Identifier.CODEC.listOf(0, MAX_SLOTS)
            .validate(ids -> new HashSet<>(ids).size() == ids.size()
                    ? DataResult.success(ids) : DataResult.error(() -> "Duplicate RuneLegend token IDs"));
    public static final Codec<PlayerTokenData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SLOTS.fieldOf("equipped").forGetter(PlayerTokenData::equipped),
            Codec.unboundedMap(Identifier.CODEC, CompoundTag.CODEC).optionalFieldOf("longTerm", Map.of())
                    .forGetter(PlayerTokenData::longTerm)
    ).apply(instance, PlayerTokenData::new));

    public PlayerTokenData {
        equipped = List.copyOf(equipped);
        if (equipped.size() > MAX_SLOTS || new HashSet<>(equipped).size() != equipped.size()) {
            throw new IllegalArgumentException("Invalid RuneLegend slots");
        }
        longTerm = copyTags(longTerm);
    }

    @Override public Map<Identifier, CompoundTag> longTerm() { return copyTags(longTerm); }

    private static Map<Identifier, CompoundTag> copyTags(Map<Identifier, CompoundTag> source) {
        return source.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> entry.getValue().copy()));
    }
}
