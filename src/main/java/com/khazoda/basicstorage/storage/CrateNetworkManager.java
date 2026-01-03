package com.khazoda.basicstorage.storage;

import com.khazoda.basicstorage.registry.BlockRegistry;
import com.khazoda.basicstorage.structure.CrateSlotComponent;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

public class CrateNetworkManager extends SavedData {
  private static final String DATA_ID = "basicstorage_networks";

  private final Map<BlockPos, UUID> blockToNetwork = new HashMap<>();
  private final Map<UUID, CrateNetwork> networks = new HashMap<>();
  private final Map<BlockPos, CrateSlotComponent> globalStorage = new HashMap<>();

  private static final Codec<BlockPos> BLOCK_POS_VALUE_CODEC = Codec.LONG.xmap(BlockPos::of, BlockPos::asLong);

  private static final Codec<BlockPos> BLOCK_POS_KEY_CODEC = Codec.STRING.xmap(s -> {
    String[] parts = s.split(",");
    return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
  }, pos -> pos.getX() + "," + pos.getY() + "," + pos.getZ());

  private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

  private static final Codec<CrateNetwork> NETWORK_CODEC = RecordCodecBuilder.create(instance -> instance.group(UUID_CODEC.fieldOf("id").forGetter(n -> n.id), Codec.unboundedMap(Codec.STRING, Codec.list(BLOCK_POS_VALUE_CODEC)).fieldOf("nodes").orElse(Map.of()).forGetter(n -> {
    Map<String, List<BlockPos>> nodes = new HashMap<>();
    n.nodes.forEach((k, v) -> nodes.put(k, new ArrayList<>(v)));
    return nodes;
  }), Codec.list(BLOCK_POS_VALUE_CODEC).fieldOf("crates").orElse(List.of()).forGetter(n -> List.of()), Codec.list(BLOCK_POS_VALUE_CODEC).fieldOf("stations").orElse(List.of()).forGetter(n -> List.of()), Codec.list(BLOCK_POS_VALUE_CODEC).fieldOf("connectors").orElse(List.of()).forGetter(n -> List.of())).apply(instance, (id, nodes, legacyCrates, legacyStations, legacyConnectors) -> {
    CrateNetwork n = new CrateNetwork(id);
    nodes.forEach((k, v) -> n.nodes.computeIfAbsent(k, _k -> new HashSet<>()).addAll(v));

    /* Future-proof migration to prevent possibility of bugged networks */
    if (!legacyCrates.isEmpty()) n.nodes.computeIfAbsent("crate", _k -> new HashSet<>()).addAll(legacyCrates);
    if (!legacyStations.isEmpty()) n.nodes.computeIfAbsent("station", _k -> new HashSet<>()).addAll(legacyStations);
    if (!legacyConnectors.isEmpty())
      n.nodes.computeIfAbsent("connector", _k -> new HashSet<>()).addAll(legacyConnectors);
    return n;
  }));

  public static final Codec<CrateNetworkManager> CODEC = RecordCodecBuilder.create(instance -> instance.group(Codec.unboundedMap(BLOCK_POS_KEY_CODEC, UUID_CODEC).fieldOf("blockToNetwork").forGetter(m -> m.blockToNetwork), Codec.unboundedMap(UUID_CODEC, NETWORK_CODEC).fieldOf("networks").forGetter(m -> m.networks), Codec.unboundedMap(BLOCK_POS_KEY_CODEC, CrateSlotComponent.CODEC).fieldOf("globalStorage").forGetter(m -> m.globalStorage)).apply(instance, (blockToNetwork, networks, globalStorage) -> {
    CrateNetworkManager manager = new CrateNetworkManager();
    manager.blockToNetwork.putAll(blockToNetwork);
    manager.networks.putAll(networks);
    manager.globalStorage.putAll(globalStorage);
    return manager;
  }));

  public CrateNetworkManager() {
  }

  public static CrateNetworkManager get(ServerLevel level) {
    return level.getDataStorage().computeIfAbsent(new SavedDataType<>(DATA_ID, CrateNetworkManager::new, CrateNetworkManager.CODEC, DataFixTypes.LEVEL));
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
    network.nodes.computeIfAbsent(type, k -> new HashSet<>()).add(pos);

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
    }
    globalStorage.remove(pos);

    if (network != null && network.isEmpty()) {
      networks.remove(networkId);
    } else {
      rebuildNetwork(level, networkId, pos);
    }

    setDirty();
  }

  private void mergeNetworks(UUID targetId, UUID sourceId) {
    if (targetId.equals(sourceId)) return;
    CrateNetwork target = networks.computeIfAbsent(targetId, CrateNetwork::new);
    CrateNetwork source = networks.remove(sourceId);
    if (source == null) return;
    source.nodes.forEach((type, posSet) -> {
      for (BlockPos pos : posSet) {
        blockToNetwork.put(pos, targetId);
        target.nodes.computeIfAbsent(type, k -> new HashSet<>()).add(pos);
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
            newNetwork.nodes.computeIfAbsent(entry.getKey(), k -> new HashSet<>()).add(current);
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
    globalStorage.put(pos, component);
    setDirty();

    UUID networkId = blockToNetwork.get(pos);
    if (networkId != null) {
      notifyStations(level, networkId);
    }
  }

  public CrateSlotComponent getStorage(BlockPos pos) {
    return globalStorage.getOrDefault(pos, CrateSlotComponent.DEFAULT);
  }

  private void notifyStations(Level level, UUID networkId) {
    CrateNetwork network = networks.get(networkId);
    if (network == null) return;
    Set<BlockPos> stations = network.nodes.get("station");
    if (stations == null) return;

    for (BlockPos stationPos : stations) {
      if (level.isLoaded(stationPos)) {
        var be = level.getBlockEntity(stationPos);
        if (be instanceof NetworkNode node) {
          node.markCacheForUpdate();
        }
      }
    }
  }


  public static class CrateNetwork {
    public final UUID id;
    public final Map<String, Set<BlockPos>> nodes = new HashMap<>();

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
}
