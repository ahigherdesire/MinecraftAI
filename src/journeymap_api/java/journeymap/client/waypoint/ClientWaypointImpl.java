/*
 * COMPILE-ONLY STUB — not bundled into the mod jar. See ClientAPI stub.
 *
 * The final constructor parameter (waypoint group) is passed as null at the
 * single call site in JourneyMapBridge, so its exact type is irrelevant to
 * compilation; Object keeps the stub dependency-free.
 */
package journeymap.client.waypoint;

import journeymap.common.waypoint.WaypointIcon;
import journeymap.common.waypoint.WaypointPos;
import journeymap.common.waypoint.WaypointSettings;

import java.util.TreeSet;

public class ClientWaypointImpl {

    public ClientWaypointImpl(String name,
                              WaypointPos pos,
                              int color,
                              WaypointSettings settings,
                              String modId,
                              TreeSet<String> dimensions,
                              WaypointIcon icon,
                              Object group) {}
}
