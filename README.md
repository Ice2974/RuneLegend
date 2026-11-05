# RuneLegend

Minecraft Java 26.3 Fabric mod for maps that use rune tokens and directional sword guarding. Install the same release on both clients and the server, together with Fabric API. Java 25 is required.

## Playing and hosting

1. In the default `WAITING` state, use `/runelegend list` and `/runelegend equip <token_id>` to choose up to three distinct tokens.
2. The map gives players items with `minecraft:blocks_attacks`. Hold Use (right mouse button by default) to block. Ordinary swords cannot block.
3. A map operator runs `/runelegend game start` to activate tokens. Use `/runelegend game stop` to clear token effects and return to equipment selection. Item blocking works in both states. Token equipment is saved by UUID in the world save.

Using any item with `blocks_attacks`, including a vanilla shield, consumes stamina. A full bar lasts eight seconds at 20 TPS, and recovery begins after ten full ticks without blocking. After a forced stop, release Use and press again once at least 10% stamina is available. Block interactions and animations use vanilla behavior. Change HUD height with the client JVM option `-Drunelegend.hudOffset=12` (positive values move it upward).

Use `/runelegend info <token_id>`, `status`, `unequip <token_id>` and `clear` to inspect or change equipment. Administrators can run `/runelegend admin <online-player> <equip|unequip|clear|status>`. Game controls fall back to game master permissions; equipment administration falls back to administrator permissions.

## Configuring a blocking sword

This command creates a sword that halves eligible damage before armor within vanilla's default horizontal range of 180°, including the exact sides. Rear hits and hits without a reliable horizontal origin are not reduced. The example cannot be disabled by an enemy weapon, and its `use_effects` slows the user's movement input and prevents sprinting.

```mcfunction
/give @s minecraft:diamond_sword[minecraft:blocks_attacks={block_delay_seconds:0.0f,disable_cooldown_scale:0.0f,damage_reductions:[{type:"#runelegend:sword_guardable",base:0.0f,factor:0.5f}],bypassed_by:"#runelegend:sword_guard_bypasses"},minecraft:use_effects={can_sprint:false,speed_multiplier:0.2f}]
```

Add `,minecraft:unbreakable={}` inside the final brackets to prevent durability loss. Otherwise the sword uses vanilla blocking durability. Keep the `damage_reductions` rule: omitting it uses vanilla's default full block. Avoid combining this recipe with consumable or swappable equipment components, which can take precedence over blocking. Shields keep their own damage rules.

When upgrading from protocol 1, update both the server and clients. Remove old `/runelegend guard ...` calls from maps and replace them with item distribution. To keep blocking out of a waiting area, control which blocking items players have there. Java integrations should remove calls to the old guard switch. Existing token equipment saves need no migration.

## Map data packs

Add or replace tag entries under these paths:

| Path in your data pack | Default and use |
|---|---|
| `data/runelegend/tags/entity_type/ghost_family.json` | Empty. Used by effects that explicitly target the ghost family. |
| `data/runelegend/tags/entity_type/bosses.json` | Ender Dragon and Wither. Classification does not confer immunity. |
| `data/runelegend/tags/entity_type/token_enemies.json` | Empty falls back to Java hostile entities. When populated, this is the explicit list for hostile-target token effects. |
| `data/runelegend/tags/damage_type/sword_guardable.json` | Direct melee, arrows, and explosions. Damage also needs a valid origin in front of the defender. |
| `data/runelegend/tags/damage_type/sword_guard_bypasses.json` | Armor-bypassing and invulnerability-bypassing damage, plus RuneLegend token damage. These bypass the example sword even if also added to the allow tag. |

For example, `{"replace":false,"values":["minecraft:husk"]}` adds husks to an entity tag, and `{"replace":false,"values":["minecraft:trident"]}` adds trident damage to `sword_guardable`. Tags classify types, not individual entities. Both entity classifications can match the same entity and update with data-pack reload. Mod integrations can use `RuneLegendApi.get(server)` on the server thread.

## Building

Use JDK 25 for the Gradle JVM. On Windows run `gradlew.bat build`; on other platforms run `./gradlew build` (or `bash ./gradlew build`). Loom supplies the `runClient` and `runServer` development launchers. See Minecraft's EULA before running a server or its game tests.
