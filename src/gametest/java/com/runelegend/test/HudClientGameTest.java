package com.runelegend.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.runelegend.combat.StaminaManager;
import com.runelegend.client.network.ClientStateCache;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

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
            server.runCommand("item replace entity @p weapon.mainhand with " + GuardGameTests.SWORD);
            context.waitFor(client -> cache.rules() != null && !cache.rules().running() && cache.view() != null
                    && cache.view().tokens().stream().anyMatch(token -> token.id().getPath().equals("echo"))
                    && client.player.getMainHandItem().is(Items.DIAMOND_SWORD));
            connection.waitForChunksRender();
            server.runCommand("tp @p ~ ~ ~ 0 -35");
            server.runCommand("title @a actionbar {text:'Vanilla action bar'}");
            context.getInput().holdKey(options -> options.keyUse);
            try {
                context.waitFor(client -> client.player.isBlocking() && cache.view() != null && cache.view().stamina() < StaminaManager.MAX);
                context.takeScreenshot("runelegend-waiting-stamina-only");
                server.runCommand("runelegend game start");
                context.waitFor(client -> cache.rules().running() && client.player.isBlocking());
                context.takeScreenshot("runelegend-hud-and-action-bar");
                context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
                context.waitTick();
                context.takeScreenshot("runelegend-native-third-person-block");
                context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
                context.waitFor(client -> cache.view() != null && cache.view().stamina() == 0 && !client.player.isUsingItem(), 240);
                context.waitFor(client -> cache.view() != null && cache.view().stamina() >= StaminaManager.MIN_TO_START, 100);
                context.waitTicks(12);
                if (context.computeOnClient(client -> client.player.isUsingItem())) {
                    throw new AssertionError("Held Use automatically restarted blocking after exhaustion");
                }
            } finally {
                context.getInput().releaseKey(options -> options.keyUse);
            }
            context.waitTick();
            context.getInput().holdKey(options -> options.keyUse);
            try {
                context.waitFor(client -> client.player.isBlocking());
            } finally {
                context.getInput().releaseKey(options -> options.keyUse);
            }
            context.waitFor(client -> !client.player.isUsingItem());
            context.waitFor(client -> cache.view() != null && cache.view().stamina() == StaminaManager.MAX, 240);
            server.runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_sword");
            server.runCommand("item replace entity @p weapon.offhand with minecraft:shield");
            context.waitFor(client -> client.player.getOffhandItem().is(Items.SHIELD));
            context.runOnClient(client -> {
                client.options.keyUse.setKey(InputConstants.getKey("key.keyboard.r"));
                KeyMapping.resetMapping();
            });
            context.getInput().holdKey(options -> options.keyUse);
            try {
                context.waitFor(client -> client.player.isBlocking() && client.player.getUsedItemHand() == InteractionHand.OFF_HAND
                        && cache.view() != null && cache.view().stamina() < StaminaManager.MAX);
            } finally {
                context.getInput().releaseKey(options -> options.keyUse);
            }
            context.waitFor(client -> !client.player.isUsingItem());
        }
    }
}
