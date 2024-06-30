package com.khazoda.basic_storage.registry;

import com.khazoda.basic_storage.BasicStorage;
import com.khazoda.basic_storage.Constants;
import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;

public class DataComponentRegistry {

  public static final ComponentType<Integer> CRATE_STACK_SIZE = ComponentType.<Integer>builder()
      .codec(Codecs.rangedInt(1, Constants.CRATE_MAX_COUNT))
      .packetCodec(PacketCodecs.VAR_INT)
      .build();

  public static void init() {
    Registry.register(Registries.DATA_COMPONENT_TYPE, Identifier.of(Constants.BS_NAMESPACE, "crate_stack_size"), CRATE_STACK_SIZE);

    BasicStorage.loadedRegistries += 1;
  }
}
