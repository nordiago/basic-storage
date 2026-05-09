package com.khazoda.basicstorage.command;

import com.khazoda.basicstorage.storage.CrateNetworkManager;
import com.khazoda.basicstorage.storage.NetworkFileManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;

import java.util.List;

public class CrateCommand {
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(Commands.literal("basicstorage")
            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
            .then(Commands.literal("networks")
                .then(Commands.literal("heal").executes(context -> {
                  ServerLevel level = context.getSource().getLevel();
                  int healed = CrateNetworkManager.get(level).verifyIntegrity(level);
                  context.getSource().sendSuccess(() -> Component.literal(String.format("Healed %d orphaned blocks in loaded chunks", healed)), true);
                  return healed;
                }))
                .then(Commands.literal("stats").executes(context -> {
                  ServerLevel level = context.getSource().getLevel();
                  var stats = CrateNetworkManager.get(level).getStats();
                  context.getSource().sendSuccess(() -> Component.literal(String.format("Networks: %d | Total Nodes: %d | Crates: %d | Stations: %d | Connectors: %d", stats.networkCount(), stats.totalNodes(), stats.totalCrates(), stats.totalStations(), stats.totalNodes() - stats.totalCrates() - stats.totalStations())), false);
                  return 1;
                }))
                .then(Commands.literal("recalculate")
                    .then(Commands.argument("from", BlockPosArgument.blockPos())
                        .then(Commands.argument("to", BlockPosArgument.blockPos()).executes(context -> {
                          ServerLevel level = context.getSource().getLevel();
                          BlockPos pos1 = BlockPosArgument.getLoadedBlockPos(context, "from");
                          BlockPos pos2 = BlockPosArgument.getLoadedBlockPos(context, "to");
                          int recalculated = CrateNetworkManager.get(level).recalculateArea(level, pos1, pos2);
                          context.getSource().sendSuccess(() -> Component.literal(String.format("Recalculated network for %d blocks", recalculated)), true);
                          return recalculated;
                        }))))
                .then(Commands.literal("purge").executes(context -> {
                  ServerLevel level = context.getSource().getLevel();
                  int purged = CrateNetworkManager.get(level).purgeOrphanedNetworks(level);
                  context.getSource().sendSuccess(() -> Component.literal(String.format("Purged %d orphaned networks in loaded chunks", purged)), true);
                  return purged;
                }))
                .then(Commands.literal("reset").executes(context -> {
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
                }))))
            /* START Temporary legacy root-file migration commands.
             * Remove after pre-26.1 root network files no longer need manual migration support. */
            .then(Commands.literal("migrate")
                .then(Commands.literal("run").executes(CrateCommand::runLegacyMigration))
                .then(Commands.literal("info").executes(CrateCommand::showLegacyMigrationInfo)))
        /* END temporary legacy root-file migration commands. */);
  }

  /* START Temporary legacy root-file migration command handlers.
   * Remove with the temporary migrate command registration above. */
  private static int runLegacyMigration(CommandContext<CommandSourceStack> context) {
    ServerLevel level = context.getSource().getLevel();
    CrateNetworkManager.ManualLegacyMigrationStatus status = CrateNetworkManager.get(level).startManualLegacyMigration(level);

    if (status.totalFiles() == 0) {
      context.getSource().sendSuccess(() -> Component.literal("No legacy network files found."), false);
      return 0;
    }

    if (!status.started() && status.running()) {
      context.getSource().sendSuccess(() -> Component.literal("Legacy network migration is already running."), false);
      return status.remainingFiles();
    }

    context.getSource().sendSuccess(() -> Component.literal("Attempting to migrate legacy networks in loaded chunks."), true);
    return status.totalFiles();
  }

  private static int showLegacyMigrationInfo(CommandContext<CommandSourceStack> context) {
    ServerLevel level = context.getSource().getLevel();
    BlockPos origin = BlockPos.containing(context.getSource().getPosition());
    List<NetworkFileManager.LegacyMigrationTarget> targets = CrateNetworkManager.get(level).findNearestLegacyMigrationTargets(origin, 3);

    if (targets.isEmpty()) {
      context.getSource().sendSuccess(() -> Component.literal("No legacy network positions found."), false);
      return 0;
    }

    context.getSource().sendSuccess(() -> Component.literal("If you've checked a set of coordinates in every dimension and find nothing, delete the file with the corresponding UUID from world/data/basicstorage/networks.").withColor(3631466), false);
    context.getSource().sendSuccess(() -> Component.literal("Possible legacy migration targets: (May point to another dimension!)"), false);
    context.getSource().sendSuccess(() -> Component.literal("\n"), false);
    for (int i = 0; i < targets.size(); i++) {
      NetworkFileManager.LegacyMigrationTarget target = targets.get(i);
      BlockPos pos = target.pos();
      String message = String.format("%d %d %d (%s)", pos.getX(), pos.getY(), pos.getZ(), describeOffset(origin, pos));
      String teleportCommand = String.format("/tp %d %d %d", pos.getX(), pos.getY(), pos.getZ());
      Component line = Component.literal(message)
          .withStyle(style -> style.withClickEvent(new ClickEvent.SuggestCommand(teleportCommand)))
          .append(Component.literal(" [uuid]").withStyle(style -> style
              .withColor(ChatFormatting.GRAY)
              .withHoverEvent(new HoverEvent.ShowText(Component.literal(target.networkId().toString())))));
      context.getSource().sendSuccess(() -> line, false);
    }
    return targets.size();
  }

  private static String describeOffset(BlockPos origin, BlockPos target) {
    List<String> parts = new java.util.ArrayList<>();
    appendDirection(parts, target.getX() - origin.getX(), "East", "West");
    appendDirection(parts, target.getZ() - origin.getZ(), "South", "North");
    return parts.isEmpty() ? "here" : String.join(", ", parts);
  }

  private static void appendDirection(List<String> parts, int delta, String positiveDirection, String negativeDirection) {
    if (delta == 0) {
      return;
    }

    int blocks = Math.abs(delta);
    String direction = delta > 0 ? positiveDirection : negativeDirection;
    parts.add(String.format("%d %s", blocks, direction));
  }
  /* END temporary legacy root-file migration command handlers. */
}
