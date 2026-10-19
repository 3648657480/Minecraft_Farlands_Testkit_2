package com.farlands.g2.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PathNavigation.class)
public class PathNavigationFarGuardMixin {

    @Shadow @Final protected Mob mob;

    @Inject(method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;",
        at = @At("HEAD"), cancellable = true)
    private void farlands$skipFarPath(CallbackInfoReturnable<Path> cir) {
        double x = this.mob.getX();
        double z = this.mob.getZ();
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE
            || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) {
            cir.setReturnValue(null);
        }
    }
}
