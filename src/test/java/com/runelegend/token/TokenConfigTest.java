package com.runelegend.token;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenConfigTest {
    @Test void defaultJsonContainsEveryBuiltInToken() {
        var root = TokenConfig.DEFAULT.toJson();
        var tokens = root.getAsJsonObject("tokens");
        assertEquals(4, tokens.size());
        assertEquals(2, tokens.getAsJsonObject("runelegend:sharpened_tip").size());
        assertEquals(2, tokens.getAsJsonObject("runelegend:soul_siphon").size());
        assertEquals(5, tokens.getAsJsonObject("runelegend:echo").size());
        assertEquals(3, tokens.getAsJsonObject("runelegend:immediate_counter").size());
    }

    @Test void codecRoundTripsEverySetting() {
        var config = new TokenConfig(
                new TokenConfig.SharpenedTipSettings(0.25F, 4.0F),
                new TokenConfig.SoulSiphonSettings(0.75F, 3.0F),
                new TokenConfig.EchoSettings(0.33F, 6.5, 4.0F, 80, 10),
                new TokenConfig.ImmediateCounterSettings(5.0F, 20, 30));
        var encoded = TokenConfig.DEFAULT.toJson();
        // Encode each section through its typed codec to prove all fields survive a round trip.
        var tokens = encoded.getAsJsonObject("tokens");
        tokens.add("runelegend:sharpened_tip", encode(TokenConfig.SharpenedTipSettings.CODEC, config.sharpenedTip()));
        tokens.add("runelegend:soul_siphon", encode(TokenConfig.SoulSiphonSettings.CODEC, config.soulSiphon()));
        tokens.add("runelegend:echo", encode(TokenConfig.EchoSettings.CODEC, config.echo()));
        tokens.add("runelegend:immediate_counter", encode(TokenConfig.ImmediateCounterSettings.CODEC, config.immediateCounter()));
        assertEquals(config, TokenConfig.fromJson(encoded));
    }

    @Test void boundedSettingsRejectInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.SharpenedTipSettings(1.01F, 1));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.SharpenedTipSettings(0.5F, -1));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.SoulSiphonSettings(-0.1F, 1));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.EchoSettings(0.5F, 0, 1, 20, 20));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.EchoSettings(0.5F, 5, -1, 20, 20));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.EchoSettings(0.5F, 5, 1, -1, 20));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.EchoSettings(0.5F, 5, 1, 20, 0));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.ImmediateCounterSettings(-1, 20, 20));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.ImmediateCounterSettings(1, -1, 20));
        assertThrows(IllegalArgumentException.class, () -> new TokenConfig.ImmediateCounterSettings(1, 20, -1));
    }

    @Test void invalidRootUsesAllDefaultsAndInvalidTokenUsesOnlyThatDefault() {
        assertEquals(TokenConfig.DEFAULT, TokenConfig.fromJson(new JsonObject()));

        var root = TokenConfig.DEFAULT.toJson();
        root.addProperty("schemaVersion", 99);
        assertEquals(TokenConfig.DEFAULT, TokenConfig.fromJson(root));

        root.addProperty("schemaVersion", 1);
        root.getAsJsonObject("tokens").add("runelegend:sharpened_tip", JsonParser.parseString("{\"chance\":2}"));
        var parsed = TokenConfig.fromJson(root);
        assertEquals(TokenConfig.SharpenedTipSettings.DEFAULT, parsed.sharpenedTip());
        assertEquals(TokenConfig.EchoSettings.DEFAULT, parsed.echo());
    }

    @Test void knownTokenMayOmitFields() {
        var root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.add("tokens", JsonParser.parseString("{\"runelegend:sharpened_tip\":{\"chance\":1}}").getAsJsonObject());
        var parsed = TokenConfig.fromJson(root);
        assertEquals(new TokenConfig.SharpenedTipSettings(1, 2), parsed.sharpenedTip());
    }

    private static <T> JsonElement encode(com.mojang.serialization.Codec<T> codec, T value) {
        return codec.encodeStart(JsonOps.INSTANCE, value).result().orElseThrow();
    }
}
