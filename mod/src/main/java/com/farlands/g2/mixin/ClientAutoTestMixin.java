package com.farlands.g2.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minimal in-client self test ({@code -Dfarlands.autotest=true}):
 * join world, teleport to the 2^31 seam, break the block below, report
 * whether the block actually became air. Logged as {@code [AUTOTEST]}.
 */
@Mixin(Minecraft.class)
public class ClientAutoTestMixin {

    private static final boolean ENABLED = Boolean.getBoolean("farlands.autotest");

    private static int farlands$ticks;
    private static int farlands$phase;
    private static BlockPos farlands$target;
    private static boolean farlands$logged;
    private static boolean farlands$shot;

    @Inject(method = "runTick", at = @At("HEAD"))
    private void farlands$tick(boolean advanceGameTime, CallbackInfo ci) {
        if (!farlands$logged) {
            farlands$logged = true;
            System.out.println("[AUTOTEST] tick enabled=" + ENABLED);
            System.out.flush();
        }
        if (!ENABLED) {
            return;
        }
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.options != null) {
            mc.options.pauseOnLostFocus = false;
        }
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        farlands$ticks++;
        switch (farlands$phase) {
            case 0 -> {
                if (farlands$ticks > 100) {
                    mc.player.connection.sendCommand("gamemode creative");
                    mc.player.connection.sendCommand("tp @s 2147483648.0 70 0.5");
                    farlands$phase = 1;
                    farlands$ticks = 0;
                    System.out.println("[AUTOTEST] teleport sent pos=" + mc.player.position()
                        + " level=" + mc.level.dimension().identifier());
                }
            }
            case 1 -> {
                if (farlands$ticks % 200 == 0) {
                    System.out.println("[AUTOTEST] wait ticks=" + farlands$ticks + " pos=" + mc.player.position());
                }
                boolean ready = farlands$chunkReady(mc);
                if (farlands$ticks > 1200 && (ready || farlands$ticks > 6000)) {
                    if (!farlands$shot) {
                        farlands$shot = true;
                        Screenshot.grab(mc, false);
                        System.out.println("[AUTOTEST] screenshot autotest-seam.png ready=" + ready);
                    }
                    if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr) {
                        farlands$target = bhr.getBlockPos();
                        System.out.println("[AUTOTEST] hitResult=" + farlands$target);
                    } else {
                        farlands$target = BlockPos.containing(
                            mc.player.getX() + 1.0, mc.player.getY() - 2.0, mc.player.getZ());
                    }
                    BlockState before = mc.level.getBlockState(farlands$target);
                    int guard = 0;
                    while ((before.isAir() || !before.getFluidState().isEmpty()) && guard++ < 40) {
                        farlands$target = farlands$target.below();
                        before = mc.level.getBlockState(farlands$target);
                    }
                    System.out.println("[AUTOTEST] player=" + mc.player.position()
                        + " target=" + farlands$target + " before=" + before);
                    if (before.isAir()) {
                        System.out.println("[AUTOTEST] BREAK_SKIP_AIR");
                        farlands$phase = 3;
                    } else {
                        String cmd = String.format(java.util.Locale.ROOT,
                            "tp @s %.1f %.1f %.1f 0 70",
                            (double) farlands$target.getLongX() + 0.5,
                            (double) farlands$target.getLongY() + 2.0,
                            (double) farlands$target.getLongZ() + 0.5);
                        System.out.println("[AUTOTEST] " + cmd);
                        mc.player.connection.sendCommand(cmd);
                        System.out.println("[AUTOTEST] moved next to target");
                        farlands$phase = 4;
                    }
                    farlands$ticks = 0;
                }
            }
            case 4 -> {
                if (farlands$ticks == 25) {
                    // Capture the selection outline before the block disappears.
                    Screenshot.grab(mc, false);
                }
                if (farlands$ticks > 30) {
                    net.minecraft.core.BlockPos side = farlands$target.offset(0, 0, 1);
                    boolean direct = mc.level.setBlock(side, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                    System.out.println("[AUTOTEST] directSetBlock=" + direct + " side=" + side + " state=" + mc.level.getBlockState(side));
                    System.out.println("[AUTOTEST] creative=" + mc.player.isCreative() + " mayBuild=" + mc.player.mayBuild()
                        + " border=" + mc.level.getWorldBorder().isWithinBounds(farlands$target));
                    boolean ok = mc.gameMode.destroyBlock(farlands$target);
                    System.out.println("[AUTOTEST] destroyBlock=" + ok);
                    farlands$phase = 2;
                    farlands$ticks = 0;
                }
            }
            case 2 -> {
                if (farlands$ticks > 40) {
                    BlockState after = mc.level.getBlockState(farlands$target);
                    System.out.println("[AUTOTEST] after=" + after
                        + (after.isAir() ? " BREAK_OK" : " BREAK_FAIL"));
                    farlands$phase = 5;
                    farlands$ticks = 0;
                }
            }
            case 5 -> {
                // Vantage shot for the seam: stand east of block 2^31, look west
                // across it from above, let the world settle, then capture.
                if (farlands$ticks == 40) {
                    mc.player.connection.sendCommand("tp @s 2147483730 100 0.5 90 25");
                    System.out.println("[AUTOTEST] vantage tp sent");
                }
                if (farlands$ticks > 460) {
                    Screenshot.grab(mc, false);
                    System.out.println("[AUTOTEST] vantage screenshot pos=" + mc.player.position());
                    farlands$phase = 3;
                }
            }
            default -> {
            }
        }
    }

    private static boolean farlands$chunkReady(Minecraft mc) {
        if (mc.level == null) {
            return false;
        }
        var src = mc.level.getChunkSource();
        return src.getChunk(134217727, 0,
                net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) != null
            && src.getChunk(134217728, 0,
                net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) != null;
    }
}