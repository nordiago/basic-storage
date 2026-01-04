package com.khazoda.basicstorage.storage;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CrateNetwork {
  public final UUID id;
  public final Map<String, Set<BlockPos>> nodes = new ConcurrentHashMap<>();

  public CrateNetwork(UUID id) {
    this.id = id;
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

  public boolean contains(BlockPos pos) {
    for (Set<BlockPos> set : nodes.values()) {
      if (set.contains(pos)) return true;
    }
    return false;
  }

  public boolean isEmpty() {
    if (nodes.isEmpty()) return true;
    for (Set<BlockPos> set : nodes.values()) {
      if (!set.isEmpty()) return false;
    }
    return true;
  }
}
