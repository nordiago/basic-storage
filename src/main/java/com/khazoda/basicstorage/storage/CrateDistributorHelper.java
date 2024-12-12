package com.khazoda.basicstorage.storage;

import com.khazoda.basicstorage.block.entity.CrateDistributorBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class CrateDistributorHelper {

  // Search for distributors within MAX_RADIUS blocks and force them to re-cache crates
  public static void notifyNearbyDistributors(World world, BlockPos pos) {
    int scanRadius = CrateDistributorBlockEntity.MAX_RADIUS;
    BlockPos.iterate(
        pos.add(-scanRadius, -scanRadius, -scanRadius),
        pos.add(scanRadius, scanRadius, scanRadius)).forEach(checkPos -> {
      BlockEntity be = world.getBlockEntity(checkPos);
      if (be instanceof CrateDistributorBlockEntity distributor) {
        distributor.markCacheForUpdate();
      }
    });
  }
}
