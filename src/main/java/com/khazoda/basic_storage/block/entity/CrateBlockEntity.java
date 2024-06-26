package com.khazoda.basic_storage.block.entity;

import com.khazoda.basic_storage.inventory.BigStackInventory;
import com.khazoda.basic_storage.registry.BlockEntityRegistry;
import com.khazoda.basic_storage.registry.BlockRegistry;
import com.khazoda.basic_storage.structure.CrateContentsComponent;
import com.khazoda.basic_storage.structure.CrateSlot;
import com.khazoda.basic_storage.structure.CrateSlotComponent;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.Sherds;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SingleStackInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class CrateBlockEntity extends BlockEntity implements BigStackInventory.BigStackBlockEntityInventory {
  private ItemStack stack;


  public CrateBlockEntity(BlockPos pos, BlockState state) {
    super(BlockEntityRegistry.CRATE_BLOCK_ENTITY, pos, state);
    this.stack = ItemStack.EMPTY;

  }

  /* NBT Sync */
  @Override
  protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
    super.writeNbt(nbt, registryLookup);
    if (!this.stack.isEmpty()) {
      nbt.put("item", this.stack.encode(registryLookup));
    }

  }

  @Override
  protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
    super.readNbt(nbt, registryLookup);
    if (nbt.contains("item", 10)) {
      this.stack = (ItemStack) ItemStack.fromNbt(registryLookup, nbt.getCompound("item")).orElse(ItemStack.EMPTY);
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
    return this.createComponentlessNbt(registryLookup);
  }

  public void readFrom(ItemStack stack) {
    this.readComponents(stack);
  }

  @Override
  protected void addComponents(ComponentMap.Builder componentMapBuilder) {
    super.addComponents(componentMapBuilder);
    componentMapBuilder.add(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(List.of(this.stack)));
  }

  @Override
  protected void readComponents(BlockEntity.ComponentsAccess components) {
    super.readComponents(components);
    this.stack = ((ContainerComponent) components.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT)).copyFirstStack();
  }

  @Override
  public ItemStack getStack() {
    return this.stack;
  }

  @Override
  public void setStack(ItemStack stack) {
    this.stack = stack;
  }

  @Override
  public ItemStack decreaseStack(int count) {
    ItemStack itemStack = this.stack.split(count);
    if (this.stack.isEmpty()) {
      this.stack = ItemStack.EMPTY;
    }

    return itemStack;
  }

  @Override
  public void removeFromCopiedStackNbt(NbtCompound nbt) {
    super.removeFromCopiedStackNbt(nbt);
    nbt.remove("item");
  }

  @Override
  public BlockEntity asBlockEntity() {
    return null;
  }
}
