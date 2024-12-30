package com.khazoda.basicstorage.registry;

import com.khazoda.basicstorage.BasicStorage;
import com.khazoda.basicstorage.Constants;
import com.khazoda.basicstorage.item.CrateHammerItem;
import java.util.function.Function;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ItemRegistry {
  public static final Item.Settings unstackableItemSettings = new Item.Settings().maxCount(1);

  public static final Item CRATE_HAMMER_ITEM = register("crate_hammer", CrateHammerItem::new, unstackableItemSettings);

  public static void init() {
    BasicStorage.loadedRegistries += 1;
  }

  private static Item register(String name, Function<Item.Settings, Item> factory, Item.Settings settings) {
    RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(Constants.BS_NAMESPACE, name));
    return Registry.register(Registries.ITEM, key, factory.apply(settings.registryKey(key)));
  }
}
