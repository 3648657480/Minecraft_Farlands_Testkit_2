package com.farlands.g2.mixin;

import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复“第二次进世界卡 30 秒”。
 *
 * <p>G2 的 {@code SectionOcclusionGraphPatch} 重写了
 * {@code SectionOcclusionGraph.update(...)}，不再调用 vanilla 的
 * {@code scheduleFullUpdate / runPartialUpdate}，因此
 * {@code needsFrustumUpdate} 永远为 false。{@code LevelExtractor.extract} 里
 * {@code applyFrustum} 只剩“相机旋转（按 2° 取整）变化”这一个触发条件。</p>
 *
 * <p>退出世界时 {@code LevelRenderer.resetLevelRenderData()} /
 * {@code invalidateCompiledGeometry()} 会清空 visibleSections；再次进入同一个
 * 世界时相机旋转与上次相同（同一朝向），{@code applyFrustum} 不会执行，
 * visibleSections 一直为空 → 没有任何 section 被排入编译 →
 * 玩家所在 section 永远 UNCOMPILED → {@code LevelLoadTracker} 等满 30 秒超时。</p>
 *
 * <p>在每个关卡切换/视距变化（{@code allChanged()}）后把旋转哨兵复位，
 * 让下一帧强制执行一次 {@code applyFrustum}，重新填充可见 section 列表。
 * 只多跑一次列表填充，不改变每帧行为。</p>
 */
@Mixin(LevelExtractor.class)
public class LevelExtractorFrustumResetMixin {

    @Shadow private double prevCamRotX;
    @Shadow private double prevCamRotY;

    @Inject(method = "allChanged", at = @At("TAIL"))
    private void farlands$forceFrustumAfterLevelChange(CallbackInfo ci) {
        this.prevCamRotX = Double.MIN_VALUE;
        this.prevCamRotY = Double.MIN_VALUE;
    }
}
