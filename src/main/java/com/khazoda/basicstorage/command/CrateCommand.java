package com.khazoda.basicstorage.command;

import com.khazoda.basicstorage.storage.CrateNetworkManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;

public class CrateCommand {
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(Commands.literal("basicstorage").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)).then(Commands.literal("networks").then(Commands.literal("heal").executes(context -> {
      ServerLevel level = context.getSource().getLevel();
      int healed = CrateNetworkManager.get(level).verifyIntegrity(level);
      context.getSource().sendSuccess(() -> Component.literal(String.format("Healed %d orphaned blocks in loaded chunks", healed)), true);
      return healed;
    })).then(Commands.literal("stats").executes(context -> {
      ServerLevel level = context.getSource().getLevel();
      var stats = CrateNetworkManager.get(level).getStats();
      context.getSource().sendSuccess(() -> Component.literal(String.format("Networks: %d | Total Nodes: %d | Crates: %d | Stations: %d | Connectors: %d", stats.networkCount(), stats.totalNodes(), stats.totalCrates(), stats.totalStations(), stats.totalNodes() - stats.totalCrates() - stats.totalStations())), false);
      return 1;
    })).then(Commands.literal("recalculate").then(Commands.argument("from", BlockPosArgument.blockPos()).then(Commands.argument("to", BlockPosArgument.blockPos()).executes(context -> {
      ServerLevel level = context.getSource().getLevel();
      BlockPos pos1 = BlockPosArgument.getLoadedBlockPos(context, "from");
      BlockPos pos2 = BlockPosArgument.getLoadedBlockPos(context, "to");
      int recalculated = CrateNetworkManager.get(level).recalculateArea(level, pos1, pos2);
      context.getSource().sendSuccess(() -> Component.literal(String.format("Recalculated network for %d blocks", recalculated)), true);
      return recalculated;
    }))))));
  }
}
