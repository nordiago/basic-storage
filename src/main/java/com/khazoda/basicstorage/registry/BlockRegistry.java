package com.khazoda.basicstorage.registry;

import com.khazoda.basicstorage.BasicStorage;
import com.khazoda.basicstorage.block.CrateBlock;
import com.khazoda.basicstorage.block.CrateDistributorBlock;
import com.khazoda.basicstorage.util.RegistryHelper;
import net.minecraft.block.Block;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.entry.RegistryEntry;

public class BlockRegistry {
  public static final Item.Settings crateItemSettings = new Item.Settings().maxCount(64).fireproof();

  public static final Block CRATE_BLOCK = register("crate", new CrateBlock(), crateItemSettings);
  public static final Block CRATE_DISTRIBUTOR_BLOCK = register("crate_distributor", new CrateDistributorBlock(), crateItemSettings);


  public static void init() {

    BasicStorage.loadedRegistries += 1;
  }

  /* Register block and item with default item settings */
  private static <B extends Block> B register(String name, B block, Item.Settings itemSettings) {
    return RegistryHelper.registerBlock(name, block, itemSettings);
  }

  /* Register block *with* corresponding item*/
  private static <I extends BlockItem> BlockItem register(String name, I blockItem) {
    return RegistryHelper.registerBlockItem(name, blockItem);
  }

  /* Register block *without* corresponding item */
  private static <B extends Block> B register(String name, B block) {
    return RegistryHelper.registerBlockOnly(name, block);
  }

  /* Register item */
  private static Item register(String name) {
    return RegistryHelper.registerItem(name, new Item(new Item.Settings().maxCount(64)));
  }

  /* Register armour material */
  private static RegistryEntry<ArmorMaterial> register(String name, ArmorMaterial material) {
    return RegistryHelper.registerArmorMaterial(name, material);
  }
}
