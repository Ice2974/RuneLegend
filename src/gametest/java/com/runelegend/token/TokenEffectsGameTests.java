package com.runelegend.token;

import com.runelegend.RuneLegendMod;
import com.runelegend.RuneLegendServer;
import com.runelegend.game.GameController;
import com.runelegend.player.PlayerTokenSavedData;
import com.runelegend.player.PlayerTokenService;
import com.runelegend.test.GuardGameTests;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public final class TokenEffectsGameTests {
    @GameTest
    public void onlyAnOptedInTokenPublishesPreparation(GameTestHelper test) {
        var minecraft = test.getLevel().getServer();
        var server = RuneLegendServer.find(minecraft);
        var player = GuardGameTests.fixture(test);
        var id = RuneLegendMod.id("preparation_fixture");
        var registry = new TokenRegistry.Builder().register(new TokenDefinition(id, Set.of(TokenEvent.Kind.KILL),
                new TokenEffectHandler() {
                    @Override public boolean preparing(net.minecraft.server.level.ServerPlayer player,
                                                       TokenRuntimeState state, long tick) { return state.charge == 1; }
                    @Override public void onEvent(TokenEngine.Context context, TokenRuntimeState state, TokenEvent event) {
                        state.charge++;
                    }
                })).build();
        var game = new GameController(() -> {}, (previous, current) -> {});
        var players = new PlayerTokenService(game, PlayerTokenSavedData.load(minecraft), registry);
        var tokens = new TokenEngine(game, players, registry, () -> 100);
        try {
            test.assertValueEqual(players.equip(player.getUUID(), id), PlayerTokenService.Result.CHANGED, "Equip fixture");
            game.startGame();
            var kill = new TokenEvent(TokenEvent.Kind.KILL, player, player, test.getLevel(), null, 0, 0, 0);
            test.assertValueEqual(tokens.views(player).getFirst().flags(), TokenEngine.READY, "Readiness is not preparation");
            tokens.dispatch(kill);
            test.assertValueEqual(tokens.views(player).getFirst().flags(), TokenEngine.READY | TokenEngine.PREPARING,
                    "The fixture enters preparation after its first kill");
            tokens.dispatch(kill);
            test.assertValueEqual(tokens.views(player).getFirst().flags(), TokenEngine.READY, "Preparation ends independently");
            for (var definition : server.registry().all()) {
                test.assertTrue(!definition.effect().preparing(player, new TokenRuntimeState(), 100),
                        definition.id() + " does not opt into preparation");
            }
            test.succeed();
        } finally {
            game.stopGame();
            players.clear(player.getUUID());
            server.leave(player);
        }
    }

    @GameTest
    public void lethalArrowKillStillReachesTokens(GameTestHelper test) {
        var level = test.getLevel();
        var server = RuneLegendServer.find(level.getServer());
        var player = GuardGameTests.fixture(test);
        var arrowTarget = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 4);
        var arrow = test.spawn(EntityTypes.ARROW, 2, 3, 3);
        player.setPos(test.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2, 2.5)));
        try {
            server.game().stopGame();
            test.assertValueEqual(server.players().equip(player.getUUID(), RuneLegendMod.id("soul_siphon")),
                    PlayerTokenService.Result.CHANGED, "Equip Soul Siphon");
            server.game().startGame();
            long seed = 0;
            while (RandomSource.create(seed).nextDouble() >= 0.05) seed++;
            player.getRandom().setSeed(seed);
            float maxHealth = player.getMaxHealth();
            arrowTarget.hurtServer(level, level.damageSources().arrow(arrow, player), 100);
            test.assertTrue(!arrowTarget.isAlive(), "Arrow damage was lethal");
            test.assertValueEqual(player.getMaxHealth(), maxHealth + 2, "The lethal arrow is credited to its shooter");
            test.succeed();
        } finally {
            server.game().stopGame();
            server.players().clear(player.getUUID());
            server.leave(player);
        }
    }

    @GameTest
    public void effectsUseIndependentPlayersAndOwnedTasks(GameTestHelper test) {
        var level = test.getLevel();
        var server = RuneLegendServer.find(level.getServer());
        var first = GuardGameTests.fixture(test);
        var second = GuardGameTests.fixture(test);
        var corpse = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 2);
        var spider = test.spawnWithNoFreeWill(EntityTypes.SPIDER, 2, 2, 3);
        var clock = new AtomicLong(100);
        var tokens = new TokenEngine(server.game(), server.players(), server.registry(), clock::get, player -> 0.01);
        float firstMax = first.getMaxHealth();
        float firstHealth = first.getHealth();
        float spiderHealth = spider.getHealth();
        try {
            server.game().stopGame();
            for (var player : List.of(first, second)) {
                for (String id : List.of("sharpened_tip", "soul_siphon", "echo")) {
                    test.assertValueEqual(server.players().equip(player.getUUID(), RuneLegendMod.id(id)),
                            PlayerTokenService.Result.CHANGED, "Each player can equip the same three tokens");
                }
            }
            var kill = new TokenEvent(TokenEvent.Kind.KILL, first, corpse, level, first.damageSources().playerAttack(first), 25, 25, 0);
            tokens.dispatch(kill);
            test.assertValueEqual(first.getMaxHealth(), firstMax, "WAITING disables token effects");
            server.game().startGame();
            var boost = tokens.melee(first, 6);
            test.assertValueEqual(boost.damage(), 8F, "Sharpened Tip adds damage to the original hit");
            tokens.confirmBoost(first, boost.applied());
            tokens.dispatch(kill);
            tokens.dispatch(kill);
            test.assertValueEqual(first.getMaxHealth(), firstMax + 2, "Soul Siphon refreshes without stacking");
            test.assertValueEqual(first.getHealth(), firstHealth, "Soul Siphon does not heal");
            test.assertValueEqual(second.getMaxHealth(), firstMax, "An identical token does not buff the other player");
            test.assertTrue(tokens.views(second).stream().noneMatch(view -> (view.flags() & (TokenEngine.ACTIVE | TokenEngine.TRIGGERED)) != 0),
                    "Token HUD state is separate for each player");
            for (int elapsed = 20; elapsed <= 160; elapsed += 20) {
                spider.damageCooldownTime = 0;
                clock.set(100 + elapsed);
                tokens.tick(List.of(first, second));
                test.assertValueEqual(spider.getHealth(), spiderHealth - 2F * (elapsed / 20), "Echo applies one pulse every twenty ticks");
            }
            tokens.clearEffects(first.getUUID());
            test.assertValueEqual(first.getMaxHealth(), firstMax + 2, "Soul Siphon maximum health is permanent");
            test.succeed();
        } finally {
            tokens.clearRound();
            server.game().stopGame();
            for (var player : List.of(first, second)) {
                server.players().clear(player.getUUID());
                server.leave(player);
            }
        }
    }
}
