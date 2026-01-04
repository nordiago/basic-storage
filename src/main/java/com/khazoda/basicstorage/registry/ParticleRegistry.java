package com.khazoda.basicstorage.registry;

import com.khazoda.basicstorage.Constants;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

public class ParticleRegistry {

  public static final SimpleParticleType TWINKLE = FabricParticleTypes.simple();

  public static void init() {
    Registry.register(BuiltInRegistries.PARTICLE_TYPE, Constants.ID("twinkle"), TWINKLE);
  }
}