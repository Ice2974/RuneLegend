package com.runelegend.test;

import com.runelegend.client.network.ClientStateCache;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.util.Properties;

public final class HudClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var props = new Properties();
        props.setProperty("online-mode", "false");
        props.setProperty("white-list", "false");
        try (var server = context.worldBuilder().createServer(props);
             var connection = server.connect()) {
            var cache = ClientStateCache.INSTANCE;
            context.waitFor(client -> client.level != null && client.player != null && cache.view() != null);
            server.runCommand("runelegend admin @p equip runelegend:echo");
            server.runCommand("item replace entity @p weapon.mainhand with minecraft:iron_sword");
            server.runCommand("runelegend guard enable");
            server.runCommand("runelegend game start");
            context.waitFor(client -> cache.rules() != null && cache.rules().canGuard() && cache.view() != null
                    && cache.view().tokens().stream().anyMatch(token -> token.id().getPath().equals("echo"))
                    && client.player.getMainHandItem().is(net.minecraft.world.item.Items.IRON_SWORD));
            connection.waitForChunksRender();
            server.runCommand("title @a actionbar {text:'Vanilla action bar'}");
            context.getInput().holdKey(options -> options.keyUse);
            try {
                context.waitFor(client -> cache.view() != null && cache.view().guarding() && cache.view().stamina() < 20_000);
                context.takeScreenshot("runelegend-hud-and-action-bar");
            } finally {
                context.getInput().releaseKey(options -> options.keyUse);
            }
            context.waitFor(client -> cache.view() != null && !cache.view().guarding());
        }
    }
}
