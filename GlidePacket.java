package com.example.stormroyale;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public class GlidePacket {
    private final boolean gliding;

    public GlidePacket(boolean gliding) {
        this.gliding = gliding;
    }

    public static void encode(GlidePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.gliding);
    }

    public static GlidePacket decode(FriendlyByteBuf buf) {
        return new GlidePacket(buf.readBoolean());
    }

    public static void handle(GlidePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) BuildSystem.setGliding(p, msg.gliding && !p.onGround());
        });
        ctx.get().setPacketHandled(true);
    }
}
