# RuneLegend

Minecraft Java 26.3 Fabric mod for maps that use rune tokens and directional sword guarding. Install the same release on both clients and the server, together with Fabric API. Java 25 is required.

## Playing and hosting

1. In the default `WAITING` state, use `/runelegend list` and `/runelegend equip <token_id>` to choose up to three distinct tokens.
2. A map operator runs `/runelegend guard enable` and `/runelegend game start`. The guard switch is independent of the game state and is off on startup.
3. Hold the Use key (right mouse button by default) with a sword in the main hand. Eligible damage from the front is halved before armor. The exact sides, back, and hits without a reliable horizontal direction are not reduced. Knockback remains vanilla.
4. Use `/runelegend game stop` to stop token effects and return to equipment selection. Equipment is saved by UUID, in the world save.

Stamina lasts eight seconds at 20 TPS. It regenerates after half a second. After a forced stop, release Use and press again once at least 10% stamina is available. Right-click interactions are reserved for the sword while an eligible guard press is held. Change HUD height with the client JVM option `-Drunelegend.hudOffset=12` (positive values move it upward).

Use `/runelegend info <token_id>`, `status`, `unequip <token_id>` and `clear` to inspect or change equipment. Administrators can run `/runelegend admin <online-player> <equip|unequip|clear|status>`. Game and guard controls fall back to game master permissions; equipment administration falls back to administrator permissions.

## Map data packs

Add or replace entity types under these paths (the namespace is always `runelegend`):

| Path in your data pack | Default and use |
|---|---|
| `data/runelegend/tags/entity_type/ghost_family.json` | Empty. Used by effects that explicitly target the ghost family. |
| `data/runelegend/tags/entity_type/bosses.json` | Ender Dragon and Wither. Classification does not confer immunity. |
| `data/runelegend/tags/entity_type/token_enemies.json` | Empty falls back to Java hostile entities. When populated, this is the explicit list for hostile-target token effects. |
| `data/runelegend/tags/damage_type/sword_guardable.json` | Direct melee, arrows, and explosions. Damage also needs a valid origin in front of the defender. |

For example, the JSON object `{"replace":false,"values":["minecraft:husk"]}` adds all husks to a tag. Tags classify types, not individual entities. Both entity classifications can match the same entity and update with data-pack reload. Mod integrations can use `RuneLegendApi.get(server)` on the server thread.

## Building

Use JDK 25 for the Gradle JVM. On Windows run `gradlew.bat build`; on other platforms run `./gradlew build` (or `bash ./gradlew build`). Loom supplies the `runClient` and `runServer` development launchers. See Minecraft's EULA before running a server or its game tests.
