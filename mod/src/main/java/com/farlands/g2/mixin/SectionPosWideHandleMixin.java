package com.farlands.g2.mixin;

import com.farlands.g2.runtime.section.SectionPosHandles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * P0 of {@code docs/RENDER-WIDE-KEYS.md}: the {@code long} section key is an
 * interned handle backed by the 2560-bit identity, instead of a packed
 * coordinate.
 *
 * <p>This repo's {@code SectionPosPatch} packs {@code asLong} as 29+29+6 bits
 * (y only 6 bits, -32..31 sections), which aliases at far Y. With
 * {@code -Dfarlands.render.wideKeys=true} every accessor below routes through
 * {@link SectionPosHandles}; foreign longs (created before the flag or by other
 * code) fall back to the exact same 29/29/6 packing so normal worlds are
 * bit-identical.</p>
 */
@Mixin(SectionPos.class)
public class SectionPosWideHandleMixin {

    private static final long X_MASK = 0x1FFFFFFFL;
    private static final long Y_MASK = 0x3FL;

    @Overwrite
    public static long asLong(int x, int y, int z) {
        long handle = SectionPosHandles.handle(x, y, z);
        if (handle != 0L) {
            return handle;
        }
        return ((long) x & X_MASK) << 35 | ((long) y & Y_MASK) | ((long) z & X_MASK) << 6;
    }

    @Overwrite
    public static long asLong(BlockPos pos) {
        // BlockPos is in block coordinates; asLong(int,int,int) takes section coords.
        return SectionPos.asLong(SectionPos.blockToSectionCoord(pos.getX()),
            SectionPos.blockToSectionCoord(pos.getY()),
            SectionPos.blockToSectionCoord(pos.getZ()));
    }

    @Overwrite
    public long asLong() {
        SectionPos self = (SectionPos) (Object) this;
        return SectionPos.asLong(self.getX(), self.getY(), self.getZ());
    }

    @Overwrite
    public static int x(long packed) {
        SectionPosHandles.Entry entry = SectionPosHandles.entry(packed);
        return entry != null ? entry.x : (int) (packed >> 35);
    }

    @Overwrite
    public static int y(long packed) {
        SectionPosHandles.Entry entry = SectionPosHandles.entry(packed);
        return entry != null ? entry.y : (int) (packed << 58 >> 58);
    }

    @Overwrite
    public static int z(long packed) {
        SectionPosHandles.Entry entry = SectionPosHandles.entry(packed);
        return entry != null ? entry.z : (int) (packed << 29 >> 35);
    }

    @Overwrite
    public static SectionPos of(long packed) {
        SectionPosHandles.Entry entry = SectionPosHandles.entry(packed);
        if (entry != null) {
            return SectionPos.of(entry.x, entry.y, entry.z);
        }
        return SectionPos.of(SectionPos.x(packed), SectionPos.y(packed), SectionPos.z(packed));
    }

    @Overwrite
    public static long offset(long packed, Direction direction) {
        return SectionPos.offset(packed, direction.getStepX(), direction.getStepY(),
            direction.getStepZ());
    }

    @Overwrite
    public static long offset(long packed, int dx, int dy, int dz) {
        SectionPosHandles.Entry entry = SectionPosHandles.entry(packed);
        if (entry != null) {
            return SectionPos.asLong(entry.x + dx, entry.y + dy, entry.z + dz);
        }
        return SectionPos.asLong(SectionPos.x(packed) + dx, SectionPos.y(packed) + dy,
            SectionPos.z(packed) + dz);
    }

    @Overwrite
    public static long blockToSection(long levelPos) {
        // Packed BlockPos long: far Y cannot be recovered from this form, so this
        // path keeps vanilla semantics; render code reaches sections through
        // asLong(int,int,int)/asLong(BlockPos) instead.
        return SectionPos.asLong(SectionPos.blockToSectionCoord(BlockPos.getX(levelPos)),
            SectionPos.blockToSectionCoord(BlockPos.getY(levelPos)),
            SectionPos.blockToSectionCoord(BlockPos.getZ(levelPos)));
    }

    @Overwrite
    public static long getZeroNode(long packed) {
        SectionPosHandles.Entry entry = SectionPosHandles.entry(packed);
        if (entry != null) {
            return SectionPos.asLong(entry.x, 0, entry.z);
        }
        return SectionPos.asLong(SectionPos.x(packed), 0, SectionPos.z(packed));
    }

    @Overwrite
    public static long getZeroNode(int x, int z) {
        return SectionPos.asLong(x, 0, z);
    }
}
