/*
 * COMPILE-ONLY STUB — not bundled into the mod jar.
 *
 * Minimal stand-in for JourneyMap's client API so that
 * baritone.util.JourneyMapBridge can be compiled without the real
 * JourneyMap mod on the classpath. At runtime the real JourneyMap classes
 * are present (the bridge is only loaded when JourneyMapHelper.isAvailable()
 * confirms JM is installed); these stubs are never loaded.
 *
 * Signatures mirror the calls in JourneyMapBridge. Any mismatch with the real
 * JM API degrades gracefully — JourneyMapHelper wraps every bridge call in
 * try/catch(Throwable).
 */
package journeymap.api.client.impl;

import journeymap.api.v2.client.display.DisplayType;
import journeymap.api.v2.client.display.PolygonOverlay;

public final class ClientAPI {

    public static final ClientAPI INSTANCE = new ClientAPI();

    private ClientAPI() {}

    public void show(PolygonOverlay overlay) {}

    public void removeAll(String modId, DisplayType displayType) {}
}
