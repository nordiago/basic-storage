package com.khazoda.basic_storage.mixin;
import net.minecraft.component.DataComponentTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(DataComponentTypes.class)
public class MixinDataComponentTypes {
  @ModifyArg(method="method_58570", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/dynamic/Codecs;rangedInt(II)Lcom/mojang/serialization/Codec;", ordinal = 0), index = 1)
  private static int adjustMaxItemStack(int orig) {
    return Math.max(orig, 1000000);
  }
}