package com.khazoda.basicstorage.renderer;

import com.khazoda.basicstorage.registry.DataComponentRegistry;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.NonNull;

import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class CrateItemSpecialRenderer implements SpecialModelRenderer<ItemStack> {

  private final BlockState baseCrateState;

  public CrateItemSpecialRenderer(BlockState baseCrateState) {
    this.baseCrateState = baseCrateState;
  }

  @Override
  public @Nullable ItemStack getData(ItemStack stack) {
    return stack;
  }

  @Override
  public void render(@Nullable ItemStack data, ItemDisplayContext displayContext, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, int overlay, boolean glint, int seed) {
    if (data == null) return;
    MinecraftClient client = MinecraftClient.getInstance();

    boolean hasContents = false;
    if (data.contains(DataComponentRegistry.CRATE_CONTENTS)) {
      var content = data.get(DataComponentRegistry.CRATE_CONTENTS);
      if (content != null && !content.item().isBlank()) {
        hasContents = true;
      }
    }

    matrices.push();

    if ((displayContext == ItemDisplayContext.GUI || displayContext == ItemDisplayContext.GROUND || displayContext.isFirstPerson()) && hasContents) {
      matrices.translate(0.5f, 0.5f, 0.5f);
      matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
      matrices.translate(-0.5f, -0.5f, -0.5f);
    }

    try (BufferAllocator allocator = new BufferAllocator(1536)) {
      VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);

      matrices.push();
      client.getBlockRenderManager().renderBlockAsEntity(this.baseCrateState, matrices, immediate, light, overlay);
      matrices.pop();
      immediate.draw();
    }

    if (hasContents) {
      var content = data.get(DataComponentRegistry.CRATE_CONTENTS);
      ItemStack innerStack = content.item().toStack();
      ItemRenderState innerItemState = new ItemRenderState();

      matrices.push();
      matrices.translate(0.5f, 0.5f, -0.01f);

      if (content.item().getItem() instanceof BlockItem) {
        matrices.scale(1.25f, 1.25f, 1.25f);
        matrices.translate(0f, 0f, 0.2f);
      } else {
        matrices.scale(0.75f, 0.75f, 0.75f);
      }

      client.getItemModelManager().update(innerItemState, innerStack, ItemDisplayContext.FIXED, client.world, null, seed);

      innerItemState.render(matrices, queue, light, overlay, seed);
      matrices.pop();
    }
    matrices.pop();
  }

  @Override
  public void collectVertices(Consumer<Vector3fc> consumer) {
    consumer.accept(new Vector3f(0.0f, 0.0f, 0.0f));
    consumer.accept(new Vector3f(1.0f, 1.0f, 1.0f));
  }

  @Environment(EnvType.CLIENT)
  public record Unbaked(Identifier blockId) implements SpecialModelRenderer.Unbaked {
    public static final MapCodec<Unbaked> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(Identifier.CODEC.fieldOf("block").forGetter(Unbaked::blockId)).apply(instance, Unbaked::new));

    @Override
    public MapCodec<? extends SpecialModelRenderer.Unbaked> getCodec() {
      return CODEC;
    }

    @Override
    public @NonNull SpecialModelRenderer<ItemStack> bake(SpecialModelRenderer.BakeContext context) {
      Block block = Registries.BLOCK.get(blockId);
      return new CrateItemSpecialRenderer(block.getDefaultState());
    }
  }
}