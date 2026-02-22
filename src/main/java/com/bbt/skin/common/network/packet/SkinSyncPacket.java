package com.bbt.skin.common.network.packet;

import com.bbt.skin.BBTSkin;
import com.bbt.skin.common.network.NetworkConstants;
import com.bbt.skin.server.network.ServerSkinHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet sent from client to server when applying a skin
 */
public class SkinSyncPacket {
    
    private final String skinId;
    private final byte[] skinData;
    private final boolean slim;
    private final String skinName;
    private final int width;
    private final int height;
    
    public SkinSyncPacket(String skinId, byte[] skinData, boolean slim, String skinName, int width, int height) {
        this.skinId = skinId;
        this.skinData = skinData;
        this.slim = slim;
        this.skinName = skinName;
        this.width = width;
        this.height = height;
    }
    
    public static void encode(SkinSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.skinId, NetworkConstants.MAX_STRING_LENGTH);
        buf.writeInt(packet.skinData.length);
        buf.writeBytes(packet.skinData);
        buf.writeBoolean(packet.slim);
        buf.writeUtf(packet.skinName, NetworkConstants.MAX_STRING_LENGTH);
        buf.writeInt(packet.width);
        buf.writeInt(packet.height);
    }
    
    public static SkinSyncPacket decode(FriendlyByteBuf buf) {
        String skinId = buf.readUtf(NetworkConstants.MAX_STRING_LENGTH);
        int dataLength = buf.readInt();
        
        byte[] skinData = new byte[0];
        if (dataLength > 0 && dataLength <= NetworkConstants.MAX_SKIN_SIZE) {
            skinData = new byte[dataLength];
            buf.readBytes(skinData);
        }
        
        boolean slim = buf.readBoolean();
        String skinName = buf.readUtf(NetworkConstants.MAX_STRING_LENGTH);
        int width = buf.readInt();
        int height = buf.readInt();
        
        return new SkinSyncPacket(skinId, skinData, slim, skinName, width, height);
    }
    
    public static void handle(SkinSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && packet.skinData.length > 0) {
                ServerSkinHandler.handleSkinSync(player, packet.skinId, packet.skinData, 
                        packet.slim, packet.skinName, packet.width, packet.height);
            }
        });
        ctx.get().setPacketHandled(true);
    }
    
    // Getters
    public String getSkinId() { return skinId; }
    public byte[] getSkinData() { return skinData; }
    public boolean isSlim() { return slim; }
    public String getSkinName() { return skinName; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
