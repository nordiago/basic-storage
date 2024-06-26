package com.khazoda.basic_storage;

import com.khazoda.basic_storage.registry.*;
import net.fabricmc.api.ModInitializer;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.khazoda.basic_storage.Constants.BS_LOG;

public class BasicStorage implements ModInitializer {

	public static int loadedRegistries = 0;

	@Override
	public void onInitialize() {
		BS_LOG.info("[Basic Storage] Filling crates...");

		BlockRegistry.init();
		BlockEntityRegistry.init();
		ItemGroupRegistry.init();
		EventRegistry.init();
		DataComponentRegistry.init();

		BS_LOG.info("[Basic Storage] {}/3 Crates filled!", loadedRegistries);
	}
}