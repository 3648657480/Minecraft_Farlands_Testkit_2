package com.farlands.g2.runtime.noise;

import java.util.Random;

/**
 * 逐阶 Perlin（移植自 infs-farlands 的 {@code CwgNoise}，MIT；见 NOTICE）。
 *
 * <p>三处语义是契约，改了顶点区（穹顶）的分界形状就变：坐标转 int 是**饱和**而不是回绕，
 * 格点加一按 int32 **回绕**，梯度索引由三个格点索引的线性组合溢出后异或右移取低八位。
 * 坐标越过 2^31 后格点索引冻结、插值权重离开 [0,1] 并按三次增长 → 噪声退化成平面。</p>
 *
 * <p>{@code foldCoordinates=false} 是出现穹顶的前提（true 时坐标被折回 int32 范围，
 * 饱和永不发生）。</p>
 */
public final class CwgNoise {

    private static final double PERSISTENCE = 0.5;
    private static final double LACUNARITY = 2.0;
    private static final double FOLD_LIMIT = 1073741824.0;

    private static final int X_NOISE_GEN = 1619;
    private static final int Y_NOISE_GEN = 31337;
    private static final int Z_NOISE_GEN = 6971;
    private static final int SEED_NOISE_GEN = 1013;
    private static final int SHIFT_NOISE_GEN = 8;

    /** 256 行梯度表，每行 4 个分量，只用前三个；构造后只读。 */
    private static final double[] GRADIENTS = new double[256 * 4];

    static {
        int[] gradX = {1, -1, 1, -1, 1, -1, 1, -1, 0, 0, 0, 0, 1, 0, -1, 0};
        int[] gradY = {1, 1, -1, -1, 0, 0, 0, 0, 1, -1, 1, -1, 1, -1, 1, -1};
        int[] gradZ = {0, 0, 0, 0, 1, 1, -1, -1, 1, 1, -1, -1, 0, 1, 0, -1};
        Random random = new Random(123456789);
        for (int i = 0; i < 256; i++) {
            int j = random.nextInt(gradX.length);
            GRADIENTS[i * 4] = gradX[j] / 2.0;
            GRADIENTS[i * 4 + 1] = gradY[j] / 2.0;
            GRADIENTS[i * 4 + 2] = gradZ[j] / 2.0;
        }
    }

    private final double frequencyX;
    private final double frequencyY;
    private final double frequencyZ;
    private final int octaves;
    private final int seed;
    private final boolean foldCoordinates;
    private final double normalization;

    public CwgNoise(double frequencyX, double frequencyY, double frequencyZ, int octaves, int seed,
        boolean foldCoordinates) {
        this.frequencyX = frequencyX;
        this.frequencyY = frequencyY;
        this.frequencyZ = frequencyZ;
        this.octaves = octaves;
        this.seed = seed;
        this.foldCoordinates = foldCoordinates;
        this.normalization = 2.0 / maxValue(octaves);
    }

    /** 长 seed 折叠成库接收的 int seed（直接强转与先折叠不同）。 */
    public static int foldSeed(long seed) {
        return (int) ((seed & 0xFFFFFFFFL) ^ (seed >>> 32));
    }

    /** 归一化到 [-1,1]。 */
    public double normalized(double x, double y, double z) {
        return sample(x, y, z) * this.normalization - 1.0;
    }

    /** 全部八度之和，未归一化。 */
    private double sample(double x, double y, double z) {
        double x1 = x * this.frequencyX;
        double y1 = y * this.frequencyY;
        double z1 = z * this.frequencyZ;
        double value = 0.0;
        double persistence = 1.0;
        for (int octave = 0; octave < this.octaves; octave++) {
            double nx = this.foldCoordinates ? fold(x1) : x1;
            double ny = this.foldCoordinates ? fold(y1) : y1;
            double nz = this.foldCoordinates ? fold(z1) : z1;
            value += coherent(nx, ny, nz, this.seed + octave) * persistence;
            x1 *= LACUNARITY;
            y1 *= LACUNARITY;
            z1 *= LACUNARITY;
            persistence *= PERSISTENCE;
        }
        return value;
    }

    private static double maxValue(int octaves) {
        return (Math.pow(PERSISTENCE, octaves) - 1.0) / (PERSISTENCE - 1.0);
    }

    private static double fold(double n) {
        if (n >= FOLD_LIMIT) {
            return (2.0 * n % FOLD_LIMIT) - FOLD_LIMIT;
        }
        if (n <= -FOLD_LIMIT) {
            return (2.0 * n % FOLD_LIMIT) + FOLD_LIMIT;
        }
        return n;
    }

    private static double coherent(double x, double y, double z, int seed) {
        int x0 = x > 0.0 ? (int) x : (int) x - 1;
        int y0 = y > 0.0 ? (int) y : (int) y - 1;
        int z0 = z > 0.0 ? (int) z : (int) z - 1;
        int x1 = x0 + 1;
        int y1 = y0 + 1;
        int z1 = z0 + 1;

        double xs = sCurve3(x - x0);
        double ys = sCurve3(y - y0);
        double zs = sCurve3(z - z0);

        double n0 = gradient(x, y, z, x0, y0, z0, seed);
        double n1 = gradient(x, y, z, x1, y0, z0, seed);
        double ix0 = lerp(n0, n1, xs);
        n0 = gradient(x, y, z, x0, y1, z0, seed);
        n1 = gradient(x, y, z, x1, y1, z0, seed);
        double ix1 = lerp(n0, n1, xs);
        double iy0 = lerp(ix0, ix1, ys);
        n0 = gradient(x, y, z, x0, y0, z1, seed);
        n1 = gradient(x, y, z, x1, y0, z1, seed);
        ix0 = lerp(n0, n1, xs);
        n0 = gradient(x, y, z, x0, y1, z1, seed);
        n1 = gradient(x, y, z, x1, y1, z1, seed);
        ix1 = lerp(n0, n1, xs);
        double iy1 = lerp(ix0, ix1, ys);
        return lerp(iy0, iy1, zs);
    }

    private static double gradient(double fx, double fy, double fz, int ix, int iy, int iz, int seed) {
        int index = X_NOISE_GEN * ix + Y_NOISE_GEN * iy + Z_NOISE_GEN * iz + SEED_NOISE_GEN * seed;
        index ^= index >> SHIFT_NOISE_GEN;
        index &= 0xFF;
        return GRADIENTS[index * 4] * (fx - ix)
            + GRADIENTS[index * 4 + 1] * (fy - iy)
            + GRADIENTS[index * 4 + 2] * (fz - iz)
            + 0.5;
    }

    private static double sCurve3(double a) {
        return a * a * (3.0 - 2.0 * a);
    }

    private static double lerp(double a, double b, double t) {
        return (1.0 - t) * a + t * b;
    }
}
