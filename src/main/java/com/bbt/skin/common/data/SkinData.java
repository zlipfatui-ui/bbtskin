package com.bbt.skin.common.data;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Represents a skin with its metadata and image data.
 * Supports dual textures for voice chat integration (mouth open/closed).
 */
public class SkinData {
    
    private final String id;
    private final String name;
    private final int width;
    private final int height;
    private final boolean slim;
    private final long createdAt;
    private final String ownerUUID;
    
    // Voice chat integration - dual texture support
    private final boolean hasVoiceTexture;
    
    @Nullable
    private byte[] imageData;  // Default/mouth closed texture
    
    @Nullable
    private byte[] mouthOpenData;  // Mouth open texture (when speaking)
    
    private SkinData(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.name = builder.name;
        this.width = builder.width;
        this.height = builder.height;
        this.slim = builder.slim;
        this.createdAt = builder.createdAt > 0 ? builder.createdAt : System.currentTimeMillis();
        this.ownerUUID = builder.ownerUUID;
        this.imageData = builder.imageData;
        this.mouthOpenData = builder.mouthOpenData;
        this.hasVoiceTexture = builder.mouthOpenData != null && builder.mouthOpenData.length > 0;
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public boolean isSlim() { return slim; }
    public long getCreatedAt() { return createdAt; }
    public String getOwnerUUID() { return ownerUUID; }
    
    /** Returns true if this skin has a separate mouth-open texture for voice chat */
    public boolean hasVoiceTexture() { return hasVoiceTexture; }
    
    @Nullable
    public byte[] getImageData() { return imageData; }
    
    /** Get the mouth-open texture data (for voice chat integration) */
    @Nullable
    public byte[] getMouthOpenData() { return mouthOpenData; }
    
    public void setImageData(byte[] data) { this.imageData = data; }
    
    public void setMouthOpenData(byte[] data) { this.mouthOpenData = data; }
    
    /**
     * Get the appropriate texture based on talking state
     * @param isTalking Whether the player is currently talking
     * @return The image data for the appropriate mouth state
     */
    @Nullable
    public byte[] getTextureForState(boolean isTalking) {
        if (isTalking && hasVoiceTexture && mouthOpenData != null) {
            return mouthOpenData;
        }
        return imageData;
    }
    
    /**
     * Check if this is a high-resolution skin
     */
    public boolean isHighRes() {
        return width > 64 || height > 64;
    }
    
    /**
     * Get the resolution multiplier (1x for standard, 2x for 128, etc.)
     */
    public int getResolutionMultiplier() {
        return Math.max(1, width / 64);
    }
    
    /**
     * Convert to JSON for persistence
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("name", name);
        json.addProperty("width", width);
        json.addProperty("height", height);
        json.addProperty("slim", slim);
        json.addProperty("createdAt", createdAt);
        json.addProperty("ownerUUID", ownerUUID);
        json.addProperty("hasVoiceTexture", hasVoiceTexture);
        return json;
    }
    
    /**
     * Create from JSON
     */
    public static SkinData fromJson(JsonObject json) {
        return new Builder()
            .id(json.has("id") ? json.get("id").getAsString() : null)
            .name(json.get("name").getAsString())
            .width(json.get("width").getAsInt())
            .height(json.get("height").getAsInt())
            .slim(json.get("slim").getAsBoolean())
            .createdAt(json.has("createdAt") ? json.get("createdAt").getAsLong() : 0)
            .ownerUUID(json.has("ownerUUID") ? json.get("ownerUUID").getAsString() : null)
            .build();
    }
    
    /**
     * Builder for creating SkinData instances
     */
    public static class Builder {
        private String id;
        private String name;
        private int width = 64;
        private int height = 64;
        private boolean slim = false;
        private long createdAt = 0;
        private String ownerUUID;
        private byte[] imageData;
        private byte[] mouthOpenData;
        
        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder width(int width) { this.width = width; return this; }
        public Builder height(int height) { this.height = height; return this; }
        public Builder slim(boolean slim) { this.slim = slim; return this; }
        public Builder createdAt(long createdAt) { this.createdAt = createdAt; return this; }
        public Builder ownerUUID(String ownerUUID) { this.ownerUUID = ownerUUID; return this; }
        public Builder imageData(byte[] imageData) { this.imageData = imageData; return this; }
        public Builder mouthOpenData(byte[] mouthOpenData) { this.mouthOpenData = mouthOpenData; return this; }
        
        public SkinData build() {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("Skin name is required");
            }
            return new SkinData(this);
        }
    }
    
    @Override
    public String toString() {
        String voiceInfo = hasVoiceTexture ? ", voice=true" : "";
        return String.format("SkinData{id='%s', name='%s', %dx%d, slim=%s%s}", 
            id, name, width, height, slim, voiceInfo);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SkinData other)) return false;
        return id.equals(other.id);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
