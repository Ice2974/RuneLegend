package com.runelegend;

import com.runelegend.combat.StaminaManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StaminaManagerTest {
    @Test void exactDrainAndRecoveryBoundaries() {
        int units = StaminaManager.MAX;
        for (int i = 0; i < 159; i++) units = StaminaManager.drain(units);
        assertEquals(125, units);
        assertEquals(0, StaminaManager.drain(units));
        units = 0;
        for (int t = 1; t <= 29; t++) units = StaminaManager.recover(units, 160 + t, 160);
        assertEquals(1900, units);
        assertEquals(StaminaManager.MIN_TO_START, StaminaManager.recover(units, 190, 160));
        assertEquals(StaminaManager.MAX, StaminaManager.recover(StaminaManager.MAX - 1, 191, 160));
    }
}
