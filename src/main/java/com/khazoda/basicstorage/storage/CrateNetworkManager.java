package com.khazoda.basicstorage.storage;

import com.khazoda.basicstorage.Constants;
import com.khazoda.basicstorage.block.entity.CrateBlockEntity;
import com.khazoda.basicstorage.registry.BlockRegistry;
import com.khazoda.basicstorage.structure.CrateSlotComponent;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CrateNetworkManager extends SavedData {
  private static final String DATA_ID = "basicstorage_networks";
  private static final Map<ServerLevel, CrateNetworkManager> INSTANCE_CACHE = new ConcurrentHashMap<>();

  private final Map<BlockPos, UUID> blockToNetwork = new ConcurrentHashMap<>();
  private final Map<UUID, CrateNetwork> networks = new ConcurrentHashMap<>();
  private final Map<BlockPos, CrateSlotComponent> globalStorage = new ConcurrentHashMap<>();
  private final Set<UUID> pendingStationUpdates = Collections.newSetFromMap(new ConcurrentHashMap<>());


  public CrateNetworkManager() {
  }

  private static final Codec<CrateNetworkManager> OPTIMIZED_CODEC = new Codec<>() {
    @Override
    public <T> DataResult<Pair<CrateNetworkManager, T>> decode(DynamicOps<T> ops, T input) {
      Tag tag = ops.convertTo(NbtOps.INSTANCE, input);
      if (tag instanceof CompoundTag nbt) {
        return DataResult.success(Pair.of(load(nbt, null), input));
      }
      return DataResult.error(() -> "Not a compound tag");
    }

    @Override
    public <T> DataResult<T> encode(CrateNetworkManager input, DynamicOps<T> ops, T prefix) {
      CompoundTag nbt = input.saveNbt(new CompoundTag(), null);
      return DataResult.success(NbtOps.INSTANCE.convertTo(ops, nbt));
    }
  };

  public static CrateNetworkManager get(ServerLevel level) {
    return INSTANCE_CACHE.computeIfAbsent(level, l -> {
      SavedDataType<CrateNetworkManager> type = new SavedDataType<>(DATA_ID, CrateNetworkManager::new, OPTIMIZED_CODEC, DataFixTypes.LEVEL);
      return l.getDataStorage().computeIfAbsent(type);
    });
  }

  public static void clearCache(ServerLevel level) {
    INSTANCE_CACHE.remove(level);
  }

  public static CrateNetworkManager load(CompoundTag nbt, HolderLookup.Provider provider) {
    CrateNetworkManager manager = new CrateNetworkManager();


    // Load blockToNetwork
    nbt.getLongArray("btn_pos").ifPresent(posArr -> {
      nbt.getLongArray("btn_uuid").ifPresent(uuidArr -> {
        int count = Math.min(posArr.length, uuidArr.length / 2);
        for (int i = 0; i < count; i++) {
          manager.blockToNetwork.put(BlockPos.of(posArr[i]), new UUID(uuidArr[i * 2], uuidArr[i * 2 + 1]));
        }
      });
    });

    // Load networks
    nbt.getCompound("networks").ifPresent(networksTag -> {
      for (String idStr : networksTag.keySet()) {
        try {
          UUID id = UUID.fromString(idStr);
          networksTag.getCompound(idStr).ifPresent(nTag -> {
            CrateNetwork network = new CrateNetwork(id);
            nTag.getCompound("nodes").ifPresent(nodesTag -> {
              for (String typeName : nodesTag.keySet()) {
                nodesTag.getLongArray(typeName).ifPresent(nodesArr -> {
                  Set<BlockPos> posSet = network.nodes.computeIfAbsent(typeName, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
                  for (long p : nodesArr) posSet.add(BlockPos.of(p));
                });
              }
            });
            manager.networks.put(id, network);
          });
        } catch (Exception ignored) {
        }
      }
    });

    // Load paletted globalStorage
    nbt.getList("gs_palette_v").ifPresent(paletteTag -> {
      nbt.getLongArray("gs_pos").ifPresent(posArr -> {
        nbt.getIntArray("gs_idx").ifPresent(idxArr -> {
          nbt.getIntArray("gs_count").ifPresent(countArr -> {
            List<ItemVariant> palette = new ArrayList<>();
            for (int i = 0; i < paletteTag.size(); i++) {
              ItemVariant.CODEC.parse(NbtOps.INSTANCE, paletteTag.get(i)).result().ifPresent(palette::add);
            }

            for (int i = 0; i < posArr.length && i < idxArr.length && i < countArr.length; i++) {
              if (idxArr[i] >= 0 && idxArr[i] < palette.size()) {
                manager.globalStorage.put(BlockPos.of(posArr[i]), new CrateSlotComponent(palette.get(idxArr[i]), countArr[i]));
              }
            }
          });
        });
      });
    });

    return manager;
  }

  public CompoundTag saveNbt(CompoundTag nbt, HolderLookup.Provider provider) {
    // Save blockToNetwork
    long[] posArr = new long[blockToNetwork.size()];
    long[] uuidArr = new long[blockToNetwork.size() * 2];
    int i = 0;
    for (Map.Entry<BlockPos, UUID> entry : blockToNetwork.entrySet()) {
      posArr[i] = entry.getKey().asLong();
      uuidArr[i * 2] = entry.getValue().getMostSignificantBits();
      uuidArr[i * 2 + 1] = entry.getValue().getLeastSignificantBits();
      i++;
    }
    nbt.putLongArray("btn_pos", posArr);
    nbt.putLongArray("btn_uuid", uuidArr);

    // Save networks
    CompoundTag networksTag = new CompoundTag();
    for (Map.Entry<UUID, CrateNetwork> entry : networks.entrySet()) {
      CompoundTag nTag = new CompoundTag();
      CompoundTag nodesTag = new CompoundTag();
      for (Map.Entry<String, Set<BlockPos>> nodesEntry : entry.getValue().nodes.entrySet()) {
        long[] nodesArr = new long[nodesEntry.getValue().size()];
        int j = 0;
        for (BlockPos pos : nodesEntry.getValue()) nodesArr[j++] = pos.asLong();
        nodesTag.putLongArray(nodesEntry.getKey(), nodesArr);
      }
      nTag.put("nodes", nodesTag);
      networksTag.put(entry.getKey().toString(), nTag);
    }
    nbt.put("networks", networksTag);

    // Save paletted globalStorage
    List<ItemVariant> palette = new ArrayList<>();
    Map<ItemVariant, Integer> variantToIdx = new HashMap<>();
    long[] gsPosArr = new long[globalStorage.size()];
    int[] gsIdxArr = new int[globalStorage.size()];
    int[] gsCountArr = new int[globalStorage.size()];
    int k = 0;
    for (Map.Entry<BlockPos, CrateSlotComponent> entry : globalStorage.entrySet()) {
      gsPosArr[k] = entry.getKey().asLong();
      gsIdxArr[k] = variantToIdx.computeIfAbsent(entry.getValue().item(), v -> {
        palette.add(v);
        return palette.size() - 1;
      });
      gsCountArr[k] = entry.getValue().count();
      k++;
    }

    ListTag paletteTag = new ListTag();
    for (ItemVariant variant : palette) {
      ItemVariant.CODEC.encodeStart(NbtOps.INSTANCE, variant).result().ifPresent(paletteTag::add);
    }

    nbt.put("gs_palette_v", paletteTag);
    nbt.putLongArray("gs_pos", gsPosArr);
    nbt.putIntArray("gs_idx", gsIdxArr);
    nbt.putIntArray("gs_count", gsCountArr);

    return nbt;
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
      if (neighborId != null && networks.containsKey(neighborId)) adjacentNetworks.add(neighborId);
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

    setDirty();
    notifyStations(level, networkId);
  }

  public void onBlockRemoved(Level level, BlockPos pos) {
    if (level.isClientSide()) return;
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
    } else if (network != null) {
      // Only rebuild network if split is likely
      if (shouldCheckForSplit(level, pos, network)) {
        rebuildNetwork(level, networkId, pos);
      }
    }

    setDirty();
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

  private void rebuildNetwork(Level level, UUID networkId, BlockPos removedPos) {
    CrateNetwork oldNetwork = networks.remove(networkId);
    if (oldNetwork == null) return;

    Set<BlockPos> remainingBlocks = new HashSet<>();
    oldNetwork.nodes.values().forEach(remainingBlocks::addAll);

    while (!remainingBlocks.isEmpty()) {
      BlockPos root = remainingBlocks.iterator().next();
      UUID newId = UUID.randomUUID();
      CrateNetwork newNetwork = new CrateNetwork(newId);
      networks.put(newId, newNetwork);

      Queue<BlockPos> todo = new LinkedList<>();
      todo.add(root);
      remainingBlocks.remove(root);

      while (!todo.isEmpty()) {
        BlockPos current = todo.poll();
        blockToNetwork.put(current, newId);

        for (Map.Entry<String, Set<BlockPos>> entry : oldNetwork.nodes.entrySet()) {
          if (entry.getValue().contains(current)) {
            newNetwork.nodes.computeIfAbsent(entry.getKey(), k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(current);
            break;
          }
        }

        for (Direction dir : Direction.values()) {
          BlockPos neighbor = current.relative(dir);
          if (remainingBlocks.contains(neighbor)) {
            todo.add(neighbor);
            remainingBlocks.remove(neighbor);
          }
        }
      }
      notifyStations(level, newId);
    }
  }

  public void updateStorage(Level level, BlockPos pos, CrateSlotComponent component) {
    if (level.isClientSide()) return;

    CrateSlotComponent old = globalStorage.put(pos, component);
    setDirty();

    UUID networkId = blockToNetwork.get(pos);
    if (networkId != null) {
      CrateNetwork network = networks.get(networkId);
      if (network != null) {

        // Skip index update and notification if only count changed
        if (old != null && old.item().equals(component.item())) {
          return;
        }

        // Incremental index update when contents change
        network.updateItemIncremental(old != null ? old.item() : null, component.item(), pos);
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
    if (pendingStationUpdates.isEmpty()) return;

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
    if (network == null) return List.of();
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
      setDirty();
    }

    return healed;
  }

  public int recalculateArea(ServerLevel level, BlockPos pos1, BlockPos pos2) {
    int x1 = Math.min(pos1.getX(), pos2.getX());
    int y1 = Math.min(pos1.getY(), pos2.getY());
    int z1 = Math.min(pos1.getZ(), pos2.getZ());
    int x2 = Math.max(pos1.getX(), pos2.getX());
    int y2 = Math.max(pos1.getY(), pos2.getY());
    int z2 = Math.max(pos1.getZ(), pos2.getZ());

    int count = 0;
    for (int x = x1; x <= x2; x++) {
      for (int y = y1; y <= y2; y++) {
        for (int z = z1; z <= z2; z++) {
          BlockPos p = new BlockPos(x, y, z);
          if (level.isLoaded(p)) {
            BlockState state = level.getBlockState(p);
            if (getType(state) != null) {
              // Force re-registration
              onBlockRemoved(level, p);
              onBlockAdded(level, p, state);

              // Refresh storage info if it's a crate - will fix valid crates showing as empty in network map
              var be = level.getBlockEntity(p);
              if (be instanceof CrateBlockEntity crate) {
                updateStorage(level, p, crate.storage.toComponent());
              }
              count++;
            }
          }
        }
      }
    }
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


