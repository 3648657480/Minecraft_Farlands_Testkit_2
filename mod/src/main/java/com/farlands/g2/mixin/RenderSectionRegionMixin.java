package com.farlands.g2.mixin;

import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCopy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Section-copy selection must use the wide block coordinates.
 *
 * <p>{@code RenderSectionRegion.getBlockState} derives the owning section with
 * {@code SectionPos.blockToSectionCoord(pos.getX())}, i.e. the int view. The
 * section compiler walks block positions built from the wide origin
 * ({@code BlockPos(long,long,long)}), so at/after block 2^31 the int view is
 * negative while the real X is positive (block 2^31 = section 2^27): the
 * derived section coordinate is off by 2^28 and {@code index(...)} falls
 * outside the 3x3x3 array. The legacy guard answered with index 0 - the
 * (minX,minY,minZ) corner - so every section east of the seam was compiled
 * from its own chunk's x-1/y-1/z-1 neighbour: wrong section content (cross
 * chunk seams, raised slabs, floating slices) while the block data itself is
 * correct.
 *
 * <p>Fix: when the int views are lossy, resolve the section from
 * {@code getLongX/Y/Z()}. At normal coordinates the vanilla body runs
 * unchanged, so results are bit-identical there.</p>
 */
@Mixin(RenderSectionRegion.class)
public abstract class RenderSectionRegionMixin {

    @Shadow @Final private SectionCopy[] sections;
    @Shadow @Final private int minSectionX;
    @Shadow @Final private int minSectionY;
    @Shadow @Final private int minSectionZ;

    @Unique private static int farlands$wideLookups;
    @Unique private static int farlands$outOfRange;

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void farlands$wideBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (farlands$intExact(pos)) {
            return;
        }
        cir.setReturnValue(farlands$wideSection(pos).getBlockState(pos));
    }

    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void farlands$wideFluidState(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        if (farlands$intExact(pos)) {
            return;
        }
        cir.setReturnValue(farlands$wideSection(pos).getBlockState(pos).getFluidState());
    }

    @Inject(method = "getBlockEntity", at = @At("HEAD"), cancellable = true)
    private void farlands$wideBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        if (farlands$intExact(pos)) {
            return;
        }
        cir.setReturnValue(farlands$wideSection(pos).getBlockEntity(pos));
    }

    @Unique
    private static boolean farlands$intExact(BlockPos pos) {
        return pos.getLongX() == (long) pos.getX()
            && pos.getLongY() == (long) pos.getY()
            && pos.getLongZ() == (long) pos.getZ();
    }

    @Unique
    private SectionCopy farlands$wideSection(BlockPos pos) {
        int sx = SectionPos.blockToSectionCoord(pos.getLongX());
        int sy = SectionPos.blockToSectionCoord(pos.getLongY());
        int sz = SectionPos.blockToSectionCoord(pos.getLongZ());
        int index = sx - this.minSectionX
            + (sy - this.minSectionY) * 3
            + (sz - this.minSectionZ) * 3 * 3;
        if (index < 0 || index >= this.sections.length) {
            if (Boolean.getBoolean("farlands.diag.region") && farlands$outOfRange++ < 5) {
                System.out.println("[RREGION] out-of-range section=" + sx + "," + sy + "," + sz
                    + " pos=" + pos.getLongX() + "," + pos.getLongY() + "," + pos.getLongZ()
                    + " regionMin=" + this.minSectionX + "," + this.minSectionY + "," + this.minSectionZ);
                System.out.flush();
            }
            index = 13; // centre: the section being compiled, never a diagonal neighbour
        } else if (Boolean.getBoolean("farlands.diag.region") && farlands$wideLookups++ < 5) {
            System.out.println("[RREGION] wide lookup pos=" + pos.getLongX() + "," + pos.getLongY()
                + "," + pos.getLongZ() + " intView=" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                + " section=" + sx + "," + sy + "," + sz
                + " regionMin=" + this.minSectionX + "," + this.minSectionY + "," + this.minSectionZ
                + " index=" + index);
            System.out.flush();
        }
        return this.sections[index];
    }

    /**
     * Last-resort guard on the vanilla index path: an out-of-range index must
     * never silently select a <em>different</em> section. Index 13 is the
     * centre of the 3x3x3 region, i.e. the section being compiled.
     */
    @Inject(method = "index(IIIIII)I", at = @At("RETURN"), cancellable = true)
    private static void farlands$guardIndex(int minX, int minY, int minZ, int sx, int sy, int sz,
        CallbackInfoReturnable<Integer> cir) {
        int idx = cir.getReturnValue();
        if (idx < 0 || idx >= 27) {
            if (Boolean.getBoolean("farlands.diag.region") && farlands$outOfRange++ < 5) {
                System.out.println("[RREGION] out-of-range index=" + idx + " section=" + sx + "," + sy
                    + "," + sz + " regionMin=" + minX + "," + minY + "," + minZ);
                System.out.flush();
            }
            cir.setReturnValue(13);
        }
    }
}
