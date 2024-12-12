package com.khazoda.basicstorage.registry;

import com.khazoda.basicstorage.BasicStorage;
import com.khazoda.basicstorage.block.CrateBlock;
import com.khazoda.basicstorage.block.CrateDistributorBlock;


public class EventRegistry {
  public static void init() {
    CrateBlock.initOnUseMethod();
    CrateDistributorBlock.initOnUseMethod();

    BasicStorage.loadedRegistries += 1;
  }
}
