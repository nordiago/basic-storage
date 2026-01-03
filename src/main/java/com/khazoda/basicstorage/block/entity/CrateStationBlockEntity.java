package com.khazoda.basicstorage.block.entity;

import com.khazoda.basicstorage.packet.StationBeamPayload;
import com.khazoda.basicstorage.registry.BlockEntityRegistry;
import com.khazoda.basicstorage.storage.CrateNetworkManager;
import com.khazoda.basicstorage.storage.NetworkNode;
import com.khazoda.basicstorage.structure.CrateSlotComponent;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CrateStationBlockEntity extends BlockEntity implements NetworkNode, WorldlyContainer {

  private final Map<ItemVariant, List<BlockPos>> crateRegistry = new HashMap<>();
  private final Set<ItemVariant> sortedVariants = new HashSet<>();
  private final Set<BlockPos> connectedValidCrates = new HashSet<>();
  private final Set<BlockPos> connectedEmptyCrates = new HashSet<>();
  private boolean needsCacheUpdate = true;
  private boolean hasCheckedRegistration = false;
  private boolean registeredOnServer = false;

  private final NonNullList<ItemStack> stationBuffer = NonNullList.withSize(54, ItemStack.EMPTY);
  private final long[] inThroughputBuckets = new long[30];
  private final long[] outThroughputBuckets = new long[30];
  private long lastThroughputTick = -1;
  private int lastStationBufferCount = -1;
  private int itemsDistributedInTick = 0;
  private int tickCounter = 0;
  private int currentDistributionInterval = 100;

  public CrateStationBlockEntity(BlockPos pos, BlockState state) {
    super(BlockEntityRegistry.CRATE_STATION_BLOCK_ENTITY, pos, state);
  }

  public static void tick(Level world, BlockPos pos, BlockState state, CrateStationBlockEntity be) {
    if (world instanceof ServerLevel serverLevel) {
      be.checkRegistration(serverLevel);
      be.tickDistribution();
      be.tickThroughputTracking(serverLevel);
    }
    if (be.needsCacheUpdate) {
      be.buildCrateCache();
      be.needsCacheUpdate = false;
    }
  }

  private void tickDistribution() {
    tickCounter++;
    if (tickCounter >= currentDistributionInterval) {
      tickCounter = 0;
      /*
       *  Randomize next interval between 3 and 6 seconds (60-120 ticks)
       *  fallback to 5 seconds to naturally desynchronize station distributions
       */
      currentDistributionInterval = 60 + (this.level != null ? this.level.random.nextInt(61) : 100);
      distributeBuffer();
    }
  }

  private void distributeBuffer() {
    if (level == null) return;
    boolean changed = false;

    List<StationBeamPayload.Target> beamTargets = new ArrayList<>();

    for (int i = 0; i < stationBuffer.size(); i++) {
      ItemStack stack = stationBuffer.get(i);
      if (stack.isEmpty()) continue;

      ItemVariant variant = ItemVariant.of(stack);
      List<BlockPos> compatibleCrates = crateRegistry.get(variant);

      if (compatibleCrates != null && !compatibleCrates.isEmpty()) {
        // Lazy nearest-variant sorting
        if (!sortedVariants.contains(variant)) {
          compatibleCrates.sort(Comparator.comparingDouble(worldPosition::distSqr));
          sortedVariants.add(variant);
        }

        // Insert items into compatible crates
        for (BlockPos cratePos : compatibleCrates) {
          BlockEntity be = level.getBlockEntity(cratePos);
          if (be instanceof CrateBlockEntity crate) {
            try (Transaction transaction = Transaction.openOuter()) {
              long inserted = crate.storage.insert(variant, stack.getCount(), transaction);
              if (inserted > 0) {
                stack.shrink((int) inserted);
                transaction.commit();
                changed = true;
                beamTargets.add(new StationBeamPayload.Target(cratePos, (int) inserted));
                this.itemsDistributedInTick += (int) inserted;
                if (stack.isEmpty()) break;
              }
            }
          }
        }
      } else {
        // Eject items if no compatible crates are found
        Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, stack.copy());
        stationBuffer.set(i, ItemStack.EMPTY);
        changed = true;
      }
    }

    if (!beamTargets.isEmpty()) {
      StationBeamPayload payload = new StationBeamPayload(worldPosition, beamTargets);
      /* TickDistribution() runs serverside only so this cast is safe */
      for (ServerPlayer player : PlayerLookup.tracking(this)) {
        ServerPlayNetworking.send(player, payload);
      }
    }

    if (changed) {
      setChanged();
    }
  }

  private void checkRegistration(ServerLevel level) {
    if (hasCheckedRegistration) return;

    CrateNetworkManager manager = CrateNetworkManager.get(level);
    this.registeredOnServer = manager.isRegistered(worldPosition);

    if (!this.registeredOnServer) {
      manager.onBlockAdded(level, worldPosition, false, true);
      this.registeredOnServer = true;
    }

    hasCheckedRegistration = true;
  }

  private void buildCrateCache() {
    if (level == null || level.isClientSide() || !(level instanceof ServerLevel serverLevel)) return;

    crateRegistry.clear();
    connectedValidCrates.clear();
    connectedEmptyCrates.clear();

    CrateNetworkManager manager = CrateNetworkManager.get(serverLevel);
    CrateNetworkManager.CrateNetwork network = manager.getNetworkFor(worldPosition);
    if (network == null) return;

    for (BlockPos cratePos : network.crates) {
      CrateSlotComponent contents = manager.getStorage(cratePos);
      if (contents == null || contents.item().isBlank()) {
        connectedEmptyCrates.add(cratePos);
      } else {
        connectedValidCrates.add(cratePos);
        ItemVariant variant = contents.item();
        crateRegistry.computeIfAbsent(variant, k -> new ArrayList<>()).add(cratePos);
      }
    }
    sortedVariants.clear();
    setChanged();
  }

  @Override
  public void setRemoved() {
    crateRegistry.clear();
    sortedVariants.clear();
    connectedValidCrates.clear();
    connectedEmptyCrates.clear();
    super.setRemoved();
  }

  public void markCacheForUpdate() {
    this.needsCacheUpdate = true;
    setChanged();
  }

  public Set<BlockPos> getConnectedValidCrates() {
    return connectedValidCrates;
  }

  public Set<BlockPos> getConnectedEmptyCrates() {
    return connectedEmptyCrates;
  }

  public Map<ItemVariant, List<BlockPos>> getCrateRegistry() {
    return crateRegistry;
  }

  public double getInRate() {
    long total = 0;
    for (long count : inThroughputBuckets) total += count;
    return Math.round((total / 30.0) * 10.0) / 10.0;
  }

  public double getOutRate() {
    long total = 0;
    for (long count : outThroughputBuckets) total += count;
    return Math.round((total / 30.0) * 10.0) / 10.0;
  }

  private void tickThroughputTracking(ServerLevel level) {
    long currentTick = level.getGameTime();
    updateThroughputBuckets(currentTick);

    int currentBufferCount = 0;
    for (ItemStack stack : stationBuffer) {
      currentBufferCount += stack.getCount();
    }

    if (lastStationBufferCount != -1) {
      int delta = currentBufferCount - lastStationBufferCount;
      int itemsIn = Math.max(0, delta + itemsDistributedInTick);
      if (itemsIn > 0) trackInput(itemsIn);
      if (itemsDistributedInTick > 0) trackOutput(itemsDistributedInTick);
    }

    lastStationBufferCount = currentBufferCount;
    itemsDistributedInTick = 0;
  }

  private void updateThroughputBuckets(long currentTick) {
    long currentSecond = currentTick / 20;
    if (lastThroughputTick == -1) {
      lastThroughputTick = currentTick;
      return;
    }
    long lastSecond = lastThroughputTick / 20;

    if (currentSecond != lastSecond) {
      // Clear all buckets between last session and now (cap at 30)
      for (long s = lastSecond + 1; s <= currentSecond; s++) {
        int idx = (int) (s % 30);
        inThroughputBuckets[idx] = 0;
        outThroughputBuckets[idx] = 0;
      }
      lastThroughputTick = currentTick;
    }
  }

  private void trackInput(int amount) {
    if (this.level == null || amount <= 0) return;
    updateThroughputBuckets(this.level.getGameTime());
    int currentBucket = (int) ((this.level.getGameTime() / 20) % 30);
    inThroughputBuckets[currentBucket] += amount;
  }

  private void trackOutput(int amount) {
    if (this.level == null || amount <= 0) return;
    updateThroughputBuckets(this.level.getGameTime());
    int currentBucket = (int) ((this.level.getGameTime() / 20) % 30);
    outThroughputBuckets[currentBucket] += amount;
  }

  @Override
  protected void saveAdditional(ValueOutput view) {
    super.saveAdditional(view);
    view.store("registered", Codec.BOOL, this.registeredOnServer);
    List<ItemStack> nonEmptyItems = new ArrayList<>();
    for (ItemStack stack : this.stationBuffer) {
      if (!stack.isEmpty()) {
        nonEmptyItems.add(stack);
      }
    }
    view.store("items", Codec.list(ItemStack.CODEC), nonEmptyItems);
  }

  @Override
  protected void loadAdditional(ValueInput view) {
    super.loadAdditional(view);
    view.read("registered", Codec.BOOL).ifPresent(v -> this.registeredOnServer = v);
    view.read("items", Codec.list(ItemStack.CODEC)).ifPresent(list -> {
      this.stationBuffer.clear();
    });
  }

  @Override
  public int getContainerSize() {
    return stationBuffer.size();
  }

  @Override
  public boolean isEmpty() {
    for (ItemStack stack : stationBuffer) {
      if (!stack.isEmpty()) return false;
    }
    return true;
  }

  @Override
  public ItemStack getItem(int slot) {
    return stationBuffer.get(slot);
  }

  @Override
  public ItemStack removeItem(int slot, int amount) {
    return ContainerHelper.removeItem(stationBuffer, slot, amount);
  }

  @Override
  public ItemStack removeItemNoUpdate(int slot) {
    return ContainerHelper.takeItem(stationBuffer, slot);
  }

  @Override
  public void setItem(int slot, ItemStack stack) {
    stationBuffer.set(slot, stack);
    if (stack.getCount() > getMaxStackSize()) {
      stack.setCount(getMaxStackSize());
    }
    setChanged();
  }

  @Override
  public boolean stillValid(Player player) {
    return Container.stillValidBlockEntity(this, player);
  }

  @Override
  public void clearContent() {
    stationBuffer.clear();
  }

  @Override
  public int[] getSlotsForFace(Direction side) {
    int[] slots = new int[getContainerSize()];
    for (int i = 0; i < slots.length; i++) slots[i] = i;
    return slots;
  }

  @Override
  public boolean canPlaceItemThroughFace(int index, ItemStack itemStack, @Nullable Direction direction) {
    return true;
  }

  @Override
  public boolean canTakeItemThroughFace(int index, ItemStack itemStack, Direction direction) {
    return false;
  }


  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    if (this.level instanceof ServerLevel serverLevel) {
      checkRegistration(serverLevel);
    }
    CompoundTag nbt = this.saveCustomOnly(registries);
    nbt.putBoolean("registered", this.registeredOnServer);
    return nbt;
  }

  @Override
  public ClientboundBlockEntityDataPacket getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }
}
