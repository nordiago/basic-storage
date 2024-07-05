package com.khazoda.basic_storage.registry;

import com.khazoda.basic_storage.BasicStorage;
import com.khazoda.basic_storage.Constants;
import com.khazoda.basic_storage.structure.CrateContentsComponent;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;

public class DataComponentRegistry {

  public static final ComponentType<Integer> CRATE_MAX_STACK_SIZE = ComponentType.<Integer>builder()
      .codec(Codecs.rangedInt(1, Constants.CRATE_MAX_COUNT))
      .packetCodec(PacketCodecs.VAR_INT)
      .build();

  public static final ComponentType<CrateContentsComponent> CRATE_CONTENTS = ComponentType.<CrateContentsComponent>builder()
      .codec(CrateContentsComponent.CODEC)
      .packetCodec(CrateContentsComponent.PACKET_CODEC)
      .cache()
      .build();

  public static void init() {
    Registry.register(Registries.DATA_COMPONENT_TYPE, Identifier.of(Constants.BS_NAMESPACE, "crate_max_stack_size"), CRATE_MAX_STACK_SIZE);
    Registry.register(Registries.DATA_COMPONENT_TYPE, Identifier.of(Constants.BS_NAMESPACE, "crate_contents"), CRATE_CONTENTS);
    BasicStorage.loadedRegistries += 1;
  }
}
