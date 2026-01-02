package com.khazoda.basicstorage.storage;

import com.khazoda.basicstorage.block.entity.CrateStationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;

public class CrateStationHelper {

  // Search for stations within MAX_RADIUS blocks and force them to re-cache crates
  public static void notifyNearbyStations(Level world, BlockPos pos) {
    int scanRadius = CrateStationBlockEntity.MAX_RADIUS;
    BlockPos.betweenClosed(
        pos.offset(-scanRadius, -scanRadius, -scanRadius),
        pos.offset(scanRadius, scanRadius, scanRadius)).forEach(checkPos -> {
      BlockEntity be = world.getBlockEntity(checkPos);
      if (be instanceof CrateStationBlockEntity station) {
        station.markCacheForUpdate();
      }
    });
  }
}
