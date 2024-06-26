package com.khazoda.basic_storage.registry;

import com.khazoda.basic_storage.BasicStorage;
import com.khazoda.basic_storage.block.CrateBlock;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;


public class EventRegistry {

  public static void init() {

    AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
      if (player.isSpectator()) return ActionResult.PASS;
      if (world.getBlockState(pos).getBlock() instanceof CrateBlock) {
        return ActionResult.PASS;
      }
      return ActionResult.PASS;
    });

    CrateBlock.initOnUseMethod();
    BasicStorage.loadedRegistries += 1;
  }
}
