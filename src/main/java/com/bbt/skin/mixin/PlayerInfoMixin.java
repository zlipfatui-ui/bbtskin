package com.bbt.skin.mixin;

import com.bbt.skin.client.BBTSkinClient;
import com.bbt.skin.client.render.SkinTextureManager;
import com.bbt.skin.common.data.SkinData;
import com.bbt.skin.common.data.SkinManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Mixin for PlayerInfo to override skin textures in tab list and other places (Forge 1.20.1)
 */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {
    
    @Shadow @Final
    private GameProfile profile;
    
    @Inject(method = "getSkinLocation", at = @At("HEAD"), cancellable = true)
    private void bbtskin$getSkinLocation(CallbackInfoReturnable<ResourceLocation> cir) {
        UUID playerUUID = this.profile.getId();
        
        BBTSkinClient client = BBTSkinClient.getInstance();
        if (client == null) return;
        
        SkinTextureManager textureManager = client.getTextureManager();
        if (textureManager == null) return;
        
        Minecraft mc = Minecraft.getInstance();
        
        // Check if this is the local player
        if (mc.player != null && mc.player.getUUID().equals(playerUUID)) {
            ResourceLocation localSkin = textureManager.getLocalSkinTexture();
            if (localSkin != null) {
                cir.setReturnValue(localSkin);
                return;
            }
            
            // Check SkinManager for applied skin
            SkinManager skinManager = client.getSkinManager();
            if (skinManager != null) {
                SkinData appliedSkin = skinManager.getAppliedSkin();
                if (appliedSkin != null && appliedSkin.getImageData() != null) {
                    textureManager.loadLocalSkin(appliedSkin);
                    ResourceLocation texture = textureManager.getLocalSkinTexture();
                    if (texture != null) {
                        cir.setReturnValue(texture);
                        return;
                    }
                }
            }
        } else {
            // Check for remote player's custom skin
            ResourceLocation remoteSkin = textureManager.getRemoteSkinTexture(playerUUID);
            if (remoteSkin != null) {
                cir.setReturnValue(remoteSkin);
            }
        }
    }
    
    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true)
    private void bbtskin$getModelName(CallbackInfoReturnable<String> cir) {
        UUID playerUUID = this.profile.getId();
        
        BBTSkinClient client = BBTSkinClient.getInstance();
        if (client == null) return;
        
        Minecraft mc = Minecraft.getInstance();
        
        // Check if this is the local player
        if (mc.player != null && mc.player.getUUID().equals(playerUUID)) {
            SkinManager skinManager = client.getSkinManager();
            if (skinManager != null) {
                SkinData appliedSkin = skinManager.getAppliedSkin();
                if (appliedSkin != null) {
                    cir.setReturnValue(appliedSkin.isSlim() ? "slim" : "default");
                }
            }
        }
    }
}
