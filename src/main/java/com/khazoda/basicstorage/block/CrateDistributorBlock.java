package com.khazoda.basicstorage.block;

import com.khazoda.basicstorage.block.entity.CrateBlockEntity;
import com.khazoda.basicstorage.block.entity.CrateDistributorBlockEntity;
import com.khazoda.basicstorage.registry.BlockEntityRegistry;
import com.khazoda.basicstorage.registry.BlockRegistry;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.PlayerInventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Right Click
 * > holding stack - Search for nearest crate containing stack item type and deposit stack into it
 * > no valid crate found? - notify user
 * > holding nothing - Display number of connected crates
 * Shift Right Click - Add all items from inventory to crates that match the items
 * > no valid crate found? - notify user
 * Left Click - Nothing
 * Shift Left Click - Nothing
 */
public class CrateDistributorBlock extends BlockWithEntity implements BlockEntityProvider {
  public static final MapCodec<CrateDistributorBlock> CODEC = CrateDistributorBlock.createCodec(CrateDistributorBlock::new);
  public static final Settings defaultSettings = Settings.create().sounds(BlockSoundGroup.WOOD).strength(3.5f).pistonBehavior(PistonBehavior.BLOCK).instrument(NoteBlockInstrument.BASS).mapColor(MapColor.OAK_TAN);

  public CrateDistributorBlock(Settings settings) {
    super(settings);
  }

  public CrateDistributorBlock() {
    this(defaultSettings);
  }

  /**
   * Event hook instead of onUse() method in order to capture interactions while sneaking
   */
  public static void initOnUseMethod() {
    UseBlockCallback.EVENT.register((PlayerEntity player, World world, Hand hand, BlockHitResult hit) -> {
      if (!world.getBlockState(hit.getBlockPos()).isOf(BlockRegistry.CRATE_DISTRIBUTOR_BLOCK)) return ActionResult.PASS;
      if (!player.canModifyBlocks() || player.isSpectator()) return ActionResult.PASS;

      BlockPos pos = hit.getBlockPos();
      BlockEntity be = world.getBlockEntity(pos);

      if (be == null) return ActionResult.PASS;

      CrateDistributorBlockEntity cdbe = (CrateDistributorBlockEntity) be;
      ItemStack playerStack = player.getMainHandStack();
      int connectedCrateCount = cdbe.getConnectedCrates().size();
      int inserted = 0;

      if (player.isSneaking()) {
        inserted = depositInventory(player, cdbe);
      } else if (!player.isSneaking()) {
        if (playerStack.isEmpty()) {
          if (!world.isClient())
            player.sendMessage(Text.translatable("message.basicstorage.distributor.connected_crate_count", connectedCrateCount).withColor(0xDDFF99), true);
          return ActionResult.PASS;
        }
        inserted = depositStack(player.getStackInHand(hand), cdbe);
      }

      if (!world.isClient()) {
        if (inserted <= 0)
          player.sendMessage(Text.translatable("message.basicstorage.distributor.no_matching_crates").withColor(0xFF9999), true);  // Changed 'true' to 'false'
        return ActionResult.CONSUME;
      }
      return ActionResult.SUCCESS;
    });
  }

  private static int depositStack(ItemStack stack, CrateDistributorBlockEntity cdbe) {
    int inserted = 0;
    if (stack.isEmpty()) return 0;

    ItemVariant variant = ItemVariant.of(stack);
    List<BlockPos> compatibleCrates = cdbe.getCrateRegistry().get(variant);
    if (compatibleCrates == null) return 0;
    World world = cdbe.getWorld();

    for (BlockPos cratePos : new ArrayList<>(compatibleCrates)) {
      if (world == null) return 0; // todo: if something goes wrong, remove this and see if things work lol
      BlockEntity be = world.getBlockEntity(cratePos);
      if (!(be instanceof CrateBlockEntity crate)) {
//        compatibleCrates.remove(cratePos); //TODO: Maybe Remove?
        continue;
      }

      try (Transaction transaction = Transaction.openOuter()) {
        inserted = (int) crate.storage.insert(variant, stack.getCount(), transaction);
        if (inserted > 0) {
          stack.decrement(inserted);
          transaction.commit();
          return inserted;
        }
      }
    }
    return inserted;
  }

  private static int depositInventory(PlayerEntity player, CrateDistributorBlockEntity cdbe) {
    int inserted = 0;
    PlayerInventoryStorage invStorage = PlayerInventoryStorage.of(player);
    World world = cdbe.getWorld();

    for (int i = 0; i < player.getInventory().main.size(); i++) {
      ItemStack stack = player.getInventory().main.get(i);
      if (!stack.isEmpty()) {
        ItemVariant variant = ItemVariant.of(stack);
        List<BlockPos> compatibleCrates = cdbe.getCrateRegistry().get(variant);

        if (compatibleCrates != null) {
          for (BlockPos cratePos : compatibleCrates) {
            if (world == null) return 0;
            BlockEntity be = world.getBlockEntity(cratePos);
            if (!(be instanceof CrateBlockEntity crate))
              continue;

            try (Transaction transaction = Transaction.openOuter()) {
              inserted += (int) crate.storage.insert(variant, stack.getCount(), transaction);
              if (inserted > 0) {
                stack.decrement(inserted);
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
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
    return validateTicker(type, BlockEntityRegistry.CRATE_DISTRIBUTOR_BLOCK_ENTITY, CrateDistributorBlockEntity::tick);
  }

  @Nullable
  @Override
  public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
    return new CrateDistributorBlockEntity(pos, state);
  }

  @Override
  public BlockState getPlacementState(ItemPlacementContext ctx) {
    return this.getDefaultState();
  }

  @Override
  protected BlockRenderType getRenderType(BlockState state) {
    return BlockRenderType.MODEL;
  }

  @Override
  public MapCodec<CrateDistributorBlock> getCodec() {
    return CODEC;
  }
}
