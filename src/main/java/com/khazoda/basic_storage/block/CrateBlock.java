package com.khazoda.basic_storage.block;

import com.khazoda.basic_storage.block.entity.CrateBlockEntity;
import com.khazoda.basic_storage.registry.BlockRegistry;
import com.khazoda.basic_storage.registry.DataComponentRegistry;
import com.khazoda.basic_storage.storage.CrateSlot;
import com.khazoda.basic_storage.structure.CrateSlotComponent;
import com.khazoda.basic_storage.util.BlockUtils;
import com.khazoda.basic_storage.util.NumberFormatter;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.PlayerInventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.MutableText;
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

import static java.lang.Math.toIntExact;

public class CrateBlock extends Block implements BlockEntityProvider {
  public static final MapCodec<CrateBlock> CODEC = CrateBlock.createCodec(CrateBlock::new);
  public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
  public static final Settings defaultSettings = Settings.create().sounds(BlockSoundGroup.WOOD).strength(3f).pistonBehavior(PistonBehavior.BLOCK);


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
    /* Method is fired on ever block right click, so immediate check for crate block class is needed */
    UseBlockCallback.EVENT.register((PlayerEntity player, World world, Hand hand, BlockHitResult hit) -> {
      if (!world.getBlockState(hit.getBlockPos()).isOf(BlockRegistry.CRATE_BLOCK)) return ActionResult.PASS;
      if (!player.canModifyBlocks() || player.isSpectator()) return ActionResult.PASS;

      BlockPos pos = hit.getBlockPos();
      BlockState state = world.getBlockState(pos);
      BlockEntity be = world.getBlockEntity(pos);
      Direction facing = state.get(Properties.HORIZONTAL_FACING);

      if (be == null) return ActionResult.PASS;
      if (facing != hit.getSide()) return ActionResult.PASS;

      CrateBlockEntity cbe = (CrateBlockEntity) be;
      ItemStack playerStack = player.getMainHandStack();
      CrateSlot slot = cbe.storage;

      int inserted = 0;
      try (var t = Transaction.openOuter()) {
        if (holdingBlacklistedStack(playerStack)) {
          if (player.isSneaking()) {
            /* Insert as many items as possible */
            if (slot.isBlank()) return ActionResult.PASS;
            inserted = (int) StorageUtil.move(PlayerInventoryStorage.of(player), slot, itemVariant -> true, Integer.MAX_VALUE, t);
          } else if (!player.isSneaking()) {
            /* Show player exact contents */
            player.sendMessage(Text.literal(NumberFormatter.toFormattedNumber(slot.getAmount()) + " " + slot.getResource().getItem().getName().getString()).withColor(0xbebebe), true);
            return ActionResult.CONSUME;
          }
        } else if (!holdingBlacklistedStack(playerStack)) {
          if (player.isSneaking()) {
            /* Insert as many items as possible */
            if (slot.isBlank()) return ActionResult.PASS;
            inserted = (int) StorageUtil.move(PlayerInventoryStorage.of(player), slot, itemVariant -> true, Integer.MAX_VALUE, t);
          } else if (!player.isSneaking()) {
            /* Insert one item */
            if (playerStack.isEmpty()) return ActionResult.PASS;
            inserted = (int) slot.insert(ItemVariant.of(playerStack), 1, t);
            playerStack.decrement(inserted);
          }
        }
        if (inserted == 0) return ActionResult.CONSUME_PARTIAL;
        t.commit();
        world.updateComparators(pos, state.getBlock());
        player.incrementStat(Stats.USED.getOrCreateStat(playerStack.getItem()));
        world.emitGameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        return ActionResult.SUCCESS;
      }
    });
  }


//  public static void initOnUseMethod() {
//    UseBlockCallback.EVENT.register((PlayerEntity player, World world, Hand hand, BlockHitResult hit) -> {
//      if (player.isSpectator()) return ActionResult.PASS;
//
//      /* Check whether block is a crate & gather required data */
//      if (world.getBlockState(hit.getBlockPos()).getBlock() instanceof CrateBlock) {
//        BlockPos pos = hit.getBlockPos();
//        BlockState state = world.getBlockState(pos);
//        BlockEntity be = world.getBlockEntity(pos);
//        Direction facing = state.get(Properties.HORIZONTAL_FACING);
//
//        /* Skip method if no crate block entity is attached to block hit */
//        if (!(be instanceof CrateBlockEntity crateBlockEntity) || !player.canModifyBlocks()) return ActionResult.PASS;
//
//        if (facing == hit.getSide()) {
//          /* Only do inventory managing logic on server */
//          if (world.isClient) return ActionResult.SUCCESS;
//
//          ItemStack crateStack = crateBlockEntity.slot.getResource().toStack((int)
//              crateBlockEntity.slot.getAmount());
//          ItemStack playerStack = player.getMainHandStack();
//          ItemVariant playerStackVariant = ItemVariant.of(playerStack);
//          PlayerInventory playerInventory = player.getInventory();
//          PlayerInventoryStorage playerInventoryStorage = PlayerInventoryStorage.of(player);
//          boolean isSneaking = player.isSneaking();
//
//          /* If player's hand isn't empty & crate is empty OR
//           *  If both player's held item and crate content item type are equal */
//          if (!holdingBlacklistedStack(playerStack) && (crateStack.isEmpty()
//              || ItemStack.areItemsEqual(crateStack, playerStack))) {
//
//            int amountToInsert = isSneaking ? playerInventory.count(playerStack.getItem()) : 1;
//            /* If inserting stack would overcap the crate (>1billion) */
//            if (crateStack.getCount() > crateMaxCount - amountToInsert) {
//              /* Is not sneaking*/
//              if (amountToInsert == 1) {
//                return ActionResult.CONSUME;
//                /* Is sneaking */
//              } else {
//                amountToInsert = crateMaxCount - crateStack.getCount();
//              }
//            }
//
//            if (amountToInsert == 0) return ActionResult.PASS;
//            var c = crateBlockEntity.slot.getCapacity();
//
//            try (var t = Transaction.openOuter()) {
//              /* Move up to one stack from player hand to crate */
//              if (playerInventoryStorage.getHandSlot(player.getActiveHand()).extract(playerStackVariant, amountToInsert, t) == amountToInsert
//                  && crateBlockEntity.slot.insert(playerStackVariant, amountToInsert, t) == amountToInsert) {
//                t.commit();
//                be.markDirty();
//              }
//              /* Move more than one stack from player inventory to crate */
//              if (amountToInsert > playerStack.getMaxCount()) {
//                if (StorageUtil.move(playerInventoryStorage, crateBlockEntity.slot, var -> true, amountToInsert - playerStack.getMaxCount(), t) == amountToInsert - playerStack.getMaxCount()) {
//                  t.commit();
//                  be.markDirty();
//                }
//              }
//            }
//            world.playSound(null, pos, SoundEvents.BLOCK_DECORATED_POT_INSERT, SoundCategory.BLOCKS, 1.0f, 0.7f + 0.5f);
//            if (world instanceof ServerWorld serverWorld) {
//              serverWorld.spawnParticles(ParticleTypes.DUST_PLUME, (double) pos.getX() + 0.5, (double) pos.getY() + 1.2, (double) pos.getZ() + 0.5, 7, 0.0, 0.0, 0.0, 0.0);
//            }
//            crateBlockEntity.markDirty();
//            world.updateComparators(pos, state.getBlock());
//
//            player.incrementStat(Stats.USED.getOrCreateStat(playerStack.getItem()));
//            world.emitGameEvent((Entity) player, GameEvent.BLOCK_CHANGE, pos);
//            return ActionResult.SUCCESS;
//          } else if (playerStack.isEmpty()) {
//            ServerPlayerEntity spe = (ServerPlayerEntity) player;
//            spe.sendMessageToClient(Text.literal(NumberFormatter.toFormattedNumber(crateStack.getCount()) + " " + crateStack.getName().getString()).withColor(0xcccccc), true);
//
//            return ActionResult.PASS;
//          }
//        }
//      }
//      return ActionResult.PASS;
//    });
//  }

  /* Add blacklisted items to this method */
  /* Stop them being inserted into crates */
  public static boolean holdingBlacklistedStack(ItemStack stack) {
    if (stack.isEmpty()) return true;
    return stack.isDamaged();
  }

  @Override
  protected void onBlockBreakStart(BlockState state, World world, BlockPos pos, PlayerEntity player) {
    CrateBlockEntity crateBlockEntity = (CrateBlockEntity) world.getBlockEntity(pos);

    if (!player.canModifyBlocks()) return;
    if (crateBlockEntity == null) return;
    if (crateBlockEntity.storage.getResource().toStack().isEmpty()) return;

    var hit = BlockUtils.getHitResult(player, pos);
    if (hit.getType() == HitResult.Type.MISS) return;
    Direction facing = state.get(Properties.HORIZONTAL_FACING);

    if (facing == hit.getSide()) {
      /*TODO: .getResource.toStack is returning 1 always? maybe getResource puts it to 1?*/
      ItemStack crateStack = crateBlockEntity.storage.getResource().toStack();
      int attemptedWithdrawalAmount = player.isSneaking() ? crateStack.getItem().getDefaultStack().getMaxCount() : 1;

      player.getInventory().offerOrDrop(withdrawItemStack(crateBlockEntity, attemptedWithdrawalAmount, crateStack));

      crateBlockEntity.markDirty();
      world.updateComparators(pos, state.getBlock());

      world.playSound(null, pos, SoundEvents.BLOCK_DECORATED_POT_INSERT, SoundCategory.BLOCKS, 1.0f, 0.7f + 0.5f);
      if (world instanceof ServerWorld serverWorld)
        serverWorld.spawnParticles(ParticleTypes.DUST_PLUME, (double) pos.getX() + 0.5, (double) pos.getY() + 1.2, (double) pos.getZ() + 0.5, 7, 0.0, 0.0, 0.0, 0.0);
      world.emitGameEvent(player, GameEvent.BLOCK_CHANGE, pos);
    }
  }

  private static ItemStack withdrawItemStack(CrateBlockEntity be, int withdrawalAmount, ItemStack currentCrateStack) {
    ItemStack withdrawalStack;
    int crateStackCount = be.storage.getResource().toStack().getCount();
    /* Crate still has items after withdrawal */
    if (crateStackCount >= withdrawalAmount) {
      try (Transaction t = Transaction.openOuter()) {
        withdrawalStack = new ItemStack(be.storage.getResource().getItem(), (int) be.storage.extract(be.storage.getResource(), withdrawalAmount, t));
        t.commit();
        be.markDirty();
      }
      /* All items removed from crate after withdrawal */
    } else {
      try (Transaction t = Transaction.openOuter()) {
        withdrawalStack = new ItemStack(be.storage.getResource().getItem(), (int) be.storage.extract(be.storage.getResource(), crateStackCount, t));
        t.commit();
        be.markDirty();
      }
    }
    return withdrawalStack;
  }

  @Override
  public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
    BlockEntity be = world.getBlockEntity(pos);
    if (!(be == null)) {
      CrateBlockEntity cbe = (CrateBlockEntity) be;
      if (!world.isClient() && player.isCreative() && !cbe.storage.getResource().toStack().isEmpty()) {
        getDroppedStacks(state, (ServerWorld) world, pos, cbe, player, player.getStackInHand(Hand.MAIN_HAND))
            .forEach(stack -> ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), stack));
      }
    }
    return super.onBreak(world, pos, state, player);
  }

  @Override
  protected List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
    return super.getDroppedStacks(state, builder);
  }

  @Override
  public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
    CrateSlotComponent contentsComponent = stack.get(DataComponentRegistry.CRATE_CONTENTS);
    if (contentsComponent == null) return;
    ItemVariant item = contentsComponent.item();
    int amount = contentsComponent.count();

    MutableText contentsText = Text.literal(NumberFormatter.toFormattedNumber(amount) + "x " + item.getItem().getName().getString()).withColor(0xFFDD99);

    tooltip.add(contentsText);
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
    if (state.isOf(newState.getBlock())) {
      return;
    }
    BlockEntity blockEntity = world.getBlockEntity(pos);
    if (blockEntity instanceof CrateBlockEntity) {
      world.updateComparators(pos, state.getBlock());
    }
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

  /**
   * Comparator Logic
   */
  @Override
  public boolean hasComparatorOutput(BlockState state) {
    return true;
  }

  @Override
  public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
    BlockEntity be = world.getBlockEntity(pos);
    if (be instanceof CrateBlockEntity cbe) {
      return BlockUtils.getComparatorOutputStrength(toIntExact(cbe.storage.getAmount()));
    } else {
      return 0;
    }
  }


  @Override
  public MapCodec<CrateBlock> getCodec() {
    return CODEC;
  }
}
