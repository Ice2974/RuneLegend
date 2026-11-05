package com.runelegend.api;

import com.runelegend.RuneLegendServer;
import net.minecraft.server.MinecraftServer;

/** Map integration. Call on the logical server thread after server startup. */
public interface RuneLegendApi {
    static RuneLegendApi get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("RuneLegend requires the server thread");
        var instance = RuneLegendServer.find(server);
        if (instance == null) throw new IllegalStateException("RuneLegend is not active on this server");
        return instance.game();
    }

    RuleSnapshot getRules();
    boolean startGame();
    boolean stopGame();
}
