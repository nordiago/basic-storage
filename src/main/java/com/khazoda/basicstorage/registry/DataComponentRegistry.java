package com.khazoda.basicstorage.registry;

import com.khazoda.basicstorage.Constants;
import com.khazoda.basicstorage.structure.CrateSlotComponent;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.function.UnaryOperator;

public class DataComponentRegistry {

  public static final DataComponentType<CrateSlotComponent> CRATE_CONTENTS = register("crate_contents", builder -> builder.persistent(CrateSlotComponent.CODEC).networkSynchronized(CrateSlotComponent.PACKET_CODEC).cacheEncoding());

  public static void init() {
  }

  private static <T> DataComponentType<T> register(String name, UnaryOperator<DataComponentType.Builder<T>> builder) {
    return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(Constants.NAMESPACE, name), builder.apply(DataComponentType.builder()).build());
  }
}
