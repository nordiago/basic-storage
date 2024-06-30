package com.khazoda.basic_storage.mixin;

import com.khazoda.basic_storage.Constants;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ItemStack.class)
public class MixinItemStack {
  @ModifyArg(method = "method_57371", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/dynamic/Codecs;rangedInt(II)Lcom/mojang/serialization/Codec;"), index = 1)
  private static int modifyMaxStack(int orig) {
    return Math.max(orig, Constants.CRATE_MAX_COUNT);
  }
}