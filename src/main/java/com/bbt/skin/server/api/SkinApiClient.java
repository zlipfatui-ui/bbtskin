package com.bbt.skin.server.api;

import com.bbt.skin.BBTSkin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP client for BBTSkin Cloudflare Worker API
 * 
 * API Endpoints:
 *   GET  /skins/{uuid}  - Get skin data for a player
 *   PUT  /skins/{uuid}  - Save/update skin data
 *   DELETE /skins/{uuid} - Remove skin data
 *   GET  /skins         - List all skins (for resync)
 */
public class SkinApiClient {
    
    private static final Gson GSON = new GsonBuilder().create();
    
    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;
    
    public SkinApiClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }
    
    /**
     * Get skin data for a player
     */
    public CompletableFuture<SkinApiResponse> getSkin(String playerUuid) {
        String url = baseUrl + "/skins/" + playerUuid;
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();
        
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            return SkinApiResponse.fromJson(json);
                        } catch (Exception e) {
                            BBTSkin.LOGGER.error("Failed to parse skin response", e);
                            return null;
                        }
                    } else if (response.statusCode() == 404) {
                        return null; // No skin found
                    } else {
                        BBTSkin.LOGGER.warn("API error {}: {}", response.statusCode(), response.body());
                        return null;
                    }
                })
                .exceptionally(e -> {
                    BBTSkin.LOGGER.error("Failed to get skin from API", e);
                    return null;
                });
    }
    
    /**
     * Save/update skin data for a player
     */
    public CompletableFuture<Boolean> saveSkin(String playerUuid, String playerName, 
                                                byte[] imageData, boolean slim,
                                                int width, int height) {
        String url = baseUrl + "/skins/" + playerUuid;
        
        // Build JSON payload
        JsonObject payload = new JsonObject();
        payload.addProperty("uuid", playerUuid);
        payload.addProperty("name", playerName);
        payload.addProperty("slim", slim);
        payload.addProperty("width", width);
        payload.addProperty("height", height);
        payload.addProperty("imageData", Base64.getEncoder().encodeToString(imageData));
        payload.addProperty("timestamp", System.currentTimeMillis());
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                .timeout(Duration.ofSeconds(60))
                .build();
        
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200 || response.statusCode() == 201) {
                        BBTSkin.LOGGER.info("Saved skin to API for player {}", playerUuid);
                        return true;
                    } else {
                        BBTSkin.LOGGER.warn("Failed to save skin: {} {}", response.statusCode(), response.body());
                        return false;
                    }
                })
                .exceptionally(e -> {
                    BBTSkin.LOGGER.error("Failed to save skin to API", e);
                    return false;
                });
    }
    
    /**
     * Delete skin data for a player
     */
    public CompletableFuture<Boolean> deleteSkin(String playerUuid) {
        String url = baseUrl + "/skins/" + playerUuid;
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .DELETE()
                .timeout(Duration.ofSeconds(30))
                .build();
        
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    return response.statusCode() == 200 || response.statusCode() == 204;
                })
                .exceptionally(e -> {
                    BBTSkin.LOGGER.error("Failed to delete skin from API", e);
                    return false;
                });
    }
    
    /**
     * Get all skins (for resync on server start or command)
     */
    public CompletableFuture<java.util.List<SkinApiResponse>> getAllSkins() {
        String url = baseUrl + "/skins";
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(60))
                .build();
        
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            java.util.List<SkinApiResponse> skins = new java.util.ArrayList<>();
                            com.google.gson.JsonArray array = JsonParser.parseString(response.body()).getAsJsonArray();
                            for (var element : array) {
                                SkinApiResponse skin = SkinApiResponse.fromJson(element.getAsJsonObject());
                                if (skin != null) {
                                    skins.add(skin);
                                }
                            }
                            return skins;
                        } catch (Exception e) {
                            BBTSkin.LOGGER.error("Failed to parse skins list", e);
                            return new java.util.ArrayList<SkinApiResponse>();
                        }
                    } else {
                        BBTSkin.LOGGER.warn("Failed to get skins list: {}", response.statusCode());
                        return new java.util.ArrayList<SkinApiResponse>();
                    }
                })
                .exceptionally(e -> {
                    BBTSkin.LOGGER.error("Failed to get skins from API", e);
                    return new java.util.ArrayList<SkinApiResponse>();
                });
    }
    
    /**
     * Response data from API
     */
    public static class SkinApiResponse {
        public String uuid;
        public String name;
        public boolean slim;
        public int width;
        public int height;
        public byte[] imageData;
        public long timestamp;
        
        @Nullable
        public static SkinApiResponse fromJson(JsonObject json) {
            try {
                SkinApiResponse response = new SkinApiResponse();
                response.uuid = json.get("uuid").getAsString();
                response.name = json.has("name") ? json.get("name").getAsString() : "Unknown";
                response.slim = json.has("slim") && json.get("slim").getAsBoolean();
                response.width = json.has("width") ? json.get("width").getAsInt() : 64;
                response.height = json.has("height") ? json.get("height").getAsInt() : 64;
                response.timestamp = json.has("timestamp") ? json.get("timestamp").getAsLong() : 0;
                
                if (json.has("imageData")) {
                    response.imageData = Base64.getDecoder().decode(json.get("imageData").getAsString());
                }
                
                return response;
            } catch (Exception e) {
                BBTSkin.LOGGER.error("Failed to parse SkinApiResponse", e);
                return null;
            }
        }
    }
}
