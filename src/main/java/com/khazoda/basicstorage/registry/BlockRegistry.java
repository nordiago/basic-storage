package com.khazoda.basicstorage.registry;

import com.khazoda.basicstorage.BasicStorage;
import com.khazoda.basicstorage.block.CrateBlock;
import com.khazoda.basicstorage.block.CrateStationBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

import java.util.function.Function;

import static com.khazoda.basicstorage.Constants.ID;


public class BlockRegistry {
  public static final Item.Settings crateItemSettings = new Item.Settings().maxCount(64).fireproof();

  public static final Block CRATE_BLOCK = register(
      "crate", CrateBlock::new, CrateBlock.defaultSettings);
  public static final Block CRATE_STATION_BLOCK = register(
      "crate_station", CrateStationBlock::new, CrateStationBlock.defaultSettings);

  public static void init() {
    BasicStorage.loadedRegistries += 1;
  }

  private static Block register(
      String name, Function<Block.Settings, Block> factory,
      Block.Settings blockSettings) {

    // Block form
    RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, ID(name));
    Block block = factory.apply(blockSettings.registryKey(blockKey));
    Registry.register(Registries.BLOCK, blockKey, block);

    // Item form
    RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, ID(name));
    // Here, useBlockPrefixedTranslationKey() sets "block.namespace.path" translation
    // key format. Without it, the client reverts to using "item.namespace.path" format.
    BlockItem item = new BlockItem(block, crateItemSettings.useBlockPrefixedTranslationKey().registryKey(itemKey));
    item.appendBlocks(Item.BLOCK_ITEMS, item);
    Registry.register(Registries.ITEM, itemKey, item);
    return block;
  }
}
