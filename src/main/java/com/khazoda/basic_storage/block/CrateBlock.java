package com.khazoda.basic_storage.block;

import com.khazoda.basic_storage.Constants;
import com.khazoda.basic_storage.block.entity.CrateBlockEntity;
import com.khazoda.basic_storage.util.BlockUtils;
import com.khazoda.basic_storage.util.NumberFormatter;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
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
  public static final int crateMaxCount = Constants.CRATE_MAX_COUNT;

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

        if (facing == hit.getSide()) {
          /* Only do inventory managing logic on server */
          if (world.isClient) return ActionResult.SUCCESS;

          ItemStack crateStack = crateBlockEntity.getStack();
          ItemStack playerStack = player.getMainHandStack();
          boolean isSneaking = player.isSneaking();

          /* If player's hand isn't empty & crate is empty OR
           *  If both player's held item and crate content item type are equal */
          if (!playerStack.isEmpty() && (crateStack.isEmpty()
              || ItemStack.areItemsAndComponentsEqual(crateStack, playerStack))) {

            /* TODO: REMOVE 1 BILLION FROM HERE WHEN NOT TESTING */
            int amountToInsert = isSneaking ? playerStack.getCount() * 1000000 : 1;

            if (crateStack.getCount() < crateMaxCount - amountToInsert) {
              amountToInsert = isSneaking ? amountToInsert : 1;
            } else if (crateStack.getCount() > crateMaxCount - amountToInsert) {
              /* Is not sneaking*/
              if (amountToInsert == 1) {
                return ActionResult.CONSUME;
                /* Is sneaking */
              } else {
                amountToInsert = crateMaxCount - crateStack.getCount();
              }
            }

            if (amountToInsert == 0) return ActionResult.PASS;
            ItemStack stackToInsert = playerStack.splitUnlessCreative(amountToInsert, player);

            if (crateBlockEntity.isEmpty()) {
              crateBlockEntity.setStack(stackToInsert);
            } else {
              int currentCrateCount = crateStack.getCount();
              crateStack.setCount(currentCrateCount + amountToInsert);
            }

            world.playSound(null, pos, SoundEvents.BLOCK_DECORATED_POT_INSERT, SoundCategory.BLOCKS, 1.0f, 0.7f + 0.5f);
            if (world instanceof ServerWorld serverWorld) {
              serverWorld.spawnParticles(ParticleTypes.DUST_PLUME, (double) pos.getX() + 0.5, (double) pos.getY() + 1.2, (double) pos.getZ() + 0.5, 7, 0.0, 0.0, 0.0, 0.0);
            }
            crateBlockEntity.triggerUpdate();

            player.incrementStat(Stats.USED.getOrCreateStat(playerStack.getItem()));
            world.emitGameEvent((Entity) player, GameEvent.BLOCK_CHANGE, pos);
            return ActionResult.SUCCESS;
          } else if (playerStack.isEmpty()) {
            ServerPlayerEntity spe = (ServerPlayerEntity) player;
            spe.sendMessageToClient(Text.literal(NumberFormatter.toFormattedNumber(crateStack.getCount()) + " " + crateStack.getName().getString()).withColor(0xcccccc), true);

            return ActionResult.PASS;
          }
        }
      }
      return ActionResult.PASS;
    });
  }

  @Override
  protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
    return super.onUse(state, world, pos, player, hit);
  }

  @Override
  protected void onBlockBreakStart(BlockState state, World world, BlockPos pos, PlayerEntity player) {
//      ServerPlayerEntity player = (ServerPlayerEntity) player;
//      ServerWorld world = (ServerWorld) world;
    CrateBlockEntity crateBlockEntity = (CrateBlockEntity) world.getBlockEntity(pos);

    if (!player.canModifyBlocks()) return;
    if (crateBlockEntity == null) return;
    if (crateBlockEntity.getStack().isEmpty()) return;

    var hit = BlockUtils.getHitResult(player, pos);
    if (hit.getType() == HitResult.Type.MISS) return;
    Direction facing = state.get(Properties.HORIZONTAL_FACING);

    if (facing == hit.getSide()) {
      ItemStack stackType = crateBlockEntity.getStack();
      boolean isSneaking = player.isSneaking();
      int amountToTake = isSneaking ? stackType.getItem().getDefaultStack().getMaxCount() : 1;
      ItemStack stackToTake = ItemStack.EMPTY;

      if (crateBlockEntity.getStack().getCount() >= amountToTake) {
        stackToTake = crateBlockEntity.decreaseStack(amountToTake);
      } else if (crateBlockEntity.getStack().getCount() < amountToTake) {
        stackToTake = crateBlockEntity.decreaseStack(crateBlockEntity.getStack().getCount());
      }
      player.giveItemStack(stackToTake);

      if (crateBlockEntity.getStack().getCount() == 0) {
        crateBlockEntity.setStack(ItemStack.EMPTY);
      }

      crateBlockEntity.triggerUpdate();

      world.playSound(null, pos, SoundEvents.BLOCK_DECORATED_POT_INSERT, SoundCategory.BLOCKS, 1.0f, 0.7f + 0.5f);
      if (world instanceof ServerWorld serverWorld)
        serverWorld.spawnParticles(ParticleTypes.DUST_PLUME, (double) pos.getX() + 0.5, (double) pos.getY() + 1.2, (double) pos.getZ() + 0.5, 7, 0.0, 0.0, 0.0, 0.0);
      world.emitGameEvent((Entity) player, GameEvent.BLOCK_CHANGE, pos);
    }
  }

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
