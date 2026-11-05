package com.runelegend.client;

import com.runelegend.client.hud.RuneLegendHud;
import com.runelegend.client.input.GuardInputController;
import com.runelegend.client.network.ClientNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class RuneLegendClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ClientNetworking.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> GuardInputController.INSTANCE.tick(client));
        RuneLegendHud.register();
    }
}
