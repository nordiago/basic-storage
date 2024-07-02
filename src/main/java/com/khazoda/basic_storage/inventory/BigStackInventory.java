package com.khazoda.basic_storage.inventory;

import com.khazoda.basic_storage.Constants;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SingleStackInventory;
import net.minecraft.item.ItemStack;

/**
 * An inventory that holds a maximum of 1,000,000 {@link ItemStack}, at slot {@code 0}.
 */
public interface BigStackInventory extends Inventory {
  public ItemStack getStack();

  default public ItemStack decreaseStack(int count) {
    return this.getStack().split(count);
  }

  public void setStack(ItemStack var1);

  @Override
  default int getMaxCountPerStack() {
    /* 1 billion items per stack*/
    return Constants.CRATE_MAX_COUNT;
  }

  default public ItemStack emptyStack() {
    return this.decreaseStack(this.getMaxCountPerStack());
  }

  @Override
  default public int size() {
    return 1;
  }

  @Override
  default public boolean isEmpty() {
    return this.getStack().isEmpty();
  }

  @Override
  default public void clear() {
    this.emptyStack();
  }

  @Override
  default public ItemStack removeStack(int slot) {
    return this.removeStack(slot, this.getMaxCountPerStack());
  }

  @Override
  default public ItemStack getStack(int slot) {
    return slot == 0 ? this.getStack() : ItemStack.EMPTY;
  }

  @Override
  default public ItemStack removeStack(int slot, int amount) {
    if (slot != 0) {
      return ItemStack.EMPTY;
    }
    return this.decreaseStack(amount);
  }

  @Override
  default public void setStack(int slot, ItemStack stack) {
    if (slot == 0) {
      this.setStack(stack);
    }
  }

  interface BigStackBlockEntityInventory
      extends BigStackInventory {

    BlockEntity asBlockEntity();

    @Override
    default boolean canPlayerUse(PlayerEntity player) {
      return Inventory.canPlayerUse(this.asBlockEntity(), player);
    }
  }
}
