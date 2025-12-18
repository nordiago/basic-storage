package com.khazoda.basicstorage.renderer;

import com.khazoda.basicstorage.Constants;
import com.khazoda.basicstorage.registry.DataComponentRegistry;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;

import java.util.Objects;

public class CrateItemRenderer implements ModelLoadingPlugin {
  public static final Identifier CRATE_ID = Identifier.of(Constants.BS_NAMESPACE, "block/crate");
  private static final Quaternionf ITEM_LIGHT_ROTATION_3D = RotationAxis.POSITIVE_X.rotationDegrees(-15).mul(RotationAxis.POSITIVE_Y.rotationDegrees(15));
  private static final Quaternionf ITEM_LIGHT_ROTATION_FLAT = RotationAxis.POSITIVE_X.rotationDegrees(-45);

  // @Override
  public void render(ItemStack stack, ItemDisplayContext mode, MatrixStack matrices, VertexConsumerProvider vertexConsumerProvider, int light, int overlay) {
    var client = MinecraftClient.getInstance();
    ItemRenderer itemRenderer = client.getItemRenderer();

    if (!mode.equals(ItemDisplayContext.GUI) || !stack.contains(DataComponentRegistry.CRATE_CONTENTS)) {
      // Render crate crateModel normally
      renderCrate(stack, mode, matrices, vertexConsumerProvider, light, overlay, itemRenderer, false);
    } else {
      // Render create crateModel in GUI with extra information
      if (stack.contains(DataComponentRegistry.CRATE_CONTENTS)) {
        renderCrate(stack, mode, matrices, vertexConsumerProvider, light, overlay, itemRenderer, true);
        ItemVariant item = Objects.requireNonNull(stack.get(DataComponentRegistry.CRATE_CONTENTS)).item();
        renderCrateContents(itemRenderer, item, light, matrices, vertexConsumerProvider);
      }
    }
  }

  private void renderCrate(ItemStack stack, ItemDisplayContext context, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, ItemRenderer itemRenderer, boolean hasContents) {
    matrices.push();
    matrices.translate(.5, .5, .5);
//    itemRenderer.renderItem(context, matrices, vertexConsumers, light, overlay, null, null, RenderLayer.getSolid(), ItemRenderState.Glint.NONE);

    // crateModel.getTransformation().getTransformation(mode).apply(false, matrices);
    matrices.pop();
  }

  @SuppressWarnings("UnreachableCode")
  private void renderCrateContents(ItemRenderer itemRenderer, ItemVariant item, int light, MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
    if (item.isBlank()) return;

    matrices.push();
    matrices.translate(0.5f, 0.5f, 1f);
    matrices.scale(0.7f, 0.7f, 1f);
    var stack = item.toStack();
    // var model = itemRenderer.getModel(stack, null, null, 0);

    var lights = RenderSystem.getShaderLights();

    matrices.peek().getNormalMatrix().rotate(ITEM_LIGHT_ROTATION_3D);

    // itemRenderer.renderItem(stack, ModelTransformationMode.GUI, false, matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV, model);

    RenderSystem.setShaderLights(lights);
    matrices.pop();
  }

  @Override
  public void initialize(Context pluginContext) {
    // pluginContext.addModels(CRATE_ID);
  }
}
