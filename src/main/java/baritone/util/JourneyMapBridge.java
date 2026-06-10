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

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalXZ;
import journeymap.api.client.impl.ClientAPI;
import journeymap.api.v2.client.display.DisplayType;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import journeymap.client.waypoint.ClientWaypointImpl;
import journeymap.client.waypoint.WaypointHandler;
import journeymap.common.waypoint.WaypointBackend;
import journeymap.common.waypoint.WaypointIcon;
import journeymap.common.waypoint.WaypointPos;
import journeymap.common.waypoint.WaypointScope;
import journeymap.common.waypoint.WaypointSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.TreeSet;

/**
 * The actual JourneyMap API calls. This class is package-private and is ONLY
 * loaded by the JVM when {@link JourneyMapHelper#isAvailable()} has returned
 * {@code true}, guaranteeing JM classes are on the classpath.
 *
 * <p>Do NOT reference this class from anywhere except {@link JourneyMapHelper}.
 */
final class JourneyMapBridge {

    private JourneyMapBridge() {}

    // ── Heatmap state ────────────────────────────────────────────────────────
    // Cached from the last #heatmap run, so toggling the JM button on/off can
    // redraw without re-running the scan.

    /** Whether the heatmap overlay is currently shown on the JM map. */
    static volatile boolean heatmapVisible = false;

    /** Last computed cells: each int[] is {centerX, centerZ, score}. */
    static volatile List<int[]> cachedCells = null;

    /** Score of the hottest cell in the last run, for colour normalisation. */
    static volatile int cachedMaxScore = 0;

    // ── Popup menu "#goto" ───────────────────────────────────────────────────

    /**
     * Subscribes a "#goto" item to JourneyMap's fullscreen right-click popup menu.
     * When clicked, the map position is sent to Baritone as a GoalXZ.
     * Called once from JourneyMapHelper after JM is confirmed available.
     */
    static void subscribePopupMenu() {
        FullscreenEventRegistry.FULLSCREEN_POPUP_MENU_EVENT.subscribe(
            "minecraftai",
            event -> event.getPopupMenu().addMenuItem(
                "Baritone #goto",
                pos -> {
                    IBaritone bar = BaritoneAPI.getProvider().getPrimaryBaritone();
                    if (bar == null) return;
                    bar.getCustomGoalProcess().setGoalAndPath(new GoalXZ(pos.getX(), pos.getZ()));
                }
            )
        );
    }

    // ── Heatmap toggle button ────────────────────────────────────────────────

    /**
     * Subscribes a toggle button to JourneyMap's addon toolbar (the row of
     * extra buttons along the top of the fullscreen map). The button is
     * fire-themed (🔥) and controls heatmap overlay visibility.
     *
     * <p>The event fires every time the fullscreen map is opened, so the button
     * is re-registered each time. The toggle state persists in {@link #heatmapVisible}.
     */
    static void subscribeHeatmapButton() {
        FullscreenEventRegistry.ADDON_BUTTON_DISPLAY_EVENT.subscribe(
            "minecraftai_heatmap",
            event -> {
                // Icon: fire charge (heat-themed) — any MC texture path works here
                Identifier icon = Identifier.withDefaultNamespace(
                        "textures/item/fire_charge.png");

                event.getThemeButtonDisplay().addThemeToggleButton(
                    "Heatmap Overlay — toggle heat zones on/off",  // tooltip
                    "Heat",                                         // label text
                    icon,
                    heatmapVisible,                                 // current state
                    btn -> {
                        heatmapVisible = Boolean.TRUE.equals(btn.getToggled());
                        if (heatmapVisible) {
                            // Redraw from cache if we have data
                            List<int[]> cells = cachedCells;
                            if (cells != null && !cells.isEmpty()) {
                                drawHeatmapOverlays(cells, cachedMaxScore);
                            }
                        } else {
                            removeHeatmapOverlays();
                        }
                    }
                );
            }
        );
    }

    // ── Heatmap drawing ──────────────────────────────────────────────────────

    /**
     * Stores the computed cells in cache, marks the overlay visible, and draws
     * colored polygons on the JM fullscreen map.
     *
     * @param cells    all cells: each int[] is {centerX, centerZ, score}, sorted high→low
     * @param maxScore score of the hottest cell for colour normalisation
     */
    static void showHeatmap(List<int[]> cells, int maxScore) {
        cachedCells    = cells;
        cachedMaxScore = maxScore;
        heatmapVisible = true;
        drawHeatmapOverlays(cells, maxScore);
    }

    /** Clears the visual overlay but keeps the cached data so the button can re-enable it. */
    static void clearHeatmap() {
        heatmapVisible = false;
        removeHeatmapOverlays();
    }

    // ── Internal drawing helpers ─────────────────────────────────────────────

    private static void drawHeatmapOverlays(List<int[]> cells, int maxScore) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || cells.isEmpty() || maxScore <= 0) return;

        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim =
                mc.level.dimension();

        // Clear any stale overlays first
        removeHeatmapOverlays();

        for (int i = 0; i < cells.size(); i++) {
            int[] cell  = cells.get(i);
            int   cx    = cell[0];
            int   cz    = cell[1];
            int   score = cell[2];

            float t       = Math.min(1.0f, (float) score / maxScore);
            int   fillRgb = heatColor(t);

            // Each cell is 32×32 blocks; half-extent = 16
            MapPolygon rect = new MapPolygon(
                    new BlockPos(cx - 16, 64, cz - 16),
                    new BlockPos(cx + 16, 64, cz - 16),
                    new BlockPos(cx + 16, 64, cz + 16),
                    new BlockPos(cx - 16, 64, cz + 16)
            );

            ShapeProperties style = new ShapeProperties()
                    .setFillColor(fillRgb)
                    .setFillOpacity(0.42f)
                    .setStrokeColor(fillRgb)
                    .setStrokeOpacity(0.85f)
                    .setStrokeWidth(1.0f);

            PolygonOverlay overlay = new PolygonOverlay(
                    "minecraftai_hm_" + i,
                    dim,
                    style,
                    rect
            );

            try {
                ClientAPI.INSTANCE.show(overlay);
            } catch (Throwable ignored) {}
        }
    }

    private static void removeHeatmapOverlays() {
        try {
            ClientAPI.INSTANCE.removeAll("minecraftai", DisplayType.Polygon);
        } catch (Throwable ignored) {}
    }

    /**
     * Heat-colour gradient: blue (t=0) → yellow (t=0.5) → red (t=1).
     * Returns packed 0xRRGGBB.
     */
    private static int heatColor(float t) {
        int r, g, b;
        if (t < 0.5f) {
            float u = t * 2f;
            r = (int) (255 * u);
            g = (int) (255 * u);
            b = (int) (255 * (1f - u));
        } else {
            float u = (t - 0.5f) * 2f;
            r = 255;
            g = (int) (255 * (1f - u));
            b = 0;
        }
        return (r << 16) | (g << 8) | b;
    }

    // ── Waypoints ────────────────────────────────────────────────────────────

    static void create(String name, BlockPos pos, int color) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;

        String dim = mc.level.dimension().identifier().toString();
        int y = pos.getY() > 0 ? pos.getY() : 64;

        WaypointPos wpPos    = new WaypointPos(pos.getX(), y, pos.getZ(), dim);
        WaypointSettings cfg = new WaypointSettings();
        WaypointIcon icon    = new WaypointIcon(WaypointIcon.DEFAULT_ICON_NORMAL);

        TreeSet<String> dims = new TreeSet<>();
        dims.add(dim);

        ClientWaypointImpl wp = new ClientWaypointImpl(
            name, wpPos, color, cfg, "minecraftai", dims, icon, null
        );

        WaypointHandler handler = WaypointHandler.getInstance();
        if (handler == null) return;

        WaypointBackend backend = handler.getBackend();
        if (backend == null) return;

        WaypointScope scope = backend.getDefaultScope();
        backend.saveWaypoint(scope, wp, true);
    }
}
