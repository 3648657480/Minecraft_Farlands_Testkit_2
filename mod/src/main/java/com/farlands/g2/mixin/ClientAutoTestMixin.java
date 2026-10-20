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
                    mc.player.connection.sendCommand("tp @s 2147483647.0 70 0.5");
                    farlands$phase = 1;
                    farlands$ticks = 0;
                    System.out.println("[AUTOTEST] teleport sent pos=" + mc.player.position());
                }
            }
            case 1 -> {
                if (farlands$ticks > 3000) {
                    if (farlands$ticks == 3001) {
                        Screenshot.grab(mc, false);
                        System.out.println("[AUTOTEST] screenshot autotest-seam.png");
                    }
                    if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr) {
                        farlands$target = bhr.getBlockPos();
                        System.out.println("[AUTOTEST] hitResult=" + farlands$target);
                    } else {
                        farlands$target = BlockPos.containing(
                            mc.player.getX() + 1.0, mc.player.getY() - 2.0, mc.player.getZ());
                    }
                    BlockState before = mc.level.getBlockState(farlands$target);
                    System.out.println("[AUTOTEST] player=" + mc.player.position()
                        + " target=" + farlands$target + " before=" + before);
                    if (before.isAir()) {
                        System.out.println("[AUTOTEST] BREAK_SKIP_AIR");
                        farlands$phase = 3;
                    } else {
                        mc.gameMode.destroyBlock(farlands$target);
                        farlands$phase = 2;
                    }
                    farlands$ticks = 0;
                }
            }
            case 2 -> {
                if (farlands$ticks > 40) {
                    BlockState after = mc.level.getBlockState(farlands$target);
                    System.out.println("[AUTOTEST] after=" + after
                        + (after.isAir() ? " BREAK_OK" : " BREAK_FAIL"));
                    farlands$phase = 3;
                }
            }
            default -> {
            }
        }
    }
}
