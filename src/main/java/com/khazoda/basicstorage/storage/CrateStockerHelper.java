package com.khazoda.basicstorage.storage;

import com.khazoda.basicstorage.block.entity.CrateStockerBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class CrateStockerHelper {

  // Search for stockers within MAX_RADIUS blocks and force them to re-cache crates
  public static void notifyNearbyStockers(World world, BlockPos pos) {
    int scanRadius = CrateStockerBlockEntity.MAX_RADIUS;
    BlockPos.iterate(
        pos.add(-scanRadius, -scanRadius, -scanRadius),
        pos.add(scanRadius, scanRadius, scanRadius)).forEach(checkPos -> {
      BlockEntity be = world.getBlockEntity(checkPos);
      if (be instanceof CrateStockerBlockEntity stocker) {
        stocker.markCacheForUpdate();
      }
    });
  }
}
