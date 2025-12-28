package com.khazoda.basicstorage.util;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class BlockUtils {

  public static BlockHitResult getHitResult(Player player, BlockPos target) {
    final Vec3 castOrigin = player.getEyePosition();
    final double castLength = Vec3.atCenterOf(target).subtract(castOrigin).length() + 1;
    final Vec3 playerRotation = player.getLookAngle();
    final Vec3 castTarget = castOrigin.add(playerRotation.scale(castLength));

    return player.level().clip(new ClipContext(castOrigin, castTarget, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
  }

  public static int getComparatorOutputStrength(int itemStackCount) {
    return Mth.floor(itemStackCount % 16);
  }
}
