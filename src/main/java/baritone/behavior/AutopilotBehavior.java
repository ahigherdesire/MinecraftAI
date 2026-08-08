/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.behavior;

import baritone.Baritone;
import baritone.api.event.events.TickEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.utils.Helper;
import baritone.api.utils.input.Input;
import baritone.util.SleepHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

/**
 * The Autopilot Survival behavior — auto-sleep only.
 *
 * <p>Originally planned with four reactive watchers (eat / flee / sleep / torch), but
 * eat / flee / torch (and the master toggle) were dropped because Meteor Client already
 * provides equivalent features. This fork keeps only the auto-sleep piece, which is
 * built on Baritone's own bed cache and dovetails with the existing {@code #sleep}
 * command via the shared {@link SleepHelper}.
 *
 * <p>Death-point waypointing remains handled by {@code WaypointBehavior.onPlayerDeath()}
 * via the built-in {@code doDeathWaypoints} setting — no code needed here.
 */
public final class AutopilotBehavior extends Behavior implements AbstractGameEventListener {

    /** Set once the auto-sleep watcher has issued a goal for the current night. */
    private boolean sleepInProgress = false;

    /** Latched once a mining failsafe has fired, until mining stops. */
    private boolean mineFled = false;

    /** Whether we're currently forcing the "use" input to eat. */
    private boolean eatingHeld = false;

    /** Tracks {@code autoSleep}'s previous tick value to fire a one-time experimental warning. */
    private boolean prevAutoSleep = false;

    public AutopilotBehavior(Baritone baritone) {
        super(baritone);
    }

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() != TickEvent.Type.IN) {
            // World unloading — reset state so re-entering re-fires the warning.
            this.sleepInProgress = false;
            this.prevAutoSleep = false;
            this.mineFled = false;
            setEatingHeld(false);
            return;
        }
        if (ctx.player() == null || ctx.world() == null) return;

        checkExperimentalWarning();
        tickSleep();
        tickMineGuards();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  MINING GUARDS  (only while #mine is running)
    //   • low health          → #stop + mineFleeCommand (/home)
    //   • player within range  → #stop + mineFleeCommand (/home)
    //   • N stacks of one item → #stop + mineFleeCommand (deposit yourself)
    //   • low hunger           → auto-eat cooked beef from the hotbar
    // ════════════════════════════════════════════════════════════════════════

    private void tickMineGuards() {
        final boolean mining = baritone.getMineProcess() != null && baritone.getMineProcess().isActive();
        if (!mining) {
            mineFled = false;
            setEatingHeld(false);
            return;
        }
        final LocalPlayer p = ctx.player();
        if (p == null) return;

        // Auto-eat runs independently — it doesn't stop mining.
        tickAutoEat(p);

        if (mineFled) return; // already fled this mining session

        // 1) Low health
        if (Baritone.settings().mineFleeOnLowHealth.value) {
            float hp = p.getHealth();
            if (hp > 0.0f && hp <= Baritone.settings().mineFleeHealth.value) {
                flee(String.format("Health low (%.1f hearts)", hp / 2.0f));
                return;
            }
        }
        // 2) Player nearby
        if (Baritone.settings().mineFleeOnPlayer.value) {
            String who = nearbyPlayerName(Baritone.settings().mineFleePlayerRadius.value);
            if (who != null) {
                flee("Player nearby: " + who);
                return;
            }
        }
        // 3) Held tool durability low
        int durThreshold = Baritone.settings().mineFleeDurability.value;
        if (durThreshold > 0) {
            ItemStack held = p.getInventory().getSelectedItem();
            if (held != null && !held.isEmpty() && held.isDamageableItem()) {
                int left = held.getMaxDamage() - held.getDamageValue();
                if (left <= durThreshold) {
                    flee("Tool durability low (" + left + " left)");
                    return;
                }
            }
        }
        // 4) N full stacks of a specific item (e.g. raw_gold)
        int itemStacks = Baritone.settings().mineFleeItemStacks.value;
        if (itemStacks > 0) {
            String id = Baritone.settings().mineFleeItem.value;
            int have = stacksOfItem(p, id);
            if (have >= itemStacks) {
                flee(have + " stacks of " + id + " collected");
                return;
            }
        }
    }

    /** Full-(64)-stack count of the item whose registry path equals {@code itemPath}. */
    private int stacksOfItem(LocalPlayer p, String itemPath) {
        try {
            int total = 0;
            for (ItemStack st : p.getInventory().getNonEquipmentItems()) {
                if (st == null || st.isEmpty()) continue;
                if (BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().equalsIgnoreCase(itemPath)) {
                    total += st.getCount();
                }
            }
            return total / 64;
        } catch (Throwable t) {
            return 0;
        }
    }

    private void flee(String reason) {
        mineFled = true;
        setEatingHeld(false);
        final String cmd = Baritone.settings().mineFleeCommand.value;
        logHelper("⚠ " + reason + " while mining — stopping and running " + cmd);
        baritone.getPathingBehavior().cancelEverything();
        runFleeCommand(cmd);
    }

    /** Name of the nearest OTHER player within {@code radius}, or {@code null}. */
    private String nearbyPlayerName(double radius) {
        try {
            final double r2 = radius * radius;
            final LocalPlayer self = ctx.player();
            for (Player pl : ctx.world().players()) {
                if (pl == self || pl == null) continue;
                if (pl.distanceToSqr(self) <= r2) return pl.getName().getString();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // ── Auto-eat (best effort: the input handler suppresses "use" while actively
    //    breaking a block, so eating happens in the gaps between breaks) ────────

    private void tickAutoEat(LocalPlayer p) {
        if (!Baritone.settings().mineAutoEat.value) {
            setEatingHeld(false);
            return;
        }
        if (p.getFoodData().getFoodLevel() > Baritone.settings().mineAutoEatHunger.value) {
            setEatingHeld(false); // full enough
            return;
        }
        int slot = hotbarSlotOf(p, Items.COOKED_BEEF);
        if (slot < 0) slot = hotbarSlotOf(p, Items.BEEF);
        if (slot < 0) {
            setEatingHeld(false); // no beef in the hotbar
            return;
        }
        p.getInventory().setSelectedSlot(slot);
        setEatingHeld(true); // hold "use" to eat
    }

    private int hotbarSlotOf(LocalPlayer p, Item item) {
        var items = p.getInventory().getNonEquipmentItems();
        for (int i = 0; i < 9 && i < items.size(); i++) {
            ItemStack st = items.get(i);
            if (st != null && !st.isEmpty() && st.getItem() == item) return i;
        }
        return -1;
    }

    private void setEatingHeld(boolean held) {
        if (held == eatingHeld) return;
        eatingHeld = held;
        try {
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, held);
        } catch (Throwable ignored) {}
    }

    /** Runs the flee command: {@code /x} → server command, {@code #x} → Baritone command. */
    private void runFleeCommand(String raw) {
        try {
            String c = raw == null ? "" : raw.trim();
            if (c.isEmpty()) return;
            if (c.startsWith("#")) {
                baritone.getCommandManager().execute(c.substring(1));
            } else {
                // Server command (Essentials /home etc.) — sendCommand takes it without the slash.
                Minecraft mc = Minecraft.getInstance();
                if (mc.getConnection() != null) {
                    mc.getConnection().sendCommand(c.startsWith("/") ? c.substring(1) : c);
                }
            }
        } catch (Throwable t) {
            logHelper("Failed to run flee command '" + raw + "': " + t.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Experimental-setting warning (one-time, on false→true transition)
    //  Mirrors the elytraWaveMode / elytraConserveFireworks pattern.
    // ════════════════════════════════════════════════════════════════════════

    private void checkExperimentalWarning() {
        final boolean curAutoSleep = Baritone.settings().autoSleep.value;
        if (curAutoSleep && !prevAutoSleep) {
            logHelper("⚠ autoSleep is EXPERIMENTAL. Requires a previously-cached bed; will not "
                    + "search beyond the disk cache. Will not interrupt active tasks unless "
                    + "autoSleepInterruptTasks=true. Disable with #autosleep off.");
        }
        prevAutoSleep = curAutoSleep;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  AUTO-SLEEP
    // ════════════════════════════════════════════════════════════════════════

    private void tickSleep() {
        if (!Baritone.settings().autoSleep.value) {
            sleepInProgress = false;
            return;
        }
        if (ctx.player().isSleeping()) {
            sleepInProgress = false; // already asleep, done for the night
            return;
        }
        if (!SleepHelper.isNightOrStorm(ctx.world())) return;

        // Don't yank an active task unless explicitly allowed
        if (!Baritone.settings().autoSleepInterruptTasks.value) {
            if (baritone.getPathingControlManager().mostRecentInControl().isPresent()) return;
        }

        if (sleepInProgress) return; // already navigating to a bed

        Optional<BlockPos> bed = SleepHelper.findNearestBed(ctx);
        if (bed.isEmpty()) return; // no bed cached — silently skip

        sleepInProgress = true;
        BlockPos b = bed.get();
        logHelper("Night detected. Navigating to bed at X=" + b.getX()
                + " Y=" + b.getY() + " Z=" + b.getZ() + ".");
        baritone.getCustomGoalProcess().setGoalAndPath(new GoalNear(b, 1));
        // Right-click on arrival is intentionally not automated here — use the explicit
        // #sleep command if you want that. This watcher only handles navigation.
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Public read-only accessor
    // ════════════════════════════════════════════════════════════════════════

    public boolean isSleepInProgress() { return sleepInProgress; }

    private void logHelper(String msg) {
        Helper.HELPER.logDirect("[Autopilot] " + msg);
    }
}
