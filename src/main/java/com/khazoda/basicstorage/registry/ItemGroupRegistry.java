package com.khazoda.basicstorage.registry;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public class ItemGroupRegistry {

  public static CreativeModeTab createItemGroup() {
    return FabricItemGroup.builder().icon(() -> new ItemStack(BlockRegistry.CRATE_BLOCK)).title(Component.translatable("basicstorage.itemGroup")).displayItems((displayContext, entries) -> {
      entries.accept(new ItemStack(BlockRegistry.CRATE_BLOCK));
      entries.accept(new ItemStack(BlockRegistry.CRATE_STATION_BLOCK));
    }).build();
  }
}
