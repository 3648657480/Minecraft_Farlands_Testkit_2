package com.farlands.g2.mixin;

import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCopy;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderSectionRegion.class)
public abstract class RenderSectionRegionMixin {

    @Unique private static final boolean farlands$traceEnabled = Boolean.getBoolean("farlands.trace.seam");

    @Shadow private int minSectionX;
    @Shadow private int minSectionY;
    @Shadow private int minSectionZ;
    @Shadow private SectionCopy[] sections;

    @Unique
    private static int farlands$relativeSection(int section, int center) {
        int rel = (section - center) & 0x0FFFFFFF;
        if (rel >= 0x08000000) {
            rel -= 0x10000000;
        }
        return rel;
    }

    @Unique
    private SectionCopy farlands$getSection(BlockPos pos) {
        int dx = farlands$relativeSection(pos.getX() >> 4, this.minSectionX);
        int dy = farlands$relativeSection(pos.getY() >> 4, this.minSectionY);
        int dz = farlands$relativeSection(pos.getZ() >> 4, this.minSectionZ);
        if (dx < 0 || dx >= 3 || dy < 0 || dy >= 3 || dz < 0 || dz >= 3) {
            return null;
        }
        return this.sections[dx + dy * 3 + dz * 9];
    }

    @Unique private static long farlands$lastCompare;

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void farlands$getBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        SectionCopy copy = this.farlands$getSection(pos);
        if (copy != null) {
            BlockState state = copy.getBlockState(pos);
            if (farlands$traceEnabled && pos.getY() == 67 && pos.getZ() == 0
                && (pos.getX() > 2_147_483_600 || pos.getX() < -2_147_483_600)) {
                long now = System.currentTimeMillis();
                if (now - farlands$lastCompare >= 1000L) {
                    farlands$lastCompare = now;
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    BlockState levelState = mc.level == null ? null : mc.level.getBlockState(pos);
                    System.out.println("[COPY] pos=" + pos
                        + " copy=" + state.getBlock().getDescriptionId()
                        + " level=" + (levelState == null ? "null"
                            : levelState.getBlock().getDescriptionId()));
                    System.out.flush();
                }
            }
            cir.setReturnValue(state);
        } else {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }

    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void farlands$getFluidState(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        SectionCopy copy = this.farlands$getSection(pos);
        if (copy != null) {
            cir.setReturnValue(copy.getBlockState(pos).getFluidState());
        } else {
            cir.setReturnValue(Fluids.EMPTY.defaultFluidState());
        }
    }

    @Inject(method = "getBlockEntity", at = @At("HEAD"), cancellable = true)
    private void farlands$getBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        SectionCopy copy = this.farlands$getSection(pos);
        if (copy != null) {
            cir.setReturnValue(copy.getBlockEntity(pos));
        } else {
            cir.setReturnValue(null);
        }
    }

}
