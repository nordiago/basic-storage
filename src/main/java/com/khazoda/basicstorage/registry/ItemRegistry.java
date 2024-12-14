package com.khazoda.basicstorage.registry;

import com.khazoda.basicstorage.BasicStorage;
import com.khazoda.basicstorage.item.CrateCollectorItem;
import com.khazoda.basicstorage.util.Reggie;
import net.minecraft.item.Item;

public class ItemRegistry {
  public static final Item.Settings unstackableItemSettings = new Item.Settings().maxCount(1);

  public static final Item CRATE_COLLECTOR_ITEM = Reggie.register("crate_collector", new CrateCollectorItem(unstackableItemSettings));

  public static void init() {
    BasicStorage.loadedRegistries += 1;
  }
}
