package com.farlands.g2.mixin;

import com.farlands.g2.runtime.WideClipCoordinates;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Entity.class)
public class EntityPickWideMixin {
    @Redirect(method = "pick(DFZ)Lnet/minecraft/world/phys/HitResult;", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"))
    private BlockHitResult farlands$restoreHit(Level level, ClipContext context) {
        return WideClipCoordinates.restore(context, level.clip(context));
    }
}
