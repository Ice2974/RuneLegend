package com.runelegend;

import com.runelegend.api.GameState;
import com.runelegend.api.RuleSnapshot;
import com.runelegend.game.GameController;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameControllerTest {
    @Test void guardNeedsBothGatesAndNoOpsDoNotStartANewRound() {
        var transitions = new ArrayList<RuleSnapshot>();
        var game = new GameController(() -> {}, (old, current) -> transitions.add(current));
        assertEquals(GameState.WAITING, game.getRules().state());
        assertTrue(game.setGuardEnabled(true));
        assertFalse(game.getRules().canGuard());
        assertTrue(game.startGame());
        assertTrue(game.getRules().canGuard());
        assertFalse(game.startGame());
        assertEquals(1, game.getRules().round());
        assertTrue(game.setGuardEnabled(false));
        assertTrue(game.getRules().running());
        assertFalse(game.getRules().canGuard());
        assertTrue(game.stopGame());
        assertFalse(game.stopGame());
        assertEquals(4, transitions.size());
        assertTrue(game.startGame());
        assertEquals(2, game.getRules().round());
    }
}
