package com.khazoda.basic_storage.block;

import com.khazoda.basic_storage.block.entity.CrateBlockEntity;
import com.khazoda.basic_storage.registry.BlockEntityRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

public class CrateBlock extends Block implements BlockEntityProvider {
  public static final MapCodec<CrateBlock> CODEC = createCodec(CrateBlock::new);
  public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
  public static final Settings defaultSettings = Settings.create().sounds(BlockSoundGroup.WOOD).strength(0.7f).pistonBehavior(PistonBehavior.BLOCK);

  public CrateBlock(Settings settings) {
    super(settings);
    setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH));
  }

  public CrateBlock() {
    this(defaultSettings);
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
  protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
    builder.add(FACING);
  }

  @Override
  public MapCodec<CrateBlock> getCodec() {
    return CODEC;
  }

}
