package com.runelegend;

import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Retains readable English in a dedicated console or a client without the language resource. */
public final class RuneLegendText {
    private static final Map<String, String> FALLBACKS = load();

    private RuneLegendText() {}

    public static Component message(String key, Object... args) {
        return Component.translatableWithFallback(key, FALLBACKS.getOrDefault(key, key), args);
    }

    private static Map<String, String> load() {
        var stream = RuneLegendText.class.getResourceAsStream("/assets/runelegend/lang/en_us.json");
        if (stream == null) return Map.of();
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var values = new HashMap<String, String>();
            JsonParser.parseReader(reader).getAsJsonObject().entrySet()
                    .forEach(entry -> values.put(entry.getKey(), entry.getValue().getAsString()));
            return Map.copyOf(values);
        } catch (IOException | RuntimeException error) {
            RuneLegendMod.LOGGER.warn("Could not load RuneLegend text fallbacks", error);
            return Map.of();
        }
    }
}
