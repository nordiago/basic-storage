package com.khazoda.basic_storage.storage;

import com.khazoda.basic_storage.block.entity.CrateBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

import java.util.Iterator;

public interface CrateStorage extends Storage<ItemVariant>{
  CrateBlockEntity getOwner();

  boolean isBlank();

  long getCapacity();

  default void update() {
    getOwner().refresh();
  }
}
