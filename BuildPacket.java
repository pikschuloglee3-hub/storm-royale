package com.example.stormroyale;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public class BuildPacket {
    private final int piece;

    public BuildPacket(int piece) {
        this.piece = piece;
    }

    public static void encode(BuildPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.piece);
    }

    public static BuildPacket decode(FriendlyByteBuf buf) {
        return new BuildPacket(buf.readVarInt());
    }

    public static void handle(BuildPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) BuildSystem.handle(p, msg.piece);
        });
        ctx.get().setPacketHandled(true);
    }
}
