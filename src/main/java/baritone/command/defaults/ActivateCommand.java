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
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * One-time license activation command.
 *
 * <p>Tokens are too long to paste into Minecraft chat (~380 chars, MC limit 256).
 * Primary flow: save token to {@code baritone/activate.txt}, then run {@code #activate}.
 * The inline {@code #activate <token>} form is kept for short tokens only.
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
            // File-based activation: read baritone/activate.txt
            token = readActivateFile();
            if (token == null) {
                logDirect("[MinecraftAI] No token in chat and no activate.txt found.");
                logDirect("Save your token to: .minecraft/baritone/activate.txt");
                logDirect("Then run #activate again (no arguments).");
                return;
            }
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
                    // Delete activate.txt after successful use so the token isn't left on disk
                    deleteActivateFile();
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

    private static Path activateFilePath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("baritone/activate.txt");
    }

    private static String readActivateFile() {
        try {
            Path p = activateFilePath();
            if (!Files.exists(p)) return null;
            return Files.readString(p, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static void deleteActivateFile() {
        try { Files.deleteIfExists(activateFilePath()); } catch (Exception ignored) {}
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
                "Tokens are too long to paste into Minecraft chat.",
                "Use the file method instead:",
                "",
                "  1. Open your .minecraft folder",
                "  2. Go into the baritone/ folder (create it if missing)",
                "  3. Create a file called activate.txt",
                "  4. Paste the full token into that file and save",
                "  5. In-game, type:  #activate",
                "",
                "The file is deleted automatically after successful activation.",
                "",
                "Alternatively (short tokens only):",
                "> #activate <token>"
        );
    }
}
