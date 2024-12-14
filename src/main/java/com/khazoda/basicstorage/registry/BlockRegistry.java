package com.khazoda.basicstorage.registry;

import com.khazoda.basicstorage.BasicStorage;
import com.khazoda.basicstorage.block.CrateBlock;
import com.khazoda.basicstorage.block.CrateStockerBlock;
import com.khazoda.basicstorage.util.Reggie;
import net.minecraft.block.Block;
import net.minecraft.item.Item;


public class BlockRegistry {
  public static final Item.Settings crateItemSettings = new Item.Settings().maxCount(64).fireproof();

  public static final Block CRATE_BLOCK = Reggie.register("crate", new CrateBlock(), crateItemSettings);
  public static final Block CRATE_STOCKER_BLOCK = Reggie.register("crate_stocker", new CrateStockerBlock(), crateItemSettings);


  public static void init() {
    BasicStorage.loadedRegistries += 1;
  }
}
