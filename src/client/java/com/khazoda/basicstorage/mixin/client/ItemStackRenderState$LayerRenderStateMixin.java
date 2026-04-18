package com.khazoda.basicstorage.mixin.client;

import com.khazoda.basicstorage.renderer.CrateItemSpecialRenderer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public class ItemStackRenderState$LayerRenderStateMixin {
    @Shadow
    @Final
    ItemStackRenderState this$0;

    @WrapOperation(
            method = "submit",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/special/SpecialModelRenderer;submit(Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V")
    )
    private <T> void captureDisplayContext(SpecialModelRenderer<T> instance, @Nullable T t, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i1, int i2, boolean b, int i3, Operation<Void> original) {
        ScopedValue.where(CrateItemSpecialRenderer.CONTEXT, ((ItemStackRenderStateAccessor) this$0).basicStorage$displayContext()).run(() -> original.call(instance, t, poseStack, submitNodeCollector, i1, i2, b, i3));
    }
}
