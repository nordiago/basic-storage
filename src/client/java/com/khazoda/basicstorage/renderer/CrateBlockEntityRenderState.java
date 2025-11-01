package com.khazoda.basicstorage.renderer;

import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;

public class CrateBlockEntityRenderState extends BlockEntityRenderState {
  public ItemRenderState itemRenderState = new ItemRenderState();
  public long itemCount = 0;

  public CrateBlockEntityRenderState() { }
}
