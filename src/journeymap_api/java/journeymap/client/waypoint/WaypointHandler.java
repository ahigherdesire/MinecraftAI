/* COMPILE-ONLY STUB — not bundled into the mod jar. See ClientAPI stub. */
package journeymap.client.waypoint;

import journeymap.common.waypoint.WaypointBackend;

public class WaypointHandler {

    public static WaypointHandler getInstance() { return new WaypointHandler(); }

    public WaypointBackend getBackend() { return new WaypointBackend(); }
}
