package com.farlands.g2.runtime.noise;

import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * 冻结八度组成的密度链（移植自 infs-farlands 的 {@code CwgDensity}，MIT；见 NOTICE）。
 *
 * <p>算子顺序是契约：selector 决定 low/high 插值位置；depth 先钳到 [-2,1] 再按正负分段缩放；
 * 最后整体乘 volatility、加 height、减 sign(volatility)*y。depth 必须先钳后分。</p>
 */
public final class CwgDensity {

    /** 密度链参数（默认值与作者 CwgNoiseSystem 一致）。 */
    public record Ops(
        double selectorFactor,
        double selectorOffset,
        double lowFactor,
        double lowOffset,
        double highFactor,
        double highOffset,
        double depthFactor,
        double depthOffset,
        double heightFactor,
        double heightOffset,
        double heightVariationFactor,
        double heightVariationOffset,
        double specialHeightVariationFactor) {
    }

    private final CwgNoise selector;
    private final CwgNoise low;
    private final CwgNoise high;
    private final CwgNoise depth;
    private final DensityFunction offsetSpline;
    private final DensityFunction factorSpline;
    private final Ops ops;

    public CwgDensity(CwgNoise selector, CwgNoise low, CwgNoise high, CwgNoise depth,
        DensityFunction offsetSpline, DensityFunction factorSpline, Ops ops) {
        this.selector = selector;
        this.low = low;
        this.high = high;
        this.depth = depth;
        this.offsetSpline = offsetSpline;
        this.factorSpline = factorSpline;
        this.ops = ops;
    }

    public double compute(DensityFunction.FunctionContext context) {
        double x = context.blockX();
        double y = context.blockY();
        double z = context.blockZ();

        double selectorValue = this.selector.normalized(x, y, z) * this.ops.selectorFactor()
            + this.ops.selectorOffset();
        selectorValue = selectorValue < 0.0 ? 0.0 : (selectorValue > 1.0 ? 1.0 : selectorValue);

        double lowValue = this.low.normalized(x, y, z) * this.ops.lowFactor() + this.ops.lowOffset();
        double highValue = this.high.normalized(x, y, z) * this.ops.highFactor() + this.ops.highOffset();

        double depthValue = this.depth.normalized(x, y, z) * this.ops.depthFactor() + this.ops.depthOffset();
        if (depthValue < 0.0) {
            depthValue *= -0.3;
        }
        depthValue = depthValue * 3.0 - 2.0;
        depthValue = depthValue < -2.0 ? -2.0 : (depthValue > 1.0 ? 1.0 : depthValue);
        if (depthValue < 0.0) {
            depthValue /= 2.0 * 2.0 * 1.4;
        } else if (depthValue > 0.0) {
            depthValue /= 8.0;
        }
        depthValue *= 0.2 * 17.0 / 64.0;

        double height = this.offsetSpline.compute(context) * this.ops.heightFactor()
            + this.ops.heightOffset();
        double variation = Math.abs(this.factorSpline.compute(context));
        if (height > y) {
            variation *= this.ops.specialHeightVariationFactor();
        }
        double volatility = variation * this.ops.heightVariationFactor()
            + this.ops.heightVariationOffset();

        double blended = lowValue + selectorValue * (highValue - lowValue);
        return (blended + depthValue) * volatility + height - Math.signum(volatility) * y;
    }
}
