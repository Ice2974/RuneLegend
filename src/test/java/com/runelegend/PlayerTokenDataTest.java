package com.runelegend;

import com.mojang.serialization.JsonOps;
import com.runelegend.player.PlayerTokenData;
import net.minecraft.resources.Identifier;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerTokenDataTest {
    @Test void unknownIdentifiersRoundTripInSlotOrder() {
        var data = new PlayerTokenData(List.of(Identifier.parse("runelegend:echo"),
                Identifier.parse("removed:token")), Map.of());
        var encoded = PlayerTokenData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        assertEquals(data, PlayerTokenData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test void codecRejectsDuplicateAndOversizedSavedSlots() {
        for (String data : List.of(
                "{\"equipped\":[\"a:a\",\"a:a\"]}",
                "{\"equipped\":[\"a:a\",\"a:b\",\"a:c\",\"a:d\"]}")) {
            assertTrue(PlayerTokenData.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(data)).isError());
        }
    }
}
