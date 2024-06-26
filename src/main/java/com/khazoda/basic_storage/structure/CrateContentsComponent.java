package com.khazoda.basic_storage.structure;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

import java.util.List;

public record CrateContentsComponent(List<CrateSlotComponent> slot) {
  public static final Codec<CrateContentsComponent> CODEC = CrateSlotComponent.CODEC.listOf(1, 1).xmap(CrateContentsComponent::new, CrateContentsComponent::slot);
  public static final PacketCodec<RegistryByteBuf, CrateContentsComponent> PACKET_CODEC = CrateSlotComponent.PACKET_CODEC.collect(PacketCodecs.toList(4)).xmap(CrateContentsComponent::new, CrateContentsComponent::slot);

}