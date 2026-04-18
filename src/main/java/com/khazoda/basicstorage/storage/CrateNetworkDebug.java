package com.khazoda.basicstorage.storage;

import com.khazoda.basicstorage.registry.BlockRegistry;
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
        player.sendSystemMessage(Component.literal("--- Network Diagnostics ---").withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("ID: ").withStyle(ChatFormatting.GRAY).append(Component.literal(network.id.toString()).withStyle(ChatFormatting.WHITE)));
        player.sendSystemMessage(Component.literal("Nodes: ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.valueOf(network.size())).withStyle(ChatFormatting.GREEN)).append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY)).append(Component.literal(network.crates().size() + " crates").withStyle(ChatFormatting.WHITE)).append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY)).append(Component.literal(network.stations().size() + " stations").withStyle(ChatFormatting.WHITE)).append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY)).append(Component.literal(network.connectors().size() + " connectors").withStyle(ChatFormatting.WHITE)).append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY)));
        var stats = manager.getStats();
        if (world.getBlockState(pos).is(BlockRegistry.CRATE_BLOCK)) {
          player.sendSystemMessage(Component.literal("Global Stats: ").withStyle(ChatFormatting.GRAY).append(Component.literal(stats.networkCount() + " networks").withStyle(ChatFormatting.BLUE)).append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY)).append(Component.literal(stats.totalCrates() + " total crates").withStyle(ChatFormatting.BLUE)));
        } else if (world.getBlockState(pos).is(BlockRegistry.CRATE_STATION_BLOCK)) {
          player.sendSystemMessage(Component.literal("Global Stats: ").withStyle(ChatFormatting.GRAY).append(Component.literal(stats.networkCount() + " networks").withStyle(ChatFormatting.BLUE)).append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY)).append(Component.literal(stats.totalStations() + " total stations").withStyle(ChatFormatting.BLUE)));
        } else if (world.getBlockState(pos).is(BlockRegistry.CRATE_CONNECTOR_BLOCK)) {
          player.sendSystemMessage(Component.literal("Global Stats: ").withStyle(ChatFormatting.GRAY).append(Component.literal(stats.networkCount() + " networks").withStyle(ChatFormatting.BLUE)).append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY)).append(Component.literal((stats.totalNodes() - stats.totalCrates() - stats.totalStations()) + " total connectors").withStyle(ChatFormatting.BLUE)));
        }
      } else {
        player.sendSystemMessage(Component.literal("No Network Detected").withStyle(ChatFormatting.RED));
      }
      return true;
    }
    return false;
  }
}
