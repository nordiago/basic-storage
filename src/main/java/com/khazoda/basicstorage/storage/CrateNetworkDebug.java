package com.khazoda.basicstorage.storage;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class CrateNetworkDebug {

  public static boolean debugStickUsed(Level world, Player player, BlockPos pos) {
    ItemStack playerStack = player.getMainHandItem();
    if (!world.isClientSide() && player.isCreative() && playerStack.is(Items.DEBUG_STICK)) {
      CrateNetworkManager manager = CrateNetworkManager.get((ServerLevel) world);
      CrateNetwork network = manager.getNetworkFor(pos);
      if (network != null) {
        player.displayClientMessage(Component.literal("--- Network Diagnostics ---").withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(Component.literal("ID: ").withStyle(ChatFormatting.GRAY).append(Component.literal(network.id.toString()).withStyle(ChatFormatting.WHITE)), false);
        player.displayClientMessage(Component.literal("Nodes: ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.valueOf(network.size())).withStyle(ChatFormatting.GREEN)).append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY)).append(Component.literal(network.crates().size() + " crates").withStyle(ChatFormatting.WHITE)).append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY)).append(Component.literal(network.stations().size() + " stations").withStyle(ChatFormatting.WHITE)).append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY)).append(Component.literal(network.connectors().size() + " connectors").withStyle(ChatFormatting.WHITE)).append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY)), false);
        var stats = manager.getStats();
        player.displayClientMessage(Component.literal("Global Stats: ").withStyle(ChatFormatting.GRAY).append(Component.literal(stats.networkCount() + " networks").withStyle(ChatFormatting.BLUE)).append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY)).append(Component.literal(stats.totalCrates() + " total crates").withStyle(ChatFormatting.BLUE)), false);
      } else {
        player.displayClientMessage(Component.literal("No Network Detected").withStyle(ChatFormatting.RED), false);
      }
      return true;
    }
    return false;
  }
}
