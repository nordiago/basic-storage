package com.khazoda.basicstorage.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public class WhooshSoundInstance extends AbstractTickableSoundInstance {
    private final Vec3[] path;
    private final int duration;
    private final int startDelay;
    private int age = 0;

    public WhooshSoundInstance(SoundEvent soundEvent, Vec3[] path, int duration, int startDelay, RandomSource random) {
        super(soundEvent, SoundSource.BLOCKS, random);
        this.path = path;
        this.duration = duration;
        this.startDelay = startDelay;
        this.looping = false;
        this.delay = 0;
        this.volume = startDelay > 0 ? 0.0f : 0.5f; // Mute until delay is over
        this.pitch = 0.9f + random.nextFloat() * 0.2f;
        
        if (path.length > 0) {
            this.x = path[0].x;
            this.y = path[0].y;
            this.z = path[0].z;
        }
    }

    @Override
    public void tick() {
        if (this.age < this.startDelay) {
            this.age++;
            return;
        }
        
        if (this.age == this.startDelay) {
            this.volume = 0.5f;
        }

        int adjustedAge = this.age - this.startDelay;

        if (adjustedAge < this.duration) {
            double progress = (double) adjustedAge / this.duration;
            int step = (int) (this.path.length * progress);
            if (step < this.path.length) {
                Vec3 pos = this.path[step];
                this.x = pos.x;
                this.y = pos.y;
                this.z = pos.z;
            }
        } else if (adjustedAge >= 10) {
            this.stop();
            return;
        }

        this.age++;
    }
}
