package com.khazoda.basic_storage.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

public record CrateSlotComponent(
    ItemVariant item,
    long count
) {
  public static final Codec<CrateSlotComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
      ItemVariant.CODEC.fieldOf("item").forGetter(CrateSlotComponent::item),
      Codec.LONG.fieldOf("count").forGetter(CrateSlotComponent::count)
  ).apply(instance, CrateSlotComponent::new));
  public static final PacketCodec<RegistryByteBuf, CrateSlotComponent> PACKET_CODEC = PacketCodecs.registryCodec(CODEC);
}