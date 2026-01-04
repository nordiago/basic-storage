package com.khazoda.basicstorage.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

public class VoidyParticle extends SimpleAnimatedParticle {
  protected VoidyParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
    super(level, x, y, z, sprites, 0.0f);
    this.xd = vx + (this.random.nextDouble() * 0.02 - 0.01);
    this.yd = vy + (this.random.nextDouble() * 0.02 - 0.01);
    this.zd = vz + (this.random.nextDouble() * 0.02 - 0.01);
    this.quadSize *= 0.6f; 
    this.lifetime = 15 + this.random.nextInt(10);
    this.hasPhysics = false;
    this.friction = 0.9f;

    this.setSpriteFromAge(sprites);

    this.roll = this.random.nextFloat() * ((float)Math.PI * 2f);
    this.oRoll = this.roll;
  }

  public static class Factory implements ParticleProvider<SimpleParticleType> {
    private final SpriteSet sprites;

    public Factory(SpriteSet sprites) {
      this.sprites = sprites;
    }

    @Override
    public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz, RandomSource random) {
      return new VoidyParticle(level, x, y, z, vx, vy, vz, this.sprites);
    }
  }
}
