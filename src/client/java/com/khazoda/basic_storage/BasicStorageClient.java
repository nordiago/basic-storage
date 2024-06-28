package com.khazoda.basic_storage;

import com.khazoda.basic_storage.registry.BlockEntityRegistry;
import com.khazoda.basic_storage.registry.BlockRegistry;
import com.khazoda.basic_storage.renderer.CrateBlockEntityRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public class BasicStorageClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		BlockEntityRendererFactories.register(BlockEntityRegistry.CRATE_BLOCK_ENTITY, CrateBlockEntityRenderer::new);
	}
}