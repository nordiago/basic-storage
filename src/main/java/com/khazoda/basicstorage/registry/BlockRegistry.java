package com.khazoda.basicstorage.registry;

import com.khazoda.basicstorage.block.CrateBlock;
import com.khazoda.basicstorage.block.CrateConnectorBlock;
import com.khazoda.basicstorage.block.CrateStationBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.Function;

import static com.khazoda.basicstorage.Constants.ID;

public class BlockRegistry {

  public static final Item.Properties crateItemSettings = new Item.Properties().stacksTo(64).fireResistant();

  public static final Block CRATE_BLOCK = register("crate", CrateBlock::new, CrateBlock.defaultSettings);
  public static final Block CRATE_STATION_BLOCK = register("crate_station", CrateStationBlock::new, CrateStationBlock.defaultSettings);
  public static final Block CRATE_CONNECTOR_BLOCK = register("crate_connector", CrateConnectorBlock::new, CrateConnectorBlock.defaultSettings);

  public static void init() {
  }

  private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties blockSettings) {

    // Block form
    ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, ID(name));
    Block block = factory.apply(blockSettings.setId(blockKey));
    Registry.register(BuiltInRegistries.BLOCK, blockKey, block);

    // Item form
    ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, ID(name));
    BlockItem item = new BlockItem(block, crateItemSettings.useBlockDescriptionPrefix().setId(itemKey));
    item.registerBlocks(Item.BY_BLOCK, item);
    Registry.register(BuiltInRegistries.ITEM, itemKey, item);
    return block;
  }
}
