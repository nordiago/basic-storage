package com.khazoda.basicstorage.structure;

import com.khazoda.basicstorage.util.NumberFormatter;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

import java.util.function.Consumer;

public record CrateSlotComponent(ItemVariant item, int count) implements TooltipProvider {

  public static final CrateSlotComponent DEFAULT = new CrateSlotComponent(ItemVariant.blank(), 0);

  public static final Codec<CrateSlotComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(ItemVariant.CODEC.fieldOf("item").forGetter(CrateSlotComponent::item), Codec.INT.fieldOf("count").forGetter(CrateSlotComponent::count)).apply(instance, CrateSlotComponent::new));

  public static final StreamCodec<RegistryFriendlyByteBuf, CrateSlotComponent> PACKET_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

  /**
   * Applies custom tooltip showing crate contents
   **/
  @Override
  public void addToTooltip(Item.TooltipContext context, Consumer<Component> textConsumer, TooltipFlag type, DataComponentGetter components) {
    textConsumer.accept(Component.literal(this.item().getItem().getName().getString()).withColor(0xCCAA77));
    textConsumer.accept(Component.literal("x" + NumberFormatter.toFormattedNumber(this.count())).withColor(0xFFDD99));
  }
}
