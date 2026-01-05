package com.khazoda.basicstorage.block;

import com.khazoda.basicstorage.storage.CrateNetworkDebug;
import com.khazoda.basicstorage.storage.CrateNetworkManager;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;


public class CrateConnectorBlock extends Block {

  public static final MapCodec<CrateConnectorBlock> CODEC = simpleCodec(CrateConnectorBlock::new);
  public static final Properties defaultSettings = getCrateSettings();


  private static Properties getCrateSettings() {
    return Properties.of().sound(SoundType.WOOD).pushReaction(PushReaction.BLOCK).instrument(NoteBlockInstrument.BASS).mapColor(MapColor.WOOD).strength(1f);
  }

  public CrateConnectorBlock(Properties settings) {
    super(settings);
  }

  public CrateConnectorBlock() {
    this(defaultSettings);
  }

  @Override
  public MapCodec<CrateConnectorBlock> codec() {
    return CODEC;
  }

  @Override
  public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
    super.setPlacedBy(world, pos, state, placer, itemStack);
    if (world instanceof ServerLevel serverLevel) {
      CrateNetworkManager.get(serverLevel).onBlockAdded(world, pos, state);
    }
    world.gameEvent(placer, GameEvent.BLOCK_PLACE, pos);
  }

  @Override
  protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved) {
    world.updateNeighbourForOutputSignal(pos, state.getBlock());
    CrateNetworkManager.get(world).onBlockRemoved(world, pos);
    world.gameEvent(null, GameEvent.BLOCK_DESTROY, pos);
    super.affectNeighborsAfterRemoval(state, world, pos, moved);
  }

  @Override
  protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
    if (CrateNetworkDebug.debugStickUsed(world, player, pos)) return InteractionResult.SUCCESS;

    if (!world.isClientSide() && player.getMainHandItem().isEmpty()) {
      CrateNetworkManager manager = CrateNetworkManager.get((ServerLevel) world);
      var network = manager.getNetworkFor(pos);
      if (network != null) {
        player.displayClientMessage(
            Component.literal("• ").withColor(0xffffff)
                .append(Component.translatable("message.basicstorage.connector.info.crates", network.crates().size()).withColor(0xFFDD99))
                .append(Component.literal(" | ").withColor(0xffffff))
                .append(Component.translatable("message.basicstorage.connector.info.stations", network.stations().size()).withColor(0xDDFF99))
                .append(Component.literal(" | ").withColor(0xffffff))
                .append(Component.translatable("message.basicstorage.connector.info.connectors", network.connectors().size()).withColor(0xBBCCFF))
                .append(Component.literal(" •").withColor(0xffffff))
            , true);
      }
    }
    return InteractionResult.PASS;
  }
}
