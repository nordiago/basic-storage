package com.khazoda.basicstorage;

import com.khazoda.basicstorage.config.ConfigSyncPayload;
import com.khazoda.basicstorage.packet.StationBeamPayload;
import com.khazoda.basicstorage.registry.*;
import com.khazoda.basicstorage.storage.CrateNetworkManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.item.v1.ComponentTooltipAppenderRegistry;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Items;

public class BasicStorage implements ModInitializer {

  public static final CreativeModeTab BW_ITEMGROUP = ItemGroupRegistry.createItemGroup();

  @Override
  public void onInitialize() {
    PayloadTypeRegistry.playS2C().register(ConfigSyncPayload.ID, ConfigSyncPayload.CODEC);
    PayloadTypeRegistry.playS2C().register(StationBeamPayload.ID, StationBeamPayload.CODEC);
    ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
      boolean serverBreakWithAxeOnly = BasicStorageConfig.getInstance().breakWithAxeOnly();
      boolean serverCanBreakIfFull = BasicStorageConfig.getInstance().canBreakIfFull();

      ServerPlayNetworking.send(handler.getPlayer(), new ConfigSyncPayload(serverBreakWithAxeOnly, serverCanBreakIfFull));
    });

    ServerWorldEvents.UNLOAD.register((server, world) -> {
      CrateNetworkManager.clearCache(world);
    });

    ServerTickEvents.END_WORLD_TICK.register(world -> {
      if (world instanceof ServerLevel serverLevel) {
        CrateNetworkManager.get(serverLevel).tick(serverLevel);
      }
    });

    CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
      com.khazoda.basicstorage.command.CrateCommand.register(dispatcher);
    });
    BasicStorageConfig.getInstance().load();
    Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Identifier.parse(Constants.NAMESPACE), BW_ITEMGROUP);
    BlockRegistry.init();
    BlockEntityRegistry.init();
    ParticleRegistry.init();
    SoundRegistry.init();
    EventRegistry.init();
    DataComponentRegistry.init();

    ComponentTooltipAppenderRegistry.addFirst(DataComponentRegistry.CRATE_CONTENTS);

    ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(content -> content.addAfter(Items.BARREL, BlockRegistry.CRATE_BLOCK, BlockRegistry.CRATE_STATION_FRAME_BLOCK, BlockRegistry.CRATE_STATION_BLOCK, BlockRegistry.CRATE_CONNECTOR_BLOCK));
    ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(content -> content.addAfter(Items.BARREL, BlockRegistry.CRATE_BLOCK, BlockRegistry.CRATE_STATION_FRAME_BLOCK, BlockRegistry.CRATE_STATION_BLOCK, BlockRegistry.CRATE_CONNECTOR_BLOCK));
    Constants.LOG.info("- Basic Storage Loaded -");
  }
}
