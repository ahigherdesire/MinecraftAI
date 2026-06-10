# Major Update — Autopilot Survival

> **Status:** ⚠️ **Partially shipped.** Only `#autosleep` made it to release. The auto-eat / auto-flee / auto-torch / `#autopilot` master toggle were **dropped** because Meteor Client already provides equivalents and duplicating them in Baritone wasn't worth the maintenance cost.
>
> See [CHANGELOG.md](CHANGELOG.md) for what actually shipped.

**What survived from this plan:**
- `#autosleep` / `#nightowl` — night-time bed autopilot
- `SleepHelper` utility (bed search + night detection)
- `AutopilotBehavior` skeleton (kept for future reactive watchers, but currently houses only the sleep tick)
- One-time experimental warning convention

**What was dropped (and why):**
- `#autoeat`, `#autoflee`, `#autotorch`, `#autopilot` — **Meteor Client already does these**
- All `autoEat*`, `autoFlee*`, `autoTorch*` settings — removed from `Settings.java`
- The four corresponding command classes — deleted from the repo
- `ClickType.SWAP` workaround / inventory swap research — no longer relevant

**What turned out to already exist in Baritone:**
- Death-waypointing — `WaypointBehavior.onPlayerDeath()` + `doDeathWaypoints` setting (default `true`)

The rest of this document is preserved as the original design spec for reference. Nothing below is a TODO.

---

Baritone currently has excellent navigation, but it ignores the player's biology. Walk into the night with zero food, no armor swap-in, and if you die it forgets where your stuff is. This update makes Baritone **keep you alive** — passively, while idle or while running other commands.

---

## Headline pitch

> *Baritone now keeps you alive.*

Five new commands, one new behavior, twelve new settings. Every feature is reactive to a measurable threshold (hunger, health, light level, time of day) — no AI guesswork, no surprises.

---

## Features

### 1. `#autoeat` — Hunger autopilot

Watches `player.getFoodData().getFoodLevel()`. When it drops below `autoEatThreshold` (default **14**), automatically:

- Find the highest-saturation edible item in inventory (skips golden apples / suspicious stew unless `autoEatAllowGapples = true`).
- Swap it to the **off-hand** slot — preserves the main-hand item, doesn't disrupt pathing.
- Hold right-click for the 1.6 s eat animation.
- Swap the off-hand back to whatever was there before.

Suppressed while sleeping, while a GUI is open, while elytra-flying (you can't eat mid-flight anyway), and during the auto-flee state.

**Settings:** `autoEat` (bool), `autoEatThreshold` (int 1–20), `autoEatAllowGapples` (bool).

```
#autoeat                → toggle on/off
#autoeat on / off       → explicit
#autoeat 10             → set threshold to 10 and enable
#autoeat status         → show current setting
```

---

### 2. `#autoflee` — Health autopilot

Watches `player.getHealth()`. When it drops below `autoFleeThreshold` (default **6 hearts**), automatically:

1. Snapshots current position as the flee origin.
2. Sets a `GoalRunAway(autoFleeDistance, origin)` — reuses the runaway code from this fork.
3. Sets a process priority above `MineProcess` but below `ElytraProcess`, so it interrupts ore-mining without yanking you out of the sky.
4. Logs `⚠ Auto-flee triggered: health=2.5, fleeing 48 blocks away.`

When health recovers above `autoFleeRecoverThreshold` (default **16**), releases control back to whatever process was running before.

**Settings:** `autoFlee`, `autoFleeThreshold`, `autoFleeRecoverThreshold`, `autoFleeDistance`.

---

### 3. `#autosleep` — Time-of-day autopilot

When `ctx.world().isDay()` flips to night (or thunderstorm) and a cached bed exists, automatically invoke the existing `SleepCommand` logic.

- Skips if a process is currently running and `autoSleepInterruptTasks = false` (the safer default — won't yank you off a mining trip).
- Reuses the bed-finding code from `SleepCommand`. That code will be extracted into a `SleepHelper.findNearestBed(ctx)` utility so the autopilot and the command share it.

**Settings:** `autoSleep`, `autoSleepInterruptTasks`.

---

### 4. `#deathpoint` — Death-location memory

Listens for the player health transition `> 0 → ≤ 0`. The tick **before** death is captured (since the death packet wipes position to spawn).

- Saves `(x, y, z, dimension, timestamp)` to a waypoint named `death-N` (auto-incremented).
- Adds a clickable chat link: `[Go to death point]` that runs `#waypoints goto death-N`.
- A rolling 5-second position buffer guards against one-shot deaths where the health watcher misses the exact tick.
- Filters out fake deaths (portal damage flicker, etc.) by requiring health ≤ 0 for ≥ 2 consecutive ticks.

**Always on by default** — pure benefit, no cost. No toggle needed.

---

### 5. `#autotorch` — Dark-area torch placement

While walking (not flying, not mining as primary task), checks ambient light at `player.blockPosition()`. If light level < `autoTorchLightLevel` (default **8** — the mob spawn threshold) and a torch is in inventory:

- Places one torch on the ground beside the player (or on a wall if a side block is available).
- Rate-limited to one torch per `autoTorchIntervalTicks` (default **100** = 5 seconds).

Disabled in the **Nether** (lava risk) and the **End** (pointless). `autoTorchOnlyInCaves = true` (default) further restricts placement to areas with sky-light < 4, so it won't torch up the surface of someone's base.

**Settings:** `autoTorch`, `autoTorchLightLevel` (int 0–15), `autoTorchIntervalTicks`, `autoTorchOnlyInCaves`.

---

### 6. `#autopilot` — Master toggle

One command that flips the four toggles together.

```
#autopilot on        → enables autoEat, autoFlee, autoSleep, autoTorch
#autopilot off       → disables all four
#autopilot status    → shows which are currently on
```

Doesn't override individual settings — just batches the group.

---

## Architecture

### New class: `baritone.behavior.AutopilotBehavior`

- Extends `Behavior`, registered in `Baritone.java` alongside `PathingBehavior`, `LookBehavior`, etc.
- Subscribes to `onTick` for periodic health / hunger / light / time-of-day checks.
- Subscribes to `onPlayerUpdate` for death detection (more reliable than tick).
- Owns the auto-eat state machine (`idle → swap → eat → restore`) — multi-tick.
- **Single source of truth.** Commands are thin wrappers that toggle settings; the behavior does the work. Same pattern as `WaypointBehavior` + `WaypointsCommand`.

### New settings in `Settings.java`

~12 new fields, all `false` by default except `deathpoint` (which has no cost).

### New commands

| Class | Aliases | Lines (est.) |
|---|---|---|
| `AutoEatCommand` | `autoeat`, `eat` | ~60 |
| `AutoFleeCommand` | `autoflee`, `paranoia` | ~60 |
| `AutoSleepCommand` | `autosleep`, `nightowl` | ~60 |
| `AutoTorchCommand` | `autotorch`, `torch` | ~60 |
| `AutopilotCommand` | `autopilot`, `survive` | ~80 |

### Refactor

Extract from `SleepCommand`:
- `SleepHelper.findNearestBed(IPlayerContext) → Optional<BlockPos>`
- `SleepHelper.isNightOrStorm(Level) → boolean`

Used by both `SleepCommand` and `AutopilotBehavior`.

---

## Edge cases being planned around

| Risk | Mitigation |
|---|---|
| Auto-eat interrupts a precise build action | Don't auto-eat if `BuilderProcess.isActive()`. Saturation can wait. |
| Auto-flee runs *into* more lava | Use `GoalRunAway` only if solid ground is beneath the player. Otherwise log `auto-flee unsafe, please intervene` and don't path. |
| Death point fires on `/kill` or portal damage flicker | Require health ≤ 0 for ≥ 2 consecutive ticks before saving. |
| Auto-torch places torches in a friend's base | `autoTorchOnlyInCaves = true` default — checks sky-light < 4, skips surface placement. |
| Auto-sleep fights with `#mine` / `#elytra` jobs | `autoSleepInterruptTasks = false` default — autopilot only activates if no process controls pathing. |
| Health watcher misses a one-shot death | Rolling buffer saves player position every 5 s; on death, save the most recent buffered position. |

---

## Implementation order (5 phases)

**Phase 1 — Foundation (no user-visible features yet)**
- Create `AutopilotBehavior`, wire into `Baritone.java`, empty tick handler.
- Add all 12 settings to `Settings.java`.
- Extract `SleepHelper` from `SleepCommand`, replace internals with calls to the helper.
- Smoke test: server starts, settings appear in `#set modified`, no regressions.

**Phase 2 — `#deathpoint` (lowest-risk, pure win)**
- Health-transition detector + rolling position buffer.
- Hook waypoint creation.
- Test by `/kill` in singleplayer.

**Phase 3 — `#autoeat` + `#autoflee` (the safety pair)**
- Eat state machine (4 sub-states, off-hand swap).
- Health watcher → `GoalRunAway` handoff.
- Add `AutoEatCommand`, `AutoFleeCommand`.
- Test: walk into starvation; walk into a zombie at low health.

**Phase 4 — `#autosleep` + `#autotorch` + `#autopilot` master**
- Time-of-day watcher hooks `SleepCommand`.
- Light-level torch placer.
- Composite `#autopilot` command.
- Test: long AFK session in survival.

**Phase 5 — Docs**
- Update `CHANGELOG.md`, `FEATURES.md`, `USAGE.md`, `README.md`, `CLAUDE.md`.

---

## What this is NOT (scope discipline)

- ❌ **Combat** — no auto-attack. Auto-flee handles defensive needs; offensive PvE is a different update.
- ❌ **Inventory management beyond food/torch** — no auto-sort, no auto-restock from chests.
- ❌ **Multi-step quest commands** ("go gather X then come back") — separate update.
- ❌ **AI prediction of player intent** — every behavior is reactive to a measurable threshold.

---

## Estimated scope

- **~1,200 lines** of new code
- **~150 lines** of refactor
- **5** new commands
- **1** new behavior
- **12** new settings

---

## Open questions (will decide before Phase 1)

1. Should `#autoflee` also trigger on fire/lava damage even if health is still above the threshold? (Leaning: yes, with a separate `autoFleeOnBurning` setting.)
2. Should `#autoeat` prefer cooked food over raw, or just go by saturation? (Leaning: saturation — that's the in-game truth.)
3. Should `#deathpoint` survive `#waypoints clear`? (Leaning: yes, give it a protected tag.)

---

*This document will be updated as implementation progresses. See `CHANGELOG.md` for shipped features.*
