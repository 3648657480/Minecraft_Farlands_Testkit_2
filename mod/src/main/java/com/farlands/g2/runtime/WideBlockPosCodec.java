package com.farlands.g2.runtime;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import net.minecraft.core.BlockPos;

/**
 * Persistence codec for block positions.
 *
 * <p>Vanilla {@code BlockPos.CODEC} is an int stream of three values, so any
 * saved position at or beyond 2^31 is truncated and the entity/tick/POI state
 * reloads at the mirrored coordinate. Encode as a long stream instead; still
 * decode the legacy int stream so existing worlds load unchanged.</p>
 */
public final class WideBlockPosCodec {

    public static final Codec<BlockPos> CODEC = Codec.either(Codec.LONG_STREAM, Codec.INT_STREAM)
        .flatXmap(WideBlockPosCodec::decode, pos -> DataResult.success(Either.left(
            LongStream.of(pos.getLongX(), pos.getLongY(), pos.getLongZ()))));

    private WideBlockPosCodec() {
    }

    private static DataResult<BlockPos> decode(Either<LongStream, IntStream> either) {
        if (either.left().isPresent()) {
            long[] values = either.left().get().toArray();
            if (values.length != 3) {
                return DataResult.error(() -> "Expected 3 block coordinates, got " + values.length);
            }
            return DataResult.success(new BlockPos(values[0], values[1], values[2]));
        }
        int[] values = either.right().orElseThrow().toArray();
        if (values.length != 3) {
            return DataResult.error(() -> "Expected 3 block coordinates, got " + values.length);
        }
        return DataResult.success(new BlockPos(values[0], values[1], values[2]));
    }
}