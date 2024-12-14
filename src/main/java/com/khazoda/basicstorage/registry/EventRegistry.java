package com.khazoda.basicstorage.registry;

import com.khazoda.basicstorage.BasicStorage;
import com.khazoda.basicstorage.block.CrateBlock;
import com.khazoda.basicstorage.block.CrateStockerBlock;


public class EventRegistry {
  public static void init() {
    CrateBlock.initOnUseMethod();
    CrateStockerBlock.initOnUseMethod();

    BasicStorage.loadedRegistries += 1;
  }
}
