package com.runelegend.network;

import com.runelegend.token.TokenEngine;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.runelegend.network.RuneLegendPayloads.*;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolStateTest {
    @Test void staminaDeltaDoesNotReplaceEquipment() {
        var token = new TokenEngine.View(Identifier.parse("runelegend:echo"), TokenEngine.ACTIVE, 0, 50, 0);
        var previous = new View(List.of(token), 20000);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PlayerDelta.CODEC.encode(buffer, new PlayerDelta(3, 4, 21, STAMINA, new View(List.of(), 19500)));
            var packet = PlayerDelta.CODEC.decode(buffer);
            var merged = merge(previous, packet.state(), packet.mask());
            assertEquals(new View(List.of(token), 19500), merged);
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test void snapshotRejectsAnOversizedSlotCount() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarLong(1);
            buffer.writeVarLong(10);
            buffer.writeByte(4);
            assertThrows(IllegalArgumentException.class, () -> PlayerSnapshot.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
