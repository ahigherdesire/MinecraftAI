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

package baritone.behavior;

import baritone.Baritone;
import baritone.api.event.events.RenderEvent;
import baritone.api.event.events.TickEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.utils.IRenderer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Simple ESP (extra-sensory perception) overlay: outlines nearby blocks whose
 * registry path matches {@link baritone.api.Settings#espBlockList}, and/or every
 * other player in range.
 *
 * <p>Two toggles, both off by default and both flipped by the {@code #esp}
 * command (or {@code #set espBlocks/espPlayers}):
 * <ul>
 *   <li><b>Block ESP</b> — a cube of half-extent {@link
 *       baritone.api.Settings#espBlockRange} around you is rescanned every {@link
 *       baritone.api.Settings#espBlockRescanTicks} ticks; matching positions are
 *       cached and drawn each frame so scanning cost is paid only occasionally.</li>
 *   <li><b>Player ESP</b> — every other player within {@link
 *       baritone.api.Settings#espPlayerRange} gets a box drawn at its
 *       partial-tick-interpolated position, so the outline tracks smoothly.</li>
 * </ul>
 */
public final class EspBehavior extends Behavior implements AbstractGameEventListener {

    /** Cached block positions to outline, refreshed on the rescan cadence. */
    private final List<BlockPos> blockHits = new ArrayList<>();
    private int sinceScan;

    public EspBehavior(Baritone baritone) {
        super(baritone);
    }

    // ── Scan (throttled) ────────────────────────────────────────────────────

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() != TickEvent.Type.IN) {
            blockHits.clear();
            sinceScan = 0;
            return;
        }
        if (!Baritone.settings().espBlocks.value) {
            if (!blockHits.isEmpty()) {
                blockHits.clear();
            }
            return;
        }
        int interval = Math.max(1, Baritone.settings().espBlockRescanTicks.value);
        if (sinceScan++ % interval == 0) {
            rescanBlocks();
        }
    }

    private void rescanBlocks() {
        blockHits.clear();
        if (ctx.player() == null || ctx.world() == null) {
            return;
        }

        String[] wanted = Baritone.settings().espBlockList.value.toLowerCase(Locale.ROOT).split("[,\\s]+");
        boolean any = false;
        for (String w : wanted) {
            if (!w.isBlank()) {
                any = true;
                break;
            }
        }
        if (!any) {
            return; // nothing to match against
        }

        final Level world = ctx.world();
        final int range = Math.max(1, Baritone.settings().espBlockRange.value);
        final int limit = Math.max(1, Baritone.settings().espBlockLimit.value);
        final BlockPos center = ctx.player().blockPosition();
        // Clamp the vertical sweep to the world so we never probe outside it.
        final int minY = Math.max(world.getMinY(), center.getY() - range);
        final int maxY = Math.min(world.getMinY() + world.getHeight() - 1, center.getY() + range);

        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = center.getX() - range; x <= center.getX() + range; x++) {
            for (int z = center.getZ() - range; z <= center.getZ() + range; z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                    if (matches(path, wanted)) {
                        blockHits.add(pos.immutable());
                        if (blockHits.size() >= limit) {
                            return;
                        }
                    }
                }
            }
        }
    }

    private static boolean matches(String path, String[] wanted) {
        for (String w : wanted) {
            if (!w.isBlank() && path.contains(w)) {
                return true;
            }
        }
        return false;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void onRenderPass(RenderEvent event) {
        if (ctx.player() == null || ctx.world() == null) {
            return;
        }
        final boolean depthIgnored = Baritone.settings().espIgnoreDepth.value;
        final float width = Baritone.settings().espLineWidthPixels.value;
        final PoseStack stack = event.getModelViewStack();

        if (Baritone.settings().espBlocks.value && !blockHits.isEmpty()) {
            BufferBuilder bb = IRenderer.startLines(Baritone.settings().colorEspBlock.value, 1.0f);
            for (BlockPos p : blockHits) {
                AABB box = new AABB(p.getX(), p.getY(), p.getZ(),
                        p.getX() + 1.0, p.getY() + 1.0, p.getZ() + 1.0);
                IRenderer.emitAABB(bb, stack, box, width);
            }
            IRenderer.endLines(bb, depthIgnored);
        }

        if (Baritone.settings().espPlayers.value) {
            renderPlayers(stack, event.getPartialTicks(), width, depthIgnored);
        }
    }

    private void renderPlayers(PoseStack stack, float partialTicks, float width, boolean depthIgnored) {
        if (!(ctx.world() instanceof ClientLevel level)) {
            return;
        }
        final double range = Baritone.settings().espPlayerRange.value;
        final double r2 = range * range;
        final UUID self = ctx.player().getUUID();

        BufferBuilder bb = IRenderer.startLines(Baritone.settings().colorEspPlayer.value, 1.0f);
        for (AbstractClientPlayer pl : level.players()) {
            if (pl == null || pl.getUUID().equals(self)) {
                continue;
            }
            if (pl.distanceToSqr(ctx.player()) > r2) {
                continue;
            }
            // Interpolate to the render position so the box doesn't lag a tick behind.
            double x = Mth.lerp(partialTicks, pl.xOld, pl.getX());
            double y = Mth.lerp(partialTicks, pl.yOld, pl.getY());
            double z = Mth.lerp(partialTicks, pl.zOld, pl.getZ());
            double hw = pl.getBbWidth() / 2.0;
            double h = pl.getBbHeight();
            AABB box = new AABB(x - hw, y, z - hw, x + hw, y + h, z + hw);
            IRenderer.emitAABB(bb, stack, box, width);
        }
        // endLines on an empty buffer builds a null mesh and safely no-ops.
        IRenderer.endLines(bb, depthIgnored);
    }
}
