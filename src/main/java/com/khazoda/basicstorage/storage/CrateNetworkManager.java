package com.khazoda.basicstorage.storage;

import com.khazoda.basicstorage.structure.CrateSlotComponent;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
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

  private static final Codec<CrateNetwork> NETWORK_CODEC = RecordCodecBuilder.create(instance -> instance.group(UUID_CODEC.fieldOf("id").forGetter(n -> n.id), Codec.list(BLOCK_POS_VALUE_CODEC).fieldOf("crates").forGetter(n -> new ArrayList<>(n.crates)), Codec.list(BLOCK_POS_VALUE_CODEC).fieldOf("stations").forGetter(n -> new ArrayList<>(n.stations))).apply(instance, (id, crates, stations) -> {
    CrateNetwork n = new CrateNetwork(id);
    n.crates.addAll(crates);
    n.stations.addAll(stations);
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
    return blockToNetwork.containsKey(pos);
  }

  public CrateNetwork getNetworkFor(BlockPos pos) {
    UUID id = blockToNetwork.get(pos);
    return id != null ? networks.get(id) : null;
  }

  public void onBlockAdded(Level level, BlockPos pos, boolean isCrate, boolean isStation) {
    if (level.isClientSide()) return;
    if (blockToNetwork.containsKey(pos)) return;

    Set<UUID> adjacentNetworks = new HashSet<>();
    for (Direction dir : Direction.values()) {
      UUID neighborId = blockToNetwork.get(pos.relative(dir));
      if (neighborId != null) adjacentNetworks.add(neighborId);
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

    CrateNetwork network = networks.get(networkId);
    blockToNetwork.put(pos, networkId);
    if (isCrate) network.crates.add(pos);
    if (isStation) network.stations.add(pos);

    setDirty();
    notifyStations(level, networkId);
  }

  public void onBlockRemoved(Level level, BlockPos pos) {
    if (level.isClientSide()) return;
    UUID networkId = blockToNetwork.remove(pos);
    if (networkId == null) return;

    CrateNetwork network = networks.get(networkId);
    network.crates.remove(pos);
    network.stations.remove(pos);
    globalStorage.remove(pos);

    if (network.isEmpty()) {
      networks.remove(networkId);
    } else {
      rebuildNetwork(level, networkId, pos);
    }

    setDirty();
  }

  private void mergeNetworks(UUID targetId, UUID sourceId) {
    if (targetId.equals(sourceId)) return;
    CrateNetwork target = networks.get(targetId);
    CrateNetwork source = networks.remove(sourceId);
    if (source == null) return;

    for (BlockPos pos : source.crates) {
      blockToNetwork.put(pos, targetId);
      target.crates.add(pos);
    }
    for (BlockPos pos : source.stations) {
      blockToNetwork.put(pos, targetId);
      target.stations.add(pos);
    }
  }

  private void rebuildNetwork(Level level, UUID networkId, BlockPos removedPos) {
    CrateNetwork oldNetwork = networks.remove(networkId);
    if (oldNetwork == null) return;

    Set<BlockPos> remainingBlocks = new HashSet<>();
    remainingBlocks.addAll(oldNetwork.crates);
    remainingBlocks.addAll(oldNetwork.stations);

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
        if (oldNetwork.crates.contains(current)) newNetwork.crates.add(current);
        if (oldNetwork.stations.contains(current)) newNetwork.stations.add(current);

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
    for (BlockPos stationPos : network.stations) {
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
    public final Set<BlockPos> crates = new HashSet<>();
    public final Set<BlockPos> stations = new HashSet<>();

    public CrateNetwork(UUID id) {
      this.id = id;
    }

    public boolean isEmpty() {
      return crates.isEmpty() && stations.isEmpty();
    }
  }
}
