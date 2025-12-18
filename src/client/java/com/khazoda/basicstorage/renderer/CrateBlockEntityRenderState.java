package com.khazoda.basicstorage.renderer;

import net.minecraft.block.enums.Orientation;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;

public class CrateBlockEntityRenderState extends BlockEntityRenderState {
  public ItemRenderState itemRenderState = new ItemRenderState();
  public long itemCount = 0;
  public Orientation orientation;

  public CrateBlockEntityRenderState() {
  }
}
