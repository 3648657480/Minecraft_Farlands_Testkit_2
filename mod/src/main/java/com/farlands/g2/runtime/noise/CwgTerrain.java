package com.farlands.g2.runtime.noise;

import java.util.Random;
import net.minecraft.data.worldgen.TerrainProvider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseRouter;

/**
 * 组装 infs-farlands 的 Cwg 噪声链（MIT；见 NOTICE）：四路逐阶 Perlin + 两条形状样条。
 *
 * <p>默认参数与作者 {@code CwgNoiseSystem} 一致；{@code farLands=true} 对应
 * {@code foldCoordinates=false}——坐标不折回 ±2^30，越过 2^31 后格点索引冻结、
 * 权重溢出 → 密度零集退化成平面（穹顶）。</p>
 *
 * <p>样条取自当前 {@link NoiseRouter} 的 continents/erosion/ridges，与群系数据无关。</p>
 */
public final class CwgTerrain {

    private static final double SELECTOR_FREQUENCY_XZ = (684.412 / 80.0 / 128.0) / 4.0;
    private static final double SELECTOR_FREQUENCY_Y = (684.412 / 80.0 / 128.0) / 8.0;
    private static final double LOW_HIGH_FREQUENCY_XZ = (684.412 / 32768.0) / 4.0;
    private static final double LOW_HIGH_FREQUENCY_Y = (684.412 / 32768.0) / 8.0;
    private static final double DEPTH_FREQUENCY_XZ = (200.0 / 32768.0) / 4.0;

    private final CwgNoise selector;
    private final CwgNoise low;
    private final CwgNoise high;
    private final CwgNoise depth;
    private final CwgDensity.Ops ops;
    private final DensityFunction offsetSpline;
    private final DensityFunction factorSpline;
    private final Ctx context = new Ctx();

    private CwgTerrain(CwgNoise selector, CwgNoise low, CwgNoise high, CwgNoise depth,
        CwgDensity.Ops ops, DensityFunction offsetSpline, DensityFunction factorSpline) {
        this.selector = selector;
        this.low = low;
        this.high = high;
        this.depth = depth;
        this.ops = ops;
        this.offsetSpline = offsetSpline;
        this.factorSpline = factorSpline;
    }

    public static CwgTerrain create(long seed, NoiseRouter router, boolean farLands) {
        boolean foldCoordinates = !farLands;
        Random random = new Random(seed);
        CwgNoise selector = new CwgNoise(SELECTOR_FREQUENCY_XZ, SELECTOR_FREQUENCY_Y,
            SELECTOR_FREQUENCY_XZ, 8, CwgNoise.foldSeed(random.nextLong()), foldCoordinates);
        CwgNoise low = new CwgNoise(LOW_HIGH_FREQUENCY_XZ, LOW_HIGH_FREQUENCY_Y,
            LOW_HIGH_FREQUENCY_XZ, 16, CwgNoise.foldSeed(random.nextLong()), foldCoordinates);
        CwgNoise high = new CwgNoise(LOW_HIGH_FREQUENCY_XZ, LOW_HIGH_FREQUENCY_Y,
            LOW_HIGH_FREQUENCY_XZ, 16, CwgNoise.foldSeed(random.nextLong()), foldCoordinates);
        CwgNoise depth = new CwgNoise(DEPTH_FREQUENCY_XZ, 0.0,
            DEPTH_FREQUENCY_XZ, 16, CwgNoise.foldSeed(random.nextLong()), foldCoordinates);

        CwgDensity.Ops ops = new CwgDensity.Ops(12.75, 0.5, 1.0, 0.0, 1.0, 0.0, 1.024, 0.0,
            64.0, 64.0, 64.0, 0.0, 0.25);

        DensityFunction ridgesFolded = peaksAndValleys(router.ridges());
        DensityFunction offsetSpline = DensityFunctions.spline(TerrainProvider.overworldOffset(
            coordinate(router.continents()), coordinate(router.erosion()), coordinate(ridgesFolded), false));
        DensityFunction factorSpline = DensityFunctions.spline(TerrainProvider.overworldFactor(
            coordinate(router.continents()), coordinate(router.erosion()), coordinate(router.ridges()),
            coordinate(ridgesFolded), false));
        return new CwgTerrain(selector, low, high, depth, ops, offsetSpline, factorSpline);
    }

    public double density(int x, int y, int z) {
        this.context.x = x;
        this.context.y = y;
        this.context.z = z;
        return new CwgDensity(this.selector, this.low, this.high, this.depth,
            this.offsetSpline, this.factorSpline, this.ops).compute(this.context);
    }

    /** 用 Cwg 密度填充一段窗口（正密度=石，否则空气）；返回填充的方块数。 */
    public long fill(ChunkAccess chunk, int minSectionY, int maxSectionY) {
        long startNanos = System.nanoTime();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        net.minecraft.world.level.chunk.LevelChunkSection[] sections = chunk.getSections();
        int minChunkSectionY = chunk.getMinSectionY();
        long filled = 0;
        for (int sy = minSectionY; sy <= maxSectionY; sy++) {
            int index = sy - minChunkSectionY;
            if (index < 0 || index >= sections.length) {
                continue;
            }
            net.minecraft.world.level.chunk.LevelChunkSection section = sections[index];
            for (int ly = 0; ly < 16; ly++) {
                int y = sy * 16 + ly;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        boolean solid = density(baseX + x, y, baseZ + z) > 0.0;
                        // Write the section directly: going through ChunkAccess per block
                        // (section lookup + heightmap hooks) dominated the fill time.
                        section.setBlockState(x, ly, z, solid ? stone : air);
                        if (solid) {
                            filled++;
                        }
                    }
                }
            }
        }
        System.out.println("[CWGFILL] sy=" + minSectionY + ".." + maxSectionY + " filled=" + filled
            + " ms=" + (System.nanoTime() - startNanos) / 1_000_000L);
        System.out.flush();
        return filled;
    }

    private static DensityFunction peaksAndValleys(DensityFunction weirdness) {
        return DensityFunctions.mul(
            DensityFunctions.add(
                DensityFunctions.add(weirdness.abs(), DensityFunctions.constant(-0.6666666666666666)).abs(),
                DensityFunctions.constant(-0.3333333333333333)),
            DensityFunctions.constant(-3.0));
    }

    private static DensityFunctions.Spline.Coordinate coordinate(DensityFunction function) {
        return new DensityFunctions.Spline.Coordinate(function);
    }

    private static final class Ctx implements DensityFunction.FunctionContext {
        int x;
        int y;
        int z;

        @Override public int blockX() {
            return this.x;
        }

        @Override public int blockY() {
            return this.y;
        }

        @Override public int blockZ() {
            return this.z;
        }
    }
}
