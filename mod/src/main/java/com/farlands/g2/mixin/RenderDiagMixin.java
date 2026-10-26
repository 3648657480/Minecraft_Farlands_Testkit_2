package com.farlands.g2.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Render-visibility probe (default off, {@code -Dfarlands.diag.render=true}).
 *
 * <p>Once per second: visible/total sections, how many have no compiled mesh,
 * how many are east of block 2^31, the compile-queue size and the player's own
 * section. Once after a few seconds: a spatial breakdown (ring histogram,
 * client chunk presence) plus samples, which separates "list never gets them"
 * from "listed but never queued" from "queued but chunk absent".</p>
 */
@Mixin(LevelRenderer.class)
public class RenderDiagMixin {

    private static int farlands$dirtyMarks;

    // Read at call time: the flag is applied after some client classes are
    // loaded, so a static final captured too early stays false.
    private static int farlands$frames;
    private static int farlands$farFrames;
    private static int farlands$dumps;

    @Inject(method = "endFrame", at = @At("HEAD"))
    private void farlands$diag(CallbackInfo ci) {
        if (!Boolean.getBoolean("farlands.diag.render")) {
            return;
        }
        int frame = ++farlands$frames;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        LevelRenderer self = (LevelRenderer) (Object) this;
        ViewArea view = self.viewArea();
        SectionRenderDispatcher disp = self.sectionRenderDispatcher();
        if (view == null || disp == null) {
            return;
        }
        if (Math.abs(mc.player.getX()) > 1.0E9) {
            farlands$farFrames++;
            // 30 / 60 / 90 s after arriving: watch the uncompiled set settle.
            if ((farlands$farFrames == 1800 || farlands$farFrames == 3600 || farlands$farFrames == 5400)
                && farlands$dumps < 3) {
                farlands$dumps++;
                farlands$dump(mc, self, view, farlands$farFrames);
                if (farlands$dumps >= 2) {
                    try {
                        net.minecraft.client.Screenshot.grab(mc, false);
                    } catch (Throwable t) {
                        System.out.println("[RDIAG] screenshot failed: " + t);
                    }
                }
            }
        }
        if (frame % 20 != 0) {
            return;
        }
        // After a ViewArea rebuild the visible list keeps the previous instance's
        // sections, so the draw pass skips the real (compiled) ones. Refill only when
        // it is empty: clearing every second would blank the screen while moving.
        if (self.visibleSections().isEmpty()) {
            self.clearVisibleSections();
            for (Object section : view.getAllSections()) {
                if (section != null) {
                    self.visibleSections().add(
                        (net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection) section);
                }
            }
        }
        int visible = self.visibleSections().size();
        int total = 0;
        for (Object ignored : view.getAllSections()) {
            total++;
        }
        int chunkPresent = 0;
        if (mc.level != null && mc.player != null) {
            int cx = (int) Math.floorDiv((long) Math.floor(mc.player.getX()), 16L);
            int cz = (int) Math.floorDiv((long) Math.floor(mc.player.getZ()), 16L);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (mc.level.getChunkSource().getChunk(cx + dx, cz + dz, false) != null) {
                        chunkPresent++;
                    }
                }
            }
        }
        int uncompiled = 0;
        int innerUncompiled = 0;
        int dirtyNull = 0;
        int dirtyFalse = 0;
        int noNeighbors = 0;
        var tracker = com.farlands.g2.runtime.TrackerProbe.LAST;
        int east = 0;
        int eastCompiled = 0;
        for (SectionRenderDispatcher.RenderSection section : self.visibleSections()) {
            boolean un = section.getSectionMesh() == CompiledSectionMesh.UNCOMPILED;
            if (un) {
                uncompiled++;
                if (section instanceof com.farlands.g2.runtime.section.RenderSectionKeyAccessor accessor) {
                    long node = section.getSectionNode();
                    var dirty = tracker == null ? null : tracker.getDirtyState(node);
                    if (dirty == null) {
                        dirtyNull++;
                    } else if (!dirty.isDirty()) {
                        dirtyFalse++;
                    }
                    if (tracker != null && mc.level != null && !tracker.hasAllNeighbors(mc.level, node)) {
                        noNeighbors++;
                        if (Boolean.getBoolean("farlands.diag.nodes") && noNeighbors == 1) {
                            System.out.println("[NODE] raw=" + node
                                + " x=" + net.minecraft.core.SectionPos.x(node)
                                + " y=" + net.minecraft.core.SectionPos.y(node)
                                + " z=" + net.minecraft.core.SectionPos.z(node)
                                + " chunkPresent=" + (mc.level.getChunkSource().getChunk(
                                    net.minecraft.core.SectionPos.x(node),
                                    net.minecraft.core.SectionPos.z(node), false) != null));
                        }
                    }
                    com.farlands.g2.runtime.section.SectionKey k = accessor.farlands$getKey();
                    if (k != null && mc.level != null
                        && mc.level.getChunkSource().getChunk(k.x, k.z, false) != null) {
                        innerUncompiled++;
                    }
                }
            }
            long ox = section.getRenderOrigin().getLongX();
            if (ox >= 2147483648L) {
                east++;
                if (!un) {
                    eastCompiled++;
                }
            }
        }
        // Diagnostic experiment: force a batch of uncompiled visible sections dirty
        // through their wide keys (int BlockPos views wrap at 2^31, which made the
        // old re-mark path target the wrong column).
        if (Boolean.getBoolean("farlands.diag.dirtyAll") && uncompiled > 0) {
            int marked = 0;
            for (SectionRenderDispatcher.RenderSection section : self.visibleSections()) {
                if (section.getSectionMesh() != CompiledSectionMesh.UNCOMPILED) {
                    continue;
                }
                if (section instanceof com.farlands.g2.runtime.section.RenderSectionKeyAccessor accessor) {
                    com.farlands.g2.runtime.section.SectionKey key = accessor.farlands$getKey();
                    if (key != null) {
                        mc.level.setSectionRangeDirty(key.x, key.y, key.z, key.x, key.y, key.z);
                        marked++;
                    }
                }
                if (marked >= 64) {
                    break;
                }
            }
            System.out.println("[DIRTYALL] marked=" + marked + " uncompiled=" + uncompiled);
            System.out.flush();
        }
        net.minecraft.core.BlockPos camPos = new net.minecraft.core.BlockPos(
            (long) Math.floor(mc.player.getX()),
            (long) Math.floor(mc.player.getY()),
            (long) Math.floor(mc.player.getZ()));
        boolean lightCam = mc.level != null && camPos != null
            && mc.level.getLightEngine().lightOnInColumn(net.minecraft.core.SectionPos.getZeroNode(
                net.minecraft.core.SectionPos.asLong((int) Math.floorDiv((long) Math.floor(mc.player.getX()), 16L),
                    (int) Math.floorDiv((long) Math.floor(mc.player.getY()), 16L),
                    (int) Math.floorDiv((long) Math.floor(mc.player.getZ()), 16L))));
        int light3x3 = 0;
        if (mc.level != null && mc.player != null) {
            int sx = (int) Math.floorDiv((long) Math.floor(mc.player.getX()), 16L);
            int sy = (int) Math.floorDiv((long) Math.floor(mc.player.getY()), 16L);
            int sz = (int) Math.floorDiv((long) Math.floor(mc.player.getZ()), 16L);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (mc.level.getLightEngine().lightOnInColumn(net.minecraft.core.SectionPos.getZeroNode(
                        net.minecraft.core.SectionPos.asLong(sx + dx, sy, sz + dz)))) {
                        light3x3++;
                    }
                }
            }
        }
        SectionRenderDispatcher.RenderSection cam = view.getRenderSectionAt(camPos);
        // Re-entry has the band right but nothing marks the sections dirty (no band
        // bridge runs, and the client tick may be paused). The render thread always
        // runs: re-mark the player's column once per diagnostic tick until compiled.
        if (cam == null || cam.getSectionMesh() == CompiledSectionMesh.UNCOMPILED) {
            int px = mc.player.blockPosition().getX() >> 4;
            int pz = mc.player.blockPosition().getZ() >> 4;
            for (int sy = view.minSectionY(); sy <= view.maxSectionY(); sy++) {
                mc.level.setSectionDirtyWithNeighbors(px, sy, pz);
            }
            if (farlands$dirtyMarks++ % 5 == 0) {
                System.out.println("[YF-DIRTY] render-thread re-mark column " + px + "," + pz
                    + " viewY=" + view.minSectionY() + ".." + view.maxSectionY());
                System.out.flush();
            }
        }
        String camInfo = cam == null ? "absent"
            : ("origin=" + cam.getRenderOrigin().getLongX()
                + "," + cam.getRenderOrigin().getLongY()
                + "," + cam.getRenderOrigin().getLongZ()
                + " aabb=" + cam.getBoundingBox()
                + " emptySolid=" + (cam.getSectionMesh()
                    instanceof net.minecraft.client.renderer.chunk.CompiledSectionMesh m
                        ? m.isEmpty(net.minecraft.client.renderer.chunk.ChunkSectionLayer.SOLID)
                        : "uncompiled")
                + " mesh=" + (cam.getSectionMesh() == CompiledSectionMesh.UNCOMPILED ? "UNCOMPILED" : "compiled"));
        var below = mc.player.blockPosition().below(1);
        int clientNonAir = 0;
        var clientChunk = mc.level.getChunkSource().getChunk(
            mc.player.blockPosition().getX() >> 4, mc.player.blockPosition().getZ() >> 4,
            net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
        if (clientChunk != null) {
            for (var s : clientChunk.getSections()) {
                if (s != null && !s.hasOnlyAir()) clientNonAir++;
            }
        }
        var clientBlock = mc.level.getBlockState(below);
        System.out.println("[RDIAG] visible=" + visible + "/" + total
            + " uncompiled=" + uncompiled
            + " innerUncompiled=" + innerUncompiled + " chunks5x5=" + chunkPresent
            + " dirtyNull=" + dirtyNull + " dirtyFalse=" + dirtyFalse + " noNeighbors=" + noNeighbors
            + " lightCam=" + lightCam + " light3x3=" + light3x3
            + " east=" + east + " eastCompiled=" + eastCompiled
            + " queue=" + disp.getCompileQueueSize()
            + " player=" + mc.player.getX() + "," + mc.player.getY() + "," + mc.player.getZ()
            + " viewY=" + view.minSectionY() + ".." + view.maxSectionY()
            + " below=" + clientBlock + " clientNonAir=" + clientNonAir
            + " chunkWin=" + (clientChunk instanceof com.farlands.g2.runtime.WindowedChunk wc
                ? (wc.farlands$windowMinSectionY() + ".." + wc.farlands$windowMaxSectionY()) : "none")
            + " camSection=" + camInfo);
        System.out.flush();
    }

    private static void farlands$dump(Minecraft mc, LevelRenderer self, ViewArea view, int frame) {
        SectionPos camPos = SectionPos.of(mc.player.blockPosition());
        int[] ringTotal = new int[64];
        int[] ringUncompiled = new int[64];
        int[] ringUncompiledLoaded = new int[64];
        int[] ringUncompiledUnloaded = new int[64];
        int[] yHist = new int[512];
        int uncompiledLoaded = 0;
        int uncompiledUnloaded = 0;
        int compiledLoaded = 0;
        int compiledUnloaded = 0;
        int unassigned = 0;
        StringBuilder samples = new StringBuilder();
        int sampled = 0;
        for (SectionRenderDispatcher.RenderSection section : self.visibleSections()) {
            long node = section.getSectionNode();
            if (node == SectionPos.asLong(-1, -1, -1)) {
                unassigned++;
            }
            int sx = SectionPos.x(node);
            int sz = SectionPos.z(node);
            int ring = Math.max(Math.abs(sx - camPos.x()), Math.abs(sz - camPos.z()));
            if (ring >= 0 && ring < ringTotal.length) {
                ringTotal[ring]++;
            }
            boolean un = section.getSectionMesh() == CompiledSectionMesh.UNCOMPILED;
            boolean loaded = mc.level.getChunkSource().hasChunk(sx, sz);
            if (un) {
                if (ring >= 0 && ring < ringUncompiled.length) {
                    ringUncompiled[ring]++;
                }
                if (loaded) {
                    uncompiledLoaded++;
                    if (ring < 64) {
                        ringUncompiledLoaded[ring]++;
                    }
                } else {
                    uncompiledUnloaded++;
                    if (ring < 64) {
                        ringUncompiledUnloaded[ring]++;
                    }
                }
                int sy = SectionPos.y(node);
                if (sy + 256 >= 0 && sy + 256 < yHist.length) {
                    yHist[sy + 256]++;
                }
                if (loaded && sampled < 8) {
                    sampled++;
                    samples.append("\n[RDIAG-SAMPLE] node=").append(sx).append(",").append(SectionPos.y(node)).append(",").append(sz)
                        .append(" origin=").append(section.getRenderOrigin().getLongX())
                        .append(" chunk=").append(sx).append(",").append(sz)
                        .append(" loaded=").append(loaded);
                }
            } else if (loaded) {
                compiledLoaded++;
            } else {
                compiledUnloaded++;
            }
        }
        StringBuilder rings = new StringBuilder();
        for (int r = 0; r < 16; r++) {
            if (ringTotal[r] > 0) {
                rings.append(r).append(":").append(ringUncompiledLoaded[r]).append("u/")
                    .append(ringUncompiledUnloaded[r]).append("x/").append(ringTotal[r]).append(" ");
            }
        }
        StringBuilder ys = new StringBuilder();
        int yMin = Integer.MAX_VALUE;
        int yMax = Integer.MIN_VALUE;
        for (int i = 0; i < yHist.length; i++) {
            if (yHist[i] > 0) {
                int sy = i - 256;
                yMin = Math.min(yMin, sy);
                yMax = Math.max(yMax, sy);
                ys.append(sy).append(":").append(yHist[i]).append(" ");
            }
        }
        System.out.println("[RDIAG-DUMP] frame=" + frame
            + " camSection=" + camPos.x() + "," + camPos.y() + "," + camPos.z()
            + " unassigned=" + unassigned
            + " uncompiledLoaded=" + uncompiledLoaded
            + " uncompiledUnloaded=" + uncompiledUnloaded
            + " compiledLoaded=" + compiledLoaded
            + " compiledUnloaded=" + compiledUnloaded
            + "\n[RDIAG-RINGS] " + rings
            + "\n[RDIAG-Y] " + yMin + ".." + yMax + " " + ys + samples);
        System.out.flush();
    }
    private static int farlands$neighChunks(long node) {
        int n = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                if (net.minecraft.client.Minecraft.getInstance().level.getChunkSource().getChunk(
                    net.minecraft.core.SectionPos.x(node) + dx,
                    net.minecraft.core.SectionPos.z(node) + dz, false) != null) {
                    n++;
                }
            }
        }
        return n;
    }

    private static int farlands$neighLit(long node) {
        int n = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                long off = net.minecraft.core.SectionPos.offset(node, dx, 0, dz);
                if (net.minecraft.client.Minecraft.getInstance().level.getLightEngine()
                    .lightOnInColumn(net.minecraft.core.SectionPos.getZeroNode(off))) {
                    n++;
                }
            }
        }
        return n;
    }

}