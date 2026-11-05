package com.runelegend.test;

import com.runelegend.token.TokenTargeting;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;

public final class TokenGameTests {
    @GameTest
    public void defaultEntityTypeTagsAreIndependent(GameTestHelper test) {
        var registry = BuiltInRegistries.ENTITY_TYPE;
        test.assertTrue(registry.wrapAsHolder(EntityTypes.WITHER).is(TokenTargeting.BOSSES), "Wither is a Boss");
        test.assertTrue(registry.wrapAsHolder(EntityTypes.ENDER_DRAGON).is(TokenTargeting.BOSSES), "Dragon is a Boss");
        test.assertTrue(!registry.wrapAsHolder(EntityTypes.WITHER).is(TokenTargeting.GHOST_FAMILY), "Bosses are not automatically ghosts");
        var zombie = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 2);
        test.assertTrue(TokenTargeting.enemy(zombie) && !TokenTargeting.boss(zombie) && !TokenTargeting.ghostFamily(zombie),
                "A normal hostile remains neither a ghost nor a boss");
        test.succeed();
    }
}
