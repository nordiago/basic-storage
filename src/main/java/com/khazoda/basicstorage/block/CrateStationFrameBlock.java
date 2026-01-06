package com.khazoda.basicstorage.block;

import com.khazoda.basicstorage.registry.BlockRegistry;
import com.khazoda.basicstorage.registry.CriterionRegistry;
import com.khazoda.basicstorage.registry.ParticleRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;

public class CrateStationFrameBlock extends Block {
  public static final Properties defaultSettings = Properties.of().sound(SoundType.WOOD).strength(2.5f).pushReaction(PushReaction.NORMAL).instrument(NoteBlockInstrument.BASS).mapColor(MapColor.WOOD);

  public CrateStationFrameBlock(Properties settings) {
    super(settings);
  }

  public CrateStationFrameBlock() {
    this(defaultSettings);
  }


  @Override
  protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
    return InteractionResult.PASS;
  }

  @Override
  protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
    if (stack.is(Items.ENDER_EYE)) {
      if (!world.isClientSide()) {
        ((ServerLevel) world).sendParticles(ParticleRegistry.VOIDY, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.2, 0.2, 0.2, 0.05);
        world.setBlock(pos, BlockRegistry.CRATE_STATION_BLOCK.defaultBlockState(), 3);
        world.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
        if (player instanceof ServerPlayer serverPlayer) {
          CriterionRegistry.STATION_TRANSFORMED.trigger(serverPlayer);
        }
        if (!player.getAbilities().instabuild) {
          stack.shrink(1);
        }
      }
      return InteractionResult.SUCCESS;
    }
    return InteractionResult.PASS;
  }
}
