package com.runelegend.game;

import com.runelegend.api.GameState;
import com.runelegend.api.RuleSnapshot;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class GameRulesSavedDataTest {
    @Test void codecKeepsStateRoundAndRevision() {
        var data = new GameRulesSavedData(1, GameState.RUNNING, 8, 11);
        var encoded = GameRulesSavedData.CODEC.encodeStart(NbtOps.INSTANCE, data)
                .result().orElseThrow();
        var decoded = GameRulesSavedData.CODEC.parse(NbtOps.INSTANCE, encoded)
                .result().orElseThrow();
        assertEquals(new RuleSnapshot(GameState.RUNNING, 8, 11), decoded.rules());
    }

    @Test void updateMarksTheStoredRulesDirtySemantically() {
        var data = new GameRulesSavedData(1, GameState.WAITING, 3, 4);
        data.update(new RuleSnapshot(GameState.RUNNING, 4, 5));
        assertEquals(new RuleSnapshot(GameState.RUNNING, 4, 5), data.rules());
        assertTrue(data.isDirty());
    }

    @Test void malformedRulesNeverProduceAPartialDefault() {
        var valid = (CompoundTag) GameRulesSavedData.CODEC.encodeStart(NbtOps.INSTANCE,
                new GameRulesSavedData(1, GameState.RUNNING, 8, 11)).getOrThrow();
        List<Consumer<CompoundTag>> corruptions = List.of(
                tag -> tag.putInt("schemaVersion", 2),
                tag -> tag.putString("state", "UNKNOWN"),
                tag -> tag.putLong("round", -1),
                tag -> tag.putLong("revision", -1),
                tag -> tag.remove("state"),
                tag -> tag.putString("round", "eight"));
        for (var corrupt : corruptions) {
            var tag = valid.copy();
            corrupt.accept(tag);
            var decoded = GameRulesSavedData.CODEC.parse(NbtOps.INSTANCE, tag);
            assertTrue(decoded.isError(), tag::toString);
            assertTrue(decoded.resultOrPartial(ignored -> {}).isEmpty(),
                    "SavedDataStorage must not accept a partial record: " + tag);
        }
    }
}

