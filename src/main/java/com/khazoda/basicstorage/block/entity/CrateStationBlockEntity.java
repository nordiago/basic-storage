package com.khazoda.basicstorage.block.entity;

import com.khazoda.basicstorage.registry.BlockEntityRegistry;
import com.khazoda.basicstorage.storage.CrateNetworkManager;
import com.khazoda.basicstorage.storage.NetworkNode;
import com.khazoda.basicstorage.structure.CrateSlotComponent;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class CrateStationBlockEntity extends BlockEntity implements NetworkNode {

  private final Map<ItemVariant, List<BlockPos>> crateRegistry = new HashMap<>();
  private final Set<BlockPos> connectedValidCrates = new HashSet<>();
  private final Set<BlockPos> connectedEmptyCrates = new HashSet<>();
  private boolean needsCacheUpdate = true;
  private boolean hasCheckedRegistration = false;

  public CrateStationBlockEntity(BlockPos pos, BlockState state) {
    super(BlockEntityRegistry.CRATE_STATION_BLOCK_ENTITY, pos, state);
  }

  public static void tick(Level world, BlockPos pos, BlockState state, CrateStationBlockEntity be) {
    if (world instanceof ServerLevel serverLevel) {
      be.checkRegistration(serverLevel);
    }
    if (be.needsCacheUpdate) {
      be.buildCrateCache();
      be.needsCacheUpdate = false;
    }
  }

  private void checkRegistration(ServerLevel level) {
    if (hasCheckedRegistration) return;
    CrateNetworkManager manager = CrateNetworkManager.get(level);
    if (!manager.isRegistered(worldPosition)) {
      manager.onBlockAdded(level, worldPosition, false, true);
    }
    hasCheckedRegistration = true;
  }

  private void buildCrateCache() {
    if (level == null || level.isClientSide() || !(level instanceof ServerLevel serverLevel)) return;

    crateRegistry.clear();
    connectedValidCrates.clear();
    connectedEmptyCrates.clear();

    CrateNetworkManager manager = CrateNetworkManager.get(serverLevel);
    CrateNetworkManager.CrateNetwork network = manager.getNetworkFor(worldPosition);
    if (network == null) return;

    for (BlockPos cratePos : network.crates) {
      CrateSlotComponent contents = manager.getStorage(cratePos);
      if (contents == null || contents.item().isBlank()) {
        connectedEmptyCrates.add(cratePos);
      } else {
        connectedValidCrates.add(cratePos);
        ItemVariant variant = contents.item();
        crateRegistry.computeIfAbsent(variant, k -> new ArrayList<>()).add(cratePos);
      }
    }
    setChanged();
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
