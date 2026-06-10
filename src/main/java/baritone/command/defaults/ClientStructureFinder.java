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

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client-side structure finder that works without a ServerLevel.
 *
 * <p>Uses the world seed (entered via {@code #seedinput}) together with the
 * client's RegistryAccess to locate structures that use
 * {@link RandomSpreadStructurePlacement}.
 *
 * <p>Structures that use ConcentricRingsStructurePlacement (strongholds) require
 * biome data unavailable client-side and are NOT supported — the result will be
 * {@code null} for those, and the calling command should show a chunkbase.com hint.
 *
 * <p>The seed is persisted to {@code baritone/seed.txt} in the game directory so
 * it survives game restarts.
 */
public final class ClientStructureFinder {

    private static volatile long storedSeed = Long.MIN_VALUE; // MIN_VALUE = not set
    private static volatile boolean fileAttempted = false;    // lazy-load guard
    private static final long UNSET = Long.MIN_VALUE;

    private ClientStructureFinder() {}

    // -------------------------------------------------------------------------
    // Seed management
    // -------------------------------------------------------------------------

    public static void setSeed(long seed) {
        storedSeed = seed;
        fileAttempted = true;
        try {
            File f = seedFile();
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), String.valueOf(seed).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    public static boolean hasSeed() {
        ensureLoaded();
        return storedSeed != UNSET;
    }

    public static long getSeed() {
        ensureLoaded();
        return storedSeed;
    }

    public static void clearSeed() {
        storedSeed = UNSET;
        fileAttempted = true;
        try { Files.deleteIfExists(seedFile().toPath()); } catch (Exception ignored) {}
    }

    // -------------------------------------------------------------------------
    // Structure search
    // -------------------------------------------------------------------------

    /**
     * Find the nearest structure matching {@code query} within
     * {@code searchChunkRadius} chunks of {@code origin}.
     *
     * @param query  either {@code "tag:<tagPath>"} or {@code "id:<structureId>"}
     *               — same format as {@link StructureCommand#ALIASES} values.
     * @return the nearest candidate BlockPos, or {@code null} if not found /
     *         not supported (e.g. strongholds).
     */
    public static BlockPos findNearest(String query, BlockPos origin, int searchChunkRadius) {
        if (!hasSeed()) return null;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return null;
        RegistryAccess registries = mc.getConnection().registryAccess();

        // Collect the structure ResourceKeys we're searching for.
        HolderLookup.RegistryLookup<Structure> structureLookup;
        HolderLookup.RegistryLookup<StructureSet> setLookup;
        try {
            structureLookup = registries.lookupOrThrow(Registries.STRUCTURE);
            setLookup       = registries.lookupOrThrow(Registries.STRUCTURE_SET);
        } catch (Exception e) {
            return null; // registries not available yet
        }

        Set<ResourceKey<Structure>> targetKeys = new HashSet<>();

        if (query.startsWith("tag:")) {
            String tagName = query.substring(4);
            TagKey<Structure> tag = TagKey.create(
                Registries.STRUCTURE, Identifier.withDefaultNamespace(tagName));
            structureLookup.get(tag).ifPresent(holders ->
                holders.forEach(h -> h.unwrapKey().ifPresent(targetKeys::add)));

        } else if (query.startsWith("id:")) {
            String idName = query.substring(3);
            ResourceKey<Structure> key = ResourceKey.create(
                Registries.STRUCTURE, Identifier.withDefaultNamespace(idName));
            structureLookup.get(key).ifPresent(h -> h.unwrapKey().ifPresent(targetKeys::add));
        }

        if (targetKeys.isEmpty()) return null;

        // Find every StructureSet that contains at least one of the target structures.
        List<StructureSet> matchingSets = new ArrayList<>();
        setLookup.listElements().forEach(ref -> {
            StructureSet set = ref.value();
            for (var entry : set.structures()) {
                if (entry.structure().unwrapKey().map(targetKeys::contains).orElse(false)) {
                    matchingSets.add(set);
                    break;
                }
            }
        });

        if (matchingSets.isEmpty()) return null;

        BlockPos nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (StructureSet set : matchingSets) {
            StructurePlacement placement = set.placement();
            if (!(placement instanceof RandomSpreadStructurePlacement rsp)) {
                // ConcentricRingsStructurePlacement (strongholds) — needs biome data.
                // Return null; callers show a targeted message for this case.
                return null;
            }
            BlockPos candidate = findNearestForPlacement(rsp, origin, searchChunkRadius);
            if (candidate != null) {
                double distSq = origin.distSqr(candidate);
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearest = candidate;
                }
            }
        }

        return nearest;
    }

    // -------------------------------------------------------------------------
    // RandomSpreadStructurePlacement candidate search
    // -------------------------------------------------------------------------

    private static BlockPos findNearestForPlacement(RandomSpreadStructurePlacement placement,
                                                     BlockPos origin, int searchChunkRadius) {
        int spacing = placement.spacing();

        // Convert block coords → region (cell) coords.
        // A "region" is a spacing×spacing chunk cell in the world grid.
        int originChunkX = origin.getX() >> 4;
        int originChunkZ = origin.getZ() >> 4;
        int originRegX = Math.floorDiv(originChunkX, spacing);
        int originRegZ = Math.floorDiv(originChunkZ, spacing);
        int searchRegRadius = (searchChunkRadius / spacing) + 2;

        BlockPos nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (int dx = -searchRegRadius; dx <= searchRegRadius; dx++) {
            for (int dz = -searchRegRadius; dz <= searchRegRadius; dz++) {
                int regX = originRegX + dx;
                int regZ = originRegZ + dz;

                // getPotentialStructureChunk is a public method on
                // RandomSpreadStructurePlacement (present since MC 1.19).
                ChunkPos chunk = placement.getPotentialStructureChunk(storedSeed, regX, regZ);

                // Centre of the chunk as a candidate block position.
                // Y=64 is a placeholder; the goal processor handles exact height.
                BlockPos pos = new BlockPos((chunk.x() << 4) + 8, 64, (chunk.z() << 4) + 8);
                double distSq = origin.distSqr(pos);
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearest = pos;
                }
            }
        }

        return nearest;
    }

    // -------------------------------------------------------------------------
    // Persistence helpers
    // -------------------------------------------------------------------------

    private static File seedFile() {
        return new File(Minecraft.getInstance().gameDirectory, "baritone/seed.txt");
    }

    private static void ensureLoaded() {
        if (fileAttempted) return;
        fileAttempted = true;
        try {
            File f = seedFile();
            if (f.exists()) {
                String content = new String(
                    Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
                storedSeed = Long.parseLong(content);
            }
        } catch (Exception ignored) {}
    }
}
