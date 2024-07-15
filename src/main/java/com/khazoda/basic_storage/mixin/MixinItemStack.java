package com.khazoda.basic_storage.mixin;

import com.khazoda.basic_storage.Constants;
import com.khazoda.basic_storage.registry.DataComponentRegistry;
import net.minecraft.component.ComponentHolder;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class MixinItemStack {
  @ModifyArg(method = "method_57371", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/dynamic/Codecs;rangedInt(II)Lcom/mojang/serialization/Codec;"), index = 1)
  private static int modifyMaxStack(int orig) {
    return Math.max(orig, Constants.CRATE_MAX_COUNT);
  }
}