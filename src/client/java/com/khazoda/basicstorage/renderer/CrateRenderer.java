package com.khazoda.basicstorage.renderer;

import com.khazoda.basicstorage.block.CrateBlock;
import com.khazoda.basicstorage.block.entity.CrateBlockEntity;
import com.khazoda.basicstorage.util.NumberFormatter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class CrateRenderer implements BlockEntityRenderer<CrateBlockEntity, CrateRenderState> {

  private static final Quaternionf ITEM_LIGHT_ROTATION_3D = Axis.XP.rotationDegrees(-15).mul(Axis.YP.rotationDegrees(15));
  private final ItemModelResolver itemModelManager;
  private final Font textRenderer;

  public CrateRenderer(BlockEntityRendererProvider.Context context) {
    this.itemModelManager = context.itemModelResolver();
    this.textRenderer = context.font();
  }

  @Override
  public CrateRenderState createRenderState() {
    return new CrateRenderState();
  }

  @Override
  public void extractRenderState(CrateBlockEntity be, CrateRenderState crateState, float progress, Vec3 camera, @Nullable ModelFeatureRenderer.CrumblingOverlay crumbling) {
    BlockEntityRenderer.super.extractRenderState(be, crateState, progress, camera, crumbling);

    BlockState state = be.getBlockState();
    FrontAndTop orientation = state.getValue(CrateBlock.ORIENTATION);
    crateState.orientation = orientation;
    Direction facingDir = orientation.front();
    BlockPos pos = be.getBlockPos();
    var world = be.getLevel();

    if (world != null) {
      BlockPos neighborPos = pos.relative(facingDir);
      BlockState neighborState = world.getBlockState(neighborPos);

      if (!Block.shouldRenderFace(state, neighborState, facingDir)) {
        crateState.itemRenderState = null;
        crateState.itemCount = 0;
        return;
      }
      crateState.lightCoords = LevelRenderer.getLightColor(world, neighborPos);
    }

    if (be.storage.isResourceBlank()) {
      crateState.itemRenderState = null;
      crateState.itemCount = 0;
      crateState.cachedFormattedCount = null;
      crateState.cachedOrderedText = null;
      return;
    }

    ItemStack itemStack = be.storage.getResource().toStack();

    ItemStackRenderState itemState = new ItemStackRenderState();
    this.itemModelManager.updateForTopItem(itemState, itemStack, ItemDisplayContext.GUI, world, be, 0);
    crateState.itemRenderState = itemState;
    crateState.itemCount = be.storage.getAmount();

    crateState.cachedFormattedCount = NumberFormatter.format(crateState.itemCount);
    crateState.cachedOrderedText = textRenderer.split(FormattedText.of(crateState.cachedFormattedCount), 128).getFirst();
  }

  @Override
  public void submit(CrateRenderState crateState, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState camera) {
    ItemStackRenderState itemState = crateState.itemRenderState;

    if (crateState.orientation == null) return;

    matrices.pushPose();
    alignMatricesToOrientation(matrices, crateState.orientation);

    if (itemState != null) {
      this.renderItem(crateState, itemState, matrices, queue);
    }
    this.renderText(crateState, matrices, queue);

    matrices.popPose();
  }

  private void renderItem(CrateRenderState crateState, ItemStackRenderState itemState, PoseStack matrices, SubmitNodeCollector queue) {
    matrices.pushPose();
    matrices.translate(0f, 0.125f, 0f);
    matrices.scale(0.5f, 0.5f, 0.5f);
    matrices.scale(0.75f, 0.75f, 1);

    matrices.last().pose().mul(new Matrix4f().scale(1, 1, 0.01f));
    matrices.last().normal().identity();
    matrices.last().normal().rotate(ITEM_LIGHT_ROTATION_3D);

    itemState.submit(matrices, queue, crateState.lightCoords, OverlayTexture.NO_OVERLAY, 0);
    matrices.popPose();
  }

  public void renderText(CrateRenderState state, PoseStack matrices, SubmitNodeCollector queue) {
    if (state.cachedOrderedText == null || state.cachedFormattedCount == null) return;

    matrices.pushPose();
    matrices.mulPose(Axis.XP.rotationDegrees(180));
    matrices.translate(0f, 0.21f, -0.01f);
    matrices.scale(0.02f, 0.02f, 0.02f);

    int color = state.itemCount > 0 ? 0xFFFFDD99 : 0x22FFDD99;

    queue.submitText(matrices, -textRenderer.width(state.cachedFormattedCount) / 2f, 0, state.cachedOrderedText, false, DisplayMode.POLYGON_OFFSET, state.lightCoords, color, 0, 0);
    matrices.popPose();
  }

  protected void alignMatricesToOrientation(PoseStack matrices, FrontAndTop orientation) {
    matrices.translate(0.5, 0.5, 0.5);
    switch (orientation) {
      case NORTH_UP -> matrices.mulPose(Axis.YP.rotationDegrees(180));
      case SOUTH_UP -> {
      }
      case EAST_UP -> matrices.mulPose(Axis.YP.rotationDegrees(90));
      case WEST_UP -> matrices.mulPose(Axis.YP.rotationDegrees(270));
      case UP_NORTH -> {
        matrices.mulPose(Axis.YP.rotationDegrees(0));
        matrices.mulPose(Axis.XP.rotationDegrees(270));
      }
      case UP_EAST -> {
        matrices.mulPose(Axis.YP.rotationDegrees(270));
        matrices.mulPose(Axis.XP.rotationDegrees(270));
      }
      case UP_SOUTH -> {
        matrices.mulPose(Axis.YP.rotationDegrees(180));
        matrices.mulPose(Axis.XP.rotationDegrees(270));
      }
      case UP_WEST -> {
        matrices.mulPose(Axis.YP.rotationDegrees(90));
        matrices.mulPose(Axis.XP.rotationDegrees(270));
      }
      case DOWN_NORTH -> {
        matrices.mulPose(Axis.YP.rotationDegrees(180));
        matrices.mulPose(Axis.XP.rotationDegrees(90));
      }
      case DOWN_EAST -> {
        matrices.mulPose(Axis.YP.rotationDegrees(90));
        matrices.mulPose(Axis.XP.rotationDegrees(90));
      }
      case DOWN_SOUTH -> {
        matrices.mulPose(Axis.XP.rotationDegrees(90));
      }
      case DOWN_WEST -> {
        matrices.mulPose(Axis.YP.rotationDegrees(270));
        matrices.mulPose(Axis.XP.rotationDegrees(90));
      }
    }
    matrices.translate(0, 0, 0.51);
  }
}
