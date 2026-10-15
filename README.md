# Storm Royale (Forge 1.20.1)

A battle-royale mod for Minecraft: instant building, a glider, guns, healing items, a closing storm,
supply drops, eliminations and a victory screen. (Inspired by the genre; original names and vanilla textures.)

## Controls (rebind in Options > Controls > "Storm Royale")
| Key | Action |
|---|---|
| U | Build Wall (3x3, 2 blocks ahead) |
| I | Build Floor (3x3 under and ahead) |
| O | Build Ramp (3-wide stairs going up) |
| P | Build Roof (3x3 overhead) |
| K | Cycle material: Wood / Stone / Brick |
| JUMP (in mid-air, after falling 4+ blocks) | Deploy / close glider. Look down to speed up, hold SNEAK to dive. |

Building costs 1 plank / cobblestone / brick per block placed (free in creative, or turn it off in the config).

## Items
- **Weapons** (hitscan, headshots do double damage, ammo comes from your inventory):
  Assault Rifle (hold right-click, full-auto), Pump Shotgun, Sniper Rifle (all use Bullets), Rocket Launcher (uses vanilla Firework Rockets).
- **Healing:** Bandage (+3 hearts), Med Kit (full heal, 10s), Mini Shield (+5 shield hearts), Shield Potion (+10 shield hearts). Hold right-click to use.
  Shield = golden absorption hearts, capped at 10.
- All craftable (see recipes in the creative tabs / recipe book), and found in supply drops.

## Running a match (needs operator permissions)
1. Gather everyone, stand where the safe zone should be centered.
2. `/storm start 200`  (radius in blocks, default 300). Everyone in survival is "in the match".
3. `/storm scatter`  teleports everyone high above random spots. Jump to open your glider!
4. The storm shrinks in stages (waits 60s, closes over 45s). Standing outside the safe zone hurts, more each stage.
5. A supply drop (golden chest with a light beam) lands at the start of each stage. `/supplydrop` spawns one near you.
6. Eliminated players become spectators. Last player alive gets VICTORY ROYALE and the storm resets.
- `/storm status` shows the stage, `/storm stop` ends the match and restores the world border.
Solo testing works too (no victory with one player, `/storm stop` to end).

## Config (`config/storm_royale-common.toml`)
`buildCostsMaterials`, `rocketBlockDamage` (off by default), `stageWaitSeconds`, `stageShrinkSeconds`.

## Building the .jar
Needs JDK 17 and internet (first build downloads Forge, a few minutes).

    gradle build

No Gradle? Copy `gradlew`, `gradlew.bat` and the `gradle/` folder from the official Forge 1.20.1 MDK
(files.minecraftforge.net) into this folder and run `./gradlew build` (Windows: `gradlew.bat build`).
The jar appears in `build/libs/`. Put it in `.minecraft/mods` (Forge 47.x, Minecraft 1.20.1). Install it on both client and server.

## Notes
- The storm uses the vanilla world border of the Overworld, so `/storm stop` resets it to the default.
- Item icons reuse vanilla textures (no custom art yet).
