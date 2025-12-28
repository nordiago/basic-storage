package com.khazoda.basicstorage.registry;

import com.khazoda.basicstorage.Constants;
import com.khazoda.basicstorage.structure.CrateSlotComponent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

public class DataComponentRegistry {

  public static final DataComponentType<CrateSlotComponent> CRATE_CONTENTS = DataComponentType.<CrateSlotComponent>builder()
      .persistent(CrateSlotComponent.CODEC)
      .networkSynchronized(CrateSlotComponent.PACKET_CODEC)
      .cacheEncoding()
      .build();

  public static void init() {
    Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(Constants.NAMESPACE, "crate_contents"), CRATE_CONTENTS);
  }
}
