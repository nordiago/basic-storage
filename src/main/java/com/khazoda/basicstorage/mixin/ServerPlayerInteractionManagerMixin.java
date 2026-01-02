package com.khazoda.basicstorage.mixin;

import com.khazoda.basicstorage.block.CrateBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerInteractionManagerMixin {

  @Shadow
  protected ServerLevel level;
  @Shadow
  @Final
  protected ServerPlayer player;
  @Shadow
  private int gameTicks;

  @Unique
  private int basicStorage$startTick = -1;
  @Unique
  private BlockPos basicStorage$targetPos = null;

  @Inject(method = "handleBlockBreakAction", at = @At("HEAD"))
  private void onCrateClickStart(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction direction, int worldHeight, int sequence, CallbackInfo ci) {
    if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
      if (this.level.getBlockState(pos).getBlock() instanceof CrateBlock) {
        this.basicStorage$startTick = this.gameTicks;
        this.basicStorage$targetPos = pos;
      } else {
        this.basicStorage$targetPos = null;
      }
    }
    if (action == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK) {
      if (this.basicStorage$targetPos != null && this.basicStorage$targetPos.equals(pos)) {
        int duration = this.gameTicks - this.basicStorage$startTick;
        BlockState state = this.level.getBlockState(pos);
        if (state.getBlock() instanceof CrateBlock) {
          if (duration <= 3) {
            CrateBlock.extractFromCrate(this.level, pos, this.player);
          }
        }
        this.basicStorage$targetPos = null;
        this.basicStorage$startTick = -1;
      }
    }
  }
}
