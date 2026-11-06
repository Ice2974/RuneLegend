package com.runelegend.game;

import com.runelegend.api.GameState;
import com.runelegend.api.RuleSnapshot;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

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
}

