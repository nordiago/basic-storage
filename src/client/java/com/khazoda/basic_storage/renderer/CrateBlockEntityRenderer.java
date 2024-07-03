package com.khazoda.basic_storage.renderer;

import com.khazoda.basic_storage.block.CrateBlock;
import com.khazoda.basic_storage.block.entity.CrateBlockEntity;
import com.khazoda.basic_storage.util.NumberFormatter;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.Objects;

public class CrateBlockEntityRenderer implements BlockEntityRenderer<CrateBlockEntity> {
  private static final Quaternionf ITEM_LIGHT_ROTATION_3D = RotationAxis.POSITIVE_X.rotationDegrees(-125).mul(RotationAxis.POSITIVE_Y.rotationDegrees(260));
  private static final Quaternionf ITEM_LIGHT_ROTATION_FLAT = RotationAxis.POSITIVE_X.rotationDegrees(-225);

  private final ItemRenderer itemRenderer;
  private final TextRenderer textRenderer;

  public CrateBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
    this.itemRenderer = context.getItemRenderer();
    this.textRenderer = context.getTextRenderer();
  }

  @Override
  public void render(CrateBlockEntity be, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
    var horizontalDir = be.getCachedState().get(CrateBlock.FACING);
    var dir = CrateBlock.getFront(be.getCachedState());
    var world = be.getWorld();

    ItemStack crateItem = be.getStack();
    ItemVariant itemVariant = ItemVariant.of(crateItem);
    String count = String.valueOf(be.getStack().getCount());
    BlockPos pos = be.getPos();

    if (!shouldRenderBE(be, dir)) return;

    matrices.push();
    alignMatrices(matrices, horizontalDir, BlockFace.WALL);

    light = WorldRenderer.getLightmapCoordinates(Objects.requireNonNull(be.getWorld()), pos.offset(dir));
    renderCrateInfo(itemVariant, count, matrices, vertexConsumers, light, (int) pos.asLong(), pos, world);
    matrices.pop();
  }

  public void renderCrateInfo(ItemVariant item, @Nullable String amount, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int seed, BlockPos pos, World world) {
    var player = MinecraftClient.getInstance().player;
    var playerPos = player == null ? Vec3d.ofCenter(pos) : player.getPos();
    if (amount == null || amount.equals("0")) {
      return;
    }
    if (pos.isWithinDistance(playerPos, 40))
      renderText(amount, light, matrices, vertexConsumers);
    if (pos.isWithinDistance(playerPos, 40))
      renderItem(item, light, matrices, vertexConsumers, world, seed);
  }

  public void renderItem(ItemVariant item, int light, MatrixStack matrices, VertexConsumerProvider vertexConsumers, World world, int seed) {
    if (item.isBlank()) return;

    matrices.push();
    matrices.scale(0.5f, 0.5f, 0.5f);
    matrices.scale(0.75f, 0.75f, 1);
    matrices.peek().getPositionMatrix().mul(new Matrix4f().scale(1, 1, 0.01f));

    var stack = item.toStack();
    var model = itemRenderer.getModel(stack, world, null, seed);

    if (model.isSideLit()) {
      matrices.peek().getNormalMatrix().rotate(ITEM_LIGHT_ROTATION_3D);
      DiffuseLighting.enableGuiDepthLighting();
    } else {
      matrices.peek().getNormalMatrix().rotate(ITEM_LIGHT_ROTATION_FLAT);
      DiffuseLighting.disableGuiDepthLighting();
    }

    itemRenderer.renderItem(stack, ModelTransformationMode.GUI, false, matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV, model);
    matrices.pop();
  }

  public void renderText(String count, int light, MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
    matrices.push();
    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180));
    matrices.translate(0, 0.25, -0.01);

    String formattedCount = NumberFormatter.format(Integer.parseInt(count));

    matrices.scale(0.02f, 0.02f, 0.02f);
    textRenderer.draw(formattedCount, -textRenderer.getWidth(formattedCount) / 2f, 0, 0xffffff, false, matrices.peek().getPositionMatrix(), vertexConsumers, TextRenderer.TextLayerType.NORMAL, 0x000000, light);
    matrices.pop();
  }

  protected void alignMatrices(MatrixStack matrices, Direction dir, BlockFace face) {
    var pos = switch (face) {
      case FLOOR -> Direction.UP.getUnitVector();
      case CEILING -> Direction.DOWN.getUnitVector();
      default -> dir.getUnitVector();
    };
    matrices.translate(pos.x / 2 + 0.5, pos.y / 2 + 0.5, pos.z / 2 + 0.5);
    // We only transform the position matrix as the normals have to stay in the old configuration for item lighting
    matrices.peek().getPositionMatrix().rotate(dir.getRotationQuaternion());
    switch (face) {
      case FLOOR -> matrices.peek().getPositionMatrix().rotate(RotationAxis.POSITIVE_X.rotationDegrees(-90));
      case CEILING -> matrices.peek().getPositionMatrix().rotate(RotationAxis.POSITIVE_X.rotationDegrees(90));
    }
    matrices.peek().getPositionMatrix().rotate(RotationAxis.POSITIVE_X.rotationDegrees(-90));
    matrices.translate(0, 0, 0.01);
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  public final boolean shouldRenderBE(BlockEntity be, Direction facing) {
    var world = be.getWorld();
    if (world == null) return false;
    var pos = be.getPos();
    var state = be.getCachedState();

    return Block.shouldDrawSide(state, world, pos, facing, pos.offset(facing));
  }
}