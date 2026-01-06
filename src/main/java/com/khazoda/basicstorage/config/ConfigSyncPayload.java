package com.khazoda.basicstorage.config;

import com.khazoda.basicstorage.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ConfigSyncPayload(boolean breakWithAxeOnly, boolean canBreakIfFull) implements CustomPacketPayload {

  public static final Type<ConfigSyncPayload> ID = new Type<>(Constants.ID("config_sync"));
  public static final StreamCodec<RegistryFriendlyByteBuf, ConfigSyncPayload> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, ConfigSyncPayload::breakWithAxeOnly, ByteBufCodecs.BOOL, ConfigSyncPayload::canBreakIfFull, ConfigSyncPayload::new);

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return ID;
  }
}
