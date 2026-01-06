package com.khazoda.basicstorage.storage;

import com.khazoda.basicstorage.structure.CrateSlotComponent;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.BlockPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a network of crates, stations, and connectors.
 */
public class CrateNetwork {
  public final UUID id;
  public final Map<String, Set<BlockPos>> nodes = new ConcurrentHashMap<>();

  /* Item index for lookups - rebuilt lazily when dirty */
  private final Map<ItemVariant, Set<BlockPos>> itemIndex = new ConcurrentHashMap<>();
  private volatile boolean indexDirty = true;

  private static final int SORT_THRESHOLD = 512;

  public CrateNetwork(UUID id) {
    this.id = id;
  }

  /**
   * Find crates that contain the given variant, sorted by distance to station
   * For networks with >512 matching crates, returns unsorted for performance
   *
   * @param variant    The item variant to search for
   * @param stationPos The position of the requesting station
   * @param manager    The network manager (for storage lookups)
   * @return List of crate positions, sorted by distance if under allowed threshold
   */
  public List<BlockPos> findCratesForItem(ItemVariant variant, BlockPos stationPos, CrateNetworkManager manager) {
    ensureIndexed(manager);

    Set<BlockPos> matches = itemIndex.get(variant);
    if (matches == null || matches.isEmpty()) {
      return List.of();
    }

    List<BlockPos> result = new ArrayList<>(matches);

    /* Sort by distance to station for nearby preference */
    if (result.size() <= SORT_THRESHOLD) {
      result.sort(Comparator.comparingDouble(stationPos::distSqr));
    }

    return result;
  }

  /**
   * Get all variants that have crates in this network
   */
  public Set<ItemVariant> getIndexedVariants(CrateNetworkManager manager) {
    ensureIndexed(manager);
    return Collections.unmodifiableSet(itemIndex.keySet());
  }

  /**
   * Mark index as dirty - will rebuild lazily on next access
   * Call this when crate contents change
   */
  public void invalidateIndex() {
    indexDirty = true;
  }

  /**
   * Update the index incrementally for a specific block position.
   */
  public void updateItemIncremental(ItemVariant oldVariant, ItemVariant newVariant, BlockPos pos) {
    if (indexDirty) return; /* Full rebuild will trigger anyway, so no need to update here */

    if (Objects.equals(oldVariant, newVariant)) return;

    if (oldVariant != null) {
      Set<BlockPos> oldSet = itemIndex.get(oldVariant);
      if (oldSet != null) {
        oldSet.remove(pos);
        if (oldSet.isEmpty()) itemIndex.remove(oldVariant);
      }
    }

    if (newVariant != null) {
      itemIndex.computeIfAbsent(newVariant, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(pos);
    }
  }

  /**
   * Ensure the index is up-to-date before reading it
   */
  private void ensureIndexed(CrateNetworkManager manager) {
    if (!indexDirty) return;
    synchronized (this) {
      if (!indexDirty) return;
      rebuildIndex(manager);
      indexDirty = false;
    }
  }

  /**
   * Rebuild the item index from current network state.
   */
  private void rebuildIndex(CrateNetworkManager manager) {
    itemIndex.clear();

    Set<BlockPos> crates = nodes.get("crate");
    if (crates == null) return;

    for (BlockPos pos : crates) {
      CrateSlotComponent contents = manager.getStorage(pos);
      if (contents != null) {
        /* Index all crates, including blank ones */
        ItemVariant variant = contents.item();
        itemIndex.computeIfAbsent(variant, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(pos);
      }
    }
  }

  public Set<BlockPos> crates() {
    return nodes.getOrDefault("crate", Set.of());
  }

  public Set<BlockPos> stations() {
    return nodes.getOrDefault("station", Set.of());
  }

  public Set<BlockPos> connectors() {
    return nodes.getOrDefault("connector", Set.of());
  }

  public int size() {
    int count = 0;
    for (Set<BlockPos> set : nodes.values()) {
      count += set.size();
    }
    return count;
  }

  public boolean contains(BlockPos pos) {
    for (Set<BlockPos> set : nodes.values()) {
      if (set.contains(pos)) return true;
    }
    return false;
  }

  /**
   * Create an immutable snapshot of the network nodes for async processing.
   */
  public Map<String, Set<BlockPos>> createSnapshot() {
    Map<String, Set<BlockPos>> snapshot = new HashMap<>(nodes.size());
    for (Map.Entry<String, Set<BlockPos>> entry : nodes.entrySet()) {
      snapshot.put(entry.getKey(), new HashSet<>(entry.getValue()));
    }
    return snapshot;
  }

  public boolean isEmpty() {
    if (nodes.isEmpty()) return true;
    for (Set<BlockPos> set : nodes.values()) {
      if (!set.isEmpty()) return false;
    }
    return true;
  }
}
