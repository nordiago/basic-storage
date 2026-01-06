package com.khazoda.basicstorage.storage;

import com.khazoda.basicstorage.Constants;
import com.khazoda.basicstorage.structure.CrateSlotComponent;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Manages per-network file I/O with atomic writes to improve scalability with many networks.
 * Each network is saved to data/basicstorage/networks/{uuid}.dat
 */
public class NetworkFileManager {
  private final Path networksDirectory;

  public NetworkFileManager(ServerLevel level) {
    Path worldDir = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
    this.networksDirectory = worldDir.resolve("data").resolve("basicstorage").resolve("networks");
    try {
      Files.createDirectories(networksDirectory);
    } catch (IOException e) {
      Constants.LOG.error("Failed to create networks directory", e);
    }
  }

  /**
   * Save a single network to its own file with atomic write.
   */
  public void saveNetwork(UUID id, CrateNetwork network, CrateNetworkManager manager) {
    Path targetFile = networksDirectory.resolve(id.toString() + ".dat");
    Path tempFile = networksDirectory.resolve(id + ".dat.tmp");

    try {
      CompoundTag nbt = new CompoundTag();

      /* Save network nodes */
      CompoundTag nodesTag = new CompoundTag();
      for (Map.Entry<String, Set<BlockPos>> entry : network.nodes.entrySet()) {
        long[] posArr = new long[entry.getValue().size()];
        int i = 0;
        for (BlockPos pos : entry.getValue()) {
          posArr[i++] = pos.asLong();
        }
        nodesTag.putLongArray(entry.getKey(), posArr);
      }
      nbt.put("nodes", nodesTag);

      /* Save storage data for crates in this network */
      Set<BlockPos> crates = network.nodes.get("crate");
      if (crates != null && !crates.isEmpty()) {
        List<ItemVariant> palette = new ArrayList<>();
        Map<ItemVariant, Integer> variantToIdx = new HashMap<>();
        long[] gsPosArr = new long[crates.size()];
        int[] gsIdxArr = new int[crates.size()];
        int[] gsCountArr = new int[crates.size()];
        int k = 0;

        for (BlockPos pos : crates) {
          CrateSlotComponent storage = manager.getStorage(pos);
          if (storage != null) {
            gsPosArr[k] = pos.asLong();
            gsIdxArr[k] = variantToIdx.computeIfAbsent(storage.item(), v -> {
              palette.add(v);
              return palette.size() - 1;
            });
            gsCountArr[k] = storage.count();
            k++;
          }
        }

        /* Trim arrays if some crates had no storage */
        if (k < crates.size()) {
          gsPosArr = Arrays.copyOf(gsPosArr, k);
          gsIdxArr = Arrays.copyOf(gsIdxArr, k);
          gsCountArr = Arrays.copyOf(gsCountArr, k);
        }

        ListTag paletteTag = new ListTag();
        for (ItemVariant variant : palette) {
          ItemVariant.CODEC.encodeStart(NbtOps.INSTANCE, variant).result().ifPresent(paletteTag::add);
        }

        nbt.put("storage_palette", paletteTag);
        nbt.putLongArray("storage_pos", gsPosArr);
        nbt.putIntArray("storage_idx", gsIdxArr);
        nbt.putIntArray("storage_count", gsCountArr);
      }

      /* Write to temp file first, then atomic rename */
      NbtIo.writeCompressed(nbt, tempFile);
      Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

      /* Ensure durability on system crash */
      try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(targetFile, java.nio.file.StandardOpenOption.WRITE)) {
        channel.force(true); // Force metadata + data to disk
      }

    } catch (IOException e) {
      Constants.LOG.error("Failed to save network {}", id, e);
      // Clean up potential temp file
      try {
        Files.deleteIfExists(tempFile);
      } catch (IOException ignored) {
      }
    }
  }

  /**
   * Load all networks and their storage data from the networks directory.
   * Returns a pair of (networks map, global storage map).
   */
  public LoadResult loadAllNetworks() {
    Map<UUID, CrateNetwork> networks = new ConcurrentHashMap<>();
    Map<BlockPos, CrateSlotComponent> globalStorage = new ConcurrentHashMap<>();

    if (!Files.exists(networksDirectory)) {
      return new LoadResult(networks, globalStorage);
    }

    try (Stream<Path> files = Files.list(networksDirectory)) {
      files.filter(p -> p.toString().endsWith(".dat")).forEach(file -> {
        String fileName = file.getFileName().toString();
        String uuidStr = fileName.substring(0, fileName.length() - 4);
        try {
          UUID id = UUID.fromString(uuidStr);
          CompoundTag nbt = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());

          CrateNetwork network = new CrateNetwork(id);

          // Load nodes
          CompoundTag nodesTag = nbt.getCompoundOrEmpty("nodes");
          for (String typeName : nodesTag.keySet()) {
            long[] posArr = nodesTag.getLongArray(typeName).orElse(new long[0]);
            Set<BlockPos> posSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
            for (long p : posArr) {
              posSet.add(BlockPos.of(p));
            }
            network.nodes.put(typeName, posSet);
          }

          networks.put(id, network);

          // Load storage data if present
          if (nbt.contains("storage_palette")) {
            ListTag paletteTag = nbt.getListOrEmpty("storage_palette");
            long[] posArr = nbt.getLongArray("storage_pos").orElse(new long[0]);
            int[] idxArr = nbt.getIntArray("storage_idx").orElse(new int[0]);
            int[] countArr = nbt.getIntArray("storage_count").orElse(new int[0]);

            List<ItemVariant> palette = new ArrayList<>();
            for (Tag tag : paletteTag) {
              ItemVariant.CODEC.parse(NbtOps.INSTANCE, tag).result().ifPresent(palette::add);
            }

            for (int i = 0; i < posArr.length && i < idxArr.length && i < countArr.length; i++) {
              if (idxArr[i] >= 0 && idxArr[i] < palette.size()) {
                globalStorage.put(BlockPos.of(posArr[i]), new CrateSlotComponent(palette.get(idxArr[i]), countArr[i]));
              }
            }
          }

        } catch (Exception e) {
          Constants.LOG.error("Failed to load network file {}", file, e);
        }
      });
    } catch (IOException e) {
      Constants.LOG.error("Failed to scan networks directory", e);
    }

    Constants.LOG.info("Loaded {} networks with {} storage entries", networks.size(), globalStorage.size());
    return new LoadResult(networks, globalStorage);
  }

  public void deleteNetwork(UUID id) {
    Path file = networksDirectory.resolve(id.toString() + ".dat");
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      Constants.LOG.error("Failed to delete network file {}", id, e);
    }
  }

  public record LoadResult(Map<UUID, CrateNetwork> networks, Map<BlockPos, CrateSlotComponent> globalStorage) {
  }
}