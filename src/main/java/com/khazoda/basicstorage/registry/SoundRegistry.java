package com.khazoda.basicstorage.registry;

import com.khazoda.basicstorage.BasicStorage;
import com.khazoda.basicstorage.Constants;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class SoundRegistry {
  public static final SoundEvent INSERT_ONE = register("insert_one");
  public static final SoundEvent INSERT_MANY = register("insert_many");


  public static void init() {

    BasicStorage.loadedRegistries += 1;
  }

  private static SoundEvent register(String name) {
    return Registry.register(Registries.SOUND_EVENT, name, SoundEvent.of(Identifier.of(Constants.BS_NAMESPACE, name)));
  }
}
