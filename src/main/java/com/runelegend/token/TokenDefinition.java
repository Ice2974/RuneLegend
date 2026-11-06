package com.runelegend.token;

import com.runelegend.RuneLegendText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.Set;

public record TokenDefinition(Identifier id, Set<TokenEvent.Kind> events, TokenEffectHandler effect) {
    public TokenDefinition {
        events = Set.copyOf(events);
    }

    public Component name() { return RuneLegendText.message("token.runelegend." + id.getPath()); }
    public Component description() {
        return RuneLegendText.message("token.runelegend." + id.getPath() + ".description", effect.descriptionArguments());
    }
}
