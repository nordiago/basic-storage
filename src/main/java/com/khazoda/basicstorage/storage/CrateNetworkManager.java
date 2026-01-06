package com.khazoda.basicstorage.storage;

import com.khazoda.basicstorage.Constants;
import com.khazoda.basicstorage.block.entity.CrateBlockEntity;
import com.khazoda.basicstorage.registry.BlockRegistry;
import com.khazoda.basicstorage.structure.CrateSlotComponent;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CrateNetworkManager {
  private static final Map<ServerLevel, CrateNetworkManager> INSTANCE_CACHE = new ConcurrentHashMap<>();

  private final Map<BlockPos, UUID> blockToNetwork = new ConcurrentHashMap<>();
  private final Map<UUID, CrateNetwork> networks = new ConcurrentHashMap<>();
  private final Map<BlockPos, CrateSlotComponent> globalStorage = new ConcurrentHashMap<>();
  private final Set<UUID> pendingStationUpdates = Collections.newSetFromMap(new ConcurrentHashMap<>());

  private final NetworkFileManager fileManager;
  private final AsyncNetworkRebuilder asyncRebuilder;
  private final Set<UUID> dirtyNetworks = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final Set<UUID> networksToDelete = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final Set<UUID> networksUndergoingRebuild = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final ServerLevel serverLevel; // Each dimension gets its own manager
  private boolean loaded = false;
  private long lastSaveTick = 0;
  private final long saveInterval; // Randomized interval for each manager to spread out save tasks

  private CrateNetworkManager(ServerLevel level) {
    this.serverLevel = level;
    this.fileManager = new NetworkFileManager(level);
    this.asyncRebuilder = new AsyncNetworkRebuilder();
    this.saveInterval = 4800 + new Random().nextInt(2400); // 4-6 minutes
  }

  public static CrateNetworkManager get(ServerLevel level) {
    return INSTANCE_CACHE.computeIfAbsent(level, l -> {
      CrateNetworkManager manager = new CrateNetworkManager(l);
      manager.loadFromDisk();
      return manager;
    });
  }

  public static void clearCache(ServerLevel level) {
    CrateNetworkManager manager = INSTANCE_CACHE.remove(level);
    if (manager != null) {
      manager.shutdown();
    }
  }

  private void loadFromDisk() {
    if (loaded) return;

    NetworkFileManager.LoadResult result = fileManager.loadAllNetworks();

    for (Map.Entry<UUID, CrateNetwork> entry : result.networks().entrySet()) {
      UUID networkId = entry.getKey();
      CrateNetwork network = entry.getValue();

      networks.put(networkId, network);

      for (Set<BlockPos> positions : network.nodes.values()) {
        for (BlockPos pos : positions) {
          blockToNetwork.put(pos, networkId);
        }
      }
    }

    globalStorage.putAll(result.globalStorage());

    loaded = true;
    Constants.LOG.info("Loaded {} networks from disk", networks.size());
  }

  /**
   * Save all dirty networks to disk.
   */
  public void save(ServerLevel level) {
    Set<UUID> toSave = new HashSet<>(dirtyNetworks);
    Set<UUID> toDelete = new HashSet<>(networksToDelete);
    dirtyNetworks.clear();
    networksToDelete.clear();

    if (toSave.isEmpty() && toDelete.isEmpty()) return;

    int saved = 0;
    int deleted = 0;

    for (UUID networkId : toSave) {
      CrateNetwork network = networks.get(networkId);
      if (network != null && !network.isEmpty()) {
        fileManager.saveNetwork(networkId, network, this);
        saved++;
      }
    }

    for (UUID networkId : toDelete) {
      fileManager.deleteNetwork(networkId);
      deleted++;
    }

    if (saved > 0 || deleted > 0) {
      Constants.LOG.debug("Saved {} networks, deleted {} networks", saved, deleted);
    }
  }

  /**
   * Shutdown async executor and save any pending changes.
   */
  public void shutdown() {
    asyncRebuilder.shutdown();
  }

  /**
   * Check if a network is currently undergoing async rebuild.
   */
  public boolean isNetworkLocked(UUID networkId) {
    return networksUndergoingRebuild.contains(networkId);
  }

  public boolean isRegistered(BlockPos pos) {
    UUID networkId = blockToNetwork.get(pos);
    if (networkId == null) return false;
    CrateNetwork network = networks.get(networkId);
    return network != null && network.contains(pos);
  }

  public CrateNetwork getNetworkFor(BlockPos pos) {
    UUID id = blockToNetwork.get(pos);
    return id != null ? networks.get(id) : null;
  }

  private String getType(BlockState state) {
    if (state.is(BlockRegistry.CRATE_BLOCK)) return "crate";
    if (state.is(BlockRegistry.CRATE_STATION_BLOCK)) return "station";
    if (state.is(BlockRegistry.CRATE_CONNECTOR_BLOCK)) return "connector";
    return null;
  }

  public void onBlockAdded(Level level, BlockPos pos, BlockState state) {
    if (level.isClientSide()) return;
    if (level != this.serverLevel) return; // Dimension guard: only manage blocks in our dimension
    String type = getType(state);
    if (type == null) return;

    UUID currentId = blockToNetwork.get(pos);
    if (currentId != null) {
      CrateNetwork network = networks.get(currentId);
      if (network != null && network.contains(pos)) return;
      blockToNetwork.remove(pos);
      if (network != null) network.nodes.values().forEach(set -> set.remove(pos));
    }

    Set<UUID> adjacentNetworks = new HashSet<>();
    for (Direction dir : Direction.values()) {
      UUID neighborId = blockToNetwork.get(pos.relative(dir));
      /* Don't merge with networks currently rebuilding to prevent corruption */
      if (neighborId != null && networks.containsKey(neighborId) && !networksUndergoingRebuild.contains(neighborId)) {
        adjacentNetworks.add(neighborId);
      }
    }

    UUID networkId;
    if (adjacentNetworks.isEmpty()) {
      networkId = UUID.randomUUID();
      networks.put(networkId, new CrateNetwork(networkId));
    } else {
      Iterator<UUID> it = adjacentNetworks.iterator();
      networkId = it.next();
      while (it.hasNext()) {
        mergeNetworks(networkId, it.next());
      }
    }

    CrateNetwork network = networks.computeIfAbsent(networkId, CrateNetwork::new);
    blockToNetwork.put(pos, networkId);
    network.nodes.computeIfAbsent(type, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(pos);
    network.invalidateIndex();

    dirtyNetworks.add(networkId);
    notifyStations(level, networkId);
  }

  public void onBlockRemoved(Level level, BlockPos pos) {
    if (level.isClientSide()) return;
    if (level != this.serverLevel) return; // Dimension guard
    UUID networkId = blockToNetwork.remove(pos);
    if (networkId == null) return;

    CrateNetwork network = networks.get(networkId);
    if (network != null) {
      network.nodes.values().forEach(set -> set.remove(pos));
      network.invalidateIndex();
    }
    globalStorage.remove(pos);

    if (network != null && network.isEmpty()) {
      networks.remove(networkId);
      networksToDelete.add(networkId);
    } else if (network != null) {
      if (networksUndergoingRebuild.contains(networkId)) return;
      dirtyNetworks.add(networkId);

      if (shouldCheckForSplit(level, pos, network) && level instanceof ServerLevel serverLevel) {
        networksUndergoingRebuild.add(networkId);
        asyncRebuilder.submitRebuild(serverLevel, networkId, network, pos, result -> {
          applyRebuildResults(serverLevel, result);
        });
      }
    }
  }

  private boolean shouldCheckForSplit(Level level, BlockPos pos, CrateNetwork network) {
    int neighbors = 0;
    List<BlockPos> adjacent = new ArrayList<>();
    for (Direction dir : Direction.values()) {
      BlockPos neighbor = pos.relative(dir);
      UUID neighborId = blockToNetwork.get(neighbor);
      if (neighborId != null && neighborId.equals(network.id)) {
        neighbors++;
        adjacent.add(neighbor);
      }
    }

    // No split possible if 1 or less
    return neighbors > 1;
  }

  private void mergeNetworks(UUID targetId, UUID sourceId) {
    if (targetId.equals(sourceId)) return;
    CrateNetwork target = networks.computeIfAbsent(targetId, CrateNetwork::new);
    CrateNetwork source = networks.remove(sourceId);
    if (source == null) return;
    target.invalidateIndex();
    source.nodes.forEach((type, posSet) -> {
      for (BlockPos pos : posSet) {
        blockToNetwork.put(pos, targetId);
        target.nodes.computeIfAbsent(type, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(pos);
      }
    });
  }

  /**
   * Apply rebuild results from async thread on the main thread.
   */
  private void applyRebuildResults(ServerLevel level, AsyncNetworkRebuilder.RebuildResult result) {
    UUID oldNetworkId = result.oldNetworkId();

    networks.remove(oldNetworkId);
    networksToDelete.add(oldNetworkId);

    for (Map<String, Set<BlockPos>> networkNodes : result.newNetworkNodes()) {
      UUID newId = UUID.randomUUID();
      CrateNetwork newNetwork = new CrateNetwork(newId);

      for (Map.Entry<String, Set<BlockPos>> entry : networkNodes.entrySet()) {
        Set<BlockPos> posSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
        posSet.addAll(entry.getValue());
        newNetwork.nodes.put(entry.getKey(), posSet);

        for (BlockPos pos : entry.getValue()) {
          blockToNetwork.put(pos, newId);
        }
      }

      networks.put(newId, newNetwork);
      dirtyNetworks.add(newId);
      notifyStations(level, newId);
    }

    networksUndergoingRebuild.remove(oldNetworkId); // Unlock Network
  }

  public void updateStorage(Level level, BlockPos pos, CrateSlotComponent component) {
    if (level.isClientSide()) return;
    if (level != this.serverLevel) return; // Dimension guard

    CrateSlotComponent old = globalStorage.put(pos, component);

    UUID networkId = blockToNetwork.get(pos);
    if (networkId != null) {
      CrateNetwork network = networks.get(networkId);
      if (network != null) {

        // Skip index update and notification if only count changed
        if (old != null && old.item().equals(component.item())) {
          return;
        }

        network.updateItemIncremental(old != null ? old.item() : null, component.item(), pos);
        dirtyNetworks.add(networkId);
        notifyStations(level, networkId);
      }
    }
  }

  public CrateSlotComponent getStorage(BlockPos pos) {
    return globalStorage.getOrDefault(pos, CrateSlotComponent.DEFAULT);
  }

  private void notifyStations(Level level, UUID networkId) {
    pendingStationUpdates.add(networkId);
  }

  public void tick(Level level) {
    if (pendingStationUpdates.isEmpty() && dirtyNetworks.isEmpty()) return;

    /* Notify stations about network changes */
    if (!pendingStationUpdates.isEmpty()) {
      for (UUID networkId : pendingStationUpdates) {
        CrateNetwork network = networks.get(networkId);
        if (network == null) continue;
        Set<BlockPos> stations = network.nodes.get("station");
        if (stations == null) continue;

        for (BlockPos stationPos : stations) {
          if (level.isLoaded(stationPos)) {
            var be = level.getBlockEntity(stationPos);
            if (be instanceof NetworkNode node) {
              node.markCacheForUpdate();
            }
          }
        }
      }
      pendingStationUpdates.clear();
    }

    /* Periodic autosave with per-manager randomized interval */
    if (level instanceof ServerLevel serverLevel && (!dirtyNetworks.isEmpty() || !networksToDelete.isEmpty())) {
      long currentTick = serverLevel.getGameTime();
      if (currentTick - lastSaveTick >= saveInterval) {
        save(serverLevel);
        lastSaveTick = currentTick;
      }
    }
  }

  /**
   * Find crates in the network containing the given variant.
   * Results are sorted by distance to station if under 64 matches.
   *
   * @param stationPos Position of the requesting station
   * @param variant    Item variant to search for
   * @return List of crate positions, empty if none found
   */
  public List<BlockPos> findCratesForItem(BlockPos stationPos, ItemVariant variant) {
    CrateNetwork network = getNetworkFor(stationPos);
    if (network == null || networksUndergoingRebuild.contains(network.id)) {
      return List.of(); // Return empty if network deleted or rebuilding
    }
    return network.findCratesForItem(variant, stationPos, this);
  }


  /**
   * Verifies network integrity and self-heals orphaned blocks.
   * <p>
   * Called via command "/basicstorage networks heal"
   *
   * @return Number of blocks that were healed
   */
  public int verifyIntegrity(ServerLevel level) {
    // Find orphaned blocks (in blockToNetwork but network doesn't exist)
    Set<BlockPos> orphans = new HashSet<>();
    for (Map.Entry<BlockPos, UUID> entry : blockToNetwork.entrySet()) {
      if (!networks.containsKey(entry.getValue())) {
        orphans.add(entry.getKey());
      }
    }

    // Re-register orphaned blocks
    for (BlockPos pos : orphans) {
      blockToNetwork.remove(pos);
      if (level.isLoaded(pos)) {
        BlockState state = level.getBlockState(pos);
        if (getType(state) != null) {
          onBlockAdded(level, pos, state);
        }
      }
    }

    // Clean up empty networks
    Set<UUID> emptyNetworks = new HashSet<>();
    for (Map.Entry<UUID, CrateNetwork> entry : networks.entrySet()) {
      if (entry.getValue().isEmpty()) {
        emptyNetworks.add(entry.getKey());
      }
    }
    emptyNetworks.forEach(networks::remove);

    int healed = orphans.size() + emptyNetworks.size();
    if (healed > 0) {
      Constants.LOG.warn("Healed {} network issues ({} orphaned blocks, {} empty networks)", healed, orphans.size(), emptyNetworks.size());
      orphans.forEach(pos -> {
        UUID networkId = blockToNetwork.get(pos);
        if (networkId != null) dirtyNetworks.add(networkId);
      });
      dirtyNetworks.addAll(emptyNetworks);
    }

    return healed;
  }

  /**
   * Recalculates networks in the given area by removing and re-adding all blocks.
   * This fixes orphaned blocks and rebuilds network topology correctly.
   * <p>
   * Removes all blocks first, then adds them back, to prevent fragmentation.
   */
  public int recalculateArea(ServerLevel level, BlockPos pos1, BlockPos pos2) {
    int x1 = Math.min(pos1.getX(), pos2.getX());
    int y1 = Math.min(pos1.getY(), pos2.getY());
    int z1 = Math.min(pos1.getZ(), pos2.getZ());
    int x2 = Math.max(pos1.getX(), pos2.getX());
    int y2 = Math.max(pos1.getY(), pos2.getY());
    int z2 = Math.max(pos1.getZ(), pos2.getZ());

    List<BlockPos> crateBlocks = new ArrayList<>();
    Map<BlockPos, BlockState> blockStates = new HashMap<>();

    for (int x = x1; x <= x2; x++) {
      for (int y = y1; y <= y2; y++) {
        for (int z = z1; z <= z2; z++) {
          BlockPos p = new BlockPos(x, y, z);
          if (level.isLoaded(p)) {
            BlockState state = level.getBlockState(p);
            if (getType(state) != null) {
              crateBlocks.add(p);
              blockStates.put(p, state);
            }
          }
        }
      }
    }

    if (crateBlocks.isEmpty()) {
      return 0;
    }

    Set<UUID> affectedNetworks = new HashSet<>();
    for (BlockPos p : crateBlocks) {
      UUID networkId = blockToNetwork.get(p);
      if (networkId != null) {
        affectedNetworks.add(networkId);
      }
      blockToNetwork.remove(p);
      globalStorage.remove(p);
    }

    // Clean up affected network node sets
    for (UUID networkId : affectedNetworks) {
      CrateNetwork network = networks.get(networkId);
      if (network != null) {
        for (BlockPos p : crateBlocks) {
          network.nodes.values().forEach(set -> set.remove(p));
        }
        // Remove network if now empty
        if (network.isEmpty()) {
          networks.remove(networkId);
          networksToDelete.add(networkId);
        } else {
          network.invalidateIndex();
          dirtyNetworks.add(networkId);
        }
      }
    }

    // Re-add all blocks and automatically merge them into correct networks
    for (BlockPos p : crateBlocks) {
      BlockState state = blockStates.get(p);
      onBlockAdded(level, p, state);

      // Refresh storage info if block is a crate
      var be = level.getBlockEntity(p);
      if (be instanceof CrateBlockEntity crate) {
        updateStorage(level, p, crate.storage.toComponent());
      }
    }

    Constants.LOG.info("Recalculated {} blocks in area", crateBlocks.size());
    return crateBlocks.size();
  }

  /**
   * Purge networks that have no valid blocks in the loaded world.
   * This cleans up orphaned network files from old data or bugs.
   */
  public int purgeOrphanedNetworks(ServerLevel level) {
    List<UUID> toPurge = new ArrayList<>();

    for (Map.Entry<UUID, CrateNetwork> entry : networks.entrySet()) {
      UUID networkId = entry.getKey();
      CrateNetwork network = entry.getValue();

      // Check if any block in this network actually exists in the world
      boolean hasValidBlock = false;
      for (Set<BlockPos> positions : network.nodes.values()) {
        for (BlockPos pos : positions) {
          if (level.isLoaded(pos)) {
            BlockState state = level.getBlockState(pos);
            if (getType(state) != null) {
              hasValidBlock = true;
              break;
            }
          }
        }
        if (hasValidBlock) break;
      }

      if (!hasValidBlock) {
        toPurge.add(networkId);
      }
    }

    // Remove orphaned networks
    for (UUID networkId : toPurge) {
      networks.remove(networkId);
      networksToDelete.add(networkId);
      Constants.LOG.info("Purged orphaned network {}", networkId);
    }

    if (!toPurge.isEmpty()) {
      Constants.LOG.warn("Purged {} orphaned networks", toPurge.size());
    }

    return toPurge.size();
  }

  /**
   * THIS METHOD REMOVES EVERY EXISTING NETWORK!!!!!!!!
   * Delete ALL networks and mark all files for deletion.
   * Used when operators want to rebuild networks from scratch via recalculate command.
   */
  public int resetAllNetworks() {
    int count = networks.size();

    networksToDelete.addAll(networks.keySet());
    networks.clear();
    blockToNetwork.clear();
    globalStorage.clear();
    dirtyNetworks.clear();
    networksUndergoingRebuild.clear();
    pendingStationUpdates.clear();

    Constants.LOG.warn("RESET: Deleted all {} networks. Use recalculate to rebuild.", count);
    return count;
  }

  /**
   * Get network statistics for debugging.
   */
  public NetworkStats getStats() {
    int totalNodes = 0;
    int totalCrates = 0;
    int totalStations = 0;
    for (CrateNetwork network : networks.values()) {
      totalNodes += network.size();
      totalCrates += network.crates().size();
      totalStations += network.stations().size();
    }
    return new NetworkStats(networks.size(), totalNodes, totalCrates, totalStations);
  }

  public record NetworkStats(int networkCount, int totalNodes, int totalCrates, int totalStations) {
  }
}


