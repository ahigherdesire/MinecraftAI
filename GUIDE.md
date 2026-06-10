# MinecraftAI — Complete Guide

Everything you can do with this Baritone fork on Minecraft 26.1.2.

If you've used vanilla Baritone before, the **What's new** sections at the end of each chapter cover what's different in this fork. If you're brand new, just read top to bottom.

---

## Table of contents

1. [Install and first launch](#1-install-and-first-launch)
2. [How commands work](#2-how-commands-work)
3. [Navigation — going places](#3-navigation--going-places)
4. [Mining — gathering resources](#4-mining--gathering-resources)
5. [Elytra flight](#5-elytra-flight)
6. [Structure finder](#6-structure-finder)
7. [Coordinate utilities](#7-coordinate-utilities)
8. [Portals](#8-portals)
9. [Sleep, retreat, autopilot](#9-sleep-retreat-autopilot)
10. [Base finder](#10-base-finder)
11. [Following, farming, building](#11-following-farming-building)
12. [Waypoints and the cache](#12-waypoints-and-the-cache)
13. [Exploration](#13-exploration)
14. [Settings reference](#14-settings-reference)
15. [Common workflows](#15-common-workflows)
16. [Troubleshooting](#16-troubleshooting)
17. [Authorization](#17-authorization)

---

## 1. Install and first launch

**Requirements**
- Minecraft **26.1.2**
- Fabric Loader **0.18.4** or newer
- Java **25** JDK (already required to run MC 26.1.2)

**Install steps**
1. Install [Fabric Loader 0.18.4](https://fabricmc.net/use/) for MC 26.1.2.
2. Drop `minecraftai-dirty.jar` into your Minecraft `mods/` folder.
3. Launch Minecraft. A `baritone/` folder appears in your game directory — that's the cache + settings.

**First launch check**
- Open chat (`T` by default).
- Type `#help` and press enter.
- You should see a list of all available commands.
- If you see a red `[MinecraftAI] Unauthorized user...` message, see [§17](#17-authorization).

That's it — you're running.

---

## 2. How commands work

All commands use the `#` prefix in chat.

**Tab completion** works on the command name *and* every argument. Hit `Tab` partway through typing and Baritone fills in the rest or shows you the choices.

**Settings** are tweaked with `#set <name> <value>`:
```
#set allowSprint false           → disable sprinting
#set primaryTimeoutMS 2000       → increase pathfinder budget
#set elytraAutoJump reset        → restore default value
#modified                        → list everything you've changed from defaults
#reset                           → reset ALL settings to defaults
```
Settings persist to `baritone/settings.txt`.

**Cancel** anything in progress:
```
#cancel   (or #stop)             → stop the current task cleanly
#forcecancel                     → stop even if mid-segment (use if #cancel hangs)
```

**Help in chat**:
```
#help                            → searchable list of every command
#help <command>                  → detailed help for one command (clickable in chat)
```

**Bind to a chat key** (vanilla Baritone feature) — go to Options → Controls → search "baritone open" to bind a key that opens the chat with the `#` prefix already typed.

---

## 3. Navigation — going places

### Basic goto

```
#goto 1000 500              → walk to X=1000 Z=500 (any Y)
#goto 1000 64 -500          → walk to exact X Y Z
#goto -57                   → walk to Y level -57 (good for diamond mining)
#goto diamond_ore           → walk to nearest known diamond ore
#goto bed                   → walk to nearest known bed
```

### Persistent goal (without auto-pathing)

```
#goal 1000 500              → set goal but don't move yet
#path                       → start walking toward the goal
#goal                       → show the current goal
#goal clear                 → clear the goal
```

### Relative directions

```
#thisway 1000               → go 1000 blocks in the direction you're facing
#axis                       → go to the nearest cardinal or diagonal axis at Y=120
#come                       → walk to wherever your camera is (useful in freecam)
#surface                    → walk up to the nearest open sky
#invert                     → flee from the current goal — useful for reversing course
```

### What's new in this fork

- `#elytra goto X Z` works in any dimension, with tilde notation for relative coords (see [§5](#5-elytra-flight)).
- `#runaway <distance>` (alias `#flee`, `#escape`) — flee from your current position, useful for retreating from mobs.

---

## 4. Mining — gathering resources

```
#mine diamond_ore                       → mine diamonds until you stop it
#mine 32 iron_ore                       → mine until you have 32 iron ingots
#mine diamond_ore coal_ore              → mine multiple block types
#mine iron_ore                          → automatically also mines deepslate_iron_ore
#cancel                                 → stop mining
```

**Legit mode** restricts mining to ore the player can actually see — Baritone wanders the world and only mines ores it spots naturally.

```
#set legitMine true                     → turn on legit mode
#set legitMineYLevel -57                → set the Y level to wander at
```

**Useful settings**:
- `mineMaxOreLocationsCount` (default 64) — how many ore positions to track at once.
- `mineGoalUpdateInterval` (default 5) — how often (in ticks) to rescan for new ore.

### What's new in this fork

- **Nearest-first scanning.** Vanilla Baritone's cache shortcut could cause it to walk to distant cached ore while ignoring closer veins in loaded chunks. This fork always live-scans loaded chunks in spiral (nearest-first) order on top of the disk cache, then keeps the 64 closest.
- **Automatic deepslate pairing.** `#mine iron_ore` covers `deepslate_iron_ore` automatically — and vice versa.

---

## 5. Elytra flight

You need: elytra equipped, fireworks in inventory.

```
#elytra goto 5000 -2000                 → fly to X Z (Y picked automatically)
#elytra goto 5000 200 -2000             → fly to exact X Y Z
#elytra goto ~500 ~ ~-200               → tilde: relative to current position
#elytra                                 → fly to your current goal (set with #goal)
#elytra reset                           → reset pathfinder state (still flying same goal)
#elytra repack                          → re-cache all loaded chunks
#elytra supported                       → check native library loaded
```

**What the bot does**
1. Finds a nearby ledge or cliff for takeoff (`elytraAutoJump=true`, default).
2. Boosts with fireworks as needed to maintain altitude.
3. Routes around terrain at the right height.
4. Picks a safe landing spot at the destination, or emergency-lands if elytra/fireworks run low.

**Crash-proof destinations** (this fork's biggest elytra fix):
- Target inside terrain or in an unloaded chunk where seed-predicted terrain might be solid → bot lifts to a safe altitude (~Y=202 overworld, Y=115 Nether, Y=80 End) instead of crashing the game.
- Underground target → bot flies to the surface above it, then hands off to ground pathfinding to mine down.

**Useful settings**:
- `elytraAutoJump` (default true) — auto-find takeoff spot.
- `elytraMinimumDurability` (default 5) — emergency-land before elytra breaks.
- `elytraMinFireworksBeforeLanding` (default 12) — emergency-land if low on rockets.
- `elytraPredictTerrain` (default false) — predict terrain in unloaded chunks from the seed (set `elytraNetherSeed` first).

### What's new in this fork

- Works in **any dimension**, not just the Nether.
- **`goto` subcommand** with tilde notation.
- **Full-height obstacle avoidance** — covers the complete world height in every dimension (no more flying through overworld mountains).
- **Crash-proof destinations** as described above.

---

## 6. Structure finder

```
#structure village                      → find + walk to nearest village
#structure stronghold
#structure nether_fortress              (also: fortress)
#structure bastion                      (also: bastion_remnant)
#structure mansion                      (also: woodland_mansion)
#structure monument                     (also: ocean_monument)
#structure ancient_city
#structure end_city
#structure buried_treasure
#structure desert_pyramid               (also: desert_temple)
#structure jungle_pyramid               (also: jungle_temple)
#structure pillager_outpost             (also: outpost)
#structure shipwreck
#structure mineshaft                    (also: mine)
#structure igloo
#structure swamp_hut                    (also: witch_hut)
#structure ruined_portal
#structure ocean_ruin                   (also: ocean_ruins)
#structure trial_chambers               (also: trial_chamber)
#structure trail_ruins
```

The search runs in the background — no game stutter.

**Singleplayer** — uses the integrated server's chunk generator, works for unexplored areas.

**Multiplayer** — uses seed-based math; you must enter the seed first:
```
#seedinput 12345678                     → save seed to baritone/seed.txt
#seedinput                              → show stored seed
#seedinput clear                        → delete stored seed
```
**Strongholds on multiplayer**: cannot be calculated client-side (need biome data). The command tells you to check chunkbase.com.

### `#where` — locate without navigating

```
#where                                  → print your current X Y Z and dimension
#where village                          → find nearest village, print coords + distance + direction
                                          (does NOT start pathing)
```

Useful for scouting before committing to a path.

### What's new in this fork

- **`#structure`, `#where`, `#seedinput`** are all new in this fork — vanilla Baritone has no structure-finder.
- **Server-thread search** (this session's biggest bug fix) — biome checks for candidate chunks now happen on the integrated server thread so structures in unexplored biomes get found. Before the fix, only structures in chunks you'd already loaded would appear.

---

## 7. Coordinate utilities

```
#nether                                 → convert your current XYZ between dimensions
#nether 800 64 -400                     → convert given coords (direction auto-detected)
#nether 800 -400                        → X and Z only, Y shown as ?
#nether overworld 800 64 -400           → explicit: overworld → nether
#nether nether 100 64 -50               → explicit: nether → overworld
```
Aliases: `#nc`, `#coords`.

X and Z scale by 8 (overworld ↔ nether); Y is the same in both.

### What's new in this fork

The whole `#nether` command is new in this fork.

---

## 8. Portals

```
#whereportal                            → walk to nearest nether portal and enter it
#portal skip                            → blacklist the nearest portal, try the next one
#portal                                 → alias for #whereportal
#findportal                             → alias for #whereportal
```

By default, the bot walks **into** the portal and teleports through. To stop at the portal instead:
```
#set enterPortal false
```

Works in both directions: overworld portals find the way to the Nether, nether portals find the way home.

### What's new in this fork

- Whole portal-finder command is new.
- `#portal skip` exposes the `blacklistClosest()` API for cases when the nearest portal is unreachable.
- Auto-opens chests, barrels, smokers, ender chests, trapped chests, and all 17 shulker box variants on arrival when `rightClickContainerOnArrival=true` (default).

---

## 9. Sleep, retreat, autopilot

### Sleep — explicit one-shot

```
#sleep
```
Finds the nearest cached bed (any of 16 colours), walks to it, and on a background thread waits for night/thunderstorm then right-clicks to sleep. Cancel with `#cancel`.

If `#sleep` says no bed found: walk near a bed once so it gets cached.

### Runaway — flee

```
#runaway                                → flee 32 blocks (default)
#runaway 50                             → flee 50 blocks
```
Aliases: `#flee`, `#escape`. The origin is locked the moment the command runs, so the bot stops once it's far enough.

### Autosleep — autopilot

```
#autosleep                              → toggle the auto-bed-at-night watcher
#autosleep on  /  off                   → explicit
#autosleep interrupt                    → toggle whether it interrupts active tasks
#autosleep status                       → show current settings
```

When night falls (or a thunderstorm), the bot automatically navigates to the nearest cached bed.

**🧪 Experimental** — first time you enable it, chat shows a one-time warning.

**Won't interrupt** an active task (mining, building, etc.) unless `autoSleepInterruptTasks=true`.

### What's NOT in this fork

The original Autopilot Survival plan included `#autoeat`, `#autoflee`, `#autotorch`, and a `#autopilot` master toggle. Those were **dropped** because Meteor Client already provides equivalents — no point duplicating. Death-waypointing remains free via Baritone's built-in `doDeathWaypoints` (on by default — every death saves a clickable waypoint).

---

## 10. Base finder

Find likely player bases by clustering indicator blocks in your chunk cache.

```
#bases                                  → top 10 bases in current dimension
#bases 25                               → top 25
#bases pie                              → type breakdown (% of each indicator block)
#bases 1 goto                           → path to base #1
```
Aliases: `#basefinder`.

**Example output**:
```
══ Detected bases (4 total, showing top 10) ══
 1. score  247 │ X= 8421 Z=-2156 │ 47 indicators │ ~9482 blocks │ 12× shulker, 8× ender_chest, 6× bed, 4× anvil
 2. score  184 │ X=-4392 Z= 6011 │ 31 indicators │ ~7611 blocks │ 8× ender_chest, 8× anvil, 4× shulker, 3× bed
 3. score   95 │ X=  512 Z= -840 │ 18 indicators │ ~ 942 blocks │ 4× ender_chest, 4× anvil, 2× enchanting_table, 2× bed
```

**How it works**
1. Pulls every indicator-block position from the cache (beacon, ender chest, enchanting table, anvil, shulker box, brewing stand, bed, chest).
2. Clusters by 2D (X/Z) proximity with DBSCAN, radius `baseFinderEpsilon` (default 50).
3. Scores each cluster: beacon=50, ender_chest=30, enchanting_table=25, anvil/shulker/brewing_stand=15, bed=8, chest=3, dragon_egg=100.
4. Filters by min-score (default 30) and min-block-count (default 3), sorts desc.

**Settings to tune**:
- `baseFinderEpsilon` — raise (e.g. 100) for sprawling bases, lower (e.g. 30) for tightly packed bases.
- `baseFinderMinScore` — lower to surface small outposts, raise to filter noise.
- `baseFinderMinIndicators` — raise to filter out 1–2 random chests.

**Important**:
- **Pure cache reader.** No packets sent, no live scan, no anticheat surface. The data is already on your disk.
- **Only finds what you've explored.** Walk or elytra-fly through the area first.
- **Per-dimension.** Run separately in Overworld, Nether, End.

### What's new in this fork

The whole `#bases` command is new in this fork.

---

## 11. Following, farming, building

### Follow

```
#follow player Steve                    → follow a named player
#follow players                         → follow any nearby player
#follow entity pig                      → follow entities of a type
#follow entities                        → follow any nearby entity
```
- `followRadius` (default 3) — distance to maintain.
- `followOffsetDistance` (default 0) and `followOffsetDirection` (default 0) — offset where the bot follows from.

### Farm

```
#farm                                   → farm crops in a wide radius around you
#farm 50                                → 50-block radius
#farm 50 home                           → 50-block radius around waypoint "home"
```

Harvests, replants, and bone-meals automatically. Set `replantCrops=false` to disable replanting.

### Build

```
#build myhouse.litematic                → build from a .litematic file (in baritone/schematics/)
#build myhouse.litematic ~ 64 ~         → place origin at the given coords (~ = player position)
```

For complex scenarios (substitutions, ignoring existing blocks, layer-by-layer, etc.), see [USAGE.md](USAGE.md#building) and the `build*` settings.

### Misc

```
#tunnel                                 → dig a 1×2 tunnel straight ahead
#click                                  → click a block in your crosshair to path to it
```

---

## 12. Waypoints and the cache

### Waypoints

```
#waypoints                              → list all waypoints
#waypoints save user coolspot           → save current position as "coolspot" (tag user)
#waypoints goal coolspot                → set goal to a waypoint
#waypoints goto coolspot                → walk to a waypoint
#waypoints delete coolspot              → delete it
```

Aliases for the home waypoint:
```
#sethome                                → save current position as "home"
#home                                   → walk to "home"
```

**Auto-saved waypoints**:
- **Death** (when you die) — `doDeathWaypoints=true` (default). Click the chat link to navigate back to your stuff.
- **Bed** (when you right-click a bed) — `doBedWaypoints=true` (default).

### Chunk cache

The cache holds every chunk you've ever loaded in a compact 2-bit-per-block format. Used by `#mine`, `#bases`, `#whereportal`, `#sleep`, structure search, and elytra.

```
#repack                                 → re-cache the chunks around you
#saveall                                → save all cached regions to disk now
#reloadall                              → reload all cached regions from disk
#find chest                             → search the disk cache for blocks
```

Useful settings:
- `chunkCaching` (default true) — write to disk. Turn off if you don't want cache files.
- `cachedChunksExpirySeconds` (default 0 = forever) — auto-prune old cache entries.

---

## 13. Exploration

Continuously path toward the nearest unexplored chunk from an origin:

```
#explore                                → explore from your current position
#explore 0 0                            → explore outward from X=0 Z=0
#explorefilter filter.json              → only consider chunks listed in the filter file
```

Useful when paired with `#bases` afterwards: explore for a while, then run `#bases` to find what's out there.

---

## 14. Settings reference

**Toggle a bool**: just type its name — `#allowSprint`.
**Set a value**: `#set primaryTimeoutMS 2000`.
**Reset one**: `#set allowBreak reset`.
**Reset all**: `#reset`.
**List changed**: `#modified`.

### Most useful settings

| Setting | Default | Notes |
|---|---|---|
| `allowBreak` | true | Let Baritone break blocks |
| `allowPlace` | true | Place blocks while pathing |
| `allowSprint` | true | Sprint while moving |
| `allowParkour` | true | Sprint-jump gaps |
| `allowParkourPlace` | true | Place landing blocks mid-jump |
| `backfill` | false | Fill mined tunnels behind you |
| `blockPlacementPenalty` | 20 | Ticks penalty per block placed |
| `acceptableThrowawayItems` | cobble, dirt, netherrack | Blocks usable as scaffolding |
| `legitMine` | false | Only mine visible ores |
| `legitMineYLevel` | -57 | Y level to explore when legit mining |
| `mineMaxOreLocationsCount` | 64 | Number of ore targets to track |
| `elytraAutoJump` | true | Auto-find a takeoff ledge |
| `elytraMinimumDurability` | 5 | Land if elytra durability drops below |
| `elytraMinFireworksBeforeLanding` | 12 | Emergency land if rockets low |
| `elytraPredictTerrain` | false | Predict unloaded terrain from seed |
| `enterPortal` | true | Walk into portal when `#whereportal` arrives |
| `rightClickContainerOnArrival` | true | Auto-open containers (chest/barrel/shulker/etc.) when `#goto` arrives |
| `followRadius` | 3 | Distance to maintain when following |
| `renderCachedChunks` | false | Visualise the disk cache (GPU-heavy) |
| `doDeathWaypoints` | true | Save waypoint on death |
| `doBedWaypoints` | true | Save waypoint when right-clicking a bed |

### Autopilot + base finder (this fork)

| Setting | Default | Notes |
|---|---|---|
| `autoSleep` | false | Master enable for auto-bed-at-night |
| `autoSleepInterruptTasks` | false | Allow autosleep to yank you out of tasks |
| `baseFinderMinScore` | 30 | Minimum cluster score |
| `baseFinderMinIndicators` | 3 | Minimum block count per cluster |
| `baseFinderEpsilon` | 50 | DBSCAN cluster radius (blocks) |

---

## 15. Common workflows

### "Mine diamonds at the right Y level"
```
#goto -57                               → walk to diamond Y level (after the 1.18 cave update)
#mine diamond_ore                       → start mining
```

### "Fly somewhere far, then start mining when I land"
```
#elytra goto 5000 ~ -3000               → fly there (lifts to safe altitude automatically)
                                          arrives, lands, idle
#mine ancient_debris                    → start mining when you're ready
```

### "Find my friend's base on a multiplayer server"
```
#seedinput 8675309                      → tell the mod the seed
                                          (only needed once per world)
                                          fly around to populate the cache:
#elytra goto 10000 ~ 0
#elytra goto 0 ~ 10000
#elytra goto -10000 ~ 0
#elytra goto 0 ~ -10000
#repack                                 → flush newly-loaded chunks into the cache
#bases                                  → ranked list of likely bases
#bases 1 goto                           → path to #1
```

### "Go to a stronghold and find the End"
```
#structure stronghold                   → singleplayer: works directly
                                          multiplayer: shows chunkbase.com link
#goto -57                               → after arrival, dig down to End portal level
```

### "Survive while AFK over lunch"
```
#sethome                                → save current spot as 'home'
                                          (eat now; this mod doesn't auto-eat — Meteor does)
#autosleep on                           → at night, will go to your bed automatically
                                          come back from lunch, you're at home
```

### "Wander a Nether highway"
```
#set elytraNetherSeed <seed>            → tell the mod the seed
#set elytraPredictTerrain true          → predict unloaded netherrack
#whereportal                            → walk into a nether portal first if needed
#elytra goto 80000 ~ 80000              → fly far
```

---

## 16. Troubleshooting

| Symptom | Likely fix |
|---|---|
| Mod doesn't respond, no chat output | Check `baritone/` folder was created. Make sure you're using `#` prefix. See [§17](#17-authorization). |
| "Unauthorized user" red message | You're not on the allowlist. Send the UUID from the chat message to the mod owner. |
| "Cracked/offline launcher detected — mod disabled" | (Old behaviour) Update to current build — XUID block was removed. |
| Walks past visible ore | Run `#repack` to force a rescan. Confirm `allowBreak=true`. |
| `#elytra` does nothing | Check elytra is in chestplate slot and fireworks in inventory. Run `#elytra supported`. |
| `#elytra goto` crashes the game | Older build — update to the current one with `safeDefaultAltitude` fix. |
| `#structure` says "not found" but it's right there | Old singleplayer bug — update to current build with server-thread fix. |
| `#structure` on multiplayer says "not found" | Did you `#seedinput <seed>` first? |
| `#whereportal` says no portal cached | You haven't been near the portal yet. Walk to a portal once. |
| `#sleep` says no bed in cache | Walk near a bed once. Run `#repack`. |
| `#bases` shows no results | Cache is empty in this dimension. Explore first, or lower `baseFinderMinScore`. |
| `#nether` shows wrong direction | Use explicit form: `#nether overworld X Y Z` or `#nether nether X Y Z`. |
| Pathfinder seems slow | Increase `primaryTimeoutMS` (default 500 → try 2000). |
| Walks into lava / fire | These are avoided by default but landing chambers occasionally fail. Use `#cancel` and step in manually. |

If the bot does something weird mid-task: **`#cancel` first, ask questions later.**

---

## 17. Authorization

This is a private build with **per-user licensing**. Two accounts are baked into the JAR — adding more requires editing `Authorization.java` and rebuilding (only the mod owner can do this).

Allowed accounts get a green chat message on the first command:
```
[MinecraftAI] Licensed to: <your-name>
```

Unauthorized accounts get a red message and every command is silently consumed.

**If you see the red unauthorized message:**
1. Make a note of the UUID shown in the message.
2. Send it to the mod owner along with your in-game name.
3. They'll add it to the allowlist and send you a new JAR.

**Cracked launchers are allowed** — both real Mojang UUIDs and offline-mode UUIDs are pre-loaded in the allowlist. If your specific launcher uses a non-standard offline UUID formula, you'll see your actual UUID in the unauthorized message; send it to the owner to add.

**You cannot share this JAR.** The friend who shares it with their friend will see a watermark in chat that names them, so the source of a leak is traceable. Don't be that person.

---

## See also

- [`FEATURES.md`](FEATURES.md) — feature catalogue with implementation details.
- [`USAGE.md`](USAGE.md) — original usage reference (deeper on builder + farm).
- [`CHANGELOG.md`](CHANGELOG.md) — what was added in each version.
- [`SETUP.md`](SETUP.md) — install + build-from-source.
