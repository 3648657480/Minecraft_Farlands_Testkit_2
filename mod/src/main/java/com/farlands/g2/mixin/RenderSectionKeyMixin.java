package com.farlands.g2.mixin;

import com.farlands.g2.client.RenderKeys;
import com.farlands.g2.runtime.section.RenderSectionKeyAccessor;
import com.farlands.g2.runtime.section.SectionKey;
import com.farlands.g2.runtime.section.WideOriginHolder;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds the 2560-bit {@link SectionKey} identity to a render section. The origin
 * and bounding box are recomputed from the key's int coordinates, so far-Y
 * sections no longer depend on the 6-bit Y packing of {@code SectionPos.asLong}.
 */
@Mixin(targets = "net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection")
public abstract class RenderSectionKeyMixin implements RenderSectionKeyAccessor {

    @Shadow private BlockPos.MutableBlockPos renderOrigin;
    @Shadow @Mutable private long sectionNode;
    @Shadow private AABB bb;
    @Shadow public abstract void reset();
    @Shadow public abstract long getSectionNode();

    @Unique private SectionKey farlands$key;
    @Unique private AABB farlands$wideBb;
    @Unique private SectionKey farlands$bbKey;
    @Unique private long farlands$wideOriginX;
    @Unique private long farlands$wideOriginY;
    @Unique private long farlands$wideOriginZ;
    @Unique private long farlands$keyNode;
    @Unique private double farlands$bbCamX;
    @Unique private double farlands$bbCamY;
    @Unique private double farlands$bbCamZ;
    @Unique private static int farlands$originLogs;

    @Override
    public SectionKey farlands$getKey() {
        return this.farlands$key;
    }

    @Override
    public long farlands$keyNode() {
        return this.farlands$keyNode;
    }

    @Override
    public long farlands$wideOriginX() {
        return this.farlands$wideOriginX;
    }

    @Override
    public long farlands$wideOriginY() {
        return this.farlands$wideOriginY;
    }

    @Override
    public long farlands$wideOriginZ() {
        return this.farlands$wideOriginZ;
    }

    @Inject(method = "setSectionNode", at = @At("HEAD"))
    private void farlands$dropOldNode(long sectionNode, CallbackInfo ci) {
        RenderKeys.remove(this.getSectionNode());
    }

    @Override
    public void farlands$setKey(SectionKey key) {
        if (key == null) {
            this.farlands$key = null;
            return;
        }
        if (key.equals(this.farlands$key)) {
            return;
        }
        com.farlands.g2.client.RenderResetStats.keySet();
        if (this.farlands$key != null) {
            RenderKeys.remove(this.farlands$key);
        }
        this.farlands$key = key;
        this.farlands$keyNode = this.sectionNode;
        this.reset();
        // X/Z long->double, following the X/Z render solution: the AABB carries the
        // exact position; the int origin is only a legacy view.
        long lx = key.lx << 4;
        long ly = key.ly << 4;
        long lz = key.lz << 4;
        this.farlands$wideOriginX = lx;
        this.farlands$wideOriginY = ly;
        this.farlands$wideOriginZ = lz;
        // Must match the gate/builder rule: once the render grid runs in a local
        // frame, EVERY section takes the camera-relative origin path. Deciding by
        // "block coords exceed int" alone mixes both origins in one frame (some
        // sections cancel the camera translation, others do not), which is the
        // shattered/overlapping geometry seen right at the 2^31 boundary.
        boolean far = com.farlands.g2.client.WideRenderBase.established()
            || lx != (long) (int) lx || ly != (long) (int) ly
            || lz != (long) (int) lz;
        // The packed node stays the LOCAL grid node: the world identity lives in
        // farlands$key. Overwriting it with SectionPos.asLong(key...) is what made the
        // grid stop compiling (all visible sections stayed UNCOMPILED).
        if (far && this instanceof WideOriginHolder holder) {
            holder.g2SetWideOrigin(lx, ly, lz);
            if (Boolean.getBoolean("farlands.diag.origin") && farlands$originLogs++ < 20) {
                System.out.println("[WIDEORIGIN] setKey key=" + key
                    + " has=" + holder.g2HasWideOrigin()
                    + " wide=" + lx + "," + ly + "," + lz);
                System.out.flush();
            }
        }
        this.renderOrigin.set((int) lx, (int) ly, (int) lz);
        this.bb = new AABB((double) lx, (double) ly, (double) lz,
            (double) (lx + 16L), (double) (ly + 16L), (double) (lz + 16L));
        this.farlands$wideBb = far ? this.bb : null;
        RenderKeys.put(key, (SectionRenderDispatcher.RenderSection) (Object) this);
        // reset() dropped the mesh; mark only this section dirty. Marking the whole
        // 3x3x3 neighbourhood here floods the queue whenever the camera moves and
        // leaves the view blank until every round of recompiles finishes.
        //
        // The coordinates must be the LOCAL node's: the update tracker and the extractor
        // work in the render grid's local frame, so dirtying key.x/y/z (world) marks
        // unrelated slots and the section itself never gets a region (measured:
        // createRegion called once instead of once per section).
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null && mc.level != null) {
            long node = this.sectionNode;
            int nx = SectionPos.x(node);
            int ny = SectionPos.y(node);
            int nz = SectionPos.z(node);
            mc.level.setSectionRangeDirty(nx, ny, nz, nx, ny, nz);
        }
    }

    @Inject(method = "getBoundingBox", at = @At("RETURN"), cancellable = true)
    private void farlands$wideCullingBox(CallbackInfoReturnable<AABB> cir) {
        SectionKey key = this.farlands$key;
        if (key == null) {
            return;
        }
        if (Boolean.getBoolean("farlands.diag.nocull")) {
            // Diagnostic: make every section unconditionally visible so a culling
            // mistake can be told apart from a placement/mesh mistake. The box is
            // built around the camera so it cannot be frustum-culled itself.
            var cam = net.minecraft.client.Minecraft.getInstance()
                .gameRenderer.mainCamera().position();
            cir.setReturnValue(new AABB(cam.x - 1.0E6, cam.y - 1.0E6, cam.z - 1.0E6,
                cam.x + 1.0E6, cam.y + 1.0E6, cam.z + 1.0E6));
            return;
        }
        // Same rule as setKey: in the local frame every section is culled with the
        // wide box (the camera is camera-relative there too).
        if (!com.farlands.g2.client.WideRenderBase.established()
            && key.lx == (long) (int) key.lx
            && key.ly == (long) (int) key.ly && key.lz == (long) (int) key.lz) {
            return;
        }
        if (key != this.farlands$bbKey || this.farlands$wideBb == null) {
            long x = key.lx << 4;
            long y = key.ly << 4;
            long z = key.lz << 4;
            // The section model-view is camera space (its translation is already
            // zero for far sections, see FarSectionMatrixXZ), so the culling box has
            // to be camera relative as well. A world-space box against a camera-space
            // frustum culls every far section - which is exactly "meshes exist but
            // nothing is on screen".
            var cam = net.minecraft.client.Minecraft.getInstance()
                .gameRenderer.mainCamera().position();
            this.farlands$wideBb = new AABB(x - cam.x, y - cam.y, z - cam.z,
                x + 16L - cam.x, y + 16L - cam.y, z + 16L - cam.z);
            this.farlands$bbKey = key;
            this.farlands$bbCamX = cam.x;
            this.farlands$bbCamY = cam.y;
            this.farlands$bbCamZ = cam.z;
        } else {
            // Camera moved since the box was built: shift it instead of rebuilding.
            var cam = net.minecraft.client.Minecraft.getInstance()
                .gameRenderer.mainCamera().position();
            if (cam.x != this.farlands$bbCamX || cam.y != this.farlands$bbCamY
                || cam.z != this.farlands$bbCamZ) {
                this.farlands$wideBb = this.farlands$wideBb.move(
                    this.farlands$bbCamX - cam.x, this.farlands$bbCamY - cam.y,
                    this.farlands$bbCamZ - cam.z);
                this.farlands$bbCamX = cam.x;
                this.farlands$bbCamY = cam.y;
                this.farlands$bbCamZ = cam.z;
            }
        }
        cir.setReturnValue(this.farlands$wideBb);
    }
}
