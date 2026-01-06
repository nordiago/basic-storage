package com.khazoda.basicstorage;

import com.khazoda.basicstorage.config.ConfigSyncPayload;
import com.khazoda.basicstorage.particle.TwinkleParticle;
import com.khazoda.basicstorage.particle.VoidyParticle;
import com.khazoda.basicstorage.registry.BlockEntityRegistry;
import com.khazoda.basicstorage.registry.ParticleRegistry;
import com.khazoda.basicstorage.renderer.CrateItemSpecialRenderer;
import com.khazoda.basicstorage.renderer.CrateRenderer;
import com.khazoda.basicstorage.renderer.ParticleBeamRendering;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;

public class BasicStorageClient implements ClientModInitializer {

  static {
    SpecialModelRenderers.ID_MAPPER.put(Identifier.fromNamespaceAndPath(Constants.NAMESPACE, "crate_renderer"), CrateItemSpecialRenderer.Unbaked.CODEC);
  }

  @Override
  public void onInitializeClient() {
    BasicStorageClientConfig.INSTANCE.load();

    /* Load server's config */
    ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.ID, (payload, context) -> {
      context.client().execute(() -> {
        BasicStorageConfig.INSTANCE.setBreakWithAxeOnly(payload.breakWithAxeOnly());
        BasicStorageConfig.INSTANCE.setCanBreakIfFull(payload.canBreakIfFull());
        Constants.LOG.info("Synced config from server: Axe Only = {}", payload.breakWithAxeOnly());
        Constants.LOG.info("Synced config from server: Can Break If Full = {}", payload.canBreakIfFull());
      });
    });

    ParticleBeamRendering.INSTANCE.registerParticleClientEvents();

    /* Revert to client's config */
    ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
      BasicStorageConfig.INSTANCE.load();
    });

    /* Register crate item contents renderer */
    BlockEntityRenderers.register(BlockEntityRegistry.CRATE_BLOCK_ENTITY, CrateRenderer::new);

    /* Register custom particles */
    ParticleFactoryRegistry.getInstance().register(ParticleRegistry.TWINKLE, TwinkleParticle.Factory::new);
    ParticleFactoryRegistry.getInstance().register(ParticleRegistry.VOIDY, VoidyParticle.Factory::new);
  }
}
