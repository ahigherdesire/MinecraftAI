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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone. If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.auth;

import baritone.api.utils.Helper;
import net.minecraft.network.chat.Component;

/**
 * Command-dispatch authorization gate.
 *
 * <p>Reads the RSA-signed license token from {@code baritone/license.key}.
 * If no license is present, tells the player how to activate.
 * If the token is invalid or expired, shows Error 001.
 * If valid, caches the result and shows a green watermark on first command.
 *
 * <p>To add a new user: run {@code generate_license.ps1 -Name "IGN" -Days 90},
 * send the output token, they type {@code #activate <token>} once. Done.
 * No recompile, no UUID lookup, no source edits required.
 *
 * <p>To revoke: tokens expire automatically. Generate short-lived tokens (30-90 days)
 * and don't renew when they expire.
 */
public final class Authorization {

    private static volatile boolean checkRan       = false;
    private static volatile boolean authorized     = false;
    private static volatile boolean watermarkLogged = false;
    private static volatile String  licensedName   = null;

    private Authorization() {}

    /**
     * Returns {@code true} if the current session has a valid license.
     * Result is cached; call {@link #reset()} after writing a new license file
     * (e.g. from {@code #activate}) to force a re-check.
     */
    public static synchronized boolean isAuthorized() {
        if (checkRan) {
            if (authorized && !watermarkLogged) {
                Helper.HELPER.logDirect(
                        Component.literal("[MinecraftAI] Licensed to: " + licensedName)
                                .withStyle(net.minecraft.ChatFormatting.GREEN));
                watermarkLogged = true;
            }
            return authorized;
        }

        try {
            doCheck();
        } catch (Throwable t) {
            authorized = false;
            Helper.HELPER.logDirect(
                    Component.literal("[MinecraftAI] Error 001. Please contact owner.")
                            .withStyle(net.minecraft.ChatFormatting.RED));
        }
        checkRan = true;
        return authorized;
    }

    /**
     * Clears the cached result so the next {@link #isAuthorized()} call reads
     * the license file fresh. Called by {@code #activate} after saving a new token.
     */
    public static synchronized void reset() {
        checkRan       = false;
        authorized     = false;
        watermarkLogged = false;
        licensedName   = null;
    }

    private static void doCheck() {
        LicenseValidator.Result result = LicenseValidator.check();
        switch (result.status) {
            case VALID -> {
                authorized   = true;
                licensedName = result.name;
            }
            case NO_LICENSE -> {
                authorized = false;
                Helper.HELPER.logDirect(
                        Component.literal("[MinecraftAI] Type #activate <token> to activate.")
                                .withStyle(net.minecraft.ChatFormatting.YELLOW));
            }
            case EXPIRED, INVALID -> {
                authorized = false;
                Helper.HELPER.logDirect(
                        Component.literal("[MinecraftAI] Error 001. Please contact owner.")
                                .withStyle(net.minecraft.ChatFormatting.RED));
            }
        }
    }
}
