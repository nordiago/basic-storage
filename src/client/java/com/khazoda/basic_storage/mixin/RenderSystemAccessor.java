package com.khazoda.basic_storage.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderSystem.class)
public interface RenderSystemAccessor {

  @Accessor ("shaderLightDirections")
  public static Vector3f[] getShaderLightDirections() {
    throw new AssertionError();
  }
}