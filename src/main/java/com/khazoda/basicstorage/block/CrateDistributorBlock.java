package com.khazoda.basicstorage.block;

import com.khazoda.basicstorage.block.entity.CrateDistributorBlockEntity;
import com.khazoda.basicstorage.registry.BlockEntityRegistry;
import com.khazoda.basicstorage.registry.BlockRegistry;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
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
      BlockState state = world.getBlockState(pos);
      BlockEntity be = world.getBlockEntity(pos);

      if (be == null) return ActionResult.PASS;

      CrateDistributorBlockEntity cdbe = (CrateDistributorBlockEntity) be;
      int connectedCrates = cdbe.getConnectedCrates().size();
      ItemStack playerStack = player.getMainHandStack();
      boolean success = false;

      if (playerStack.isEmpty() && !player.isSneaking()) {
        if (!world.isClient())
          player.sendMessage(Text.literal("-".concat(String.valueOf(connectedCrates)).concat(" Crates Connected-")).withColor(0xDDFF99), true);
      } else {
        success = cdbe.handleInteraction(player, hand);
      }

      if (success) {
        return ActionResult.SUCCESS;
      } else {
        return ActionResult.PASS;
      }
    });
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
