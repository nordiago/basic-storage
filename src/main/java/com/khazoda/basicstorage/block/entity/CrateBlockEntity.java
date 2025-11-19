package com.khazoda.basicstorage.block.entity;

import com.khazoda.basicstorage.block.CrateBlock;
import com.khazoda.basicstorage.registry.BlockEntityRegistry;
import com.khazoda.basicstorage.registry.DataComponentRegistry;
import com.khazoda.basicstorage.storage.CrateSlot;
import com.khazoda.basicstorage.structure.CrateSlotComponent;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
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
import net.minecraft.util.HeldItemContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class CrateBlockEntity extends BlockEntity implements HeldItemContext {
  public final CrateSlot storage = new CrateSlot(this);
  public static final Codec<CrateSlotComponent> SLOT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
      ItemVariant.CODEC.fieldOf("item").orElse(ItemVariant.blank()).forGetter(CrateSlotComponent::item),
      Codec.INT.fieldOf("count").orElse(0).forGetter(CrateSlotComponent::count)
  ).apply(instance, CrateSlotComponent::new));

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
   * WriteView creates the RegistryOps internally,
   * allowing Enchantments and other registry-dependent data to be saved correctly.
   */
  @Override
  protected void writeData(WriteView view) {
    super.writeData(view);
    CrateSlotComponent component = storage.toComponent();
    view.put("crateStack", SLOT_CODEC, component);
  }

  @Override
  protected void readData(ReadView view) {
    super.readData(view);
    view.read("crateStack", SLOT_CODEC).ifPresent(storage::readComponent);
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

  public World getEntityWorld() { return this.world; }
  public Vec3d getEntityPos() { return this.getPos().toCenterPos(); }
  public float getBodyYaw() { return this.getCachedState().get(CrateBlock.FACING).getOpposite().getPositiveHorizontalDegrees(); }
}