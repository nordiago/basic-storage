package com.khazoda.basicstorage.registry;

import com.khazoda.basicstorage.BasicStorage;
import com.khazoda.basicstorage.block.CrateBlock;


public class EventRegistry {
  public static void init() {
    CrateBlock.initOnUseMethod();
    BasicStorage.loadedRegistries += 1;
  }
}
