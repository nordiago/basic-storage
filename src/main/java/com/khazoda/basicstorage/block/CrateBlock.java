package com.khazoda.basicstorage.block;

import com.khazoda.basicstorage.block.entity.CrateBlockEntity;
import com.khazoda.basicstorage.registry.BlockRegistry;
import com.khazoda.basicstorage.registry.DataComponentRegistry;
import com.khazoda.basicstorage.storage.CrateSlot;
import com.khazoda.basicstorage.structure.CrateSlotComponent;
import com.khazoda.basicstorage.util.BlockUtils;
import com.khazoda.basicstorage.util.NumberFormatter;
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
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.loot.context.LootContextParameterSet;
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

/**
 * Right Click
 * > holding valid stack - Add one item
 * > holding invalid stack / nothing - Display exact crate contents
 * Shift Right Click - Add all items from inventory that match
 * Left Click - Remove one item
 * Shift Left Click - Remove one stack
 */
public class CrateBlock extends Block implements BlockEntityProvider {
  public static final MapCodec<CrateBlock> CODEC = CrateBlock.createCodec(CrateBlock::new);
  public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
  public static final Settings defaultSettings = Settings.create().sounds(BlockSoundGroup.WOOD).strength(5f).pistonBehavior(PistonBehavior.BLOCK).instrument(NoteBlockInstrument.BASS);


  public CrateBlock(Settings settings) {
    super(settings);
    setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH));
  }

  public CrateBlock() {
    this(defaultSettings);
  }


  /**
   * Event hook instead of onUse() method in order to capture interactions while sneaking
   */
  public static void initOnUseMethod() {
    /* Method is fired on every block right click, so immediate check for crate block class is needed */
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
      if (playerStack.isOf(Items.DEBUG_STICK)) return debugInitOnUseMethod(player, slot);

      try (var t = Transaction.openOuter()) {
        int inserted = 0;
        if (player.isSneaking()) {
          inserted = insertMaximum(player, playerStack, slot, t);
        } else if (!player.isSneaking()) {
          if (holdingBlacklistedStack(playerStack, slot)) return listExactContents(player, slot);
          if (!holdingBlacklistedStack(playerStack, slot)) inserted = insertOne(playerStack, slot, t);
        }
        if (inserted == 0) {
          t.abort();
          return ActionResult.CONSUME_PARTIAL;
        }
        t.commit();
        state.updateNeighbors(world, pos, 1);
        cbe.refresh();
        world.updateComparators(pos, state.getBlock());
        player.incrementStat(Stats.USED.getOrCreateStat(playerStack.getItem()));
        world.emitGameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        return ActionResult.SUCCESS;
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
    playerStack.decrement(inserted);
    return inserted;
  }

  /**
   * UseBlockCallback helper method
   **/
  private static int insertMaximum(PlayerEntity player, ItemStack playerStack, CrateSlot slot, Transaction transaction) {
    /* Insert as many items as possible from player's inventory if slot is empty, or matches held stack */
    if (slot.isBlank() && playerStack.isEmpty()) {
      return 0;
    } else if (slot.isBlank() && !playerStack.isEmpty()) {
      /* Insert into empty crate */
      int i = (int) slot.insert(ItemVariant.of(playerStack), playerStack.getCount(), transaction);
      playerStack.decrement(i);
      return i;
    } else {
      /* Insert into crate with items */
      return (int) StorageUtil.move(PlayerInventoryStorage.of(player), slot, itemVariant -> true, Integer.MAX_VALUE, transaction);
    }
  }

  /**
   * UseBlockCallback helper method
   **/
  private static ActionResult listExactContents(PlayerEntity player, CrateSlot slot) {
    /* Show exact contents of crate to play via message */
    Text message;
    if (slot.isBlank()) {
      message = Text.translatable("message.basicstorage.crate.empty").withColor(0xFFDD99);
    } else {
      message = Text.literal(NumberFormatter.toFormattedNumber(slot.getAmount()) + " " + slot.getResource().getItem().getName().getString()).withColor(0xFFDD99);
    }
    player.sendMessage(message, true);
    return ActionResult.CONSUME;
  }

  /**
   * UseBlockCallback helper method
   **/
  /* Add blacklisted items to this method */
  /* Stop them being inserted into crates */
  public static boolean holdingBlacklistedStack(ItemStack stack, CrateSlot slot) {
    if (stack.isEmpty()) return true;
    if (stack.isDamaged()) return true;
    return !stack.isOf(slot.getResource().getItem()) && !slot.isBlank();
  }

  /**
   * Method for removing either 1 item or a whole stack of items from a crate
   */
  @Override
  protected void onBlockBreakStart(BlockState state, World world, BlockPos pos, PlayerEntity player) {
    if (!player.canModifyBlocks()) return;

    CrateBlockEntity cbe = (CrateBlockEntity) world.getBlockEntity(pos);
    if (cbe == null) return;
    if (cbe.storage.isBlank()) return;

    var hit = BlockUtils.getHitResult(player, pos);
    if (hit.getType() == HitResult.Type.MISS) return;

    Direction facing = state.get(Properties.HORIZONTAL_FACING);
    if (facing != hit.getSide()) return;

    try (var t = Transaction.openOuter()) {
      var item = cbe.storage.getResource();
      var extracted = (int) cbe.storage.extract(item, player.isSneaking() ? item.getItem().getMaxCount() : 1, t);
      if (extracted == 0) {
        t.abort();
        return;
      }
      player.getInventory().offerOrDrop(item.toStack(extracted));
      t.commit();
    }
    cbe.refresh();
    state.updateNeighbors(world, pos, 1);
    world.updateComparators(pos, state.getBlock());
    world.playSound(null, pos, SoundEvents.BLOCK_DECORATED_POT_INSERT, SoundCategory.BLOCKS, 1.0f, 0.7f + 0.5f);
    world.emitGameEvent(player, GameEvent.BLOCK_CHANGE, pos);
  }

  /**
   * Handles breaking in creative mode
   */
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

  /**
   * Applies custom tooltip showing crate contents
   **/
  @Override
  public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
    CrateSlotComponent contentsComponent = stack.get(DataComponentRegistry.CRATE_CONTENTS);
    if (contentsComponent == null) return;
    ItemVariant item = contentsComponent.item();
    int amount = contentsComponent.count();

    MutableText contents_line_1 = Text.literal(NumberFormatter.toFormattedNumber(amount)).withColor(0xFFDD99);
    MutableText contents_line_2 = Text.literal(item.getItem().getName().getString()).withColor(0xCCAA77);

    tooltip.add(contents_line_1);
    tooltip.add(contents_line_2);
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


  @Override
  public boolean hasComparatorOutput(BlockState state) {
    return true;
  }

  /**
   * Comparator Logic
   * 1-16 items = signal strength, loops to 1 billion
   */
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

  /**
   * Debugging Methods, not for survival gameplay use
   */
  private static ActionResult debugInitOnUseMethod(PlayerEntity player, CrateSlot slot) {
    try (Transaction t = Transaction.openOuter()) {
      if (slot.isBlank()) return ActionResult.PASS;
      if (player.isSneaking()) slot.extract(slot.getResource(), 50000000, t);
      if (!player.isSneaking()) slot.insert(slot.getResource(), 100000000, t);
      t.commit();
    }
    slot.update();
    return ActionResult.SUCCESS;
  }
}