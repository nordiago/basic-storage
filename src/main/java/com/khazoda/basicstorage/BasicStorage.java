package com.khazoda.basicstorage;

import com.khazoda.basicstorage.registry.BlockEntityRegistry;
import com.khazoda.basicstorage.registry.BlockRegistry;
import com.khazoda.basicstorage.registry.DataComponentRegistry;
import com.khazoda.basicstorage.registry.EventRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;

import static com.khazoda.basicstorage.Constants.BS_LOG;

public class BasicStorage implements ModInitializer {

  public static int loadedRegistries = 0;

  @Override
  public void onInitialize() {
    BS_LOG.info("[Basic Storage] Filling crates...");

    BlockRegistry.init();
    BlockEntityRegistry.init();
    EventRegistry.init();
    DataComponentRegistry.init();

    ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register(content -> {
      content.addAfter(Items.BARREL, BlockRegistry.CRATE_BLOCK);
    });

    BS_LOG.info("[Basic Storage] {}/4 Crates filled!", loadedRegistries);
  }
}