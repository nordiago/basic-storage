package com.khazoda.basicstorage.config;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import static com.khazoda.basicstorage.Constants.NAMESPACE;

public record ConfigSyncPayload(boolean breakWithAxeOnly) implements CustomPayload {

  public static final Id<ConfigSyncPayload> ID = new Id<>(Identifier.of(NAMESPACE, "config_sync"));
  public static final PacketCodec<RegistryByteBuf, ConfigSyncPayload> CODEC = PacketCodec.tuple(
      PacketCodecs.BOOLEAN, ConfigSyncPayload::breakWithAxeOnly,
      ConfigSyncPayload::new
  );

  @Override
  public Id<? extends CustomPayload> getId() {
    return ID;
  }
}