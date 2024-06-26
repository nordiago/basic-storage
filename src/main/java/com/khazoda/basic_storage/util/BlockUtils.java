package com.khazoda.basic_storage.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class BlockUtils {

  public static BlockHitResult getHitResult(PlayerEntity player, BlockPos target) {
    final Vec3d castOrigin = player.getEyePos();
    final double castLength = Vec3d.ofCenter(target).subtract(castOrigin).length() + 1;
    final Vec3d playerRotation = player.getRotationVector();
    final Vec3d castTarget = castOrigin.add(playerRotation.multiply(castLength));
    
    return player.getWorld().raycast(new RaycastContext(castOrigin, castTarget, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
  }

  public static int getQuadrant(Direction facing, Vec3d hit) {
    // We want to transform this 3D location to 2D so that we can more easily check which quadrant is hit
    double x = getXFromHit(facing, hit);
    double y = getYFromHit(facing, hit);
    // Calculate the correct quadrant
    int quadrant = 0;
    if (x < .5 && y > .5) {
      quadrant = 1;
    } else if (x > .5 && y < .5) {
      quadrant = 2;
    } else if (x > .5 && y > .5) {
      quadrant = 3;
    }
    return quadrant;
  }

  private static double getYFromHit(Direction facing, Vec3d hit) {
    return switch (facing) {
      case UP -> 1 - hit.x;
      case DOWN -> 1 - hit.x;
      case NORTH -> 1 - hit.x;
      case SOUTH -> hit.x;
      case WEST -> hit.z;
      case EAST -> 1 - hit.z;
    };
  }

  private static double getXFromHit(Direction facing, Vec3d hit) {
    return switch (facing) {
      case UP -> hit.z;
      case DOWN -> 1 - hit.z;
      case NORTH -> hit.y;
      case SOUTH -> hit.y;
      case WEST -> hit.y;
      case EAST -> hit.y;
    };
  }
}
