package com.khazoda.basic_storage.block.entity;

import com.khazoda.basic_storage.registry.BlockEntityRegistry;
import com.khazoda.basic_storage.registry.BlockRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;

public class CrateBlockEntity extends BlockEntity {

  public CrateBlockEntity(BlockPos pos, BlockState state) {
    super(BlockEntityRegistry.CRATE_BLOCK_ENTITY, pos, state);
  }


}
