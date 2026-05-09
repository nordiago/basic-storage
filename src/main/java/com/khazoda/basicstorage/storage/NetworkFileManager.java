package com.khazoda.basicstorage.storage;

import com.khazoda.basicstorage.Constants;
import com.khazoda.basicstorage.registry.BlockRegistry;
import com.khazoda.basicstorage.structure.CrateSlotComponent;
import com.mojang.serialization.DynamicOps;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Manages per-network file I/O with atomic writes to improve scalability with many networks.
 * Each network is saved to data/basicstorage/networks/{namespace}/{dimension}/{uuid}.dat
 */
public class NetworkFileManager {
  private static final int CURRENT_NETWORK_FORMAT_VERSION = 1;

  /* START Temporary legacy root-file migration state.
   * Remove with the legacy migration section further down once pre-26.1 network files
   * no longer need to be claimed into dimension-specific folders. */
  private static final Set<Path> CLAIMED_LEGACY_FILES = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private static final Set<Path> IGNORED_NETWORK_FILES = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private static final int LEGACY_FILES_PER_CLAIM_PASS = 64;

  private final Path rootNetworksDirectory;
  private final Path networksDirectory;
  private final Path legacyArchiveDirectory;
  private final Map<UUID, Path> legacyFilesToArchive = new ConcurrentHashMap<>();
  private final Identifier dimensionId;
  private final ServerLevel level;
  private boolean hasUnclaimedLegacyFiles = true;
  private int legacyClaimCursor = 0;
  /* END temporary legacy root-file migration state. */

  public NetworkFileManager(ServerLevel level) {
    this.level = level;
    this.dimensionId = level.dimension().identifier();
    Path worldDir = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
    this.rootNetworksDirectory = worldDir.resolve("data").resolve("basicstorage").resolve("networks");
    this.networksDirectory = rootNetworksDirectory.resolve(dimensionId.getNamespace()).resolve(dimensionId.getPath());
    this.legacyArchiveDirectory = worldDir.resolve("data").resolve("basicstorage").resolve("legacy_migrated_network_copies");
    try {
      Files.createDirectories(networksDirectory);
    } catch (IOException e) {
      Constants.LOG.error("Failed to create networks directory", e);
    }
  }

  /* START Temporary legacy root-file migration cache cleanup. */
  public static void clearLegacyClaims() {
    CLAIMED_LEGACY_FILES.clear();
    IGNORED_NETWORK_FILES.clear();
  }
  /* END temporary legacy root-file migration cache cleanup. */

  /**
   * Save a single network to its own file with atomic write.
   */
  public boolean saveNetwork(UUID id, CrateNetwork network, CrateNetworkManager manager) {
    Path targetFile = networksDirectory.resolve(id.toString() + ".dat");
    Path tempFile = networksDirectory.resolve(id + ".dat.tmp");

    try {
      CompoundTag nbt = new CompoundTag();
      nbt.putInt("format_version", CURRENT_NETWORK_FORMAT_VERSION);
      nbt.putString("network_id", id.toString());
      nbt.putString("dimension", dimensionId.toString());

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
        DynamicOps<Tag> ops = registryOps();
        List<Tag> palette = new ArrayList<>();
        Map<ItemVariant, Integer> variantToIdx = new HashMap<>();
        long[] gsPosArr = new long[crates.size()];
        int[] gsIdxArr = new int[crates.size()];
        int[] gsCountArr = new int[crates.size()];
        int k = 0;

        for (BlockPos pos : crates) {
          CrateSlotComponent storage = manager.getStorageForIndex(id, pos);
          if (storage != null) {
            Integer paletteIndex = variantToIdx.get(storage.item());
            if (paletteIndex == null) {
              Optional<Tag> encoded = ItemVariant.CODEC.encodeStart(ops, storage.item()).resultOrPartial(error -> Constants.LOG.warn("Failed to encode crate storage variant at {} in network {}: {}", pos, id, error));
              if (encoded.isEmpty()) {
                continue;
              }

              paletteIndex = palette.size();
              palette.add(encoded.get());
              variantToIdx.put(storage.item(), paletteIndex);
            }

            gsPosArr[k] = pos.asLong();
            gsIdxArr[k] = paletteIndex;
            gsCountArr[k] = storage.count();
            k++;
          }
        }

        if (k < crates.size()) {
          gsPosArr = Arrays.copyOf(gsPosArr, k);
          gsIdxArr = Arrays.copyOf(gsIdxArr, k);
          gsCountArr = Arrays.copyOf(gsCountArr, k);
        }

        ListTag paletteTag = new ListTag();
        paletteTag.addAll(palette);

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
      archiveLegacyFile(id);
      return true;

    } catch (IOException e) {
      Constants.LOG.error("Failed to save network {}", id, e);
      // Clean up potential temp file
      try {
        Files.deleteIfExists(tempFile);
      } catch (IOException ignored) {
      }
      return false;
    }
  }

  /**
   * Load all networks and their storage data from the networks directory.
   * Returns a pair of (networks map, global storage map).
   */
  public LoadResult loadAllNetworks() {
    Map<UUID, CrateNetwork> networks = new ConcurrentHashMap<>();
    Map<BlockPos, CrateSlotComponent> globalStorage = new ConcurrentHashMap<>();
    Set<UUID> networksNeedingStorageRepair = Collections.newSetFromMap(new ConcurrentHashMap<>());
    Set<UUID> networksNeedingSave = Collections.newSetFromMap(new ConcurrentHashMap<>());
    Set<UUID> claimedLegacyNetworks = Collections.newSetFromMap(new ConcurrentHashMap<>());

    loadNetworksFromDirectory(networksDirectory, networks, globalStorage, networksNeedingStorageRepair, networksNeedingSave);
    ClaimResult claimResult = claimLegacyNetworksFromRoot(networks, globalStorage, networksNeedingStorageRepair, claimedLegacyNetworks, NetworkSnapshot.fromNetworks(networks), LEGACY_FILES_PER_CLAIM_PASS);

    Constants.LOG.info("{} - Loaded {} networks with {} saved crate storage {}", dimensionId, networks.size(), globalStorage.size(), globalStorage.size() == 1 ? "record" : "records");
    return new LoadResult(networks, globalStorage, networksNeedingStorageRepair, networksNeedingSave, claimedLegacyNetworks, claimResult.hasUnclaimedLegacyFiles(), claimResult.filesProcessed());
  }

  public LoadResult claimLegacyNetworks(NetworkSnapshot existingNetworks) {
    return claimLegacyNetworks(existingNetworks, LEGACY_FILES_PER_CLAIM_PASS);
  }

  public LoadResult claimLegacyNetworks(NetworkSnapshot existingNetworks, int maxFiles) {
    Map<UUID, CrateNetwork> networks = new ConcurrentHashMap<>();
    Map<BlockPos, CrateSlotComponent> globalStorage = new ConcurrentHashMap<>();
    Set<UUID> networksNeedingStorageRepair = Collections.newSetFromMap(new ConcurrentHashMap<>());
    Set<UUID> claimedLegacyNetworks = Collections.newSetFromMap(new ConcurrentHashMap<>());

    ClaimResult claimResult = claimLegacyNetworksFromRoot(networks, globalStorage, networksNeedingStorageRepair, claimedLegacyNetworks, existingNetworks, maxFiles);
    return new LoadResult(networks, globalStorage, networksNeedingStorageRepair, Set.of(), claimedLegacyNetworks, claimResult.hasUnclaimedLegacyFiles(), claimResult.filesProcessed());
  }

  private void loadNetworksFromDirectory(Path directory, Map<UUID, CrateNetwork> networks, Map<BlockPos, CrateSlotComponent> globalStorage, Set<UUID> networksNeedingStorageRepair, Set<UUID> networksNeedingSave) {
    if (!Files.exists(directory)) {
      return;
    }

    try (Stream<Path> files = Files.list(directory)) {
      files.filter(p -> p.toString().endsWith(".dat")).forEach(file -> {
        Optional<UUID> networkId = networkIdFromFile(file);
        if (networkId.isEmpty()) {
          return;
        }

        try {
          UUID id = networkId.get();
          if (networks.containsKey(id)) {
            return;
          }

          LoadedNetwork loaded = loadNetwork(file, id);
          if (loaded == null) {
            return;
          }
          addLoadedNetwork(id, loaded, networks, globalStorage, networksNeedingStorageRepair);
          if (loaded.needsMetadataRewrite()) {
            networksNeedingSave.add(id);
          }
        } catch (Exception e) {
          Constants.LOG.error("Failed to load network file {}", file, e);
        }
      });
    } catch (IOException e) {
      Constants.LOG.error("Failed to scan networks directory {}", directory, e);
    }
  }

  private void addLoadedNetwork(UUID id, LoadedNetwork loaded, Map<UUID, CrateNetwork> networks, Map<BlockPos, CrateSlotComponent> globalStorage, Set<UUID> networksNeedingStorageRepair) {
    networks.put(id, loaded.network());
    globalStorage.putAll(loaded.globalStorage());
    if (loaded.needsStorageRepair()) {
      networksNeedingStorageRepair.add(id);
    }
  }

  /* START Temporary legacy root network migration section.
   * Root-level files from older versions do not record their dimension, so a
   * dimension may only claim them when currently loaded blocks prove ownership.
   * Remove after the pre-26.1 global network folder migration window. */
  private ClaimResult claimLegacyNetworksFromRoot(Map<UUID, CrateNetwork> networks, Map<BlockPos, CrateSlotComponent> globalStorage, Set<UUID> networksNeedingStorageRepair, Set<UUID> claimedLegacyNetworks, NetworkSnapshot existingNetworks, int maxFiles) {
    if (!hasUnclaimedLegacyFiles || !Files.exists(rootNetworksDirectory)) {
      hasUnclaimedLegacyFiles = false;
      return new ClaimResult(false, 0);
    }

    Set<UUID> claimedBeforeLoad = new HashSet<>(claimedLegacyNetworks);
    ClaimResult result = claimLegacyNetworksFromDirectory(networks, globalStorage, networksNeedingStorageRepair, claimedLegacyNetworks, existingNetworks, maxFiles);
    hasUnclaimedLegacyFiles = result.hasUnclaimedLegacyFiles();
    int claimed = claimedLegacyNetworks.size() - claimedBeforeLoad.size();
    if (claimed > 0) {
      Constants.LOG.info("{} - Migrated {} legacy networks", dimensionId, claimed);
    }
    return result;
  }

  private ClaimResult claimLegacyNetworksFromDirectory(Map<UUID, CrateNetwork> networks, Map<BlockPos, CrateSlotComponent> globalStorage, Set<UUID> networksNeedingStorageRepair, Set<UUID> claimedLegacyNetworks, NetworkSnapshot existingNetworks, int maxFiles) {
    try (Stream<Path> files = Files.list(rootNetworksDirectory)) {
      List<Path> candidates = files
          .filter(p -> p.toString().endsWith(".dat"))
          .filter(p -> networkIdFromFile(p).isPresent())
          .map(p -> p.toAbsolutePath().normalize())
          .filter(p -> !CLAIMED_LEGACY_FILES.contains(p))
          .sorted()
          .toList();
      if (candidates.isEmpty()) {
        legacyClaimCursor = 0;
        return new ClaimResult(false, 0);
      }

      int processed = Math.min(Math.max(1, maxFiles), candidates.size());
      int start = Math.floorMod(legacyClaimCursor, candidates.size());
      for (int i = 0; i < processed; i++) {
        Path file = candidates.get((start + i) % candidates.size());
        claimLegacyNetworkFile(file, networks, globalStorage, networksNeedingStorageRepair, claimedLegacyNetworks, existingNetworks);
      }

      int nextCursor = start + processed;
      if (nextCursor >= candidates.size()) {
        legacyClaimCursor = 0;
      } else {
        legacyClaimCursor = nextCursor;
      }

      if (candidates.size() <= LEGACY_FILES_PER_CLAIM_PASS) {
        for (Path candidate : candidates) {
          if (Files.exists(candidate)) {
            return new ClaimResult(true, processed);
          }
        }
        return new ClaimResult(false, processed);
      }
      return new ClaimResult(true, processed);
    } catch (IOException e) {
      Constants.LOG.error("Failed to scan legacy networks directory {}", rootNetworksDirectory, e);
      return new ClaimResult(true, 0);
    }
  }

  private boolean claimLegacyNetworkFile(Path file, Map<UUID, CrateNetwork> networks, Map<BlockPos, CrateSlotComponent> globalStorage, Set<UUID> networksNeedingStorageRepair, Set<UUID> claimedLegacyNetworks, NetworkSnapshot existingNetworks) {
    Optional<UUID> networkId = networkIdFromFile(file);
    if (networkId.isEmpty()) {
      return false;
    }

    try {
      UUID id = networkId.get();
      Path legacyFileKey = file.toAbsolutePath().normalize();
      if (CLAIMED_LEGACY_FILES.contains(legacyFileKey)) {
        return false;
      }

      LoadedNetwork loaded = loadNetwork(file, id);
      if (loaded == null) {
        return true;
      }
      CrateNetwork legacyNetwork = loaded.network();

      if (networks.containsKey(id) || existingNetworks.networkIds().contains(id)) {
        archiveAlreadyMigratedLegacyFile(id, legacyFileKey, legacyNetwork);
        return Files.exists(legacyFileKey);
      }

      if (overlapsLoadedNetworks(legacyNetwork, networks) || existingNetworks.overlaps(legacyNetwork)) {
        archiveOverlappingLegacyFile(id, legacyFileKey, legacyNetwork);
        return Files.exists(legacyFileKey);
      }

      if (!matchesLoadedLegacyNodes(legacyNetwork)) {
        return true;
      }

      addLoadedNetwork(id, loaded, networks, globalStorage, networksNeedingStorageRepair);
      CLAIMED_LEGACY_FILES.add(legacyFileKey);
      legacyFilesToArchive.put(id, legacyFileKey);
      claimedLegacyNetworks.add(id);
      return true;
    } catch (Exception e) {
      Constants.LOG.error("Failed to load legacy network file {}", file, e);
      return true;
    }
  }

  private void archiveLegacyFile(UUID id) {
    Path legacyFile = legacyFilesToArchive.remove(id);
    if (legacyFile == null || !Files.exists(legacyFile)) {
      return;
    }

    try {
      Files.createDirectories(legacyArchiveDirectory);
      Path archiveFile = legacyArchiveDirectory.resolve(legacyFile.getFileName());
      try {
        Files.move(legacyFile, archiveFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(legacyFile, archiveFile, StandardCopyOption.REPLACE_EXISTING);
      }
      CLAIMED_LEGACY_FILES.add(legacyFile.toAbsolutePath().normalize());
      Constants.LOG.info("{} - Archived migrated legacy network {}", dimensionId, id);
    } catch (IOException e) {
      legacyFilesToArchive.put(id, legacyFile);
      hasUnclaimedLegacyFiles = true;
      Constants.LOG.warn("Saved migrated network {} for {}, but failed to archive legacy file {}", id, dimensionId, legacyFile, e);
    }
  }

  private void archiveAlreadyMigratedLegacyFile(UUID id, Path legacyFileKey, CrateNetwork legacyNetwork) throws IOException {
    if (!Files.exists(networksDirectory.resolve(id.toString() + ".dat"))) {
      return;
    }

    if (!matchesLoadedLegacyNodes(legacyNetwork)) {
      return;
    }

    legacyFilesToArchive.put(id, legacyFileKey);
    archiveLegacyFile(id);
    if (!Files.exists(legacyFileKey)) {
      CLAIMED_LEGACY_FILES.add(legacyFileKey);
    }
  }

  private void archiveOverlappingLegacyFile(UUID id, Path legacyFileKey, CrateNetwork legacyNetwork) throws IOException {
    if (!matchesLoadedLegacyNodes(legacyNetwork)) {
      return;
    }

    legacyFilesToArchive.put(id, legacyFileKey);
    archiveLegacyFile(id);
    if (!Files.exists(legacyFileKey)) {
      CLAIMED_LEGACY_FILES.add(legacyFileKey);
    }
  }

  private Optional<UUID> networkIdFromFile(Path file) {
    String fileName = file.getFileName().toString();
    String uuidStr = fileName.substring(0, fileName.length() - 4);
    try {
      return Optional.of(UUID.fromString(uuidStr));
    } catch (IllegalArgumentException e) {
      Path fileKey = file.toAbsolutePath().normalize();
      if (IGNORED_NETWORK_FILES.add(fileKey)) {
        Constants.LOG.warn("Ignoring network file with non-UUID name: {}", file);
      }
      return Optional.empty();
    }
  }

  private LoadedNetwork loadNetwork(Path file, UUID id) throws IOException {
    CompoundTag nbt = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
    CrateNetwork network = new CrateNetwork(id);
    Map<BlockPos, CrateSlotComponent> loadedStorage = new HashMap<>();
    boolean needsStorageRepair = false;
    boolean needsMetadataRewrite = validateMetadata(nbt, file, id);

    Optional<String> metadataDimension = nbt.getString("dimension");
    if (metadataDimension.isPresent() && !metadataDimension.get().equals(dimensionId.toString())) {
      Constants.LOG.warn("{} - Skipping network file {} because it declares dimension {}", dimensionId, file, metadataDimension.get());
      return null;
    }

    loadNodes(nbt, network);

    if (nbt.contains("storage_palette")) {
      ListTag paletteTag = nbt.getListOrEmpty("storage_palette");
      long[] posArr = nbt.getLongArray("storage_pos").orElse(new long[0]);
      int[] idxArr = nbt.getIntArray("storage_idx").orElse(new int[0]);
      int[] countArr = nbt.getIntArray("storage_count").orElse(new int[0]);
      int invalidStorageEntries = 0;
      DynamicOps<Tag> ops = registryOps();

      if (posArr.length != idxArr.length || posArr.length != countArr.length) {
        needsStorageRepair = true;
        Constants.LOG.warn("Network {} has mismatched storage arrays: positions={}, indexes={}, counts={}", id, posArr.length, idxArr.length, countArr.length);
      }

      List<ItemVariant> palette = new ArrayList<>();
      for (Tag tag : paletteTag) {
        Optional<ItemVariant> variant = ItemVariant.CODEC.parse(ops, tag).resultOrPartial(error -> Constants.LOG.warn("Failed to decode crate storage palette entry in network {}: {}", id, error));
        if (variant.isPresent()) {
          palette.add(variant.get());
        } else {
          palette.add(null);
          needsStorageRepair = true;
        }
      }

      Set<BlockPos> crateNodes = network.crates();
      for (int i = 0; i < posArr.length && i < idxArr.length && i < countArr.length; i++) {
        BlockPos storagePos = BlockPos.of(posArr[i]);
        if (idxArr[i] >= 0 && idxArr[i] < palette.size() && palette.get(idxArr[i]) != null && crateNodes.contains(storagePos) && countArr[i] >= 0 && countArr[i] <= Constants.CRATE_MAX_COUNT) {
          loadedStorage.put(storagePos, new CrateSlotComponent(palette.get(idxArr[i]), countArr[i]));
        } else {
          needsStorageRepair = true;
          invalidStorageEntries++;
        }
      }

      if (invalidStorageEntries > 0) {
        Constants.LOG.warn("{} - Network {} contains {} invalid storage entries. Automatically repairing..", dimensionId, id, invalidStorageEntries);
      }
    } else {
      needsStorageRepair = true;
    }

    return new LoadedNetwork(network, loadedStorage, needsStorageRepair, needsMetadataRewrite);
  }

  private boolean validateMetadata(CompoundTag nbt, Path file, UUID id) {
    boolean needsRewrite = false;

    Optional<Integer> formatVersion = nbt.getInt("format_version");
    if (formatVersion.isEmpty()) {
      needsRewrite = true;
    } else if (formatVersion.get() < CURRENT_NETWORK_FORMAT_VERSION) {
      needsRewrite = true;
    } else if (formatVersion.get() > CURRENT_NETWORK_FORMAT_VERSION) {
      Constants.LOG.warn("{} - Network file {} was written by newer format version {}", dimensionId, file, formatVersion.get());
    }

    Optional<String> metadataNetworkId = nbt.getString("network_id");
    if (metadataNetworkId.isEmpty()) {
      needsRewrite = true;
    } else {
      try {
        UUID parsedId = UUID.fromString(metadataNetworkId.get());
        if (!parsedId.equals(id)) {
          needsRewrite = true;
          Constants.LOG.warn("{} - Network file {} declares id {} but filename is {}", dimensionId, file, parsedId, id);
        }
      } catch (IllegalArgumentException e) {
        needsRewrite = true;
        Constants.LOG.warn("{} - Network file {} has invalid network_id metadata {}", dimensionId, file, metadataNetworkId.get());
      }
    }

    Optional<String> metadataDimension = nbt.getString("dimension");
    if (metadataDimension.isEmpty()) {
      needsRewrite = true;
    } else if (!metadataDimension.get().equals(dimensionId.toString())) {
      needsRewrite = true;
    }

    return needsRewrite;
  }

  private void loadNodes(CompoundTag nbt, CrateNetwork network) {
    CompoundTag nodesTag = nbt.getCompoundOrEmpty("nodes");
    for (String typeName : nodesTag.keySet()) {
      long[] posArr = nodesTag.getLongArray(typeName).orElse(new long[0]);
      Set<BlockPos> posSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
      for (long p : posArr) {
        posSet.add(BlockPos.of(p));
      }
      network.nodes.put(typeName, posSet);
    }
  }

  private boolean matchesLoadedLegacyNodes(CrateNetwork network) {
    boolean foundMatchingLoadedNode = false;
    for (Map.Entry<String, Set<BlockPos>> entry : network.nodes.entrySet()) {
      for (BlockPos pos : entry.getValue()) {
        if (!level.isLoaded(pos)) {
          continue;
        }

        if (!isNetworkBlock(entry.getKey(), level.getBlockState(pos))) {
          return false;
        }
        foundMatchingLoadedNode = true;
      }
    }
    return foundMatchingLoadedNode;
  }

  private boolean overlapsLoadedNetworks(CrateNetwork legacyNetwork, Map<UUID, CrateNetwork> networks) {
    for (CrateNetwork network : networks.values()) {
      for (Map.Entry<String, Set<BlockPos>> entry : legacyNetwork.nodes.entrySet()) {
        Set<BlockPos> loadedPositions = network.nodes.get(entry.getKey());
        if (loadedPositions != null && !Collections.disjoint(loadedPositions, entry.getValue())) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isNetworkBlock(String type, BlockState state) {
    return switch (type) {
      case "crate" -> state.is(BlockRegistry.CRATE_BLOCK);
      case "station" -> state.is(BlockRegistry.CRATE_STATION_BLOCK);
      case "connector" -> state.is(BlockRegistry.CRATE_CONNECTOR_BLOCK);
      default -> false;
    };
  }

  public int countLegacyNetworkFiles() {
    if (!Files.exists(rootNetworksDirectory)) {
      return 0;
    }

    try (Stream<Path> files = Files.list(rootNetworksDirectory)) {
      return (int) files
          .filter(p -> p.toString().endsWith(".dat"))
          .filter(p -> networkIdFromFile(p).isPresent())
          .map(p -> p.toAbsolutePath().normalize())
          .filter(p -> !CLAIMED_LEGACY_FILES.contains(p))
          .count();
    } catch (IOException e) {
      Constants.LOG.error("Failed to count legacy networks in {}", rootNetworksDirectory, e);
      return 0;
    }
  }

  public List<LegacyMigrationTarget> findNearestLegacyNetworkPositions(BlockPos origin, int limit) {
    return findNearestLegacyNetworkPositions(origin, limit, new NetworkSnapshot(Set.of(), Map.of()));
  }

  public List<LegacyMigrationTarget> findNearestLegacyNetworkPositions(BlockPos origin, int limit, NetworkSnapshot existingNetworks) {
    if (limit <= 0 || !Files.exists(rootNetworksDirectory)) {
      return List.of();
    }

    PriorityQueue<LegacyMigrationTarget> nearest = new PriorityQueue<>(Comparator.comparingDouble(LegacyMigrationTarget::distanceSqr).reversed());
    try (Stream<Path> files = Files.list(rootNetworksDirectory)) {
      files
          .filter(p -> p.toString().endsWith(".dat"))
          .forEach(file -> collectNearestLegacyNetworkTarget(file, origin, limit, nearest, existingNetworks));
    } catch (IOException e) {
      Constants.LOG.error("Failed to scan legacy networks directory {}", rootNetworksDirectory, e);
    }

    List<LegacyMigrationTarget> result = new ArrayList<>(nearest);
    result.sort(Comparator.comparingDouble(LegacyMigrationTarget::distanceSqr));
    return result;
  }

  private void collectNearestLegacyNetworkTarget(Path file, BlockPos origin, int limit, PriorityQueue<LegacyMigrationTarget> nearest, NetworkSnapshot existingNetworks) {
    Optional<UUID> networkId = networkIdFromFile(file);
    if (networkId.isEmpty()) {
      return;
    }

    Path legacyFileKey = file.toAbsolutePath().normalize();
    if (CLAIMED_LEGACY_FILES.contains(legacyFileKey) || existingNetworks.networkIds().contains(networkId.get()) || Files.exists(networksDirectory.resolve(networkId.get() + ".dat"))) {
      return;
    }

    try {
      CrateNetwork network = loadNetworkNodes(file, networkId.get());
      if (existingNetworks.overlaps(network)) {
        return;
      }

      LegacyMigrationTarget nearestInNetwork = null;
      for (Map.Entry<String, Set<BlockPos>> entry : network.nodes.entrySet()) {
        for (BlockPos pos : entry.getValue()) {
          LegacyMigrationTarget candidate = new LegacyMigrationTarget(networkId.get(), entry.getKey(), pos, distanceSqr(origin, pos));
          if (nearestInNetwork == null || candidate.distanceSqr() < nearestInNetwork.distanceSqr()) {
            nearestInNetwork = candidate;
          }
        }
      }
      if (nearestInNetwork != null) {
        offerNearestLegacyTarget(nearest, nearestInNetwork, limit);
      }
    } catch (IOException e) {
      Constants.LOG.error("Failed to inspect legacy network file {}", file, e);
    }
  }

  private void offerNearestLegacyTarget(PriorityQueue<LegacyMigrationTarget> nearest, LegacyMigrationTarget candidate, int limit) {
    if (nearest.size() < limit) {
      nearest.offer(candidate);
      return;
    }

    LegacyMigrationTarget farthest = nearest.peek();
    if (farthest != null && candidate.distanceSqr() < farthest.distanceSqr()) {
      nearest.poll();
      nearest.offer(candidate);
    }
  }

  private CrateNetwork loadNetworkNodes(Path file, UUID id) throws IOException {
    CompoundTag nbt = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
    CrateNetwork network = new CrateNetwork(id);
    loadNodes(nbt, network);
    return network;
  }

  private double distanceSqr(BlockPos a, BlockPos b) {
    long dx = (long) a.getX() - b.getX();
    long dy = (long) a.getY() - b.getY();
    long dz = (long) a.getZ() - b.getZ();
    return (double) dx * dx + (double) dy * dy + (double) dz * dz;
  }
  /* END temporary legacy root network migration section. */

  public boolean deleteNetwork(UUID id) {
    Path file = networksDirectory.resolve(id.toString() + ".dat");
    try {
      Files.deleteIfExists(file);
      archiveLegacyFile(id);
      return true;
    } catch (IOException e) {
      Constants.LOG.error("Failed to delete network file {}", id, e);
      return false;
    }
  }

  private DynamicOps<Tag> registryOps() {
    return RegistryOps.create(NbtOps.INSTANCE, level.registryAccess());
  }

  private record LoadedNetwork(CrateNetwork network, Map<BlockPos, CrateSlotComponent> globalStorage,
                               boolean needsStorageRepair, boolean needsMetadataRewrite) {
  }

  private record ClaimResult(boolean hasUnclaimedLegacyFiles, int filesProcessed) {
  }

  public record LegacyMigrationTarget(UUID networkId, String nodeType, BlockPos pos, double distanceSqr) {
  }

  public record NetworkSnapshot(Set<UUID> networkIds, Map<String, Set<BlockPos>> nodesByType) {
    static NetworkSnapshot fromNetworks(Map<UUID, CrateNetwork> networks) {
      Map<String, Set<BlockPos>> nodesByType = new HashMap<>();
      for (CrateNetwork network : networks.values()) {
        for (Map.Entry<String, Set<BlockPos>> entry : network.nodes.entrySet()) {
          nodesByType.computeIfAbsent(entry.getKey(), k -> new HashSet<>()).addAll(entry.getValue());
        }
      }

      Map<String, Set<BlockPos>> immutableNodesByType = new HashMap<>();
      for (Map.Entry<String, Set<BlockPos>> entry : nodesByType.entrySet()) {
        immutableNodesByType.put(entry.getKey(), Set.copyOf(entry.getValue()));
      }

      return new NetworkSnapshot(Set.copyOf(networks.keySet()), immutableNodesByType);
    }

    boolean overlaps(CrateNetwork network) {
      for (Map.Entry<String, Set<BlockPos>> entry : network.nodes.entrySet()) {
        Set<BlockPos> loadedPositions = nodesByType.get(entry.getKey());
        if (loadedPositions != null && !Collections.disjoint(loadedPositions, entry.getValue())) {
          return true;
        }
      }
      return false;
    }
  }

  public record LoadResult(Map<UUID, CrateNetwork> networks, Map<BlockPos, CrateSlotComponent> globalStorage,
                           Set<UUID> networksNeedingStorageRepair, Set<UUID> networksNeedingSave,
                           Set<UUID> claimedLegacyNetworks, boolean hasUnclaimedLegacyFiles, int legacyFilesProcessed) {
  }
}
