package com.bbt.skin.common.network.packet;

import com.bbt.skin.server.network.ServerSkinHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet sent from client to server when resetting skin to default
 */
public class SkinResetPacket {
    
    public SkinResetPacket() {
    }
    
    public static void encode(SkinResetPacket packet, FriendlyByteBuf buf) {
        // No data needed
    }
    
    public static SkinResetPacket decode(FriendlyByteBuf buf) {
        return new SkinResetPacket();
    }
    
    public static void handle(SkinResetPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ServerSkinHandler.handleSkinReset(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
