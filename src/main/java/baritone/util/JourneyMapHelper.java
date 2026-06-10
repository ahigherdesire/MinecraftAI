/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.util;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Optional JourneyMap integration.
 *
 * <p>This class has NO JourneyMap imports — it is always safe to load even when
 * JourneyMap is absent. All actual JM calls live in {@link JourneyMapBridge},
 * which is only classloaded if {@link #isAvailable()} returns {@code true}.
 *
 * <p>Usage from any command:
 * <pre>
 *   // Structure found:
 *   JourneyMapHelper.addWaypoint("Village", result, JourneyMapHelper.COLOR_STRUCTURE);
 *
 *   // Base found:
 *   JourneyMapHelper.addWaypoint("Base #1", new BlockPos(cx, 64, cz), JourneyMapHelper.COLOR_BASE);
 * </pre>
 */
public final class JourneyMapHelper {

    /** Gold — used for structure waypoints. */
    public static final int COLOR_STRUCTURE = 0xFFAA00;

    /** Purple — used for player base waypoints. */
    public static final int COLOR_BASE      = 0xAA00FF;

    /** Cyan — used for portal waypoints. */
    public static final int COLOR_PORTAL    = 0x00FFEE;

    private static boolean checked   = false;
    private static boolean available = false;

    private JourneyMapHelper() {}

    /**
     * Returns {@code true} if JourneyMap is loaded and its waypoint API is
     * reachable.  Result is cached after the first call.
     */
    public static boolean isAvailable() {
        if (!checked) {
            try {
                Class.forName("journeymap.client.waypoint.WaypointHandler");
                available = true;
            } catch (Throwable t) {
                available = false;
            }
            checked = true;
        }
        return available;
    }

    /**
     * Creates a waypoint in JourneyMap at the given position with the given
     * packed-RGB colour.  Silently does nothing if JourneyMap is not installed
     * or if the API call fails for any reason.
     *
     * <p><b>Must be called on the game/client thread.</b>
     *
     * @param name   Label shown in JM (keep it short)
     * @param pos    Block position for the waypoint
     * @param color  Packed RGB colour (e.g. {@link #COLOR_STRUCTURE})
     */
    public static void addWaypoint(String name, BlockPos pos, int color) {
        if (!isAvailable()) return;
        try {
            JourneyMapBridge.create(name, pos, color);
        } catch (Throwable t) {
            // JM API changed or unavailable — fail silently, never crash Baritone.
        }
    }

    /**
     * Subscribes our "#goto" item to the JourneyMap fullscreen right-click popup menu.
     * Safe to call multiple times — only subscribes once.  Silently does nothing if JM
     * is not installed.
     *
     * <p>Called lazily from {@link baritone.command.manager.CommandManager#execute} the
     * first time any authorized command runs, guaranteeing JM is fully loaded by then.
     */
    public static void ensureSubscribed() {
        if (subscribed || !isAvailable()) return;
        try {
            JourneyMapBridge.subscribePopupMenu();
            JourneyMapBridge.subscribeHeatmapButton();
            subscribed = true;
        } catch (Throwable t) {
            // JM API changed — fail silently.
        }
    }

    private static volatile boolean subscribed = false;

    /**
     * Draws a visual heatmap on the JourneyMap fullscreen map — one colored
     * 512×512 rectangle per cell, shaded from blue (low) to red (high score).
     * Replaces any previous heatmap overlay. Silently does nothing if JM is absent.
     *
     * @param cells    list of {centerX, centerZ, score}
     * @param maxScore score of the hottest cell for colour normalisation
     */
    public static void showHeatmap(List<int[]> cells, int maxScore) {
        if (!isAvailable()) return;
        try {
            JourneyMapBridge.showHeatmap(cells, maxScore);
        } catch (Throwable t) {
            // JM API changed or unavailable — fail silently.
        }
    }

    /**
     * Removes all heatmap polygon overlays from JourneyMap.
     * Silently does nothing if JM is absent.
     */
    public static void clearHeatmap() {
        if (!isAvailable()) return;
        try {
            JourneyMapBridge.clearHeatmap();
        } catch (Throwable t) {
            // fail silently
        }
    }
}
