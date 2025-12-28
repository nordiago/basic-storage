package com.khazoda.basicstorage.registry;

import com.khazoda.basicstorage.Constants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public class SoundRegistry {

  public static final SoundEvent EXTRACT_ONE = register("extract_one");
  public static final SoundEvent EXTRACT_MANY = register("extract_many");
  public static final SoundEvent INSERT_ONE = register("insert_one");
  public static final SoundEvent INSERT_MANY = register("insert_many");
  public static final SoundEvent INSERT_LOADS = register("insert_loads");
  public static final SoundEvent NO_MATCH = register("no_match");

  public static void init() {
  }

  private static SoundEvent register(String name) {
    return Registry.register(BuiltInRegistries.SOUND_EVENT, name, SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(Constants.NAMESPACE, name)));
  }
}
