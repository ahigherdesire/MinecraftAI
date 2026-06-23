/*
 * COMPILE-ONLY STUB — not bundled into the mod jar. See ClientAPI stub.
 *
 * Models JourneyMap's fullscreen event channels closely enough for
 * JourneyMapBridge's lambda subscriptions to type-check. The nested event
 * types expose only the accessors the bridge actually calls.
 */
package journeymap.api.v2.common.event;

import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

public final class FullscreenEventRegistry {

    private FullscreenEventRegistry() {}

    public static final EventChannel<PopupMenuEvent> FULLSCREEN_POPUP_MENU_EVENT =
            new EventChannel<>();

    public static final EventChannel<AddonButtonDisplayEvent> ADDON_BUTTON_DISPLAY_EVENT =
            new EventChannel<>();

    /** A subscribable event channel. */
    public static final class EventChannel<T> {
        public void subscribe(String id, Consumer<T> handler) {}
    }

    // ── Fullscreen right-click popup menu ────────────────────────────────────

    public static final class PopupMenuEvent {
        public PopupMenu getPopupMenu() { return new PopupMenu(); }
    }

    public static final class PopupMenu {
        public void addMenuItem(String label, Consumer<MenuPos> onClick) {}
    }

    public static final class MenuPos {
        public int getX() { return 0; }
        public int getY() { return 0; }
        public int getZ() { return 0; }
    }

    // ── Addon toolbar toggle button ──────────────────────────────────────────

    public static final class AddonButtonDisplayEvent {
        public ThemeButtonDisplay getThemeButtonDisplay() { return new ThemeButtonDisplay(); }
    }

    public static final class ThemeButtonDisplay {
        public void addThemeToggleButton(String tooltip,
                                         String label,
                                         Identifier icon,
                                         boolean toggled,
                                         Consumer<ThemeToggleButton> onPress) {}
    }

    public static final class ThemeToggleButton {
        public Boolean getToggled() { return Boolean.FALSE; }
    }
}
