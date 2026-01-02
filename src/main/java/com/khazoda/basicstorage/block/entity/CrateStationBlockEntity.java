package com.khazoda.basicstorage.block.entity;

import com.khazoda.basicstorage.registry.BlockEntityRegistry;
import com.khazoda.basicstorage.storage.CrateSlot;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class CrateStationBlockEntity extends BlockEntity {

  private final Map<ItemVariant, List<BlockPos>> crateRegistry = new HashMap<>();
  private final Set<BlockPos> connectedValidCrates = new HashSet<>();
  private final Set<BlockPos> connectedEmptyCrates = new HashSet<>();
  public static final int MAX_RADIUS = 16;
  private boolean needsCacheUpdate = true;

  public CrateStationBlockEntity(BlockPos pos, BlockState state) {
    super(BlockEntityRegistry.CRATE_STATION_BLOCK_ENTITY, pos, state);
  }

  public static void tick(Level world, BlockPos pos, BlockState state, CrateStationBlockEntity be) {
    if (be.needsCacheUpdate) {
      be.buildCrateCache();
      be.needsCacheUpdate = false;
    }
  }

  private void buildCrateCache() {
    if (level == null || level.isClientSide()) return;

    crateRegistry.clear();
    connectedValidCrates.clear();
    connectedEmptyCrates.clear();

    Queue<BlockPos> toExplore = new LinkedList<>();
    Set<BlockPos> visited = new HashSet<>();
    toExplore.add(worldPosition);

    while (!toExplore.isEmpty()) {
      BlockPos current = toExplore.poll();
      if (visited.contains(current) || !isWithinRange(current)) continue;

      visited.add(current);
      BlockEntity be = level.getBlockEntity(current);
      if (be instanceof CrateStationBlockEntity) addDirectionsToExplore(toExplore, current);
      if (be instanceof CrateBlockEntity crate) {
        registerCrate(current, crate.storage);
        addDirectionsToExplore(toExplore, current);
      }
    }
    setChanged();
  }

  private void addDirectionsToExplore(Queue<BlockPos> blockPositionExplorationQueue, BlockPos currentBlockPosition) {
    for (Direction dir : Direction.values()) {
      blockPositionExplorationQueue.add(currentBlockPosition.relative(dir));
    }
  }

  private void registerCrate(BlockPos cratePos, CrateSlot storage) {
    if (storage.isBlank()) {
      connectedEmptyCrates.add(cratePos);
    } else {
      connectedValidCrates.add(cratePos);
      ItemVariant variant = storage.getResource();
      crateRegistry.computeIfAbsent(variant, k -> new ArrayList<>()).add(cratePos);
    }
  }

  private boolean isWithinRange(BlockPos target) {
    return Math.abs(target.getX() - worldPosition.getX()) <= MAX_RADIUS && Math.abs(target.getY() - worldPosition.getY()) <= MAX_RADIUS && Math.abs(target.getZ() - worldPosition.getZ()) <= MAX_RADIUS;
  }

  @Override
  public void setRemoved() {
    crateRegistry.clear();
    connectedValidCrates.clear();
    connectedEmptyCrates.clear();
    super.setRemoved();
  }

  public void markCacheForUpdate() {
    this.needsCacheUpdate = true;
    setChanged();
  }

  public Set<BlockPos> getConnectedValidCrates() {
    return connectedValidCrates;
  }

  public Set<BlockPos> getConnectedEmptyCrates() {
    return connectedEmptyCrates;
  }

  public Map<ItemVariant, List<BlockPos>> getCrateRegistry() {
    return crateRegistry;
  }
}
