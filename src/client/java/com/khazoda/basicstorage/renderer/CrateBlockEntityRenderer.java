package com.khazoda.basicstorage.renderer;

import com.khazoda.basicstorage.block.CrateBlock;
import com.khazoda.basicstorage.block.entity.CrateBlockEntity;
import com.khazoda.basicstorage.util.NumberFormatter;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.Objects;

public class CrateBlockEntityRenderer implements BlockEntityRenderer<CrateBlockEntity, CrateBlockEntityRenderState> {
  private final ItemModelManager itemModelManager;
  private final TextRenderer textRenderer;

  public CrateBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
    this.itemModelManager = context.itemModelManager();
    this.textRenderer = context.textRenderer();
  }

  public CrateBlockEntityRenderState createRenderState() {
    return new CrateBlockEntityRenderState();
  }

  public void updateRenderState(
    CrateBlockEntity be,
    CrateBlockEntityRenderState crateState,
    float progress,
    Vec3d camera,
    @Nullable ModelCommandRenderer.CrumblingOverlayCommand crumbling
  ) {
      BlockEntityRenderer.super.updateRenderState(be, crateState, progress, camera, crumbling);

      if (be.storage.isResourceBlank()) {
        return;
      }

      ItemStack itemStack = be.storage.getResource().toStack();

      ItemRenderState itemState = new ItemRenderState();
      this.itemModelManager.clearAndUpdate(
        itemState, itemStack, ItemDisplayContext.GUI, be.getWorld(), be, 0
      );
      crateState.itemRenderState = itemState;
      crateState.itemCount = be.storage.getAmount();

      Direction dir = CrateBlock.getFront(be.getCachedState());
      crateState.lightmapCoordinates = WorldRenderer.getLightmapCoordinates(Objects.requireNonNull(be.getWorld()), be.getPos().offset(dir));
   }

  public void render(CrateBlockEntityRenderState crateState, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
    Direction direction = (Direction) crateState.blockState.get(CrateBlock.FACING);
    float rotation = direction.getAxis().isHorizontal() ? -direction.getPositiveHorizontalDegrees() : 180.0F;

    ItemRenderState itemState = crateState.itemRenderState;

    if (itemState == null || crateState.itemCount == 0) {
      return;
    }

    alignMatrices(matrices, direction);

    this.renderItem(crateState, itemState, matrices, queue, rotation);
    this.renderText(crateState, matrices, queue);
   }

  private void renderItem(CrateBlockEntityRenderState crateState, ItemRenderState itemState, MatrixStack matrices, OrderedRenderCommandQueue queue, float rotation) {
    matrices.push();
    matrices.translate(0f, 0.125f, 0f);
    matrices.scale(0.5f, 0.5f, 0.5f);
    matrices.scale(0.75f, 0.75f, 1);
    matrices.peek().getPositionMatrix().mul(new Matrix4f().scale(1, 1, 0.01f));
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
    queue.submitText(matrices, -textRenderer.getWidth(formattedCount) / 2f, 0, orderedText, false, TextLayerType.POLYGON_OFFSET, state.lightmapCoordinates, 0xFFFFDD99, 0, 0);
    matrices.pop();
  }

  protected void alignMatrices(MatrixStack matrices, Direction dir) {
    var pos = dir.getUnitVector();
    matrices.translate(pos.x / 2 + 0.5, pos.y / 2 + 0.5, pos.z / 2 + 0.5);
    matrices.peek().getPositionMatrix().rotate(dir.getRotationQuaternion());
    matrices.peek().getPositionMatrix().rotate(RotationAxis.POSITIVE_X.rotationDegrees(-90));
    matrices.translate(0, 0, 0.01);
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  public final boolean shouldRenderBE(BlockEntity be, Direction facing) {
    var world = be.getWorld();
    if (world == null) return false;
    var pos = be.getPos();
    var state = be.getCachedState();

    return Block.shouldDrawSide(state, world.getBlockState(pos.offset(facing)), facing);
  }
}
