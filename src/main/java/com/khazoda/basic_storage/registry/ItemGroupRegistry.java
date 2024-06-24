package com.khazoda.basic_storage.registry;

import com.khazoda.basic_storage.BasicStorage;
import com.khazoda.basic_storage.util.RegistryHelper;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

public class ItemGroupRegistry {

  public static final ItemGroup BS_GROUP = register(ItemGroupRegistry.createItemGroup());

  public static ItemGroup createItemGroup() {
    return FabricItemGroup.builder()
        .icon(() -> new ItemStack(BlockRegistry.CRATE_BLOCK))
        .displayName(Text.translatable("itemGroup.basic_storage"))
        .entries((displayContext, entries) -> {
          entries.add(new ItemStack(BlockRegistry.CRATE_BLOCK));
        }).build();
  }

  public static void init() {
    BasicStorage.loadedRegistries += 1;
  }

  /* Register an ItemGroup */
  private static <I extends ItemGroup> I register(I itemGroup) {
    return RegistryHelper.registerItemGroup(itemGroup);
  }
}
