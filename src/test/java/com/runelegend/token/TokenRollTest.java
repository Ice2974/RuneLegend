package com.runelegend.token;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokenRollTest {
    @Test void probabilityThresholdIsExclusive() {
        assertTrue(TokenEngine.roll(0.11, () -> 0.109999));
        assertFalse(TokenEngine.roll(0.11, () -> 0.11));
        assertTrue(TokenEngine.roll(0.09, () -> 0.089999));
        assertFalse(TokenEngine.roll(0.50, () -> 0.50));
    }
}
