package com.bbt.skin.mixin;

import com.bbt.skin.client.BBTSkinClient;
import com.bbt.skin.client.gui.widget.PlayerModelWidget;
import com.bbt.skin.client.render.SkinTextureManager;
import com.bbt.skin.common.data.SkinData;
import com.bbt.skin.common.data.SkinManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Mixin to override player skin textures with custom BBTSkin textures (Forge)
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    
    /**
     * Inject into getSkinTextureLocation to return custom skin if available
     */
    @Inject(method = "getSkinTextureLocation", at = @At("HEAD"), cancellable = true)
    private void bbtskin$getSkinTextureLocation(CallbackInfoReturnable<ResourceLocation> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        UUID playerUUID = player.getUUID();
        
        Minecraft mc = Minecraft.getInstance();
        
        // PRIORITY 1: Check for preview texture (when viewing skins in GUI)
        // Only apply to local player
        if (mc.player != null && mc.player.getUUID().equals(playerUUID)) {
            ResourceLocation previewTexture = PlayerModelWidget.getCurrentPreviewTexture();
            if (previewTexture != null) {
                cir.setReturnValue(previewTexture);
                return;
            }
        }
        
        // Check if BBTSkinClient is initialized
        BBTSkinClient client = BBTSkinClient.getInstance();
        if (client == null) return;
        
        SkinTextureManager textureManager = client.getTextureManager();
        if (textureManager == null) return;
        
        // PRIORITY 2: Check for local player's applied skin
        if (mc.player != null && mc.player.getUUID().equals(playerUUID)) {
            
            ResourceLocation localSkin = textureManager.getLocalSkinTexture();
            if (localSkin != null) {
                cir.setReturnValue(localSkin);
                return;
            }
            
            // Load applied skin if not loaded
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
            // PRIORITY 3: Check for remote player's custom skin
            ResourceLocation remoteSkin = textureManager.getRemoteSkinTexture(playerUUID);
            if (remoteSkin != null) {
                cir.setReturnValue(remoteSkin);
            }
        }
    }
    
    /**
     * Inject into getModelName to return correct model type (slim/classic)
     */
    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true)
    private void bbtskin$getModelName(CallbackInfoReturnable<String> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        UUID playerUUID = player.getUUID();
        
        Minecraft mc = Minecraft.getInstance();
        
        // Check for preview mode first
        if (mc.player != null && mc.player.getUUID().equals(playerUUID)) {
            if (PlayerModelWidget.hasActivePreview()) {
                cir.setReturnValue(PlayerModelWidget.isPreviewSlim() ? "slim" : "default");
                return;
            }
        }
        
        BBTSkinClient client = BBTSkinClient.getInstance();
        if (client == null) return;
        
        // Check for local player's applied skin
        if (mc.player != null && mc.player.getUUID().equals(playerUUID)) {
            SkinManager skinManager = client.getSkinManager();
            if (skinManager != null) {
                SkinData appliedSkin = skinManager.getAppliedSkin();
                if (appliedSkin != null) {
                    cir.setReturnValue(appliedSkin.isSlim() ? "slim" : "default");
                    return;
                }
            }
        }
    }
}
