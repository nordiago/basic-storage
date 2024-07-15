package com.khazoda.basic_storage.block.entity;

import com.khazoda.basic_storage.Constants;
import com.khazoda.basic_storage.registry.BlockEntityRegistry;
import com.khazoda.basic_storage.registry.DataComponentRegistry;
import com.khazoda.basic_storage.storage.CrateSlot;
import com.khazoda.basic_storage.structure.CrateSlotComponent;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public class CrateBlockEntity extends BlockEntity {
  public final CrateSlot storage = new CrateSlot(this);

  /**
   * Constructor
   **/
  public CrateBlockEntity(BlockPos pos, BlockState state) {
    super(BlockEntityRegistry.CRATE_BLOCK_ENTITY, pos, state);
  }

  /**
   * modified markDirty() method
   */
  public void refresh() {
    if (world instanceof ServerWorld) {
      world.getWorldChunk(pos).setNeedsSaving(true);
      var state = getCachedState();
      world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
      world.updateComparators(pos, state.getBlock());
    }
  }

  /**
   * NBT Operations
   **/
  @Override
  protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
    var storageNbt = new NbtCompound();
    storage.writeNbt(storageNbt, registryLookup);
    nbt.put("crateStack", storageNbt);

  }


  @Override
  protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
    super.readNbt(nbt, registryLookup);
    if (nbt.contains("crateStack", 10)) {
      storage.readNbt(nbt.getCompound("crateStack"), registryLookup);
    }
  }

  /**
   * Block Entity Boilerplate
   */
  @Override
  public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
    var nbt = new NbtCompound();
    writeNbt(nbt, registryLookup);
    return nbt;
  }

  @Override
  public BlockEntityUpdateS2CPacket toUpdatePacket() {
    return BlockEntityUpdateS2CPacket.create(this);
  }

  /**
   * Data to save and read from ItemStack versions of crate
   */
  @Override
  protected void addComponents(ComponentMap.Builder componentMapBuilder) {
    if (this.storage.isBlank()) return;
    componentMapBuilder.add(DataComponentRegistry.CRATE_CONTENTS,
        new CrateSlotComponent(
            this.storage.getResource(),
            (int) this.storage.getAmount()));
  }

  @Override
  protected void readComponents(BlockEntity.ComponentsAccess components) {
    CrateSlotComponent contents = components.getOrDefault(DataComponentRegistry.CRATE_CONTENTS, CrateSlotComponent.DEFAULT);
    if (contents == null) return;
    try (Transaction t = Transaction.openOuter()) {
      this.storage.insert(contents.item(), contents.count(), t);
      t.commit();
    }
    this.markDirty();
  }

}
