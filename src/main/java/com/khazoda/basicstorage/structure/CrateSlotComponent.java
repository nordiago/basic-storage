package com.khazoda.basicstorage.structure;

import com.khazoda.basicstorage.util.NumberFormatter;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.component.ComponentsAccess;
import net.minecraft.item.Item;
import net.minecraft.item.tooltip.TooltipAppender;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public record CrateSlotComponent(ItemVariant item, int count) implements TooltipAppender {
  public static final CrateSlotComponent DEFAULT = new CrateSlotComponent(ItemVariant.blank(), 0);

  public static final Codec<CrateSlotComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
      ItemVariant.CODEC.fieldOf("item").forGetter(CrateSlotComponent::item),
      Codec.INT.fieldOf("count").forGetter(CrateSlotComponent::count)
  ).apply(instance, CrateSlotComponent::new));

  public static final PacketCodec<RegistryByteBuf, CrateSlotComponent> PACKET_CODEC = PacketCodecs.registryCodec(CODEC);

  /**
   * Applies custom tooltip showing crate contents
   **/
  @Override
  public void appendTooltip(Item.TooltipContext context, Consumer<Text> textConsumer, TooltipType type, ComponentsAccess components) {
    textConsumer.accept(Text.literal(this.item().getItem().getName().getString()).withColor(0xCCAA77));
    textConsumer.accept(Text.literal("x" + NumberFormatter.toFormattedNumber(this.count())).withColor(0xFFDD99));
  }
}