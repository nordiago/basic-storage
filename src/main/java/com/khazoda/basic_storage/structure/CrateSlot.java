package com.khazoda.basic_storage.structure;

import com.khazoda.basic_storage.block.entity.CrateBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.item.Item;

public class CrateSlot implements SingleSlotStorage<ItemVariant> {
  private final CrateBlockEntity be;
  private final Item.Settings settings;
  private ItemVariant item = ItemVariant.blank();
  private long count;


  public CrateSlot(CrateBlockEntity be) {
    this.be = be;
    settings = new Item.Settings();
  }

  public void read(CrateSlotComponent component) {
    item = component.item();
    count = component.count();
    if (item.isBlank()) count = 0;
  }

  public CrateSlotComponent write() {
    return new CrateSlotComponent(
        item,
        count
    );
  }

  public CrateBlockEntity getBlockEntity() {
    return be;
  }

  @Override
  public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
    if (!resource.equals(item) && !item.isBlank()) return 0;
    var inserted = Math.min(getCapacity() - count, maxAmount);
    if (inserted > 0) {
//      updateSnapshots(transaction);
      count += inserted;
      if (item.isBlank()) {
        item = resource;
      }
    } else if (inserted < 0) {
      return 0;
    }
    // In voiding mode we return the max even if it doesn't fit. We just delete it this way
    return inserted;
  }

  @Override
  public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
    return 0;
  }

  @Override
  public boolean isResourceBlank() {
    return item.isBlank();
  }

  @Override
  public ItemVariant getResource() {
    return item;
  }

  @Override
  public long getAmount() {
    return count;
  }

  @Override
  public long getCapacity() {
    return Integer.MAX_VALUE;
  }
}
