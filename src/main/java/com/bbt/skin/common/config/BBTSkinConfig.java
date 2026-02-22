package com.bbt.skin.common.config;

import com.bbt.skin.BBTSkin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration for BBTSkin mod (Forge)
 */
public class BBTSkinConfig {
    
    private static final String CONFIG_FILE = "bbtskin.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    // Singleton instance
    private static BBTSkinConfig instance;
    
    // Configuration values
    private String cloudflareWorkerUrl = "https://skin.beforebedtime.net";
    private String cloudflareApiKey = ""; // Set this in config file!
    private boolean enableCloudflareSync = true;
    private boolean enableHighResSkins = true;
    private int maxSkinResolution = 8192;
    private int skinCacheSize = 100;
    private boolean enableAutoSync = true;
    private int syncIntervalSeconds = 300;
    private boolean showSkinLoadingIndicator = true;
    private boolean enableDebugLogging = false;
    
    private BBTSkinConfig() {}
    
    /**
     * Get the config instance
     */
    public static BBTSkinConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }
    
    /**
     * Load configuration from file
     */
    public static void load() {
        instance = new BBTSkinConfig();
        
        Path configFile = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE);
        
        if (Files.exists(configFile)) {
            try {
                String json = Files.readString(configFile);
                JsonObject root = GSON.fromJson(json, JsonObject.class);
                
                if (root.has("cloudflareWorkerUrl")) {
                    instance.cloudflareWorkerUrl = root.get("cloudflareWorkerUrl").getAsString();
                }
                if (root.has("cloudflareApiKey")) {
                    instance.cloudflareApiKey = root.get("cloudflareApiKey").getAsString();
                }
                if (root.has("enableCloudflareSync")) {
                    instance.enableCloudflareSync = root.get("enableCloudflareSync").getAsBoolean();
                }
                if (root.has("enableHighResSkins")) {
                    instance.enableHighResSkins = root.get("enableHighResSkins").getAsBoolean();
                }
                if (root.has("maxSkinResolution")) {
                    instance.maxSkinResolution = root.get("maxSkinResolution").getAsInt();
                }
                if (root.has("skinCacheSize")) {
                    instance.skinCacheSize = root.get("skinCacheSize").getAsInt();
                }
                if (root.has("enableAutoSync")) {
                    instance.enableAutoSync = root.get("enableAutoSync").getAsBoolean();
                }
                if (root.has("syncIntervalSeconds")) {
                    instance.syncIntervalSeconds = root.get("syncIntervalSeconds").getAsInt();
                }
                if (root.has("showSkinLoadingIndicator")) {
                    instance.showSkinLoadingIndicator = root.get("showSkinLoadingIndicator").getAsBoolean();
                }
                if (root.has("enableDebugLogging")) {
                    instance.enableDebugLogging = root.get("enableDebugLogging").getAsBoolean();
                }
                
                BBTSkin.LOGGER.info("Loaded configuration from {}", configFile);
                
            } catch (Exception e) {
                BBTSkin.LOGGER.error("Failed to load configuration", e);
            }
        } else {
            // Save default configuration
            save();
        }
    }
    
    /**
     * Save configuration to file
     */
    public static void save() {
        if (instance == null) {
            instance = new BBTSkinConfig();
        }
        
        Path configFile = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE);
        
        try {
            JsonObject root = new JsonObject();
            root.addProperty("cloudflareWorkerUrl", instance.cloudflareWorkerUrl);
            root.addProperty("cloudflareApiKey", instance.cloudflareApiKey);
            root.addProperty("enableCloudflareSync", instance.enableCloudflareSync);
            root.addProperty("enableHighResSkins", instance.enableHighResSkins);
            root.addProperty("maxSkinResolution", instance.maxSkinResolution);
            root.addProperty("skinCacheSize", instance.skinCacheSize);
            root.addProperty("enableAutoSync", instance.enableAutoSync);
            root.addProperty("syncIntervalSeconds", instance.syncIntervalSeconds);
            root.addProperty("showSkinLoadingIndicator", instance.showSkinLoadingIndicator);
            root.addProperty("enableDebugLogging", instance.enableDebugLogging);
            
            Files.writeString(configFile, GSON.toJson(root));
            BBTSkin.LOGGER.info("Saved configuration to {}", configFile);
            
        } catch (IOException e) {
            BBTSkin.LOGGER.error("Failed to save configuration", e);
        }
    }
    
    // Getters
    public String getCloudflareWorkerUrl() { return cloudflareWorkerUrl; }
    public String getCloudflareApiKey() { return cloudflareApiKey; }
    public boolean isCloudflareEnabled() { return enableCloudflareSync; }
    public boolean isHighResSkinsEnabled() { return enableHighResSkins; }
    public int getMaxSkinResolution() { return maxSkinResolution; }
    public int getSkinCacheSize() { return skinCacheSize; }
    public boolean isAutoSyncEnabled() { return enableAutoSync; }
    public int getSyncIntervalSeconds() { return syncIntervalSeconds; }
    public boolean showSkinLoadingIndicator() { return showSkinLoadingIndicator; }
    public boolean isDebugLoggingEnabled() { return enableDebugLogging; }
    
    // Setters
    public void setCloudflareWorkerUrl(String url) { 
        this.cloudflareWorkerUrl = url; 
        save();
    }
    
    public void setCloudflareEnabled(boolean enabled) { 
        this.enableCloudflareSync = enabled; 
        save();
    }
    
    public void setHighResSkinsEnabled(boolean enabled) { 
        this.enableHighResSkins = enabled; 
        save();
    }
    
    public void setMaxSkinResolution(int resolution) { 
        this.maxSkinResolution = Math.max(64, Math.min(8192, resolution)); 
        save();
    }
    
    public void setDebugLoggingEnabled(boolean enabled) {
        this.enableDebugLogging = enabled;
        save();
    }
}
