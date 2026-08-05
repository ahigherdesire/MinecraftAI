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
import baritone.util.SleepHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

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

    /** Latched while health is low so the mining failsafe fires once per low-health episode. */
    private boolean mineFleeTriggered = false;

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
            this.mineFleeTriggered = false;
            return;
        }
        if (ctx.player() == null || ctx.world() == null) return;

        checkExperimentalWarning();
        tickSleep();
        tickMineFlee();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  LOW-HEALTH MINING FAILSAFE
    //  While #mine is running, if health drops to/below mineFleeHealth, stop
    //  everything (like #stop) and run mineFleeCommand (default /home).
    // ════════════════════════════════════════════════════════════════════════

    private void tickMineFlee() {
        if (!Baritone.settings().mineFleeOnLowHealth.value) {
            mineFleeTriggered = false;
            return;
        }
        // Only guard while the mine process is actually running.
        if (baritone.getMineProcess() == null || !baritone.getMineProcess().isActive()) {
            mineFleeTriggered = false;
            return;
        }

        final float hp = ctx.player().getHealth();
        if (hp <= 0.0f) return; // already dead — nothing to do

        // Re-arm once health recovers back above the threshold.
        if (hp > Baritone.settings().mineFleeHealth.value) {
            mineFleeTriggered = false;
            return;
        }
        if (mineFleeTriggered) return; // already fired for this low-health episode
        mineFleeTriggered = true;

        final String cmd = Baritone.settings().mineFleeCommand.value;
        logHelper(String.format("⚠ Health low (%.1f hearts) while mining — stopping and running %s",
                hp / 2.0f, cmd));

        // #stop: cancel mining + all pathing first.
        baritone.getPathingBehavior().cancelEverything();

        // Then run the escape command.
        runFleeCommand(cmd);
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
