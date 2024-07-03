package com.khazoda.basic_storage.block.entity;

import com.khazoda.basic_storage.inventory.BigStackInventory;
import com.khazoda.basic_storage.registry.BlockEntityRegistry;
import com.khazoda.basic_storage.registry.DataComponentRegistry;
import com.khazoda.basic_storage.structure.CrateContentsComponent;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public class CrateBlockEntity extends BlockEntity implements BigStackInventory.BigStackBlockEntityInventory {
  private ItemStack stack;
  private ItemStack stack_before_broken;

  public CrateBlockEntity(BlockPos pos, BlockState state) {
    super(BlockEntityRegistry.CRATE_BLOCK_ENTITY, pos, state);
    this.stack = ItemStack.EMPTY;
    this.stack_before_broken = ItemStack.EMPTY;
  }

  /* NBT Sync */
  @Override
  protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
    super.writeNbt(nbt, registryLookup);
    if (!this.stack.isEmpty()) {
      nbt.put("itemVariant", this.stack.encode(registryLookup));
    } else {
      removeFromCopiedStackNbt(nbt);
    }
  }

  @Override
  protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
    super.readNbt(nbt, registryLookup);
    if (nbt.contains("itemVariant", 10)) {
      this.stack = ItemStack.fromNbt(registryLookup, nbt.getCompound("itemVariant")).orElse(ItemStack.EMPTY);
    } else {
      this.stack = ItemStack.EMPTY;
    }
  }


  @Override
  public BlockEntityUpdateS2CPacket toUpdatePacket() {
    return BlockEntityUpdateS2CPacket.create(this);
  }


  @Override
  public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
    var nbt = new NbtCompound();
    writeNbt(nbt, registryLookup);
    return nbt;
  }

  public void readFrom(ItemStack stack) {
    this.readComponents(stack);
  }

  @Override
  protected void addComponents(ComponentMap.Builder componentMapBuilder) {
    componentMapBuilder.add(DataComponentRegistry.CRATE_CONTENTS, new CrateContentsComponent(ItemVariant.of(this.stack_before_broken), this.stack_before_broken.getCount()));
  }

  @Override
  protected void readComponents(BlockEntity.ComponentsAccess components) {
    CrateContentsComponent contents = components.getOrDefault(DataComponentRegistry.CRATE_CONTENTS, CrateContentsComponent.DEFAULT);
    if (contents == null) return;

    int count = contents.count();
    ItemVariant itemVariant = contents.item();
    this.stack = new ItemStack(itemVariant.getItem(), count);
  }

  public void triggerUpdate() {
    if (world instanceof ServerWorld serverWorld) {
      serverWorld.getWorldChunk(pos).setNeedsSaving(true);
      var state = getCachedState();
      serverWorld.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
    }
  }

  @Override
  public ItemStack getStack() {
    return this.stack;
  }

  @Override
  public void setStack(ItemStack stack) {
    this.stack = stack;
    triggerUpdate();
  }

  public void setStackBeforeBroken(ItemStack stack) {
    this.stack_before_broken = stack;
  }

  @Override
  public ItemStack decreaseStack(int count) {
    ItemStack itemStack = this.stack.split(count);
    if (this.stack.isEmpty()) {
      this.stack = ItemStack.EMPTY;
    }
    triggerUpdate();
    return itemStack;
  }

  @Override
  public void removeFromCopiedStackNbt(NbtCompound nbt) {
    super.removeFromCopiedStackNbt(nbt);
    nbt.remove("itemVariant");
    markDirty();
  }

  @Override
  public BlockEntity asBlockEntity() {
    return this;
  }
}
