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

package baritone.utils;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import baritone.utils.accessor.IEntityRenderManager;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.*;

/**
 * Baritone's debug line/box overlay renderer.
 *
 * <p><b>MC 26.2 status:</b> temporarily disabled. Minecraft 26.2 replaced the
 * whole immediate-mode render pipeline (removed {@code Tesselator},
 * {@code MultiBufferSource}, {@code ShapeRenderer}, {@code RenderType.draw})
 * with a low-level GPU-buffer API. The overlay draw code has to be reimplemented
 * against that new pipeline; until then these methods are no-ops so the bot runs
 * with full functionality (pathing, mining, all commands) — only the visual path
 * lines / goal boxes are absent. The method surface is preserved so every caller
 * (PathRenderer, SelectionRenderer, GuiClick, SelCommand, ElytraBehavior)
 * compiles and runs unchanged.
 */
public interface IRenderer {

    /** Retained for callers that read the current colour; harmless when unused. */
    float[] color = new float[]{1.0F, 1.0F, 1.0F, 255.0F};

    // Retained so PathRenderer / SelectionRenderer still resolve these — they are
    // unrelated to the removed blaze3d draw pipeline.
    IEntityRenderManager renderManager = (IEntityRenderManager) Minecraft.getInstance().getEntityRenderDispatcher();
    Settings settings = BaritoneAPI.getSettings();

    static void glColor(Color color, float alpha) {
        float[] colorComponents = color.getColorComponents(null);
        IRenderer.color[0] = colorComponents[0];
        IRenderer.color[1] = colorComponents[1];
        IRenderer.color[2] = colorComponents[2];
        IRenderer.color[3] = alpha;
    }

    static BufferBuilder startLines(Color color, float alpha) {
        glColor(color, alpha);
        return null;
    }

    static BufferBuilder startLines(Color color) {
        return startLines(color, .4f);
    }

    static void endLines(BufferBuilder bufferBuilder, boolean ignoredDepth) {
        // overlay disabled on 26.2 — see class javadoc
    }

    static BufferBuilder startBlockQuads() {
        return null;
    }

    static void endBuffer(BufferBuilder bufferBuilder, RenderType renderType) {
        // overlay disabled on 26.2
    }

    static void emitLine(BufferBuilder bufferBuilder, PoseStack stack,
                         double x1, double y1, double z1, double x2, double y2, double z2, float lineWidth) {
        // overlay disabled on 26.2
    }

    static void emitLine(BufferBuilder bufferBuilder, PoseStack stack,
                         double x1, double y1, double z1,
                         double x2, double y2, double z2,
                         double nx, double ny, double nz,
                         float lineWidth) {
        // overlay disabled on 26.2
    }

    static void emitLine(BufferBuilder bufferBuilder, PoseStack stack,
                         float x1, float y1, float z1,
                         float x2, float y2, float z2,
                         float nx, float ny, float nz,
                         float lineWidth) {
        // overlay disabled on 26.2
    }

    static void emitAABB(BufferBuilder bufferBuilder, PoseStack stack, AABB aabb, float lineWidth) {
        // overlay disabled on 26.2
    }

    static void emitAABB(BufferBuilder bufferBuilder, PoseStack stack, AABB aabb, double expand, float lineWidth) {
        // overlay disabled on 26.2
    }

    static void emitLine(BufferBuilder bufferBuilder, PoseStack stack, Vec3 start, Vec3 end, float lineWidth) {
        // overlay disabled on 26.2
    }

    static void emitTexturedVertex(BufferBuilder bufferBuilder, PoseStack.Pose pose, float x, float y, float z,
                                   int color, float u, float v, float nx, float ny, float nz) {
        // overlay disabled on 26.2
    }

    static RenderType beaconBeam(Identifier identifier, boolean bl) {
        return null;
    }

    static RenderType beaconBeam(Identifier identifier, boolean bl, boolean ignoreDepth) {
        return null;
    }
}
