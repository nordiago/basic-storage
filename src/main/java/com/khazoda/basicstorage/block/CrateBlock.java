package com.khazoda.basicstorage.block;

import com.khazoda.basicstorage.BasicStorageConfig;
import com.khazoda.basicstorage.Constants;
import com.khazoda.basicstorage.block.entity.CrateBlockEntity;
import com.khazoda.basicstorage.registry.BlockRegistry;
import com.khazoda.basicstorage.registry.DataComponentRegistry;
import com.khazoda.basicstorage.registry.SoundRegistry;
import com.khazoda.basicstorage.storage.CrateNetworkDebug;
import com.khazoda.basicstorage.storage.CrateNetworkManager;
import com.khazoda.basicstorage.storage.CrateSlot;
import com.khazoda.basicstorage.util.BlockUtils;
import com.khazoda.basicstorage.util.NumberFormatter;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.PlayerInventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

import static java.lang.Math.toIntExact;

/**
 * Right Click
 * > holding valid stack - Add one item
 * > holding invalid stack / nothing - Display exact crate contents
 * Shift Right Click - Add all items from inventory that match
 * Left Click - Remove one item
 * Shift Left Click - Remove one stack
 */
public class CrateBlock extends BaseEntityBlock {

  public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
  public static final EnumProperty<FrontAndTop> ORIENTATION = BlockStateProperties.ORIENTATION;
  public static final Properties defaultSettings = getCrateSettings();
  private static Random random;
  public static final MapCodec<CrateBlock> CODEC = simpleCodec(CrateBlock::new);

  public CrateBlock(Properties settings) {
    super(settings);
    random = new Random();
    registerDefaultState(this.stateDefinition.any().setValue(ORIENTATION, FrontAndTop.NORTH_UP).setValue(FACING, Direction.NORTH));
  }

  public CrateBlock() {
    this(defaultSettings);
  }

  private static Properties getCrateSettings() {
    return Properties.of().sound(SoundType.WOOD).pushReaction(PushReaction.BLOCK).instrument(NoteBlockInstrument.BASS).mapColor(MapColor.WOOD).strength(1f);
  }

  /**
   * Event hook instead of onUse() method in order to capture interactions while
   * sneaking
   */
  public static void initOnUseMethod() {
    /*
     * Method is fired on every block right click, so immediate check for crate
     * block class is needed
     */
    UseBlockCallback.EVENT.register((Player player, Level world, InteractionHand hand, BlockHitResult hit) -> {
      if (!world.getBlockState(hit.getBlockPos()).is(BlockRegistry.CRATE_BLOCK)) return InteractionResult.PASS;
      if (!player.mayBuild() || player.isSpectator()) return InteractionResult.PASS;

      BlockPos pos = hit.getBlockPos();
      BlockState state = world.getBlockState(pos);

      if (CrateNetworkDebug.debugStickUsed(world, player, pos)) return InteractionResult.SUCCESS_SERVER;

      /* START Temporary crate orientation migration trigger.
       * Remove after legacy crates have had a chance to rewrite ORIENTATION from FACING. */
      if (!world.isClientSide()) {
        fixLegacyState(state, world, pos);
        // Refresh the state variable to ensure method uses corrected data
        state = world.getBlockState(pos);
      }
      /* END temporary crate orientation migration trigger. */

      BlockEntity be = world.getBlockEntity(pos);
      Direction facing = state.getValue(BlockStateProperties.ORIENTATION).front();

      if (be == null) return InteractionResult.PASS;
      if (facing != hit.getDirection()) return InteractionResult.PASS;

      CrateBlockEntity cbe = (CrateBlockEntity) be;
      CrateSlot slot = cbe.storage;
      ItemStack playerStack = player.getMainHandItem();

      try (var t = Transaction.openOuter()) {
        int inserted = 0;
        if (player.isShiftKeyDown()) {
          if (!canInsert(playerStack, slot, true)) return listExactContents(player, slot);
          inserted = insertMaximum(player, playerStack, slot, t);
        } else if (!player.isShiftKeyDown()) {
          if (!canInsert(playerStack, slot, false)) return listExactContents(player, slot);
          inserted = insertOne(playerStack, slot, t);
        }

        if (inserted == 0) {
          t.abort();
          return InteractionResult.CONSUME;
        }

        t.commit();
        if (inserted == 1)
          world.playSound(null, pos, SoundRegistry.INSERT_ONE, SoundSource.BLOCKS, 1f, 1f + ((-0.5f + random.nextFloat() * (1 + 0.5f)) / 10));
        if (inserted > 1) world.playSound(null, pos, SoundRegistry.INSERT_MANY, SoundSource.BLOCKS, 1f, 1f);
        state.updateNeighbourShapes(world, pos, 1);
        world.updateNeighbourForOutputSignal(pos, state.getBlock());
        player.awardStat(Stats.ITEM_USED.get(playerStack.getItem()));
        world.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        return InteractionResult.SUCCESS;
      }
    });
  }

  /**
   * UseBlockCallback helper method
   **/
  private static int insertOne(ItemStack playerStack, CrateSlot slot, Transaction t) {
    /* Insert one item into crate, if matching player's active held stack */
    if (playerStack.isEmpty()) return 0;
    int inserted = (int) slot.insert(ItemVariant.of(playerStack), 1, t);
    playerStack.shrink(inserted);
    return inserted;
  }

  /**
   * UseBlockCallback helper method
   **/
  private static int insertMaximum(Player player, ItemStack playerStack, CrateSlot slot, Transaction transaction) {
    /*
     * Insert as many items as possible from player's inventory if slot is empty, or
     * matches held stack
     */
    if (slot.isBlank() && playerStack.isEmpty()) {
      return 0;
    } else if (slot.isBlank() && !playerStack.isEmpty()) {
      /* Insert into empty crate */
      int i = (int) slot.insert(ItemVariant.of(playerStack), playerStack.getCount(), transaction);
      playerStack.shrink(i);
      return i;
    } else {
      /* Insert into crate with items */
      return (int) StorageUtil.move(PlayerInventoryStorage.of(player), slot, itemVariant -> true, Integer.MAX_VALUE, transaction);
    }
  }

  /**
   * UseBlockCallback helper method
   **/
  private static InteractionResult listExactContents(Player player, CrateSlot slot) {
    /* Show exact contents of crate to play via message */
    Component message;
    if (slot.isBlank()) {
      message = Component.translatable("message.basicstorage.crate.empty").withColor(0xffefcd);
    } else {
      message = Component.literal(NumberFormatter.toFormattedNumber(slot.getAmount()) + " " + slot.getResource().getItem().getName(slot.getResource().toStack()).getString()).withColor(0xFFDD99);
    }
    player.sendOverlayMessage(message);
    return InteractionResult.CONSUME;
  }

  /**
   * UseBlockCallback helper method
   **/
  /* Add blacklisted items to this method */
  /* Stop them being inserted into crates */
  public static boolean canInsert(ItemStack stack, CrateSlot slot, boolean insertingMultiple) {
    if (insertingMultiple) {
      return !slot.isBlank() || canInsert(stack, slot, false);
      // Prevents stacked undesirables from being insertable
      // when sneaking
      // This is ok as another check is done when actually inserting the items in
      // CrateSlot#insert
    } else {
      if (stack.isEmpty()) return false;
      if (stack.isDamaged()) return false;
      if (stack.is(BlockRegistry.CRATE_BLOCK.asItem()) && stack.has(DataComponentRegistry.CRATE_CONTENTS)) return false;
      if (!ItemVariant.of(stack).equals(slot.getResource()) && !slot.isBlank()) return false;
      return slot.isBlank() || stack.is(slot.getResource().getItem());
    }
  }

  public static void extractFromCrate(Level world, BlockPos pos, Player player) {
    if (!player.mayBuild()) return;
    CrateBlockEntity cbe = (CrateBlockEntity) world.getBlockEntity(pos);
    if (cbe == null) return;

    if (cbe.storage.getAmount() == 0 && !cbe.storage.isResourceBlank()) {
      cbe.storage.unlock();
      world.playSound(null, pos, SoundRegistry.EXTRACT_ONE, SoundSource.BLOCKS, 0.4f, 1.5f);
      cbe.refresh();
      return;
    }

    if (cbe.storage.isBlank()) return;

    BlockState state = world.getBlockState(pos);
    var hit = BlockUtils.getHitResult(player, pos);
    if (hit.getType() == HitResult.Type.MISS) return;

    Direction facing = state.getValue(BlockStateProperties.ORIENTATION).front();
    if (facing != hit.getDirection()) return;

    try (var t = Transaction.openOuter()) {
      var item = cbe.storage.getResource();
      var extracted = (int) cbe.storage.extract(item, player.isShiftKeyDown() ? item.getItem().getDefaultMaxStackSize() : 1, t);
      if (extracted == 0) {
        t.abort();
        return;
      }
      player.getInventory().placeItemBackInInventory(item.toStack(extracted));
      t.commit();

      if (extracted == 1)
        world.playSound(null, pos, SoundRegistry.EXTRACT_ONE, SoundSource.BLOCKS, 0.6f, 1.2f + ((-1 + random.nextFloat() * (1 + 1)) / 10));
      if (extracted > 1) world.playSound(null, pos, SoundRegistry.EXTRACT_MANY, SoundSource.BLOCKS, 0.75f, 1f);
      world.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.35f, 1f);
    }
    cbe.refresh();
    state.updateNeighbourShapes(world, pos, 1);
    world.updateNeighbourForOutputSignal(pos, state.getBlock());
    world.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
  }

  public static Direction getFront(BlockState state) {
    return state.getValue(FACING);
  }

  private static void fixLegacyState(BlockState state, Level world, BlockPos pos) {
    FrontAndTop currentOrientation = state.getValue(ORIENTATION);
    Direction legacyFacing = state.getValue(FACING);
    if (currentOrientation != FrontAndTop.NORTH_UP) {
      return;
    }
    if (legacyFacing != Direction.NORTH) {
      Constants.LOG.warn("[Crate Migration] Fixing block at x={} y={} z={}. Legacy says '{}', but Orientation was Default.", pos.getX(), pos.getY(), pos.getZ(), legacyFacing);
      FrontAndTop fixedOrientation = FrontAndTop.fromFrontAndTop(legacyFacing, Direction.UP);
      BlockState fixedState = state.setValue(ORIENTATION, fixedOrientation);
      world.setBlock(pos, fixedState, Block.UPDATE_ALL);

      Constants.LOG.info("[Crate Migration] FIXED x={} y={} z={}: Rotated to '{}'", pos.getX(), pos.getY(), pos.getZ(), fixedOrientation);
    }
  }

  @Override
  public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
    super.setPlacedBy(world, pos, state, placer, itemStack);
    if (world instanceof ServerLevel serverLevel) {
      CrateNetworkManager.get(serverLevel).onBlockAdded(world, pos, state);
    }
    world.gameEvent(placer, GameEvent.BLOCK_PLACE, pos);
    if (!world.isClientSide()) {
      tryConsolidateBelow(world, pos);
    }
  }

  private void tryConsolidateBelow(Level world, BlockPos pos) {
    BlockEntity topBe = world.getBlockEntity(pos);
    BlockEntity bottomBe = world.getBlockEntity(pos.below());

    if (topBe instanceof CrateBlockEntity topCrate && bottomBe instanceof CrateBlockEntity bottomCrate) {
      if (topCrate.storage.isResourceBlank() || bottomCrate.storage.isResourceBlank()) return;
      if (!topCrate.storage.getResource().equals(bottomCrate.storage.getResource())) return;

      try (Transaction transaction = Transaction.openOuter()) {
        long moved = StorageUtil.move(topCrate.storage, bottomCrate.storage, variant -> true, Constants.CRATE_MAX_COUNT, transaction);
        if (moved > 0) {
          transaction.commit();
          world.playSound(null, pos, SoundRegistry.INSERT_MANY, SoundSource.BLOCKS, 1f, 1f);
          topCrate.refresh();
          bottomCrate.refresh();
        }
      }
    }
  }

  @Override
  public float getExplosionResistance() {
    if (BasicStorageConfig.INSTANCE.breakWithAxeOnly() || !BasicStorageConfig.INSTANCE.canBreakIfFull()) {
      return 3600000.0f;
    }
    return super.getExplosionResistance();
  }

  @Override
  protected float getDestroyProgress(BlockState state, Player player, BlockGetter world, BlockPos pos) {
    if (!player.mayBuild()) return 0.0f;
    if (!BasicStorageConfig.INSTANCE.canBreakIfFull()) {
      BlockEntity be = world.getBlockEntity(pos);
      if (be instanceof CrateBlockEntity cbe && !cbe.storage.isBlank()) return 0.0f;
    }
    if (BasicStorageConfig.INSTANCE.breakWithAxeOnly()) {
      boolean usingAxe = player.getMainHandItem().is(ItemTags.AXES);
      if (!usingAxe) return 0.0f;
    }
    return super.getDestroyProgress(state, player, world, pos);
  }

  /**
   * Handles breaking in creative mode
   */
  @Override
  public BlockState playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
    BlockEntity be = world.getBlockEntity(pos);
    if (!(be == null)) {
      CrateBlockEntity cbe = (CrateBlockEntity) be;
      if (!world.isClientSide() && player.isCreative() && !cbe.storage.getResource().toStack().isEmpty()) {
        getDrops(state, (ServerLevel) world, pos, cbe, player, player.getItemInHand(InteractionHand.MAIN_HAND)).forEach(stack -> Containers.dropItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack));
      }
    }
    return super.playerWillDestroy(world, pos, state, player);
  }

  @Override
  protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
    return super.getDrops(state, builder);
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(ORIENTATION, FACING);
  }

  @Override
  protected boolean isPathfindable(BlockState state, PathComputationType type) {
    return false;
  }

  @Nullable
  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new CrateBlockEntity(pos, state);
  }

  @Nullable
  @Override
  public BlockState getStateForPlacement(BlockPlaceContext ctx) {
    Direction facing = ctx.getNearestLookingDirection().getOpposite();
    Direction rotation;

    if (facing.getAxis().isVertical()) {
      rotation = ctx.getHorizontalDirection();
      if (facing == Direction.DOWN) {
        rotation = rotation.getOpposite();
      }
    } else {
      rotation = Direction.UP;
    }

    /* START Temporary crate placement compatibility state.
     * Remove after legacy FACING no longer needs to be written alongside ORIENTATION. */
    Direction legacyFacing = facing;
    if (facing.getAxis().isVertical()) {
      legacyFacing = ctx.getHorizontalDirection().getOpposite();
    }
    BlockState placedState = this.defaultBlockState().setValue(BlockStateProperties.ORIENTATION, FrontAndTop.fromFrontAndTop(facing, rotation)).setValue(BlockStateProperties.HORIZONTAL_FACING, legacyFacing);
    /* END temporary crate placement compatibility state. */

    return placedState;
  }

  @Override
  protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved) {
    world.updateNeighbourForOutputSignal(pos, state.getBlock());
    CrateNetworkManager.get(world).onBlockRemoved(world, pos);
    world.gameEvent(null, GameEvent.BLOCK_DESTROY, pos);
    super.affectNeighborsAfterRemoval(state, world, pos, moved);
  }

  /* START Temporary crate orientation migration section.
   * Remove after legacy crates have had a chance to rewrite ORIENTATION from FACING. */
  @Override
  protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, @Nullable Orientation wireOrientation, boolean notify) {
    if (!world.isClientSide()) {
      fixLegacyState(state, world, pos);
    }
    super.neighborChanged(state, world, pos, sourceBlock, wireOrientation, notify);
  }
  /* END temporary crate orientation migration section. */

  @Override
  protected BlockState rotate(BlockState state, Rotation rotation) {
    FrontAndTop current = state.getValue(ORIENTATION);
    Direction newFacing = rotation.rotate(current.front());
    Direction newRotation = rotation.rotate(current.top());
    return state.setValue(ORIENTATION, FrontAndTop.fromFrontAndTop(newFacing, newRotation));
  }

  @Override
  protected BlockState mirror(BlockState state, Mirror mirror) {
    FrontAndTop current = state.getValue(ORIENTATION);
    Direction newFacing = mirror.mirror(current.front());
    Direction newRotation = mirror.mirror(current.top());
    return state.setValue(ORIENTATION, FrontAndTop.fromFrontAndTop(newFacing, newRotation));
  }

  @Override
  public boolean hasAnalogOutputSignal(BlockState state) {
    return true;
  }

  /**
   * Comparator Logic
   * 1-16 items = signal strength, loops to 1 billion
   */
  @Override
  protected int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos, Direction direction) {
    BlockEntity be = world.getBlockEntity(pos);
    if (be instanceof CrateBlockEntity cbe) {
      return BlockUtils.getComparatorOutputStrength(toIntExact(cbe.storage.getAmount()));
    } else {
      return 0;
    }
  }

  @Override
  public MapCodec<CrateBlock> codec() {
    return CODEC;
  }
}
