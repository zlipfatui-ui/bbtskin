package com.bbt.skin.common.network.packet;

import com.bbt.skin.common.network.NetworkConstants;
import com.bbt.skin.server.network.ServerSkinHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet sent from client to server to request another player's skin
 */
public class SkinRequestPacket {
    
    private final String targetPlayerUUID;
    
    public SkinRequestPacket(String targetPlayerUUID) {
        this.targetPlayerUUID = targetPlayerUUID;
    }
    
    public static void encode(SkinRequestPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.targetPlayerUUID, NetworkConstants.MAX_STRING_LENGTH);
    }
    
    public static SkinRequestPacket decode(FriendlyByteBuf buf) {
        return new SkinRequestPacket(buf.readUtf(NetworkConstants.MAX_STRING_LENGTH));
    }
    
    public static void handle(SkinRequestPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ServerSkinHandler.handleSkinRequest(player, packet.targetPlayerUUID);
            }
        });
        ctx.get().setPacketHandled(true);
    }
    
    public String getTargetPlayerUUID() {
        return targetPlayerUUID;
    }
}
