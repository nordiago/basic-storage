package com.khazoda.basicstorage.storage;

import com.khazoda.basicstorage.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Manages async network rebuilds to prevent TPS lag during network splits due to BFS time complexity.
 */
public class AsyncNetworkRebuilder {
  private final ExecutorService executor;
  private final Map<UUID, Future<?>> pendingRebuilds = new ConcurrentHashMap<>();
  private final Map<UUID, Integer> rebuildVersions = new ConcurrentHashMap<>();

  public AsyncNetworkRebuilder() {
    this.executor = Executors.newFixedThreadPool(2, r -> {
      Thread t = new Thread(r, "BasicStorage-NetworkRebuilder");
      t.setDaemon(true);
      return t;
    });
  }

  /**
   * Submit a rebuild task - Cancels any pending rebuild for the same network.
   */
  public void submitRebuild(ServerLevel level, UUID oldNetworkId, CrateNetwork oldNetwork, BlockPos removedPos, Consumer<RebuildResult> callback) {
    /* Cancel existing rebuild for this network */
    Future<?> existing = pendingRebuilds.get(oldNetworkId);
    if (existing != null && !existing.isDone()) {
      existing.cancel(true);
      Constants.LOG.debug("Cancelled stale rebuild for network {}", oldNetworkId);
    }

    /* Increment version to invalidate old results */
    int version = rebuildVersions.compute(oldNetworkId, (k, v) -> v == null ? 1 : v + 1);

    /* Create snapshot of network state */
    Map<String, Set<BlockPos>> snapshot = oldNetwork.createSnapshot();

    /* Submit async task */
    Future<?> future = executor.submit(() -> {
      try {
        RebuildResult result = performRebuild(oldNetworkId, snapshot, removedPos, version);

        /* Apply results on main thread */
        level.getServer().execute(() -> {
          Integer currentVersion = rebuildVersions.get(oldNetworkId);
          if (currentVersion != null && currentVersion == version) {
            callback.accept(result);
            pendingRebuilds.remove(oldNetworkId);
            rebuildVersions.remove(oldNetworkId);
          } else {
            Constants.LOG.debug("Discarded stale rebuild result for network {}", oldNetworkId);
          }
        });
      } catch (Exception e) {
        Constants.LOG.error("Error during network rebuild", e);
      }
    });

    pendingRebuilds.put(oldNetworkId, future);
  }

  /**
   * Perform BFS traversal on snapshot to find connected components.
   */
  private RebuildResult performRebuild(UUID oldNetworkId, Map<String, Set<BlockPos>> snapshot, BlockPos removedPos, int version) {
    List<Map<String, Set<BlockPos>>> newNetworks = new ArrayList<>();

    Set<BlockPos> remainingBlocks = new HashSet<>();
    for (Set<BlockPos> positions : snapshot.values()) {
      remainingBlocks.addAll(positions);
    }

    while (!remainingBlocks.isEmpty()) {
      if (Thread.currentThread().isInterrupted()) {
        throw new RuntimeException(new InterruptedException());
      }

      BlockPos root = remainingBlocks.iterator().next();
      Map<String, Set<BlockPos>> newNetwork = new ConcurrentHashMap<>();

      Queue<BlockPos> todo = new LinkedList<>();
      todo.add(root);
      remainingBlocks.remove(root);

      while (!todo.isEmpty()) {
        if (Thread.currentThread().isInterrupted()) {
          throw new RuntimeException(new InterruptedException());
        }

        BlockPos current = todo.poll();

        for (Map.Entry<String, Set<BlockPos>> entry : snapshot.entrySet()) {
          if (entry.getValue().contains(current)) {
            newNetwork.computeIfAbsent(entry.getKey(), k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(current);
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

      newNetworks.add(newNetwork);
    }

    return new RebuildResult(oldNetworkId, newNetworks, version);
  }

  /**
   * Shutdown the executor service on world unloading.
   */
  public void shutdown() {
    for (Future<?> future : pendingRebuilds.values()) {
      future.cancel(true);
    }
    pendingRebuilds.clear();
    rebuildVersions.clear();

    executor.shutdownNow();
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        Constants.LOG.warn("AsyncNetworkRebuilder did not terminate cleanly");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public record RebuildResult(UUID oldNetworkId, List<Map<String, Set<BlockPos>>> newNetworkNodes, int version) {
  }
}
