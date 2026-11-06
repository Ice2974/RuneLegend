package com.runelegend.game;

import com.runelegend.RuneLegendMod;
import com.runelegend.api.GameState;
import com.runelegend.api.RuleSnapshot;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class GameRulesStorageGameTests {
    @GameTest
    public void rulesReloadAndFailedReadsNeverOverwriteTheFile(GameTestHelper test) throws Exception {
        var directory = Files.createTempDirectory("runelegend-rules-test-");
        var file = RuneLegendMod.id("game_rules.dat").resolveAgainst(directory);
        var expected = new RuleSnapshot(GameState.RUNNING, 8, 11);
        try {
            try (var storage = storage(test, directory)) {
                var rules = GameRulesSavedData.load(storage, directory);
                test.assertValueEqual(rules.rules(), new RuleSnapshot(GameState.WAITING, 0, 0),
                        "A missing file starts in WAITING");
                rules.update(expected);
                storage.saveAndJoin();
            }
            try (var storage = storage(test, directory)) {
                var rules = GameRulesSavedData.load(storage, directory);
                test.assertValueEqual(rules.rules(), expected, "A fresh storage reads the saved rules");
                test.assertTrue(!rules.isDirty(), "Loading valid data does not request another write");
            }

            var data = (CompoundTag) GameRulesSavedData.CODEC.encodeStart(NbtOps.INSTANCE,
                    new GameRulesSavedData(1, GameState.RUNNING, 8, 11)).getOrThrow();
            data.putInt("schemaVersion", 2);
            var unsupported = new CompoundTag();
            unsupported.put("data", data);
            NbtIo.writeCompressed(unsupported, file);
            assertRejectedAndPreserved(test, directory, file);

            Files.write(file, new byte[]{1, 2, 3, 4});
            assertRejectedAndPreserved(test, directory, file);
            test.succeed();
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.getParent());
            Files.deleteIfExists(directory);
        }
    }

    private static SavedDataStorage storage(GameTestHelper test, Path directory) {
        var server = test.getLevel().getServer();
        return new SavedDataStorage(directory, server.getFixerUpper(), server.registryAccess());
    }

    private static void assertRejectedAndPreserved(GameTestHelper test, Path directory, Path file) throws Exception {
        byte[] original = Files.readAllBytes(file);
        try (var storage = storage(test, directory)) {
            boolean rejected = false;
            try {
                GameRulesSavedData.load(storage, directory);
            } catch (IllegalStateException expected) {
                rejected = expected.getMessage().contains(file.toString());
            }
            test.assertTrue(rejected, "Failed reads abort with the affected file path");
            storage.saveAndJoin();
        }
        test.assertTrue(Arrays.equals(original, Files.readAllBytes(file)),
                "Saving and closing after rejection preserves the original bytes");
    }
}
