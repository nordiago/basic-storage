package com.khazoda.basicstorage.block;

import com.khazoda.basicstorage.block.entity.CrateBlockEntity;
import com.khazoda.basicstorage.block.entity.CrateStationBlockEntity;
import com.khazoda.basicstorage.packet.StationBeamPayload;
import com.khazoda.basicstorage.registry.BlockEntityRegistry;
import com.khazoda.basicstorage.registry.BlockRegistry;
import com.khazoda.basicstorage.registry.SoundRegistry;
import com.khazoda.basicstorage.storage.CrateNetworkManager;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.gameevent.GameEvent;
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

  public static final MapCodec<CrateStationBlock> CODEC = simpleCodec(CrateStationBlock::new);
  public static final Properties defaultSettings = Properties.of().sound(SoundType.WOOD).strength(3.5f).pushReaction(PushReaction.BLOCK).instrument(NoteBlockInstrument.BASS).mapColor(MapColor.WOOD);

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
      if (!world.getBlockState(hit.getBlockPos()).is(BlockRegistry.CRATE_STATION_BLOCK)) return InteractionResult.PASS;
      if (!player.mayBuild() || player.isSpectator()) return InteractionResult.PASS;
      if (player.getItemInHand(hand).is(BlockRegistry.CRATE_BLOCK.asItem()) && player.isShiftKeyDown()) {
        return InteractionResult.PASS;
      }

      BlockPos pos = hit.getBlockPos();
      BlockState state = world.getBlockState(pos);
      BlockEntity be = world.getBlockEntity(pos);

      if (be == null) return InteractionResult.PASS;

      CrateStationBlockEntity cdbe = (CrateStationBlockEntity) be;
      ItemStack playerStack = player.getMainHandItem();
      int connectedValidCrateCount = cdbe.getConnectedValidCrates().size();
      int connectedEmptyCrateCount = cdbe.getConnectedEmptyCrates().size();
      int inserted = 0;
      List<StationBeamPayload.Target> beamTargets = new ArrayList<>();

      if (player.isShiftKeyDown()) {
        inserted = depositInventory(player, cdbe, beamTargets);
      } else if (!player.isShiftKeyDown()) {
        if (playerStack.isEmpty()) {
          if (!world.isClientSide()) {
            Component message;
            double inRate = cdbe.getInRate();
            double outRate = cdbe.getOutRate();

            if (cdbe.isClogged()) {
              message = Component.translatable("message.basicstorage.station.status.clogged").withColor(0xFF5555);
            } else if (inRate > 0 || outRate > 0) {
              /* If station is having items fed to it, display the in/out rate per second */
              message = Component.translatable("message.basicstorage.station.throughput", inRate, outRate).withColor(getThroughputTextColor(inRate));
            } else {
              /* Otherwise, show network connection details */
              message = Component.translatable("message.basicstorage.station.connected_valid_crate_count", connectedValidCrateCount).withColor(0xddff99).append(Component.literal(" | ").withColor(0xffffff)).append(Component.translatable("message.basicstorage.station.connected_empty_crate_count", connectedEmptyCrateCount).withColor(0xffefcd));
            }
            player.displayClientMessage(message, true);
          }
          return InteractionResult.PASS;
        }
        inserted = depositStack(player.getItemInHand(hand), cdbe, beamTargets);
      }

      if (!world.isClientSide()) {
        if (inserted <= 0) {
          player.displayClientMessage(Component.translatable("message.basicstorage.station.no_matching_crates").withColor(0xFF9999), true);
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

        if (!beamTargets.isEmpty()) {
          StationBeamPayload payload = new StationBeamPayload(pos, beamTargets);
          for (ServerPlayer p : PlayerLookup.tracking(cdbe)) {
            ServerPlayNetworking.send(p, payload);
          }
        }

        state.updateNeighbourShapes(world, pos, 1);
        cdbe.setChanged();
        player.awardStat(Stats.ITEM_USED.get(playerStack.getItem()));
        world.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
      }

      return InteractionResult.SUCCESS;
    });
  }

  private static int depositStack(ItemStack stack, CrateStationBlockEntity cdbe, List<StationBeamPayload.Target> beamTargets) {
    if (stack.isEmpty()) return 0;
    int totalInserted = 0;
    ItemVariant variant = ItemVariant.of(stack);
    Level world = cdbe.getLevel();
    if (world == null) return 0;

    List<BlockPos> compatibleCrates = cdbe.getCrateRegistry().get(variant);
    if (compatibleCrates != null) {
      for (BlockPos cratePos : new ArrayList<>(compatibleCrates)) {
        BlockEntity be = world.getBlockEntity(cratePos);
        if (be instanceof CrateBlockEntity crate) {
          try (Transaction transaction = Transaction.openOuter()) {
            int inserted = (int) crate.storage.insert(variant, stack.getCount(), transaction);
            if (inserted > 0) {
              stack.shrink(inserted);
              transaction.commit();
              totalInserted += inserted;
              beamTargets.add(new StationBeamPayload.Target(cratePos, inserted));
              if (stack.isEmpty()) return totalInserted;
            }
          }
        }
      }
    }

    return totalInserted;
  }

  private static int depositInventory(Player player, CrateStationBlockEntity cdbe, List<StationBeamPayload.Target> beamTargets) {
    int insertedCount = 0;
    Level world = cdbe.getLevel();
    if (world == null) return 0;

    for (int i = 0; i < player.getInventory().getNonEquipmentItems().size(); i++) {
      ItemStack stack = player.getInventory().getNonEquipmentItems().get(i);
      if (!stack.isEmpty()) {
        insertedCount += depositStack(stack, cdbe, beamTargets);
      }
    }
    return insertedCount;
  }

  @Nullable
  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
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
  public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
    super.setPlacedBy(world, pos, state, placer, itemStack);
    if (world instanceof ServerLevel serverLevel) {
      CrateNetworkManager.get(serverLevel).onBlockAdded(world, pos, state);
    }
    world.gameEvent(placer, GameEvent.BLOCK_PLACE, pos);
  }

  @Override
  protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved) {
    BlockEntity blockEntity = world.getBlockEntity(pos);
    if (blockEntity instanceof CrateStationBlockEntity station) {
      Containers.dropContents(world, pos, station);
    }
    world.updateNeighbourForOutputSignal(pos, state.getBlock());
    CrateNetworkManager.get(world).onBlockRemoved(world, pos);
    world.gameEvent(null, GameEvent.BLOCK_DESTROY, pos);
    super.affectNeighborsAfterRemoval(state, world, pos, moved);
  }

  @Override
  protected RenderShape getRenderShape(BlockState state) {
    return RenderShape.MODEL;
  }

  @Override
  public boolean hasAnalogOutputSignal(BlockState state) {
    return true;
  }

  @Override
  protected int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos, Direction direction) {
    return AbstractContainerMenu.getRedstoneSignalFromContainer((CrateStationBlockEntity) world.getBlockEntity(pos));
  }

  @Override
  public MapCodec<CrateStationBlock> codec() {
    return CODEC;
  }

  private static int getThroughputTextColor(double inRate) {
    double max = 700.0;
    double ratio = Math.min(1.0, inRate / max);

    int r1 = 0xbb, g1 = 0xcc, b1 = 0xff;
    int r2 = 0xff, g2 = 0x55, b2 = 0x55;

    int r = (int) (r1 + (r2 - r1) * ratio);
    int g = (int) (g1 + (g2 - g1) * ratio);
    int b = (int) (b1 + (b2 - b1) * ratio);

    return (r << 16) | (g << 8) | b;
  }
}
