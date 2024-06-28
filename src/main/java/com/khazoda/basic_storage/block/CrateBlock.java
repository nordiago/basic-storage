package com.khazoda.basic_storage.block;

import com.khazoda.basic_storage.block.entity.CrateBlockEntity;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CrateBlock extends Block implements BlockEntityProvider {
  public static final MapCodec<CrateBlock> CODEC = CrateBlock.createCodec(CrateBlock::new);
  public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
  public static final Settings defaultSettings = Settings.create().sounds(BlockSoundGroup.WOOD).strength(0.7f).pistonBehavior(PistonBehavior.BLOCK);
  public static final int crateMaxCount = 1000000;

  public CrateBlock(Settings settings) {
    super(settings);
    setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH));
  }

  public CrateBlock() {
    this(defaultSettings);
  }

  /*
   * Right Click - Add one item
   * Shift Right Click - Add stack
   * Shift Right Click Hold - Add all in inventory
   * Left Click - Remove one item
   * Shift Left Click - Remove one stack
   * */

  /**
   * Event hook instead of onUse() method in order to capture interactions while sneaking
   */
  public static void initOnUseMethod() {
    UseBlockCallback.EVENT.register((PlayerEntity player, World world, Hand hand, BlockHitResult hit) -> {
      if (player.isSpectator()) return ActionResult.PASS;

      /* Check whether block is a crate & gather required data */
      if (world.getBlockState(hit.getBlockPos()).getBlock() instanceof CrateBlock) {
        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        BlockEntity be = world.getBlockEntity(pos);
        Direction facing = state.get(Properties.HORIZONTAL_FACING);

        /* Skip method if no crate block entity is attached to block hit */
        if (!(be instanceof CrateBlockEntity crateBlockEntity) || !player.canModifyBlocks()) return ActionResult.PASS;

        /* Only do inventory managing logic on server */
        if (world.isClient) return ActionResult.SUCCESS;

        if (facing == hit.getSide()) {
          ItemStack crateStack = crateBlockEntity.getStack();
          ItemStack playerStack = player.getMainHandStack();

          boolean isSneaking = player.isSneaking();
          int amountToInsert = isSneaking ? playerStack.getCount() : 1;

          if (!playerStack.isEmpty() && (crateStack.isEmpty()
              || ItemStack.areItemsAndComponentsEqual(crateStack, playerStack)
              && crateStack.getCount() < crateMaxCount - amountToInsert)) {
            player.incrementStat(Stats.USED.getOrCreateStat(playerStack.getItem()));
            ItemStack stackToInsert = playerStack.splitUnlessCreative(isSneaking ? amountToInsert : 1, player);
            if (crateBlockEntity.isEmpty()) {
              crateBlockEntity.setStack(stackToInsert);
            } else {
              int crateCountHolder = crateStack.getCount();
              crateStack.setCount(crateCountHolder + amountToInsert);
            }

            world.playSound(null, pos, SoundEvents.BLOCK_DECORATED_POT_INSERT, SoundCategory.BLOCKS, 1.0f, 0.7f + 0.5f);
            if (world instanceof ServerWorld serverWorld) {
              serverWorld.spawnParticles(ParticleTypes.DUST_PLUME, (double) pos.getX() + 0.5, (double) pos.getY() + 1.2, (double) pos.getZ() + 0.5, 7, 0.0, 0.0, 0.0, 0.0);
            }

            crateBlockEntity.triggerUpdate();
            world.emitGameEvent((Entity) player, GameEvent.BLOCK_CHANGE, pos);
            return ActionResult.SUCCESS;
          }
          player.sendMessage(Text.literal(crateStack.toString()));
          return ActionResult.PASS;
        }
      }
      return ActionResult.PASS;
    });
  }

  @Override
  protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {

    return super.onUse(state, world, pos, player, hit);
  }


//  @Override
//  protected void onBlockBreakStart(BlockState state, World world, BlockPos pos, PlayerEntity player) {
//    if (!player.canModifyBlocks()) return;
//
//    Direction facing = state.get(Properties.HORIZONTAL_FACING);
//
//    var hit = BlockUtils.getHitResult(player, pos);
//    if (hit.getType() == HitResult.Type.MISS) return;
//
//    if (facing == hit.getSide()) {
//      /* Get Block Entity */
//      CrateBlockEntity be = (CrateBlockEntity) world.getBlockEntity(pos);
//      if (be == null) return;
//      /* Get Inventory Slot */
//      var slot = be.slot;
//      /* If crate is empty */
//      if (slot.getResource().isBlank()) return;
//
//
//      if (world.isClient()) {
//        player.sendMessage(Text.literal("Taking 1 Item"));
//      }
//    }
//  }

  @Override
  public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
    return super.onBreak(world, pos, state, player);
  }

  @Override
  protected List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
    return super.getDroppedStacks(state, builder);
  }

  public static Direction getFront(BlockState state) {
    return state.get(FACING);
  }

  @Override
  protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
    builder.add(FACING);
  }

  @Override
  protected boolean canPathfindThrough(BlockState state, NavigationType type) {
    return false;
  }

  @Nullable
  @Override
  public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
    return new CrateBlockEntity(pos, state);
  }

  @Nullable
  @Override
  public BlockState getPlacementState(ItemPlacementContext ctx) {
    return this.getDefaultState().with(Properties.HORIZONTAL_FACING, ctx.getHorizontalPlayerFacing().getOpposite());
  }

  @Override
  protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
    ItemScatterer.onStateReplaced(state, newState, world, pos);
    super.onStateReplaced(state, world, pos, newState, moved);
  }

  @Override
  protected BlockState rotate(BlockState state, BlockRotation rotation) {
    return state.with(FACING, rotation.rotate(state.get(FACING)));
  }

  @Override
  protected BlockState mirror(BlockState state, BlockMirror mirror) {
    return state.rotate(mirror.getRotation(state.get(FACING)));
  }

  @Override
  public boolean hasComparatorOutput(BlockState state) {
    return true;
  }

  @Override
  public MapCodec<CrateBlock> getCodec() {
    return CODEC;
  }
}
