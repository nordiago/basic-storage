package com.khazoda.basicstorage.packet;

import com.khazoda.basicstorage.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record StationBeamPayload(BlockPos origin, List<Target> targets) implements CustomPacketPayload {

  public record Target(BlockPos pos, int amount) {
  }

  public static final CustomPacketPayload.Type<StationBeamPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Constants.NAMESPACE, "station_beam"));

  public static final StreamCodec<ByteBuf, StationBeamPayload> CODEC = StreamCodec.of((buf, value) -> {
    FriendlyByteBuf friendlyBuf = new FriendlyByteBuf(buf);
    friendlyBuf.writeBlockPos(value.origin);
    friendlyBuf.writeCollection(value.targets, (b, t) -> {
      b.writeBlockPos(t.pos);
      b.writeVarInt(t.amount);
    });
  }, (buf) -> {
    FriendlyByteBuf friendlyBuf = new FriendlyByteBuf(buf);
    BlockPos origin = friendlyBuf.readBlockPos();
    List<Target> targets = friendlyBuf.readList(b -> new Target(b.readBlockPos(), b.readVarInt()));
    return new StationBeamPayload(origin, targets);
  });

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return ID;
  }
}
