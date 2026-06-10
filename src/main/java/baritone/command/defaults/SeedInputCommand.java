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

package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Stores the world seed so that {@code #structure} and {@code #where} can
 * find structures on multiplayer servers.
 *
 * <p>In singleplayer the integrated server is used directly (no seed required).
 * On multiplayer, enter the seed once and the commands will use client-side
 * structure placement math for the rest of the session.  The seed is persisted
 * to {@code baritone/seed.txt} so it survives game restarts.
 *
 * <pre>
 *   #seedinput 12345678        - store this seed
 *   #seedinput -98765432       - negative seeds are fine too
 *   #seedinput                 - show the currently stored seed
 *   #seedinput clear           - forget the stored seed
 * </pre>
 */
public class SeedInputCommand extends Command {

    public SeedInputCommand(IBaritone baritone) {
        super(baritone, "seedinput", "seed");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(1);

        if (!args.hasAny()) {
            // No argument — report current state
            if (ClientStructureFinder.hasSeed()) {
                logDirect("Stored seed: " + ClientStructureFinder.getSeed());
                logDirect("#structure and #where will use this seed on multiplayer.");
            } else {
                logDirect("No seed stored.");
                logDirect("Usage:  #seedinput <seed>   (e.g.  #seedinput -4172144997902289642)");
            }
            return;
        }

        String arg = args.getString().trim();

        if (arg.equalsIgnoreCase("clear")) {
            if (ClientStructureFinder.hasSeed()) {
                long old = ClientStructureFinder.getSeed();
                ClientStructureFinder.clearSeed();
                logDirect("Seed " + old + " cleared. Structure commands will fall back to singleplayer-only mode.");
            } else {
                logDirect("No seed was stored.");
            }
            return;
        }

        // Parse the seed — MC seeds are signed 64-bit integers
        long seed;
        try {
            seed = Long.parseLong(arg);
        } catch (NumberFormatException e) {
            logDirect("Invalid seed: '" + arg + "'");
            logDirect("Seeds must be a whole number (positive or negative).  Example:  #seedinput -4172144997902289642");
            return;
        }

        ClientStructureFinder.setSeed(seed);
        logDirect("Seed stored: " + seed);
        logDirect("You can now use  #structure  and  #where  on this multiplayer server.");
        logDirect("(Seed is saved to baritone/seed.txt — no need to re-enter after restart)");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            String prefix = "";
            try { prefix = args.peekString().toLowerCase(); } catch (Exception ignored) {}
            if ("clear".startsWith(prefix)) return Stream.of("clear");
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Store the world seed for structure finding on multiplayer";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
            "Stores the world seed so that #structure and #where work on multiplayer servers.",
            "",
            "In singleplayer the integrated server is used directly and no seed is needed.",
            "On multiplayer, enter the seed here first — it is saved to baritone/seed.txt",
            "so you only need to do this once per server.",
            "",
            "How to find your seed:",
            "  - Ask an admin / use /seed if you have permission",
            "  - Check server files (level.dat)",
            "  - Use a seed from a known seed list or your own notes",
            "",
            "Limitations on multiplayer (seed-based search):",
            "  - Village, fortress, bastion, monument, ancient city etc: fully supported",
            "  - Strongholds: NOT supported (they use biome-based ring placement)",
            "    Use chunkbase.com with your seed to find strongholds instead.",
            "",
            "Usage:",
            "> seedinput <seed>   - store a seed (use quotes for negative: #seedinput -12345)",
            "> seedinput          - show the currently stored seed",
            "> seedinput clear    - forget the stored seed"
        );
    }
}
