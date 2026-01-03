package com.khazoda.basicstorage.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import org.jspecify.annotations.Nullable;


public class CrateConnectorBlock extends BaseEntityBlock {

  public static final MapCodec<CrateConnectorBlock> CODEC = simpleCodec(CrateConnectorBlock::new);
  public static final Properties defaultSettings = getCrateSettings();


  private static Properties getCrateSettings() {
    return Properties.of().sound(SoundType.WOOD).pushReaction(PushReaction.BLOCK).instrument(NoteBlockInstrument.BASS).mapColor(MapColor.WOOD).strength(1f);
  }

  public CrateConnectorBlock(Properties settings) {
    super(settings);
    registerDefaultState(this.stateDefinition.any());
  }

  public CrateConnectorBlock() {
    this(defaultSettings);
  }

  @Override
  public MapCodec<CrateConnectorBlock> codec() {
    return CODEC;
  }

  @Override
  public @Nullable BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
    return null;
  }
}
