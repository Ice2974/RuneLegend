package com.runelegend;

import com.runelegend.combat.GuardDirectionResolver;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuardDirectionTest {
    @Test void onlyHorizontalFrontCounts() {
        assertTrue(GuardDirectionResolver.inFront(0, 0, 1));
        assertTrue(GuardDirectionResolver.inFront(0, 10, 0.01));
        assertFalse(GuardDirectionResolver.inFront(0, 1, 0));
        assertFalse(GuardDirectionResolver.inFront(0, 10, -0.01));
        assertFalse(GuardDirectionResolver.inFront(0, 0, -1));
        assertFalse(GuardDirectionResolver.inFront(0, 0, 0));
        assertFalse(GuardDirectionResolver.inFront(0, Double.NaN, 1));
    }

    @Test void serverYawRotatesTheFront() {
        assertTrue(GuardDirectionResolver.inFront(90, -1, 0));
        assertFalse(GuardDirectionResolver.inFront(90, 1, 0));
        assertFalse(GuardDirectionResolver.inFront(90, 0, 1));
        assertTrue(GuardDirectionResolver.inFront(180, 0, -1));
    }
}
