package com.khazoda.basic_storage.registry;

import com.khazoda.basic_storage.BasicStorage;
import com.khazoda.basic_storage.Constants;
import com.khazoda.basic_storage.block.entity.CrateBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class BlockEntityRegistry {


  public static final BlockEntityType<CrateBlockEntity> CRATE_BLOCK_ENTITY = Registry.register(
      Registries.BLOCK_ENTITY_TYPE, Identifier.of(Constants.BS_NAMESPACE, "crate_block_entity"),
      BlockEntityType.Builder.create(CrateBlockEntity::new,
          BlockRegistry.CRATE_BLOCK).build());

  public static void init() {
    BasicStorage.loadedRegistries += 1;
  }
}
