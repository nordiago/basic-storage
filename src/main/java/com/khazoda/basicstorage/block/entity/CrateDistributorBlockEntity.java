package com.khazoda.basicstorage.block.entity;

import com.khazoda.basicstorage.registry.BlockEntityRegistry;
import com.khazoda.basicstorage.storage.CrateSlot;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.PlayerInventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.*;

public class CrateDistributorBlockEntity extends BlockEntity {
  private final Map<ItemVariant, List<BlockPos>> crateRegistry = new HashMap<>();
  private final Set<BlockPos> connectedCrates = new HashSet<>();
  public static final int MAX_RADIUS = 16;
  private boolean needsCacheUpdate = true;

  public CrateDistributorBlockEntity(BlockPos pos, BlockState state) {
    super(BlockEntityRegistry.CRATE_DISTRIBUTOR_BLOCK_ENTITY, pos, state);
  }

  public static void tick(World world, BlockPos pos, BlockState state, CrateDistributorBlockEntity be) {
    if (be.needsCacheUpdate) {
      be.buildCrateCache();
      be.needsCacheUpdate = false;
    }
  }

  private void buildCrateCache() {
    if (world == null || world.isClient)
      return;

    crateRegistry.clear();
    connectedCrates.clear();

    Queue<BlockPos> toExplore = new LinkedList<>();
    Set<BlockPos> visited = new HashSet<>();
    toExplore.add(pos);

    while (!toExplore.isEmpty()) {
      BlockPos current = toExplore.poll();
      if (visited.contains(current) || !isWithinRange(current))
        continue;

      visited.add(current);
      BlockEntity be = world.getBlockEntity(current);
      if (be instanceof CrateDistributorBlockEntity)
        addDirectionsToExplore(toExplore, current);
      if (be instanceof CrateBlockEntity crate) {
        registerCrate(current, crate.storage);
        addDirectionsToExplore(toExplore, current);
      }
    }
//    world.getPlayers().getFirst().sendMessage(Text.literal("Updated cache. New crate number: ".concat(String.valueOf(connectedCrates.size())))); Todo: Uncomment to debug crate connections
    markDirty();
  }

  private void addDirectionsToExplore(Queue<BlockPos> blockPositionExplorationQueue, BlockPos currentBlockPosition) {
    for (Direction dir : Direction.values()) {
      blockPositionExplorationQueue.add(currentBlockPosition.offset(dir));
    }
  }

  private void registerCrate(BlockPos cratePos, CrateSlot storage) {
    if (!storage.isBlank()) {
      ItemVariant variant = storage.getResource();
      crateRegistry.computeIfAbsent(variant, k -> new ArrayList<>()).add(cratePos);
      connectedCrates.add(cratePos);
    }
  }

  private boolean isWithinRange(BlockPos target) {
    return Math.abs(target.getX() - pos.getX()) <= MAX_RADIUS &&
        Math.abs(target.getY() - pos.getY()) <= MAX_RADIUS &&
        Math.abs(target.getZ() - pos.getZ()) <= MAX_RADIUS;
  }

  public boolean handleInteraction(PlayerEntity player, Hand hand) {
    if (world == null || world.isClient)
      return false;

    ItemStack heldStack = player.getStackInHand(hand);

    if (player.isSneaking()) {
      return depositInventory(player);
    } else {
      return depositStack(heldStack);
    }
  }

  private boolean depositStack(ItemStack stack) {
    if (stack.isEmpty())
      return false;

    ItemVariant variant = ItemVariant.of(stack);
    List<BlockPos> compatibleCrates = crateRegistry.get(variant);
    if (compatibleCrates == null)
      return false;

    for (BlockPos cratePos : new ArrayList<>(compatibleCrates)) {
      if (world == null)
        return false; // todo: if something goes wrong, remove this and see if things work lol
      BlockEntity be = world.getBlockEntity(cratePos);
      if (!(be instanceof CrateBlockEntity crate)) {
        compatibleCrates.remove(cratePos);
        continue;
      }

      try (Transaction transaction = Transaction.openOuter()) {
        long inserted = crate.storage.insert(variant, stack.getCount(), transaction);
        if (inserted > 0) {
          stack.decrement((int) inserted);
          transaction.commit();
          return true;
        }
      }
    }
    return false;
  }

  private boolean depositInventory(PlayerEntity player) {
    boolean depositedAny = false;
    PlayerInventoryStorage invStorage = PlayerInventoryStorage.of(player);

    for (int i = 0; i < player.getInventory().main.size(); i++) {
      ItemStack stack = player.getInventory().main.get(i);
      if (!stack.isEmpty()) {
        ItemVariant variant = ItemVariant.of(stack);
        List<BlockPos> compatibleCrates = crateRegistry.get(variant);

        if (compatibleCrates != null) {
          for (BlockPos cratePos : compatibleCrates) {
            BlockEntity be = world.getBlockEntity(cratePos);
            if (!(be instanceof CrateBlockEntity crate))
              continue;

            try (Transaction transaction = Transaction.openOuter()) {
              long inserted = crate.storage.insert(variant, stack.getCount(), transaction);
              if (inserted > 0) {
                stack.decrement((int) inserted);
                transaction.commit();
                depositedAny = true;
                break;
              }
            }
          }
        }
      }
    }

    return depositedAny;
  }

  public void markCacheForUpdate() {
    this.needsCacheUpdate = true;
    markDirty();
  }

  public Set<BlockPos> getConnectedCrates() {
    return connectedCrates;
  }
}
