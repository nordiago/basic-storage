package com.khazoda.basicstorage.renderer;

import com.khazoda.basicstorage.block.CrateBlock;
import com.khazoda.basicstorage.block.entity.CrateBlockEntity;
import com.khazoda.basicstorage.util.NumberFormatter;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.Orientation;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class CrateBlockEntityRenderer implements BlockEntityRenderer<CrateBlockEntity, CrateBlockEntityRenderState> {
  private static final Quaternionf ITEM_LIGHT_ROTATION_3D = RotationAxis.POSITIVE_X.rotationDegrees(-15).mul(RotationAxis.POSITIVE_Y.rotationDegrees(15));
  private final ItemModelManager itemModelManager;
  private final TextRenderer textRenderer;

  public CrateBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
    this.itemModelManager = context.itemModelManager();
    this.textRenderer = context.textRenderer();
  }

  @Override
  public CrateBlockEntityRenderState createRenderState() {
    return new CrateBlockEntityRenderState();
  }

  @Override
  public void updateRenderState(
      CrateBlockEntity be,
      CrateBlockEntityRenderState crateState,
      float progress,
      Vec3d camera,
      @Nullable ModelCommandRenderer.CrumblingOverlayCommand crumbling
  ) {
    BlockEntityRenderer.super.updateRenderState(be, crateState, progress, camera, crumbling);

    BlockState state = be.getCachedState();
    Orientation orientation = state.get(CrateBlock.ORIENTATION);
    crateState.orientation = orientation;
    Direction facingDir = orientation.getFacing();
    BlockPos pos = be.getPos();
    var world = be.getWorld();

    if (world != null) {
      BlockPos neighborPos = pos.offset(facingDir);
      BlockState neighborState = world.getBlockState(neighborPos);

      if (!Block.shouldDrawSide(state, neighborState, facingDir)) {
        crateState.itemRenderState = null;
        crateState.itemCount = 0;
        return;
      }
      crateState.lightmapCoordinates = WorldRenderer.getLightmapCoordinates(world, neighborPos);
    }

    if (be.storage.isResourceBlank()) {
      crateState.itemRenderState = null;
      return;
    }

    ItemStack itemStack = be.storage.getResource().toStack();

    ItemRenderState itemState = new ItemRenderState();
    this.itemModelManager.clearAndUpdate(
        itemState, itemStack, ItemDisplayContext.GUI, world, be, 0
    );
    crateState.itemRenderState = itemState;
    crateState.itemCount = be.storage.getAmount();
  }

  @Override
  public void render(CrateBlockEntityRenderState crateState, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
    ItemRenderState itemState = crateState.itemRenderState;

    if (itemState == null || crateState.itemCount == 0 || crateState.orientation == null) {
      return;
    }

    matrices.push();
    alignMatricesToOrientation(matrices, crateState.orientation);

    this.renderItem(crateState, itemState, matrices, queue);
    this.renderText(crateState, matrices, queue);

    matrices.pop();
  }

  private void renderItem(CrateBlockEntityRenderState crateState, ItemRenderState itemState, MatrixStack matrices, OrderedRenderCommandQueue queue) {
    matrices.push();
    matrices.translate(0f, 0.125f, 0f);
    matrices.scale(0.5f, 0.5f, 0.5f);
    matrices.scale(0.75f, 0.75f, 1);

    matrices.peek().getPositionMatrix().mul(new Matrix4f().scale(1, 1, 0.01f));
    matrices.peek().getNormalMatrix().identity();
    matrices.peek().getNormalMatrix().rotate(ITEM_LIGHT_ROTATION_3D);

    itemState.render(matrices, queue, crateState.lightmapCoordinates, OverlayTexture.DEFAULT_UV, 0);
    matrices.pop();
  }

  public void renderText(CrateBlockEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue) {
    String itemCount = String.valueOf(state.itemCount);
    String formattedCount = NumberFormatter.format(Integer.parseInt(itemCount));
    OrderedText orderedText = textRenderer.wrapLines(StringVisitable.plain(formattedCount), 128).get(0);

    matrices.push();
    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180));
    matrices.translate(0f, 0.21f, -0.01f);
    matrices.scale(0.02f, 0.02f, 0.02f);

    queue.submitText(
        matrices,
        -textRenderer.getWidth(formattedCount) / 2f,
        0,
        orderedText,
        false,
        TextLayerType.POLYGON_OFFSET,
        state.lightmapCoordinates,
        0xFFFFDD99,
        0,
        0
    );
    matrices.pop();
  }

  protected void alignMatricesToOrientation(MatrixStack matrices, Orientation orientation) {
    matrices.translate(0.5, 0.5, 0.5);
    switch (orientation) {
      case NORTH_UP -> matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
      case SOUTH_UP -> {
      }
      case EAST_UP -> matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90));
      case WEST_UP -> matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(270));
      case UP_NORTH -> {
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(0));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(270));
      }
      case UP_EAST -> {
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(270));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(270));
      }
      case UP_SOUTH -> {
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(270));
      }
      case UP_WEST -> {
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(270));
      }
      case DOWN_NORTH -> {
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
      }
      case DOWN_EAST -> {
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
      }
      case DOWN_SOUTH -> {
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
      }
      case DOWN_WEST -> {
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(270));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
      }
    }
    matrices.translate(0, 0, 0.51);
  }
}