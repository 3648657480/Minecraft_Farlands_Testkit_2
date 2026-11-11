package com.farlands.g2.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Y-F dynamic: keep the client's section grid on the player's current band.
 *
 * <p>Runs at the END of {@code Minecraft.tick}, i.e. after the network tick has
 * applied teleports: a rebuild placed at the tick head would use the pre-tp
 * position and the render frame's graph nodes would fall outside the grid
 * ({@code section == null} in {@code compileSections}). The camera follows the
 * player within one section, so centring the grid on the player keeps the camera
 * inside it.</p>
 */
@Mixin(Minecraft.class)
public class ClientBandFollowMixin {

    @org.spongepowered.asm.mixin.Unique private static int farlands$lastBand = Integer.MIN_VALUE;
    /** Last far page the renderer was rebuilt for (page start section). */
    @org.spongepowered.asm.mixin.Unique private static int farlands$lastPage = Integer.MIN_VALUE;

    /** Last level the wide-key table was built for; cleared when the level changes. */
    /** Strict band contract: no local window moves / refill / dirty hacks. */
    private static final boolean STRICT_BAND =
        Boolean.parseBoolean(System.getProperty("farlands.client.strictBand", "true"));
    private static Object farlands$lastLevel;
    private static boolean farlands$farReady;
    private static int farlands$dirtyTicks;

    @Inject(method = "tick", at = @At("TAIL"))
    private void farlands$followBand(CallbackInfo ci) {
        Minecraft mc0 = (Minecraft) (Object) this;
        // The fixed base / local frame must be established for ANY far camera, not
        // only when the far-Y window experiment is enabled: a plain install has no
        // JVM flags, and gating this on farlands.y.window meant [WIDEBASE] never ran
        // there and the screen stayed empty.
        boolean farCamera = mc0.level != null && mc0.gameRenderer != null
            && mc0.player != null
            && com.farlands.g2.client.WideRenderBase.isFar(
                mc0.gameRenderer.mainCamera().position().x,
                mc0.gameRenderer.mainCamera().position().z);
        if (!Boolean.getBoolean("farlands.y.window") && !farCamera) {
            // Leaving the far domain (e.g. teleporting back to spawn) must drop the
            // local frame: localSectionOf() returns null out of window, so the grid
            // silently falls back to world coordinates while the tracker, gate and
            // region builder still answer "far" - the two frames then disagree and
            // the normal world renders broken.
            if (com.farlands.g2.client.WideRenderBase.established()) {
                com.farlands.g2.client.WideRenderBase.clear();
                com.farlands.g2.client.RenderKeys.clear();
                com.farlands.g2.client.FarRegionBuilder.clearCache();
                if (mc0.level != null && mc0.levelRenderer != null && mc0.gameRenderer != null) {
                    mc0.levelRenderer.invalidateCompiledGeometry(mc0.level, mc0.options,
                        mc0.gameRenderer.mainCamera(), mc0.getBlockColors());
                }
                System.out.println("[WIDEBASE] cleared (left far domain)");
                System.out.flush();
                com.farlands.g2.runtime.FarLightMode.set(false);
            }
            return;
        }
        Minecraft mc = mc0;
        if (mc.level == null || mc.gameRenderer == null || mc.levelRenderer == null
            || mc.player == null) {
            return;
        }
        if (mc.level != farlands$lastLevel) {
            farlands$farReady = false;
            farlands$lastLevel = mc.level;
            try {
                com.farlands.g2.client.RenderKeys.clear();
            } catch (Throwable ignored) {
                // client classes only
            }
            // World switch: queued pushes / band flag from the previous level must
            // not be applied to chunks of the new world at the same coordinates.
            com.farlands.g2.client.ClientBandBridge.clearPending();
            com.farlands.g2.client.BandState.setFar(false);
            com.farlands.g2.client.WideRenderBase.clear();
        }
        if (!mc.hasSingleplayerServer()) {
            return; // multiplayer unsupported: keep the dimension band
        }
        try {
            com.farlands.g2.client.ClientBandBridge.retry();
        } catch (Throwable ignored) {
            // client classes only; never let the bridge break the tick
        }
        // Chunks whose window is still on the dimension band hold ordinary terrain;
        // drawing them at the page position paints that terrain at the far Y (the
        // displaced-cliffs screenshot). Move them to the current page locally: the
        // wide map has no sections there, so they render empty until the server
        // pushes the real page. Guarded to far Y so normal worlds are untouched.
        int blankSection = (int) Math.floorDiv(mc.player.blockPosition().getLongY(), 16L);
        if (!STRICT_BAND
            && (blankSection < mc.level.getMinSectionY() || blankSection > mc.level.getMaxSectionY())) {
            int pageStart = com.farlands.g2.runtime.VerticalBounds.pageStart(blankSection, 24);
            int radius = Math.min(8, mc.options.getEffectiveRenderDistance());
            int pcx = mc.player.chunkPosition().x();
            int pcz = mc.player.chunkPosition().z();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    var clientChunk = mc.level.getChunkSource().getChunk(pcx + dx, pcz + dz,
                        net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
                    if (clientChunk instanceof com.farlands.g2.runtime.WindowedChunk wc
                        && wc.farlands$windowMinSectionY() != pageStart) {
                        wc.farlands$moveWindowToStart(pageStart);
                        for (int i = 0; i < clientChunk.getSections().length; i++) {
                            mc.level.setSectionDirtyWithNeighbors(pcx + dx, pageStart + i, pcz + dz);
                        }
                    }
                }
            }
        }
        var viewArea = mc.levelRenderer.viewArea();
        if (viewArea == null) {
            return;
        }
        var camPos = mc.gameRenderer.mainCamera().position();
        if (com.farlands.g2.client.WideRenderBase.isFar(camPos.x, camPos.z)) {
            int farRenderDistance = Integer.getInteger("farlands.far.renderDistance", 4);
            if (mc.options.renderDistance().get() != farRenderDistance) {
                // The 9x9 data window is the view; a 25x25 section grid would
                // compile 15000 mostly-empty sections for no visual benefit.
                mc.options.renderDistance().set(farRenderDistance);
                com.farlands.g2.client.RenderKeys.clear();
                com.farlands.g2.client.FarRegionBuilder.clearCache();
            }
            // Fixed base + local frame: the renderer grid is fed local camera
            // sections, and keys are base + localNode. The base therefore moves
            // only on (re-)base, never per chunk.
            int baseState = com.farlands.g2.client.WideRenderBase.ensure(camPos.x, camPos.z);
            if (baseState != 0) {
                com.farlands.g2.runtime.FarLightMode.set(true);
                System.out.println("[WIDEBASE] state=" + baseState + " "
                    + com.farlands.g2.client.WideRenderBase.describe(camPos.x, camPos.z));
                System.out.flush();
                // Rebuild on the first establish too: the grid was built before the base
                // existed, so its slots still carry world sections (aliased past the
                // 22-bit packing) and would never re-key by themselves.
                mc.levelRenderer.invalidateCompiledGeometry(mc.level, mc.options,
                    mc.gameRenderer.mainCamera(), mc.getBlockColors());
                // re-keying calls reset() on every slot, which clears the mesh but does
                // not re-queue a compile. allChanged() is vanilla's "mark everything
                // dirty" path (render-distance change); its SectionPos.of calls are
                // redirected to the local frame, so the tracker re-dirties exactly the
                // slots of this grid.
                if (mc.levelExtractor != null) {
                    mc.levelExtractor.allChanged();
                }
                int redirtied = com.farlands.g2.client.RenderKeys.redirtyAll();
                System.out.println("[WIDEBASE] redirtied=" + redirtied);
                System.out.flush();
            }
        }
        // On re-entry the chunks load with the window already moved and no band
        // bridge runs, so nothing marks the sections dirty; force one full renderer
        // reload when the player first stands in a far window.
        if (!STRICT_BAND && !farlands$farReady && mc.player != null && mc.level != null) {
            var pc = mc.level.getChunkSource().getChunk(mc.player.blockPosition().getX() >> 4,
                mc.player.blockPosition().getZ() >> 4,
                net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
            if (pc instanceof com.farlands.g2.runtime.WindowedChunk wc
                && wc.farlands$windowMinSectionY() > 1_000_000) {
                farlands$farReady = true;
                mc.levelRenderer.invalidateCompiledGeometry(mc.level, mc.options,
                    mc.gameRenderer.mainCamera(), mc.getBlockColors());
                System.out.println("[YF-ALLCHANGED] far window entered, renderer reloaded");
                System.out.flush();
            }
        }
        // The fork bypasses SectionOcclusionGraph.update, so applyFrustum only runs
        // when the camera rotation changes. After a band move with a still camera the
        // visible list stays empty and nothing is compiled; refill it from the grid.
        if (!STRICT_BAND && mc.levelRenderer.visibleSections().isEmpty()) {
            try {
                mc.levelRenderer.sectionOcclusionGraph().waitAndReset(viewArea);
                mc.levelRenderer.sectionOcclusionGraph().addSectionsInFrustum(com.farlands.g2.client.RenderKeys.frustum(),
                    mc.levelRenderer.visibleSections(), mc.levelRenderer.nearbyVisibleSections());
                System.out.println("[YF-VIS] refilled visible="
                    + mc.levelRenderer.visibleSections().size());
                System.out.flush();
            } catch (Throwable t) {
                System.out.println("[YF-VIS] refill failed: " + t);
                System.out.flush();
            }
        }
        Camera camera = mc.gameRenderer.mainCamera();
        // Re-entry loads chunks with the band already moved, so the packet path's
        // dirty flags are consumed before the wide keys exist and nothing compiles.
        // Re-mark the player's column whenever its section is still uncompiled.
        var camSection = viewArea.getRenderSectionAt(mc.player.blockPosition());
        if (!STRICT_BAND && (camSection == null
            || camSection.getSectionMesh()
                == net.minecraft.client.renderer.chunk.CompiledSectionMesh.UNCOMPILED)) {
            if (farlands$dirtyTicks++ % 20 == 0) {
                int px = mc.player.blockPosition().getX() >> 4;
                int pz = mc.player.blockPosition().getZ() >> 4;
                for (int sy = viewArea.minSectionY(); sy <= viewArea.maxSectionY(); sy++) {
                    mc.level.setSectionDirtyWithNeighbors(px, sy, pz);
                }
                if (farlands$dirtyTicks % 200 == 0) {
                    System.out.println("[YF-DIRTY] re-marked column " + px + "," + pz
                        + " viewY=" + viewArea.minSectionY() + ".." + viewArea.maxSectionY());
                    System.out.flush();
                }
            }
        }
        // A ViewArea rebuilt while the camera was still uninitialised never got its
        // rotating centre, so every lookup returns null and nothing compiles.
        if (!com.farlands.g2.client.BandState.far()
            && viewArea.getRenderSectionAt(mc.player.blockPosition()) == null) {
            // SectionPos.of(BlockPos) does NOT go through the Position redirects in
            // LevelRenderer, so passing the world section here fought the local frame:
            // the grid alternated between the local camera section and the world one
            // (0 <-> -134217728), re-keying all 1943 sections every frame.
            var camPos2 = mc.gameRenderer.mainCamera().position();
            var localCenter = com.farlands.g2.client.WideRenderBase.established()
                ? com.farlands.g2.client.WideRenderBase.localSectionOf(
                    camPos2.x, camPos2.y, camPos2.z)
                : null;
            viewArea.repositionCamera(localCenter != null ? localCenter
                : net.minecraft.core.SectionPos.of(camera.blockPosition()));
            var playerSec = net.minecraft.core.SectionPos.of(mc.player.blockPosition());
            var midPos = new net.minecraft.core.BlockPos(mc.player.blockPosition().getX(),
                viewArea.minSectionY() * 16 + 8, mc.player.blockPosition().getZ());
            System.out.println("[YF-REPOS] playerSec=" + playerSec
                + " center=" + viewArea.getCameraSectionPos()
                + " midFound=" + (viewArea.getRenderSectionAt(midPos) != null)
                + " keys=" + com.farlands.g2.client.RenderKeys.size()
                + " stillAbsent=" + (viewArea.getRenderSectionAt(mc.player.blockPosition()) == null)
                + " viewY=" + viewArea.minSectionY() + ".." + viewArea.maxSectionY());
            System.out.flush();
        }
        int playerSection = (int) Math.floorDiv(mc.player.blockPosition().getLongY(), 16L);
        if (com.farlands.g2.client.BandState.far()) {
            // SectionPos cannot encode a far camera Y, so repositionCamera would
            // loop forever. But the ViewArea band must be rebuilt when the PLAYER'S
            // PAGE differs from the ViewArea's page, even while the player is still
            // inside the old band: smoke showed viewY 696..719 / chunkWin 703..726
            // (17-section overlap) because 710 is inside 696..719.
            int pageMin = com.farlands.g2.runtime.VerticalBounds.pageStart(playerSection, 24);
            int viewPageMin = com.farlands.g2.runtime.VerticalBounds.pageStart(
                viewArea.minSectionY(), 24);
            if (playerSection >= viewArea.minSectionY()
                && playerSection <= viewArea.maxSectionY()
                && pageMin == viewPageMin) {
                return;
            }
            if (pageMin == farlands$lastPage) {
                return;
            }
            farlands$lastPage = pageMin;
            System.out.println("[YF-REBUILD] far page playerSection=" + playerSection
                + " page=" + pageMin + " viewPage=" + viewPageMin
                + " oldView=" + viewArea.minSectionY()
                + ".." + viewArea.maxSectionY());
            System.out.flush();
            mc.levelRenderer.invalidateCompiledGeometry(mc.level, mc.options, camera,
                mc.getBlockColors());
            return;
        }
        if (playerSection >= viewArea.minSectionY() && playerSection <= viewArea.maxSectionY()) {
            return;
        }
        System.out.println("[YF-REBUILD] playerSection=" + playerSection + " oldView="
            + viewArea.minSectionY() + ".." + viewArea.maxSectionY());
        System.out.flush();
        mc.levelRenderer.invalidateCompiledGeometry(mc.level, mc.options, camera, mc.getBlockColors());
    }
}
