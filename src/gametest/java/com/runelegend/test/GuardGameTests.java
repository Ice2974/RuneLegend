package com.runelegend.test;

import com.runelegend.RuneLegendMod;
import com.runelegend.RuneLegendServer;
import com.runelegend.player.PlayerTokenService;
import com.runelegend.token.TokenEngine;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class GuardGameTests {
    @GameTest
    public void meleeAndArrowsUseTheirOwnApproach(GameTestHelper test) {
        var server = RuneLegendServer.find(test.getLevel().getServer());
        var player = fixture(test);
        var originalDifficulty = test.getLevel().getDifficulty();
        test.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        var origin = test.absoluteVec(new Vec3(2.5, 2, 2.5));
        var mob = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 3);
        var shooter = test.spawnWithNoFreeWill(EntityTypes.SKELETON, 2, 2, 1);
        var arrow = test.spawn(EntityTypes.ARROW, 2, 2, 2);
        try {
            server.game().stopGame();
            test.assertValueEqual(PlayerTokenService.Result.CHANGED, server.players().equip(player.getUUID(), RuneLegendMod.id("immediate_counter")), "Equip while waiting");
            start(server, test, player);
            player.setXRot(-85);
            mob.setPos(origin.add(0, 0, -1));
            reset(player, origin, 9);
            hit(test, player, player.damageSources().mobAttack(mob), 3, "Back melee should be unreduced");
            test.assertTrue((server.tokens().views(player).getFirst().flags() & TokenEngine.ACTIVE) == 0,
                    "A back hit must not activate Immediate Counter");
            mob.setPos(origin.add(1, 0, 0));
            reset(player, origin, 20);
            hit(test, player, player.damageSources().mobAttack(mob), 14, "Exact side should be unreduced");
            mob.setPos(origin.add(0, 0, 1));
            reset(player, origin, 9);
            hit(test, player, player.damageSources().mobAttack(mob), 6, "Front melee should be halved");
            test.assertTrue((server.tokens().views(player).getFirst().flags() & TokenEngine.ACTIVE) != 0,
                    "A successful front guard below half health should activate Immediate Counter");

            shooter.setPos(origin.add(0, 0, -3));
            arrow.setDeltaMovement(0, 0, -2);
            reset(player, origin, 20);
            hit(test, player, player.damageSources().arrow(arrow, shooter), 17, "Front arrow should be halved even with shooter behind");
            shooter.setPos(origin.add(0, 0, 3));
            arrow.setDeltaMovement(0, 0, 2);
            reset(player, origin, 20);
            hit(test, player, player.damageSources().arrow(arrow, shooter), 14, "Back arrow should be full damage even with shooter in front");

            server.game().setGuardEnabled(false);
            test.assertTrue(!server.guards().get(player).guarding(), "Disabling guard should end an active hold immediately");
            test.assertTrue((server.tokens().views(player).getFirst().flags() & TokenEngine.ACTIVE) != 0,
                    "Disabling guard should not clear active tokens");
            server.game().stopGame();
            test.assertValueEqual(0, server.tokens().views(player).getFirst().flags(), "Stopping should clear active effects");
            test.succeed();
        } finally {
            server.game().stopGame();
            server.game().setGuardEnabled(false);
            test.getLevel().getServer().setDifficulty(originalDifficulty, true);
            server.players().clear(player.getUUID());
            server.leave(player);
        }
    }

    @GameTest
    public void sourceLessExplosionUsesTheRealCenter(GameTestHelper test) {
        var server = RuneLegendServer.find(test.getLevel().getServer());
        var player = fixture(test);
        var originalDifficulty = test.getLevel().getDifficulty();
        test.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        var origin = test.absoluteVec(new Vec3(2.5, 2, 2.5));
        var level = test.getLevel();
        reset(player, origin, 20);
        level.addNewPlayer(player);
        try {
            start(server, test, player);
            var fixedDamage = new ExplosionDamageCalculator() {
                @Override public float getEntityDamageAmount(Explosion explosion, Entity entity, float exposure) { return 6; }
            };
            level.explode(null, null, fixedDamage, origin.add(0, 0, 1), 1, false, Level.ExplosionInteraction.NONE);
            test.assertValueEqual(player.getHealth(), 17F, "Source-less front explosion should be halved");
            reset(player, origin, 20);
            var igniter = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 4);
            igniter.setPos(origin.add(0, 0, 4));
            level.explode(igniter, null, fixedDamage, origin.add(0, 0, -1), 1, false, Level.ExplosionInteraction.NONE);
            test.assertValueEqual(player.getHealth(), 14F, "Source-less rear explosion should not be halved");
            test.succeed();
        } finally {
            server.game().stopGame();
            server.game().setGuardEnabled(false);
            test.getLevel().getServer().setDifficulty(originalDifficulty, true);
            server.leave(player);
            level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        }
    }

    public static ServerPlayer fixture(GameTestHelper test) {
        var player = (ServerPlayer) test.makeMockServerPlayer(GameType.SURVIVAL);
        var server = test.getLevel().getServer();
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        RuneLegendServer.find(server).guards().join(player);
        player.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
        return player;
    }

    private static void start(RuneLegendServer server, GameTestHelper test, ServerPlayer player) {
        server.game().startGame();
        server.game().setGuardEnabled(true);
        var rules = server.game().getRules();
        server.guards().start(player, 1, rules.revision(), player.getInventory().getSelectedSlot());
        test.assertTrue(server.guards().active(player), "Fixture player should be guarding");
    }

    private static void reset(ServerPlayer player, Vec3 origin, float health) {
        player.setPos(origin);
        player.setYRot(0);
        player.setHealth(health);
        player.damageCooldownTime = 0;
    }

    private static void hit(GameTestHelper test, ServerPlayer player, net.minecraft.world.damagesource.DamageSource source,
                            float remaining, String message) {
        test.assertTrue(player.hurtServer(test.getLevel(), source, 6), "Vanilla should accept hit");
        test.assertValueEqual(player.getHealth(), remaining, message);
    }
}
