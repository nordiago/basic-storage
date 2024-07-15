package com.khazoda.basicstorage;

import com.khazoda.basicstorage.registry.BlockEntityRegistry;
import com.khazoda.basicstorage.registry.BlockRegistry;
import com.khazoda.basicstorage.registry.DataComponentRegistry;
import com.khazoda.basicstorage.renderer.CrateBlockEntityRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.item.ClampedModelPredicateProvider;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.util.Identifier;

public class BasicStorageClient implements ClientModInitializer {
  /* These two fields allow for a predicate that changes the crate item's model if it has items in it */
  public static final Identifier HAS_ITEMS_ID = Identifier.of(Constants.BS_NAMESPACE, "has_items");
  public static final ClampedModelPredicateProvider HAS_ITEMS = ((stack, world, entity, seed) -> stack.get(DataComponentRegistry.CRATE_CONTENTS) == null ? 0 : 1);

  @Override
  public void onInitializeClient() {
    BlockEntityRendererFactories.register(BlockEntityRegistry.CRATE_BLOCK_ENTITY, CrateBlockEntityRenderer::new);

    ModelPredicateProviderRegistry.register(BlockRegistry.CRATE_BLOCK.asItem(), HAS_ITEMS_ID, HAS_ITEMS);
  }
}