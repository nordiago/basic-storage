package com.khazoda.basicstorage.renderer;

import com.khazoda.basicstorage.registry.DataComponentRegistry;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class CrateItemSpecialRenderer implements SpecialModelRenderer<ItemStack> {
  public static final ScopedValue<ItemDisplayContext> CONTEXT = ScopedValue.newInstance();
  private final BlockState baseCrateState;

  public CrateItemSpecialRenderer(BlockState baseCrateState) {
    this.baseCrateState = baseCrateState;
  }

  @Override
  public @Nullable ItemStack extractArgument(ItemStack stack) {
    return stack;
  }

  @Override
  public void submit(@Nullable ItemStack data, PoseStack matrices, SubmitNodeCollector queue, int light, int overlay, boolean glint, int outlineColor) {
    if (data == null) return;
    if (!CONTEXT.isBound()) return;
    var displayContext = CONTEXT.get();
    ScopedValue.where(CONTEXT, null).run(() -> {
        Minecraft client = Minecraft.getInstance();

        boolean hasContents = false;
        if (data.has(DataComponentRegistry.CRATE_CONTENTS)) {
            var content = data.get(DataComponentRegistry.CRATE_CONTENTS);
            if (content != null && !content.item().isBlank()) {
                hasContents = true;
            }
        }

        matrices.pushPose();

        if ((displayContext == ItemDisplayContext.GUI || displayContext == ItemDisplayContext.GROUND || displayContext.firstPerson()) && hasContents) {
            matrices.translate(0.5f, 0.5f, 0.5f);
            matrices.mulPose(Axis.XP.rotationDegrees(90));
            matrices.translate(-0.5f, -0.5f, -0.5f);
        }

        matrices.pushPose();
        BlockModelRenderState state = new BlockModelRenderState();
        client.getModelManager().getBlockModelSet().get(this.baseCrateState).update(state, this.baseCrateState, BlockDisplayContext.create(), 45);
        state.submit(matrices, queue, light, overlay, outlineColor);
        matrices.popPose();

        if (hasContents) {
            var content = data.get(DataComponentRegistry.CRATE_CONTENTS);
            ItemStack innerStack = content.item().toStack();
            ItemStackRenderState innerItemState = new ItemStackRenderState();

            client.getItemModelResolver().appendItemLayers(innerItemState, innerStack, ItemDisplayContext.FIXED, client.level, null, 99);

            matrices.pushPose();
            matrices.translate(0.5f, 0.5f, -0.01f);

            if (innerItemState.usesBlockLight()) {
                /* Proper Block */
                matrices.mulPose(Axis.XP.rotationDegrees(90));
                matrices.scale(1.25f, 1.25f, 1.25f);
                matrices.translate(0f, 0.23f, 0f);
            } else {
                /* Item Sprite */
                matrices.scale(0.75f, 0.75f, 0.75f);
            }
            innerItemState.submit(matrices, queue, light, overlay, outlineColor);
            matrices.popPose();
        }
        matrices.popPose();
    });
  }

  @Override
  public void getExtents(Consumer<Vector3fc> consumer) {
    consumer.accept(new Vector3f(0.0f, 0.0f, 0.0f));
    consumer.accept(new Vector3f(1.0f, 1.0f, 1.0f));
  }

  @Environment(EnvType.CLIENT)
  public record Unbaked(Identifier blockId) implements SpecialModelRenderer.Unbaked<ItemStack> {
    public static final MapCodec<com.khazoda.basicstorage.renderer.CrateItemSpecialRenderer.Unbaked> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(Identifier.CODEC.fieldOf("block").forGetter(com.khazoda.basicstorage.renderer.CrateItemSpecialRenderer.Unbaked::blockId)).apply(instance, com.khazoda.basicstorage.renderer.CrateItemSpecialRenderer.Unbaked::new));

    @Override
    public MapCodec<? extends SpecialModelRenderer.Unbaked<ItemStack>> type() {
      return CODEC;
    }

    @Override
    public @NonNull SpecialModelRenderer<ItemStack> bake(SpecialModelRenderer.BakingContext context) {
      Block block = BuiltInRegistries.BLOCK.getValue(blockId);
      return new CrateItemSpecialRenderer(block.defaultBlockState());
    }
  }
}
