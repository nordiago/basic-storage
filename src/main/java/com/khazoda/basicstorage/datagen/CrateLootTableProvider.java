package com.khazoda.basicstorage.datagen;

import com.khazoda.basicstorage.registry.BlockRegistry;
import com.khazoda.basicstorage.registry.DataComponentRegistry;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootTableProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import java.util.concurrent.CompletableFuture;

public class CrateLootTableProvider extends FabricBlockLootTableProvider {

  protected CrateLootTableProvider(FabricDataOutput dataOutput, CompletableFuture<HolderLookup.Provider> registryLookup) {
    super(dataOutput, registryLookup);
  }

  @Override
  public void generate() {
    add(BlockRegistry.CRATE_BLOCK, this::drawerDrops);
    dropSelf(BlockRegistry.CRATE_STATION_BLOCK);
  }

  private LootTable.Builder drawerDrops(Block drop) {
    return LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1.0f)).add(LootItem.lootTableItem(drop).apply(CopyComponentsFunction.copyComponentsFromBlockEntity(LootContextParams.BLOCK_ENTITY).include(DataComponentRegistry.CRATE_CONTENTS))));
  }
}
