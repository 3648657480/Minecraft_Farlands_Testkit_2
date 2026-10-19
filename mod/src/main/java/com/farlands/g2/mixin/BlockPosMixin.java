package com.farlands.g2.mixin;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockPos.class)
public class BlockPosMixin {

    @Shadow private static long PACKED_X_MASK;
    @Shadow private static long PACKED_Y_MASK;
    @Shadow private static long PACKED_Z_MASK;
    @Shadow public static int PACKED_HORIZONTAL_LENGTH;
    @Shadow private static int X_OFFSET;
    @Shadow private static int Z_OFFSET;

    @Unique private static final long HANDLE_FLAG = Long.MIN_VALUE;
    @Unique private static final ConcurrentHashMap<Long, int[]> REAL_COORDS = new ConcurrentHashMap<>();
    @Unique private static final AtomicLong NEXT_HANDLE = new AtomicLong(0);
    @Unique private static final int MAX_MAP_SIZE = 200000;

    // NOTE (F0-2): the PACKED_HORIZONTAL_LENGTH widening redirect that used to
    // live here changed the packing layout for ALL coordinates, which changed
    // BlockPos.hashCode and therefore HashSet/HashMap iteration order versus
    // vanilla (reproducible terrain differences, e.g. tree features at
    // chunk (625000,625000)). Vanilla packing already covers the whole
    // vanilla range (+/-33,554,431); coordinates beyond that use the handle
    // map below. Do not reintroduce a global layout change.

    @Overwrite
    public static long asLong(int x, int y, int z) {
        int max = (1 << (PACKED_HORIZONTAL_LENGTH - 1)) - 1;
        int min = -(1 << (PACKED_HORIZONTAL_LENGTH - 1));
        if (x >= min && x <= max && z >= min && z <= max) {
            long node = 0L;
            node |= ((long)x & PACKED_X_MASK) << X_OFFSET;
            node |= ((long)y & PACKED_Y_MASK) << 0;
            node |= ((long)z & PACKED_Z_MASK) << Z_OFFSET;
            return node;
        }
        if (REAL_COORDS.size() < MAX_MAP_SIZE) {
            long handle = HANDLE_FLAG | NEXT_HANDLE.incrementAndGet();
            REAL_COORDS.put(handle, new int[]{x, y, z});
            return handle;
        }
        x = Mth.clamp(x, min, max);
        z = Mth.clamp(z, min, max);
        long node = 0L;
        node |= ((long)x & PACKED_X_MASK) << X_OFFSET;
        node |= ((long)y & PACKED_Y_MASK) << 0;
        node |= ((long)z & PACKED_Z_MASK) << Z_OFFSET;
        return node;
    }

    @Inject(method = "getX", at = @At("RETURN"), cancellable = true)
    private static void farlands$getX(long node, CallbackInfoReturnable<Integer> cir) {
        if ((node & HANDLE_FLAG) != 0) {
            int[] c = REAL_COORDS.get(node);
            if (c != null) cir.setReturnValue(c[0]);
        }
    }

    @Inject(method = "getY", at = @At("RETURN"), cancellable = true)
    private static void farlands$getY(long node, CallbackInfoReturnable<Integer> cir) {
        if ((node & HANDLE_FLAG) != 0) {
            int[] c = REAL_COORDS.get(node);
            if (c != null) cir.setReturnValue(c[1]);
        }
    }

    @Inject(method = "getZ", at = @At("RETURN"), cancellable = true)
    private static void farlands$getZ(long node, CallbackInfoReturnable<Integer> cir) {
        if ((node & HANDLE_FLAG) != 0) {
            int[] c = REAL_COORDS.get(node);
            if (c != null) cir.setReturnValue(c[2]);
        }
    }

    @Inject(method = "containing(DDD)Lnet/minecraft/core/BlockPos;",
        at = @At("HEAD"), cancellable = true)
    private static void farlands$containingWide(double x, double y, double z, CallbackInfoReturnable<BlockPos> cir) {
        long lx = (long)Math.floor(x);
        long ly = Mth.floor(y);
        long lz = (long)Math.floor(z);
        cir.setReturnValue(new BlockPos(lx, ly, lz));
    }

    /**
     * The patched BlockPos.betweenClosed clamps every corner into the int
     * range. For a render section east of 2^31 the long corner is 2^31..2^31+15,
     * both corners clamp to Integer.MAX_VALUE, and the section compiler visits
     * only block x=2147483647 instead of the east section. Keep the clamp for
     * large worldgen boxes, but let small (<=16^3) section-sized boxes use the
     * wrapped int view so the int iterator walks the real 16-block span.
     */
    @Overwrite
    public static Iterable<BlockPos> betweenClosed(BlockPos a, BlockPos b) {
        long minX = Math.min(a.getLongX(), b.getLongX());
        long minY = Math.min(a.getLongY(), b.getLongY());
        long minZ = Math.min(a.getLongZ(), b.getLongZ());
        long maxX = Math.max(a.getLongX(), b.getLongX());
        long maxY = Math.max(a.getLongY(), b.getLongY());
        long maxZ = Math.max(a.getLongZ(), b.getLongZ());
        long spanX = maxX - minX;
        long spanY = maxY - minY;
        long spanZ = maxZ - minZ;
        if (spanX <= 16L && spanY <= 16L && spanZ <= 16L) {
            final long fx0 = minX;
            final long fy0 = minY;
            final long fz0 = minZ;
            final long fx1 = maxX;
            final long fy1 = maxY;
            final long fz1 = maxZ;
            return () -> new Iterator<BlockPos>() {
                private long x = fx0;
                private long y = fy0;
                private long z = fz0;

                @Override
                public boolean hasNext() {
                    return this.y <= fy1;
                }

                @Override
                public BlockPos next() {
                    if (!this.hasNext()) {
                        throw new NoSuchElementException();
                    }
                    BlockPos pos = new BlockPos(this.x, this.y, this.z);
                    if (++this.x > fx1) {
                        this.x = fx0;
                        if (++this.z > fz1) {
                            this.z = fz0;
                            ++this.y;
                        }
                    }
                    return pos;
                }
            };
        }
        int cx0 = (int) Math.max((long) Integer.MIN_VALUE, Math.min((long) Integer.MAX_VALUE, minX));
        int cy0 = (int) Math.max((long) Integer.MIN_VALUE, Math.min((long) Integer.MAX_VALUE, minY));
        int cz0 = (int) Math.max((long) Integer.MIN_VALUE, Math.min((long) Integer.MAX_VALUE, minZ));
        int cx1 = (int) Math.max((long) Integer.MIN_VALUE, Math.min((long) Integer.MAX_VALUE, maxX));
        int cy1 = (int) Math.max((long) Integer.MIN_VALUE, Math.min((long) Integer.MAX_VALUE, maxY));
        int cz1 = (int) Math.max((long) Integer.MIN_VALUE, Math.min((long) Integer.MAX_VALUE, maxZ));
        return BlockPos.betweenClosed(cx0, cy0, cz0, cx1, cy1, cz1);
    }
}
