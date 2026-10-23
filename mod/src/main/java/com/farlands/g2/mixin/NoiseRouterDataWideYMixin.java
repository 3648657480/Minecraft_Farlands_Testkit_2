package com.farlands.g2.mixin;

import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * infs-farlands 的 Y 线索手法（仅实验开关 {@code -Dfarlands.router.wideY=true}）。
 *
 * <p>{@code NoiseRouterData.bootstrap} 用 {@code DimensionType.MIN_Y/MAX_Y} 构造
 * {@code belowBottom = MIN_Y*2}（−128）、{@code aboveTop = MAX_Y*2}（640）、
 * {@code Y = yClampedGradient(±)} 以及矿脉的上下界。把这三个读点换成
 * ±(MAX_BLOCK/2)（乘 2 后仍落在 int 内），Y 就不再被钳在 −128…640：
 * 高 Y 的密度会按真实坐标求值，而不是常数尾巴。</p>
 *
 * <p>默认关闭（原值返回），因此普通世界逐位不变。</p>
 */
@Mixin(NoiseRouterData.class)
public class NoiseRouterDataWideYMixin {

    /** ±MAX_BLOCK/2：乘 2 后仍落在 int 内的最大对称边界。 */
    private static final int FARLANDS_SATURATED = 2147483647 / 2;

    @Redirect(method = "bootstrap",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/dimension/DimensionType;MIN_Y:I"))
    private static int farlands$minY() {
        return Boolean.getBoolean("farlands.router.wideY") ? -FARLANDS_SATURATED : DimensionType.MIN_Y;
    }

    @Redirect(method = "bootstrap",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/dimension/DimensionType;MAX_Y:I"))
    private static int farlands$maxY() {
        return Boolean.getBoolean("farlands.router.wideY") ? FARLANDS_SATURATED : DimensionType.MAX_Y;
    }
}
