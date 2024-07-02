package com.khazoda.basic_storage.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.collection.DefaultedList;

public record CrateContentsComponent(
    ItemVariant item,
    int count
) {
  public static final CrateContentsComponent DEFAULT = new CrateContentsComponent(ItemVariant.blank(), 0);

  public static final Codec<CrateContentsComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
      ItemVariant.CODEC.fieldOf("item").forGetter(CrateContentsComponent::item),
      Codec.INT.fieldOf("count").forGetter(CrateContentsComponent::count)
  ).apply(instance, CrateContentsComponent::new));

  public static final PacketCodec<RegistryByteBuf, CrateContentsComponent> PACKET_CODEC = PacketCodecs.registryCodec(CODEC);


}