package com.runelegend;

import com.runelegend.api.GameState;
import com.runelegend.api.RuleSnapshot;
import com.runelegend.game.GameController;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameControllerTest {
    @Test void onlyRealTransitionsReviseTheRulesAndOnlyStartsAdvanceTheRound() {
        var transitions = new ArrayList<RuleSnapshot>();
        var game = new GameController(() -> {}, (old, current) -> transitions.add(current));
        assertEquals(GameState.WAITING, game.getRules().state());
        assertFalse(game.stopGame());
        assertTrue(game.startGame());
        assertTrue(game.getRules().running());
        assertFalse(game.startGame());
        assertEquals(1, game.getRules().round());
        assertTrue(game.stopGame());
        assertFalse(game.stopGame());
        assertEquals(2, transitions.size());
        assertEquals(2, game.getRules().revision());
        assertTrue(game.startGame());
        assertEquals(2, game.getRules().round());
        assertEquals(3, game.getRules().revision());
    }

    @Test void restoredRulesContinueFromTheirSavedRoundAndOnlyChangesPersist() {
        var transitions = new ArrayList<RuleSnapshot>();
        var persisted = new ArrayList<RuleSnapshot>();
        var game = new GameController(() -> {}, (old, current) -> transitions.add(current),
                new RuleSnapshot(GameState.RUNNING, 7, 9), persisted::add);
        assertEquals(new RuleSnapshot(GameState.RUNNING, 7, 9), game.getRules());
        assertFalse(game.startGame());
        assertTrue(persisted.isEmpty());
        assertTrue(game.stopGame());
        assertEquals(new RuleSnapshot(GameState.WAITING, 7, 10), game.getRules());
        assertTrue(game.startGame());
        assertEquals(List.of(new RuleSnapshot(GameState.WAITING, 7, 10),
                new RuleSnapshot(GameState.RUNNING, 8, 11)), persisted);
        assertEquals(2, transitions.size());
    }
}
