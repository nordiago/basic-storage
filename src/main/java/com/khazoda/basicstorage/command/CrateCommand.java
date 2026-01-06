package com.khazoda.basicstorage.command;

import com.khazoda.basicstorage.storage.CrateNetworkManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
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
    })))).then(Commands.literal("purge").executes(context -> {
      ServerLevel level = context.getSource().getLevel();
      int purged = CrateNetworkManager.get(level).purgeOrphanedNetworks(level);
      context.getSource().sendSuccess(() -> Component.literal(String.format("Purged %d orphaned networks with no valid blocks", purged)), true);
      return purged;
    })).then(Commands.literal("reset").executes(context -> {
      // Show warning and usage when no confirmation provided
      context.getSource().sendFailure(Component.literal("§c§l[WARNING] This will DELETE ALL networks in the ENTIRE WORLD!"));
      context.getSource().sendFailure(Component.literal("§eYou will need to manually recalculate networks area by area."));
      context.getSource().sendFailure(Component.literal("§7To confirm, use: §f/basicstorage networks reset CONFIRM"));
      return 0;
    }).then(Commands.argument("confirmation", StringArgumentType.word()).executes(context -> {
      String confirmation = StringArgumentType.getString(context, "confirmation");

      if (!confirmation.equals("CONFIRM")) {
        context.getSource().sendFailure(Component.literal("§cInvalid confirmation. Type exactly: §fCONFIRM"));
        return 0;
      }

      ServerLevel level = context.getSource().getLevel();
      CrateNetworkManager manager = CrateNetworkManager.get(level);
      int deleted = manager.resetAllNetworks();

      manager.save(level);

      context.getSource().sendSuccess(() -> Component.literal(String.format("§c§l[RESET] Deleted ALL %d networks!", deleted)), true);
      context.getSource().sendSuccess(() -> Component.literal("§eUse /basicstorage networks recalculate <from> <to> to rebuild networks."), false);
      return deleted;
    })))));
  }
}
