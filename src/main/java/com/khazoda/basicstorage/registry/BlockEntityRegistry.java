package com.khazoda.basicstorage.registry;

import com.khazoda.basicstorage.Constants;
import com.khazoda.basicstorage.block.entity.CrateBlockEntity;
import com.khazoda.basicstorage.block.entity.CrateStationBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class BlockEntityRegistry {

  public static final BlockEntityType<CrateBlockEntity> CRATE_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath(Constants.NAMESPACE, "crate_block_entity"), FabricBlockEntityTypeBuilder.create(CrateBlockEntity::new, BlockRegistry.CRATE_BLOCK).build());
  public static final BlockEntityType<CrateStationBlockEntity> CRATE_STATION_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath(Constants.NAMESPACE, "crate_station_block_entity"), FabricBlockEntityTypeBuilder.create(CrateStationBlockEntity::new, BlockRegistry.CRATE_STATION_BLOCK).build());


  public static void init() {
    /* Lets crates work with hoppers and other item transfer */
    ItemStorage.SIDED.registerForBlockEntity((blockEntity, direction) -> blockEntity.storage, CRATE_BLOCK_ENTITY);
    ItemStorage.SIDED.registerForBlockEntity(InventoryStorage::of, BlockEntityRegistry.CRATE_STATION_BLOCK_ENTITY);
  }
}
