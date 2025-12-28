package com.khazoda.basicstorage.config;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

import static com.khazoda.basicstorage.Constants.NAMESPACE;

public record ConfigSyncPayload(boolean breakWithAxeOnly, boolean canBreakIfFull) implements CustomPacketPayload {

  public static final Type<ConfigSyncPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(NAMESPACE, "config_sync"));
  public static final StreamCodec<RegistryFriendlyByteBuf, ConfigSyncPayload> CODEC = StreamCodec.composite(
      ByteBufCodecs.BOOL, ConfigSyncPayload::breakWithAxeOnly,
      ByteBufCodecs.BOOL, ConfigSyncPayload::canBreakIfFull,
      ConfigSyncPayload::new
  );

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return ID;
  }
}
