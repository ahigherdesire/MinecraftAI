# MinecraftAI (Baritone fork — MC 26.1 & 26.2)

A Minecraft pathfinding bot forked from [Baritone](https://github.com/cabaletta/baritone), updated for **Minecraft 26.1 and 26.2** (Java 25, Fabric) with several new features.

## Versions

| Minecraft Version | MinecraftAI version | Dependencies |
|---|---|---|
| 26.2 | [1.17.0](https://github.com/ahigherdesire/MinecraftAI/releases/tag/v1.17.0-mc26.2) | [Fabric Loader 0.16.0+](https://fabricmc.net/use/installer/) |
| 26.1 – 26.1.2 | [1.17.0](https://github.com/ahigherdesire/MinecraftAI/releases/tag/v1.17.0-mc26.1) | [Fabric Loader 0.16.0+](https://fabricmc.net/use/installer/) |

> Download the JAR matching your Minecraft version from the linked release and drop it into your `mods/` folder.

> ## What's new in this fork
>
> - 👁️ **`#threats`** / **`#players`** — proximity alerts + persistent player sighting log; see everyone who's been near you and navigate to their last position
> - 🧱 **`#chest <item>`** — silently records every container you open; search by item name and navigate to the result
> - 🔥 **`#heatmap`** — live JourneyMap overlay scoring every 32×32 block cell by player-activity indicators (blue → yellow → red)
> - 🏠 **`#bases`** — DBSCAN cluster analysis of the chunk cache to surface likely player bases
> - 🛡️ **`#autosleep`** 🧪 — navigate to bed automatically at night *(experimental)*
> - 🗺️ **`#structure`** / **`#where`** — find any structure in singleplayer or multiplayer (seed-based)
> - ✈️ **`#elytra`** — now works in the Overworld and any dimension, not just the Nether
>
> See [CHANGELOG.md](CHANGELOG.md) for the full list.

## Download & setup

1. Drop `build/libs/minecraftai-dirty.jar` into your `mods/` folder alongside Fabric Loader 0.18.4.
2. Launch Minecraft. On first command you'll see:
   ```
   [MinecraftAI] Type #activate <token> to activate.
   ```
3. Enter the token you were given:
   ```
   #activate <your-token>
   ```
   Saved permanently — you only need to do this once.

> **📖 Full guide: [GUIDE.md](GUIDE.md)** — every command, every feature, common workflows, troubleshooting. Start here if you just got this JAR.

> Minecraft 26.1.2 is fully unobfuscated so there is no remapping step — the jar contains plain class names.

## Quick start

Type commands in chat with the `#` prefix:

```
#goto 1000 500              → walk to x=1000 z=500
#mine diamond_ore           → mine diamonds (deepslate variant included automatically)
#elytra goto 5000 -2000     → fly to coordinates with elytra (any dimension)
#whereportal                → navigate to the nearest nether portal (and enter it)
#where                      → print your current X Y Z and dimension
#where village              → show where the nearest village is (coords + direction)
#structure stronghold       → find and walk to the nearest stronghold
#nether                     → convert your current XYZ to the other dimension
#seedinput <seed>           → store your world seed for multiplayer structure finding
#sleep                      → go to the nearest bed and sleep when night falls
#runaway 50                 → flee 50 blocks away from the current position
#bases                      → find likely player bases by clustering cached indicator blocks
#heatmap                    → draw a live heat overlay on JourneyMap showing player-activity zones
#chest diamond              → find every recorded chest that had diamonds in it
#chest diamond goto         → find and immediately path to the nearest one
#threats on                 → alert in chat whenever a player comes within 64 blocks
#threats 128                → change alert radius to 128 blocks
#players                    → list all players you've ever seen (name, coords, time ago)
#players Steve goto         → path to Steve's last known position
#stop                       → stop everything
#help                       → list all commands with descriptions and tab completion
```

**Stay alive while doing other things** (Autopilot Survival update):
```
#autosleep                  → auto-navigate to a bed at night
#autosleep status           → show its settings
```
(Auto-eat / auto-flee / auto-torch were dropped — Meteor Client already does them.)

## What's new in this fork

### 👁️ Player tracker + Threat alerts

`ThreatsBehavior` runs every tick and silently records every player entity you can see — saving name, UUID, position, and dimension to `baritone/player_memory.json`. No configuration needed; it just happens.

**`#players`** — query the database:
```
#players                    → list all known players (most-recent first)
#players Steve              → Steve's full sighting history with timestamps
#players goto Steve         → path to Steve's last known position
#players forget Steve       → remove Steve from the database
#players clear              → wipe everything
```
Aliases: `#player`, `#seen`

**`#threats`** — proximity alerts when enabled:
```
#threats                    → toggle on/off
#threats on                 → enable (default radius 64 blocks)
#threats 128                → set radius to 128 blocks
#threats status             → show current state and radius
```
Alert message example: `[Threats] ⚠ PLAYER NEARBY: Steve  X=123 Y=64 Z=456  (~45 blocks)  [overworld]`

Re-alerts after 30 seconds if the player stays in range. Cooldown resets if they leave and come back. Player memory is always recorded regardless of alert state. Alias: `#threat`

### 🛡️ Autopilot Survival 🧪 *experimental*
A reactive watcher that walks you to the nearest cached bed when night falls (or during a thunderstorm). Toggle with `#autosleep`. Won't interrupt active tasks unless `autoSleepInterruptTasks=true`. Prints a one-time warning when enabled.

```
#autosleep              → toggle the bed-at-night watcher
#autosleep interrupt    → toggle whether to interrupt active tasks
#autosleep status       → show settings
```

Auto-eat / auto-flee / auto-torch / the `#autopilot` master toggle were **dropped** — Meteor Client already provides equivalents and there's no point duplicating them.

Built-in Baritone death-waypointing (`doDeathWaypoints`, on by default) creates a clickable death-position waypoint on every death, so you can always run `#wp goto death @ <timestamp>` to come back for your stuff.

### Ore detection overhaul
The scanner now always checks currently-loaded chunks in spiral (nearest-first) order on top of the disk cache. Previously the cache short-circuited the live scan, causing Baritone to ignore nearby veins and walk to distant cached ore instead. Detection rate is dramatically higher and the closest ores are always targeted first.

- Each `#mine` rescan collects up to 256 positions from nearest loaded chunks (sequential scan, limit fires closest-first) then merges with the cache and keeps the 64 closest overall.
- `deepslate_*` ore variants are automatically included when you specify the stone variant and vice versa — `#mine iron_ore` covers both.

### `#structure` — find any structure (singleplayer + multiplayer)
Locates the nearest structure and paths to it. In singleplayer, uses the integrated server's world-gen data so it works even for unexplored chunks. In multiplayer, uses the world seed (enter with `#seedinput`) and grid math to calculate candidate positions client-side.

```
#structure village          #structure nether_fortress   #structure bastion
#structure stronghold       #structure mansion           #structure monument
#structure ancient_city     #structure end_city          #structure buried_treasure
#structure desert_pyramid   #structure jungle_pyramid    #structure pillager_outpost
#structure shipwreck        #structure mineshaft         #structure igloo
#structure swamp_hut        #structure ruined_portal     #structure ocean_ruin
#structure trial_chambers   #structure trail_ruins
```

### `#where` — locate without navigating
`#where <structure>` shows the nearest structure's coordinates, distance, and compass direction without starting navigation. Useful for scouting before committing to a path. `#where` alone prints your current X Y Z and dimension.

### `#seedinput` — multiplayer structure finding
Stores your world seed so `#structure` and `#where` work on multiplayer servers.

```
#seedinput 12345678       → save seed
#seedinput                → show stored seed
#seedinput clear          → forget seed
```

The seed is persisted to `baritone/seed.txt` and reloaded automatically on next launch.

### `#nether` — coordinate converter
Converts coordinates between the Overworld and the Nether (X and Z ÷8 / ×8; Y is the same in both).

```
#nether                       → convert your current X Y Z (auto-detects dimension)
#nether 800 64 -400           → convert given coords (direction auto-detected)
#nether overworld 800 64 -400 → explicitly convert overworld → nether
#nether nether 100 64 -50     → explicitly convert nether → overworld
```

Aliases: `#nc`, `#coords`

### `#whereportal` — go to the nearest portal
Navigates to the nearest nether portal. Portal blocks are tracked in Baritone's cache, so any portal you've been near is found instantly. If `enterPortal` is enabled (the default), the bot walks straight into the portal and teleports through it.

```
#whereportal      → find and enter the nearest portal
```

Aliases: `#portal`, `#findportal`

### `#bases` — find player bases
Scans Baritone's chunk cache for clusters of "base indicator" blocks (beacons, ender chests, enchanting tables, anvils, shulker boxes, brewing stands, beds, chests) and ranks them by weighted score. Pure cache read — no packets, no anticheat surface. If JourneyMap is installed, drops a purple waypoint on every detected base.

```
#bases              → top 10 likely bases in current dimension
#bases 20           → top 20
#bases pie          → indicator type breakdown
#bases 3 goto       → path to the 3rd base
```

Alias: `#basefinder`

### `#heatmap` — activity heat overlay
Scores every 32×32-block cell in the cache by density of player-indicator blocks and draws live coloured rectangles on the JourneyMap fullscreen map — 🔵 blue (cold) → 🟡 yellow (moderate) → 🔴 red (very hot). A **"Heat"** toggle button in JM's toolbar shows/hides the overlay without re-scanning. Includes broader signals than `#bases`: nether portals, hoppers, observers, player heads, jukeboxes.

```
#heatmap            → scan + draw overlay (top 10 in chat)
#heatmap 20         → top 20 in chat
#heatmap 3 goto     → path to the 3rd hotspot
```

Alias: `#activity`

### `#chest` — chest content memory
Every container you open (chest, barrel, shulker box, hopper, dropper, furnace…) is silently recorded to `baritone/chest_memory.json`. Search that database by item name at any time to find and navigate to specific loot.

```
#chest diamond           → list every recorded chest that had diamonds
#chest diamond goto      → find + immediately walk there
#chest 2 goto            → walk to the 2nd result from the last search
#chest list              → browse all recorded containers
#chest forget 1          → remove a stale entry
#chest clear             → wipe the database
```

Aliases: `#chests`, `#findchest`

### Elytra in the overworld (and any dimension)
`#elytra` previously did nothing outside the Nether. It now works in the overworld, the End, and any custom dimension:

- The pathfinder's voxel octree now packs **all** chunk sections (overworld has 24 sections, −64 to +320) with correct absolute Y coordinates. Previously only the bottom 8 sections (Y 0–127) were packed, so mountains above sea level were invisible.
- Block-update tracking no longer has a `y >= 128` cutoff.
- New `goto` subcommand: `#elytra goto X Z` or `#elytra goto X Y Z`.

## JourneyMap integration

If [JourneyMap](https://www.curseforge.com/minecraft/mc-mods/journeymap) is installed:

- **`#structure`** and **`#where`** automatically drop a gold waypoint on the map when a structure is found.
- **`#bases`** drops a purple waypoint for every detected base cluster.
- **`#heatmap`** draws colored 32×32-block rectangles across the whole map — 🔵 blue to 🔴 red by activity score. A **"Heat"** toggle button in JM's toolbar shows/hides the overlay.
- **Right-click anywhere on the fullscreen map** → select **"Baritone #goto"** → the bot starts pathing there. Works on multiplayer where JourneyMap's built-in Teleport is unavailable.

No extra configuration — the integration activates automatically if JourneyMap is present.

## Commands reference

See [USAGE.md](USAGE.md) for the full command list. See [FEATURES.md](FEATURES.md) for the complete feature overview.

## Settings

Settings persist in `baritone/settings.txt` in your Minecraft folder. Toggle booleans by typing their name in chat (`#allowSprint`), set numerics with `#primaryTimeoutMS 2000`, reset all with `#reset`.

Notable settings:
- `legitMine` — only mine ores actually visible, no x-ray effect
- `elytraAutoJump` — automatically find a ledge to jump from when starting elytra
- `elytraPredictTerrain` — use world seed to predict unloaded terrain ahead (Nether)
- `enterPortal` — walk into a portal when `#whereportal` arrives at it (default: true)
- `mineMaxOreLocationsCount` — how many ore targets to track at once (default 64)
- `renderCachedChunks` — visualise the disk cache in-game (GPU-heavy)

## Building from source

See [SETUP.md](SETUP.md). Requires Java 25 JDK — if the build fails with `invalid source release: 25`, your JDK is too old.

## Credits

**Original Baritone** — [leijurv](https://github.com/leijurv/) and [contributors](https://github.com/cabaletta/baritone/graphs/contributors)  
**3D elytra pathfinding** — [babbaj](https://github.com/babbaj/) (nether-pathfinder native library)  
**MC 26.1.2 port & new features** — this fork  
**License** — LGPL 3.0
