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

package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.auth.Authorization;
import baritone.auth.LicenseValidator;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * One-time license activation command.
 *
 * <p>Tokens are ~380 chars — too long for MC chat (256 limit).
 * Primary flow: copy token to clipboard, type {@code #activate}, done.
 *
 * <p>This command is intentionally exempt from the authorization check in
 * {@code CommandManager} so that unlicensed players can activate.
 */
public class ActivateCommand extends Command {

    public ActivateCommand(IBaritone baritone) {
        super(baritone, "activate", "license");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        String token;

        if (!args.hasAny()) {
            // Clipboard activation: copy token, type #activate
            token = readClipboard();
            if (token == null || token.isEmpty()) {
                logDirect("[MinecraftAI] Copy your token to clipboard, then type #activate");
                return;
            }
            logDirect("[MinecraftAI] Read token from clipboard...");
        } else {
            args.requireMax(1);
            token = args.getString();
        }

        applyToken(token.trim());
    }

    private void applyToken(String token) throws CommandInvalidStateException {
        LicenseValidator.Result result = LicenseValidator.validate(token);
        switch (result.status) {
            case VALID -> {
                try {
                    LicenseValidator.save(token);
                    Authorization.reset();
                    logDirect("[MinecraftAI] Activated! Licensed to: " + result.name
                            + " (expires " + result.expiry + ")");
                } catch (Exception e) {
                    throw new CommandInvalidStateException(
                            "Failed to save license file: " + e.getMessage());
                }
            }
            case EXPIRED -> logDirect("[MinecraftAI] Error 001. Token expired. Contact owner.");
            case INVALID -> logDirect("[MinecraftAI] Error 001. Invalid token. Contact owner.");
            case NO_LICENSE -> logDirect("[MinecraftAI] Error 001. Please contact owner.");
        }
    }

    private static String readClipboard() {
        try {
            return (String) Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .getData(DataFlavor.stringFlavor);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Activate MinecraftAI with a license token";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Activates MinecraftAI using a license token from the mod owner.",
                "",
                "How to activate:",
                "  1. Copy the token you were given (Ctrl+C)",
                "  2. In-game type:  #activate",
                "",
                "That's it. The token is saved and you won't need to enter it again."
        );
    }
}
