package com.khazoda.basicstorage.block;

import com.khazoda.basicstorage.block.entity.CrateBlockEntity;
import com.khazoda.basicstorage.block.entity.CrateStationBlockEntity;
import com.khazoda.basicstorage.registry.BlockEntityRegistry;
import com.khazoda.basicstorage.registry.BlockRegistry;
import com.khazoda.basicstorage.registry.SoundRegistry;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Right Click
 * > holding stack - Search for nearest crate containing stack item type and
 * deposit stack into it
 * > no valid crate found? - notify user
 * > holding nothing - Display number of connected crates
 * Shift Right Click - Add all items from inventory to crates that match the
 * items
 * > no valid crate found? - notify user
 * Left Click - Nothing
 * Shift Left Click - Nothing
 */
public class CrateStationBlock extends BaseEntityBlock {

  public static final MapCodec<CrateStationBlock> CODEC = CrateStationBlock.simpleCodec(CrateStationBlock::new);
  public static final Properties defaultSettings = Properties.of().sound(SoundType.WOOD).strength(3.5f)
      .pushReaction(PushReaction.BLOCK).instrument(NoteBlockInstrument.BASS).mapColor(MapColor.WOOD);

  public CrateStationBlock(Properties settings) {
    super(settings);
  }

  public CrateStationBlock() {
    this(defaultSettings);
  }

  /**
   * Event hook instead of onUse() method in order to capture interactions while
   * sneaking
   */
  public static void initOnUseMethod() {
    UseBlockCallback.EVENT.register((Player player, Level world, InteractionHand hand, BlockHitResult hit) -> {
      if (!world.getBlockState(hit.getBlockPos()).is(BlockRegistry.CRATE_STATION_BLOCK))
        return InteractionResult.PASS;
      if (!player.mayBuild() || player.isSpectator())
        return InteractionResult.PASS;
      if (player.getItemInHand(hand).is(BlockRegistry.CRATE_BLOCK.asItem()) && player.isShiftKeyDown()) {
        return InteractionResult.PASS;
      }

      BlockPos pos = hit.getBlockPos();
      BlockState state = world.getBlockState(pos);
      BlockEntity be = world.getBlockEntity(pos);

      if (be == null)
        return InteractionResult.PASS;

      CrateStationBlockEntity cdbe = (CrateStationBlockEntity) be;
      ItemStack playerStack = player.getMainHandItem();
      int connectedCrateCount = cdbe.getConnectedCrates().size();
      int inserted = 0;

      if (player.isShiftKeyDown()) {
        inserted = depositInventory(player, cdbe);
      } else if (!player.isShiftKeyDown()) {
        if (playerStack.isEmpty()) {
          if (!world.isClientSide())
            player.displayClientMessage(
                Component.translatable("message.basicstorage.station.connected_crate_count", connectedCrateCount)
                    .withColor(0xDDFF99),
                true);
          return InteractionResult.PASS;
        }
        inserted = depositStack(player.getItemInHand(hand), cdbe);
      }

      if (!world.isClientSide()) {
        if (inserted <= 0) {
          player.displayClientMessage(Component.translatable("message.basicstorage.station.no_matching_crates").withColor(0xFF9999),
              true);
          world.playSound(null, pos, SoundRegistry.NO_MATCH, SoundSource.BLOCKS, 1.1f, 1f);
          return InteractionResult.CONSUME;
        }

        if (inserted == 1) {
          world.playSound(null, pos, SoundRegistry.INSERT_ONE, SoundSource.BLOCKS, 1f, 1.05f);
        } else if (inserted <= 64) {
          world.playSound(null, pos, SoundRegistry.INSERT_MANY, SoundSource.BLOCKS, 1f, 1.05f);
        } else {
          world.playSound(null, pos, SoundRegistry.INSERT_LOADS, SoundSource.BLOCKS, 1f, 1.05f);
        }

        state.updateNeighbourShapes(world, pos, 1);
        cdbe.setChanged();
        player.awardStat(Stats.ITEM_USED.get(playerStack.getItem()));
        world.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
      }

      return InteractionResult.SUCCESS;
    });
  }

  private static int depositStack(ItemStack stack, CrateStationBlockEntity cdbe) {
    int inserted = 0;
    if (stack.isEmpty())
      return 0;

    ItemVariant variant = ItemVariant.of(stack);
    List<BlockPos> compatibleCrates = cdbe.getCrateRegistry().get(variant);
    if (compatibleCrates == null)
      return 0;
    Level world = cdbe.getLevel();

    for (BlockPos cratePos : new ArrayList<>(compatibleCrates)) {
      if (world == null)
        return 0; // todo: if something goes wrong, remove this and see if things work lol
      BlockEntity be = world.getBlockEntity(cratePos);
      if (!(be instanceof CrateBlockEntity crate)) {
        // compatibleCrates.remove(cratePos); //TODO: Maybe Remove?
        continue;
      }

      try (Transaction transaction = Transaction.openOuter()) {
        inserted = (int) crate.storage.insert(variant, stack.getCount(), transaction);
        if (inserted > 0) {
          stack.shrink(inserted);
          transaction.commit();
          return inserted;
        }
      }
    }
    return inserted;
  }

  private static int depositInventory(Player player, CrateStationBlockEntity cdbe) {
    int inserted = 0;
    Level world = cdbe.getLevel();

    for (int i = 0; i < player.getInventory().getNonEquipmentItems().size(); i++) {
      ItemStack stack = player.getInventory().getNonEquipmentItems().get(i);
      if (!stack.isEmpty()) {
        ItemVariant variant = ItemVariant.of(stack);
        List<BlockPos> compatibleCrates = cdbe.getCrateRegistry().get(variant);

        if (compatibleCrates != null) {
          for (BlockPos cratePos : compatibleCrates) {
            if (world == null)
              return 0;
            BlockEntity be = world.getBlockEntity(cratePos);
            if (!(be instanceof CrateBlockEntity crate))
              continue;

            try (Transaction transaction = Transaction.openOuter()) {
              inserted += (int) crate.storage.insert(variant, stack.getCount(), transaction);
              if (inserted > 0) {
                stack.shrink(inserted);
                transaction.commit();
                break;
              }
            }
          }
        }
      }
    }
    return inserted;
  }

  @Nullable
  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state,
                                                                BlockEntityType<T> type) {
    return createTickerHelper(type, BlockEntityRegistry.CRATE_STATION_BLOCK_ENTITY, CrateStationBlockEntity::tick);
  }

  @Nullable
  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new CrateStationBlockEntity(pos, state);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext ctx) {
    return this.defaultBlockState();
  }

  @Override
  protected RenderShape getRenderShape(BlockState state) {
    return RenderShape.MODEL;
  }

  @Override
  public MapCodec<CrateStationBlock> codec() {
    return CODEC;
  }
}
