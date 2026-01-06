package com.khazoda.basicstorage.registry;

import com.khazoda.basicstorage.Constants;
import com.khazoda.basicstorage.advancement.StationTransformedCriterion;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public class CriterionRegistry {

  public static final StationTransformedCriterion STATION_TRANSFORMED = Registry.register(BuiltInRegistries.TRIGGER_TYPES, Constants.ID("station_transformed"), new StationTransformedCriterion());

  public static void init() {
  }
}
