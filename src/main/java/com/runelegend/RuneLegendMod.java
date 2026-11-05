package com.runelegend;

import com.runelegend.command.RuneLegendCommands;
import com.runelegend.network.RuneLegendPayloads;
import com.runelegend.network.ServerNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RuneLegendMod implements ModInitializer {
    public static final String MOD_ID = "runelegend";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }

    @Override public void onInitialize() {
        RuneLegendPayloads.register();
        ServerNetworking.register();
        RuneLegendCommands.register();

        ServerLifecycleEvents.SERVER_STARTED.register(RuneLegendServer::open);
        ServerLifecycleEvents.SERVER_STOPPING.register(minecraft -> {
            var server = RuneLegendServer.find(minecraft);
            if (server != null) server.close();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(RuneLegendServer::remove);
        ServerTickEvents.END_SERVER_TICK.register(minecraft -> {
            var server = RuneLegendServer.find(minecraft);
            if (server != null) server.endTick();
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                var server = RuneLegendServer.find(player.level().getServer());
                if (server != null) server.died(player);
            }
        });
        ServerEntityEvents.EQUIPMENT_CHANGE.register((entity, slot, oldStack, newStack) -> {
            if (entity instanceof ServerPlayer player && slot == EquipmentSlot.MAINHAND) {
                var server = RuneLegendServer.find(player.level().getServer());
                if (server != null) server.guards().active(player);
            }
        });

        UseItemCallback.EVENT.register((player, level, hand) -> guardInteraction(player));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> guardInteraction(player));
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> guardInteraction(player));
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> guardInteraction(player));
    }

    private static InteractionResult guardInteraction(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            var server = RuneLegendServer.find(serverPlayer.level().getServer());
            if (server != null && server.guards().active(serverPlayer)) return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }
}
