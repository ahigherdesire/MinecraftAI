# Features

## Access control

MinecraftAI is access-controlled via **RSA-signed license tokens**. No UUID configuration required on the owner's end.

- **`#activate <token>`** — enter a token once to activate. Saved to `baritone/license.key` and verified automatically on every future session.
- Tokens embed a display name and an expiry date. When the token expires the mod stops working until a new token is issued.
- The owner generates tokens with `tools/generate_license.ps1` — no source edit or recompile needed.
- Anyone without a valid token sees only: `Error 001. Please contact owner.`

## JourneyMap integration

When [JourneyMap](https://www.curseforge.com/minecraft/mc-mods/journeymap) is installed alongside this mod:

- **Auto-waypoints** — `#structure` and `#where` automatically drop a **gold** JourneyMap waypoint when a structure is found. `#bases` drops a **purple** waypoint for each detected base cluster. `#heatmap` drops a waypoint at every top-N hotspot centre.
- **Heatmap overlay** — `#heatmap` draws colored 32×32-block polygons across the fullscreen map (blue → yellow → red by activity score). A **"Heat"** toggle button in JM's addon toolbar shows/hides the overlay without re-scanning.
- **Right-click #goto** — right-clicking anywhere on the fullscreen map shows a **"Baritone #goto"** option. Clicking it sends Baritone to that map position. Works on multiplayer where JourneyMap's own Teleport option is greyed out.

The integration activates automatically if JourneyMap is present and requires no configuration.

## 🛡️ Autopilot Survival &nbsp;·&nbsp; 🧪 EXPERIMENTAL

> ⚠️ **Experimental.** `#autosleep` may misfire or interact poorly with other Baritone processes (`#mine`, `#elytra`, `#farm`). Prints a one-time in-chat warning when enabled. Toggle off with `#autosleep off`.

- **`#autosleep`** — at night or during a thunderstorm, navigates to the nearest cached bed (any of 16 colours). Won't interrupt an active process unless `autoSleepInterruptTasks = true`. Cache-only — walk near your bed once so Baritone learns its location.
- **Death waypoints** — built-in Baritone feature (`doDeathWaypoints`, default `true`). Each death creates a clickable `death @ <timestamp>` waypoint — independently of this update.

**Dropped from this update:** the originally-planned `#autoeat`, `#autoflee`, `#autotorch`, and `#autopilot` master toggle were removed because **Meteor Client already provides equivalents** and duplicating them in Baritone wasn't worth the maintenance cost. The design spec for the dropped pieces remains in `UPCOMING.md` as a reference.

## Player intelligence

### Player tracker — `#players`

- **`#players`** — lists every player ever seen, sorted by most-recently spotted. Each entry shows name, horizontal distance from you now, last-seen coordinates, dimension, and time elapsed since last sighting.
  - **`#players <name>`** — full sighting history for that player (up to 20 entries newest-first), with ISO timestamps and coordinates.
  - **`#players goto <name>`** — sets a `GoalXZ` goal at their last known X/Z position and starts pathing. Warns if the sighting was in a different dimension.
  - **`#players forget <name>`** — removes the player's entire record. Partial, case-insensitive name matching.
  - **`#players clear`** — wipe the entire database.
  - **`#players stats`** — show total player count in memory.
- **Always-on recording** — `ThreatsBehavior` scans `ClientLevel.players()` every tick and records every non-self player entity. No enable required; data accumulates automatically from the moment you load a world.
- **Persistent** — saved to `baritone/player_memory.json` (throttled async write, at most once per 15 s). Up to 20 sightings per player retained.
- Aliases: `#player`, `#seen`.

### Threat alerts — `#threats`

- **`#threats`** — toggles proximity alerts. When ON, fires an in-chat warning the first time each player enters within the configured radius each session.
  - Alert format: `[Threats] ⚠ PLAYER NEARBY: Steve  X=123 Y=64 Z=456  (~45 blocks)  [overworld]`
  - **`#threats on/off`** — explicit toggle.
  - **`#threats <N>`** — set alert radius in blocks (1–2048; default 64). Distance check is 3D Euclidean.
  - **`#threats status`** — show whether enabled, current radius, and cooldown.
- **Anti-spam** — 30-second re-alert cooldown per player. Cooldown resets if the player leaves render distance and comes back, so a player stalking you will always trigger a fresh alert.
- **Player memory is independent** — disabling threats stops alerts but does NOT stop recording sightings. Use `#threats off` freely; `#players` data keeps accumulating.
- Alias: `#threat`.

## Cache intelligence

### Chest content memory

- **`#chest <item>`** — search every container you've ever opened for items matching the query. Results are sorted by total quantity and show the chest's coordinates, dimension, and matched item counts.
  - **Automatic capture** — every time you open a chest, barrel, shulker box, hopper, dropper, or other container, MinecraftAI silently records its BlockPos and contents. Nothing to enable.
  - **Partial search** — `#chest diamond` matches `minecraft:diamond`, `minecraft:diamond_sword`, `minecraft:diamond_ore`, etc. Case-insensitive.
  - **Navigate** — append `goto` to immediately path to the best result: `#chest diamond goto`. Or search first, then use `#chest <N> goto` for the Nth result.
  - **Housekeeping** — `#chest list` browses all recorded containers (paginated); `#chest forget <N>` removes a record; `#chest clear` wipes everything; `#chest stats` shows database size.
  - **Persistent** — saved to `baritone/chest_memory.json` and reloaded automatically on every session.

### Activity heatmap

- **`#heatmap`** — scores every **32×32-block chunk cell** in Baritone's cache by weighted density of player-indicator blocks and draws a live colour overlay on the JourneyMap fullscreen map.
  - **Color scale:** 🔵 blue (cold) → 🟡 yellow (moderate) → 🔴 red (very hot). Hottest cell = red; all others scaled relative to it.
  - **Toggle button:** a **"Heat"** button in JourneyMap's addon toolbar shows/hides the overlay without re-scanning.
  - **Broader signals than `#bases`:** includes nether portals, hoppers, observers, player heads, jukeboxes alongside the standard base-quality blocks.
  - **Workflow:** `#heatmap` → spot red cluster on JM map → `#bases` inside that area for the exact base footprint.
  - Subcommands: `#heatmap` (top 10), `#heatmap <N>`, `#heatmap <N> goto`. Alias: `#activity`.

## Base finder

- **`#bases`** — Scans Baritone's per-dimension chunk cache for **clusters** of "base indicator" blocks (beacons, ender chests, enchanting tables, anvils, shulkers, brewing stands, beds, chests). Each cluster gets a weighted score; the top N are likely player bases.
- **Pure cache reader** — no packets sent, no live scan, no anticheat surface. The data is already on your disk.
- **DBSCAN clustering** with configurable `baseFinderEpsilon` (default 50 blocks). Cluster scores use a weight table where beacons count 50, ender chests 30, enchanting tables 25, shulkers 15, beds 8, chests 3.
- **Subcommands:**
  - `#bases` — top 10 by score
  - `#bases <N>` — top N
  - `#bases pie` — type breakdown (how many of each indicator across the whole cache)
  - `#bases <N> goto` — path to the Nth base via `GoalXZ`
- **Only finds what you've explored.** Best workflow: elytra-fly long distances at high altitude with `chunkCaching=true` to populate the cache, then run `#bases`.
- **Per-dimension.** Run separately in the Overworld, Nether, and End.
- Aliases: `#basefinder`.

## Sleep and retreat

- **`#sleep`** — explicit one-shot: finds the nearest cached bed (any of 16 colours), navigates with `GoalNear(bed, 1)`, then on a background thread waits for night and right-clicks to sleep. Cancel any time with `#cancel`.
- **`#runaway <distance>`** — sets `GoalRunAway(distance, playerFeet)`. Origin is locked at the moment the command runs so the bot stops once far enough. Default distance 32. Aliases `#flee`, `#escape`.

## Pathfinding

- **Long-distance spliced pathing** — calculates in segments, pre-calculates the next segment before the current one ends so movement is continuous.
- **Chunk caching** — explored chunks are compressed to a 2-bit-per-block representation (AIR / SOLID / WATER / AVOID) and optionally saved to disk for faster long-distance routing.
- **Block breaking** — considers tool set and hotbar when deciding whether to mine through obstacles. Diamond Eff V pick makes stone walls cheap; a wood pick makes climbing over them cheaper.
- **Block placing** — sneak-back-placing, pillaring, bridging. Configurable penalty (default 1 second) to conserve resources.
- **Falling** — up to 3 blocks onto solid ground (configurable). With a water bucket: up to 23 blocks. Unlimited into existing still water.
- **Vines and ladders** — climbs, descends, and grabs mid-air to break falls. Optional strafe-to-adjacent-column support (`allowVines`).
- **Doors and fence gates**
- **Slabs and stairs**
- **Falling-block awareness** — accounts for the cost of gravel/sand columns above a target block.
- **Danger avoidance** — fire, magma, lava corners, blocks touching liquids.
- **Parkour** — sprint-jumping over 1–3 block gaps; parkour-place (place the landing block mid-jump).

## Mining

- **Nearest-ore-first scanning** — chunk scanner runs in spiral order (nearest chunk first) so the 64 ore targets tracked at any time are always the closest available, not random distant cached ones.
- **Always live-scans loaded chunks** — the disk cache no longer short-circuits the live scan. Both sources are merged and distance-sorted on every rescan.
- **Automatic deepslate pairing** — specifying `iron_ore` automatically includes `deepslate_iron_ore` and vice versa, no need to list both.
- **Dropped-item pickup** — after breaking ore, lingers briefly to pick up drops before moving to the next target.
- **Legit mine mode** — optional setting that restricts mining to ores the player can actually see (no x-ray effect).

## Elytra flying

- **Any dimension** — works in the overworld, Nether, End, and custom dimensions. No longer restricted to the Nether.
- **`#elytra goto X Z` / `#elytra goto X Y Z`** — fly directly to coordinates without setting a goal first. Supports tilde notation: `#elytra goto ~500 ~ ~-200` flies to a position relative to your current location.
- **Crash-proof destination handling** — if the target Y is inside terrain (loaded chunk) or in an unloaded chunk where seed-predicted terrain might be solid, the bot lifts to a safe altitude. Underground targets trigger an automatic land-and-mine-down handoff to `CustomGoalProcess`. (The native pathfinder segfaults the entire JVM on solid destinations — this guard was the original `#elytra goto 0 0 0` crash fix.)
- **Full-height obstacle avoidance** — the voxel octree covers the complete world height for every dimension (overworld −64 to +320). Mountains, hills, and structures above sea level are correctly avoided.
- **Correct Y-coordinate mapping** — blocks at any world height are stored at their true absolute Y in the pathfinder's octree, including below sea level (Y < 0 in the overworld).
- **Live terrain updates** — block changes at any Y level are reflected immediately in the pathfinder, not just below Y=128.
- **Auto takeoff** — finds a nearby ledge or cliff to jump from automatically (`elytraAutoJump`).
- **Auto landing** — finds a safe landing spot when the path is complete, or emergency-lands on low durability/fireworks.
- **Dimension-aware safe landing** — Nether lands on netherrack/gravel/basalt/blackstone; overworld/End accepts any solid non-hazardous block.
- **Nether terrain prediction** — optionally uses the world seed to predict terrain in unloaded Nether chunks ahead of the flight path (`elytraPredictTerrain`).

## Structure finding

- **`#structure <name>`** — locates the nearest named structure and navigates to it. Runs asynchronously so there's no tick stutter.
  - **Singleplayer:** uses the integrated server's chunk generator — works for unexplored areas, no seed needed.
  - **Multiplayer:** uses the world seed (entered via `#seedinput`) and RandomSpreadStructurePlacement grid math to calculate candidate positions client-side.
- **`#where <structure>`** — same search, but only reports coordinates, distance, and compass direction — no navigation started. Useful for scouting.
- **`#where`** (no argument) — prints current X Y Z and dimension name.
- **Full structure list:** `stronghold`, `village`, `nether_fortress` / `fortress`, `bastion` / `bastion_remnant`, `mansion` / `woodland_mansion`, `monument` / `ocean_monument`, `ancient_city`, `end_city`, `buried_treasure`, `desert_pyramid` / `desert_temple`, `jungle_pyramid` / `jungle_temple`, `pillager_outpost` / `outpost`, `shipwreck`, `mineshaft` / `mine`, `igloo`, `swamp_hut` / `witch_hut`, `ruined_portal`, `ocean_ruin` / `ocean_ruins`, `trial_chambers` / `trial_chamber`, `trail_ruins`.
- All searches run on a background thread and print results to chat when done.

## Multiplayer seed-based features

- **`#seedinput <seed>`** — stores the world seed to `baritone/seed.txt`, persisted across restarts. Required to use `#structure` and `#where` on multiplayer servers.
- **`#seedinput`** (no arg) — shows the currently stored seed.
- **`#seedinput clear`** — deletes the stored seed.
- **Client-side structure math** — for RandomSpreadStructurePlacement structures (villages, fortresses, bastions, etc.) the mod calculates candidate chunk positions from the seed without any server involvement.
- **Stronghold limitation** — strongholds use ConcentricRingsStructurePlacement which requires biome data unavailable client-side. On multiplayer, the command redirects you to chunkbase.com with your stored seed.

## Coordinate utilities

- **`#nether`** (no args) — converts your current X Y Z between dimensions based on which dimension you're in. X and Z are scaled ÷8 (overworld→nether) or ×8 (nether→overworld). Y is the same in both.
- **`#nether X Y Z`** — convert given coordinates; direction is auto-detected from current dimension.
- **`#nether X Z`** — same but without Y.
- **`#nether overworld X Y Z`** / **`#nether nether X Y Z`** — explicit direction regardless of current dimension.
- Aliases: `#nc`, `#coords`.

## Portal navigation

- **`#whereportal`** — navigates to the nearest nether portal in the current dimension. Portal blocks are tracked in Baritone's block cache, so any portal previously visited is found instantly. If none are cached, the bot explores to find one.
- **Auto-entry** — if the `enterPortal` setting is enabled (default: true), the bot walks directly into the portal block and teleports through it.
- **`#portal skip`** — blacklist the nearest portal and search for the next one. Useful when the closest portal is unreachable (trapped, lava-blocked, etc.).
- Works in both directions: overworld portals (→ Nether) and nether portals (→ Overworld).
- Aliases: `#portal`, `#findportal`.

## Container interaction (expanded)

`#goto <container_block>` will walk up to and (if `rightClickContainerOnArrival = true`) automatically open the container on arrival. Supported containers now include:

- **Workstations:** crafting table, furnace, blast furnace, smoker.
- **Storage:** chest, trapped chest, ender chest, barrel, all 17 shulker box variants (16 colours + undyed).

Chests / ender chests / trapped chests / shulker boxes also require headroom — Baritone removes the block above when navigating to them. Barrels open like a door so they need no clearance.

## Other automation

- **Farming** — harvests, replants, and bone-meals crops automatically within a configurable radius.
- **Builder** — builds Litematica / NBT schematics block by block.
- **Explorer** — systematically explores the world from an origin point, always pathing toward the nearest unseen chunk.
- **Follower** — follows a player or entity type, staying within a configurable radius.
- **Backfill** — fills in mined tunnels behind the player.
- **Waypoints** — named positions saved to disk. Auto-saves death and bed locations.

## Pathfinding internals

- **A\* with segmented calculation** — exits early at the render distance edge or on timeout, selects best partial segment via incremental cost backoff.
- **Minimum improvement repropagation** — skips repropagating alternate routes with < 0.01 tick improvement.
- **Backtrack cost favoring** — reduces cost of backtracking the current segment so the path can splice onto the next segment sooner.
- **Backtrack detection and pausing** — pauses execution when the best calculated path passes back through the player's current position, avoiding unnecessary forward movement.
