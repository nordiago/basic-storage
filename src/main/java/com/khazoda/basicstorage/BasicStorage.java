package com.khazoda.basicstorage;

import com.khazoda.basicstorage.registry.*;
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
    SoundRegistry.init();
    EventRegistry.init();
    DataComponentRegistry.init();

    ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register(content -> {
      content.addAfter(Items.BARREL, BlockRegistry.CRATE_BLOCK);
    });
    ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(content -> {
      content.addAfter(Items.BARREL, BlockRegistry.CRATE_BLOCK);
    });

    BS_LOG.info("[Basic Storage] {}/5 Crates filled!", loadedRegistries);
  }
}