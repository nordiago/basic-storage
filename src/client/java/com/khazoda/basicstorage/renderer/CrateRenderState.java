package com.khazoda.basicstorage.renderer;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.FrontAndTop;
import net.minecraft.util.FormattedCharSequence;

public class CrateRenderState extends BlockEntityRenderState {

  public ItemStackRenderState itemRenderState = new ItemStackRenderState();
  public long itemCount = 0;
  public FormattedCharSequence cachedOrderedText;
  public String cachedFormattedCount;
  public int lightCoords = 0;
  public FrontAndTop orientation;

  public CrateRenderState() {
  }
}
