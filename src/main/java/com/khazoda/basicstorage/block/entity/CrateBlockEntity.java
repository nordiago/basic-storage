package com.khazoda.basicstorage.block.entity;

import com.khazoda.basicstorage.registry.BlockEntityRegistry;
import com.khazoda.basicstorage.registry.DataComponentRegistry;
import com.khazoda.basicstorage.storage.CrateSlot;
import com.khazoda.basicstorage.structure.CrateSlotComponent;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.ComponentsAccess;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
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
      world.getWorldChunk(pos).markNeedsSaving();
      var state = getCachedState();
      world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
      world.updateComparators(pos, state.getBlock());
    }
  }

  /**
   * NBT Operations
   **/
  @Override
  protected void writeData(WriteView view) {
    super.writeData(view);
    var storageNbt = new NbtCompound();
    storage.writeNbt(storageNbt);
    view.put("crateStack", NbtCompound.CODEC, storageNbt);
  }

  @Override
  protected void readData(ReadView view) {
    super.readData(view);
    storage.readNbt(view.read("crateStack", NbtCompound.CODEC));
  }

  /**
   * Block Entity Boilerplate
   */
  @Override
  public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
    return this.createComponentlessNbt(registries);
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
    if (this.storage.isBlank())
      return;
    componentMapBuilder
        .add(DataComponentRegistry.CRATE_CONTENTS,
            new CrateSlotComponent(
                this.storage.getResource(),
                (int) this.storage.getAmount()));
  }

  @Override
  protected void readComponents(ComponentsAccess components) {
    CrateSlotComponent contents = components.getOrDefault(DataComponentRegistry.CRATE_CONTENTS,
        CrateSlotComponent.DEFAULT);
    if (contents == null || contents.count() == 0)
      return;
    try (Transaction t = Transaction.openOuter()) {
      if (!this.storage.isBlank())
        return; // Prevents creative block pick from duping items
      this.storage.insert(contents.item(), contents.count(), t);
      t.commit();
    }
    this.refresh();
  }
}
