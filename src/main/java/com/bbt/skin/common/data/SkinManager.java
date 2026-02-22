package com.bbt.skin.common.data;

import com.bbt.skin.BBTSkin;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Manages skins using NexSkins-compatible .meta format
 * 
 * Storage structure:
 * {gameDir}/bbtskin/
 *   ├── SkinName.png
 *   ├── SkinName.meta
 *   ├── AnotherSkin.png
 *   └── AnotherSkin.meta
 */
public class SkinManager {
    
    private final Path skinsDirectory;
    private final Map<String, SkinMetadata> loadedSkins = new ConcurrentHashMap<>();
    @Nullable
    private SkinMetadata selectedSkin = null;
    
    public SkinManager() {
        this.skinsDirectory = FMLPaths.GAMEDIR.get().resolve("bbtskin");
        initializeDirectory();
    }
    
    private void initializeDirectory() {
        try {
            Files.createDirectories(skinsDirectory);
            BBTSkin.LOGGER.info("BBTSkin directory: {}", skinsDirectory);
        } catch (IOException e) {
            BBTSkin.LOGGER.error("Failed to create skins directory", e);
        }
    }
    
    /**
     * Load all skins from directory (call from client init)
     */
    public void loadLocalSkins() {
        loadedSkins.clear();
        selectedSkin = null;
        
        try (Stream<Path> files = Files.list(skinsDirectory)) {
            files.filter(p -> p.toString().endsWith(".meta"))
                 .forEach(metaFile -> {
                     try {
                         SkinMetadata meta = SkinMetadata.load(metaFile);
                         String id = meta.generateId();
                         loadedSkins.put(id, meta);
                         
                         if (meta.isSelected()) {
                             selectedSkin = meta;
                         }
                         
                         BBTSkin.LOGGER.debug("Loaded skin: {} (selected={})", 
                                 meta.getName(), meta.isSelected());
                     } catch (IOException e) {
                         BBTSkin.LOGGER.warn("Failed to load metadata: {}", metaFile, e);
                     }
                 });
        } catch (IOException e) {
            BBTSkin.LOGGER.error("Failed to scan skins directory", e);
        }
        
        BBTSkin.LOGGER.info("Loaded {} skins, selected: {}", 
                loadedSkins.size(), 
                selectedSkin != null ? selectedSkin.getName() : "none");
    }
    
    /**
     * Save skins (for compatibility - now saves metadata files)
     */
    public void saveLocalSkins() {
        for (SkinMetadata meta : loadedSkins.values()) {
            try {
                meta.save();
            } catch (IOException e) {
                BBTSkin.LOGGER.warn("Failed to save metadata for: {}", meta.getName(), e);
            }
        }
    }
    
    /**
     * Import a new skin from raw data
     */
    @Nullable
    public SkinData importSkin(String name, byte[] imageData, int width, int height, boolean slim) {
        try {
            // Generate safe filename
            String safeName = sanitizeFilename(name);
            Path skinFile = skinsDirectory.resolve(safeName + ".png");
            Path metaFile = skinsDirectory.resolve(safeName + ".meta");
            
            // Handle duplicates
            int counter = 1;
            while (Files.exists(skinFile) || Files.exists(metaFile)) {
                safeName = sanitizeFilename(name) + "_" + counter;
                skinFile = skinsDirectory.resolve(safeName + ".png");
                metaFile = skinsDirectory.resolve(safeName + ".meta");
                counter++;
            }
            
            // Write skin file
            Files.write(skinFile, imageData);
            
            // Get player info
            String playerName = "Unknown";
            String playerUuid = "";
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                playerName = mc.player.getName().getString();
                playerUuid = mc.player.getUUID().toString();
            } else if (mc.getUser() != null) {
                playerName = mc.getUser().getName();
                playerUuid = mc.getUser().getProfileId().toString();
            }
            
            // Create metadata
            SkinMetadata meta = SkinMetadata.create(name, skinFile, slim, 
                    playerName, playerUuid, width, height);
            meta.save(metaFile);
            
            // Add to loaded skins
            String id = meta.generateId();
            loadedSkins.put(id, meta);
            
            BBTSkin.LOGGER.info("Imported skin: {} -> {}", name, skinFile);
            
            return meta.toSkinData();
            
        } catch (Exception e) {
            BBTSkin.LOGGER.error("Failed to import skin", e);
            return null;
        }
    }
    
    /**
     * Import skin from file path
     */
    public SkinData importSkin(String name, Path sourceFile, boolean slim) throws IOException {
        // Validate
        if (name == null || name.trim().isEmpty()) {
            throw new IOException("Skin name cannot be empty");
        }
        
        // Read and validate image
        BufferedImage image = ImageIO.read(sourceFile.toFile());
        if (image == null) {
            throw new IOException("Failed to read image file");
        }
        
        int width = image.getWidth();
        int height = image.getHeight();
        
        // Validate dimensions
        if (!isValidSkinDimensions(width, height)) {
            throw new IOException("Invalid skin dimensions: " + width + "x" + height);
        }
        
        // Read bytes
        byte[] imageData = Files.readAllBytes(sourceFile);
        
        SkinData result = importSkin(name, imageData, width, height, slim);
        if (result == null) {
            throw new IOException("Failed to import skin");
        }
        return result;
    }
    
    /**
     * Delete a skin
     */
    public boolean deleteSkin(String skinId) {
        SkinMetadata meta = loadedSkins.get(skinId);
        if (meta == null) {
            // Try to find by name
            meta = findByName(skinId);
        }
        
        if (meta == null) {
            BBTSkin.LOGGER.warn("Skin not found for deletion: {}", skinId);
            return false;
        }
        
        // Cannot delete selected skin
        if (meta.isSelected()) {
            BBTSkin.LOGGER.warn("Cannot delete selected skin: {}", meta.getName());
            return false;
        }
        
        try {
            // Delete files
            if (meta.getSkinFilePath() != null && Files.exists(meta.getSkinFilePath())) {
                Files.delete(meta.getSkinFilePath());
            }
            if (meta.getMetaFilePath() != null && Files.exists(meta.getMetaFilePath())) {
                Files.delete(meta.getMetaFilePath());
            }
            
            loadedSkins.remove(meta.generateId());
            BBTSkin.LOGGER.info("Deleted skin: {}", meta.getName());
            return true;
        } catch (IOException e) {
            BBTSkin.LOGGER.error("Failed to delete skin: {}", meta.getName(), e);
            return false;
        }
    }
    
    /**
     * Set applied skin (select it)
     * Updates all .meta files to ensure only one is selected
     */
    public void setAppliedSkin(SkinData skin) {
        if (skin == null) {
            clearAppliedSkin();
            return;
        }
        
        selectSkin(skin.getId());
    }
    
    /**
     * Select a skin by ID
     */
    public boolean selectSkin(String skinId) {
        SkinMetadata newSelection = loadedSkins.get(skinId);
        if (newSelection == null) {
            newSelection = findByName(skinId);
        }
        
        if (newSelection == null) {
            BBTSkin.LOGGER.warn("Skin not found: {}", skinId);
            return false;
        }
        
        // Deselect all other skins
        for (SkinMetadata meta : loadedSkins.values()) {
            if (meta.isSelected() && meta != newSelection) {
                meta.setSelected(false);
                try {
                    meta.save();
                } catch (IOException e) {
                    BBTSkin.LOGGER.warn("Failed to update metadata: {}", meta.getName(), e);
                }
            }
        }
        
        // Select new skin
        newSelection.setSelected(true);
        try {
            newSelection.save();
        } catch (IOException e) {
            BBTSkin.LOGGER.error("Failed to save selection: {}", newSelection.getName(), e);
            return false;
        }
        
        selectedSkin = newSelection;
        BBTSkin.LOGGER.info("Selected skin: {}", newSelection.getName());
        
        return true;
    }
    
    /**
     * Clear applied skin (reset to default)
     */
    public void clearAppliedSkin() {
        if (selectedSkin != null) {
            selectedSkin.setSelected(false);
            try {
                selectedSkin.save();
            } catch (IOException e) {
                BBTSkin.LOGGER.warn("Failed to clear selection", e);
            }
            selectedSkin = null;
        }
        
        BBTSkin.LOGGER.info("Cleared skin selection");
    }
    
    /**
     * Get currently applied skin
     */
    @Nullable
    public SkinData getAppliedSkin() {
        return selectedSkin != null ? selectedSkin.toSkinData() : null;
    }
    
    /**
     * Check if any skin is applied
     */
    public boolean hasAppliedSkin() {
        return selectedSkin != null;
    }
    
    /**
     * Get metadata for applied skin
     */
    @Nullable
    public SkinMetadata getAppliedMetadata() {
        return selectedSkin;
    }
    
    /**
     * Get all skins as SkinData list
     */
    public List<SkinData> getAllSkins() {
        List<SkinData> skins = new ArrayList<>();
        for (SkinMetadata meta : loadedSkins.values()) {
            skins.add(meta.toSkinData());
        }
        // Sort by name
        skins.sort(Comparator.comparing(SkinData::getName, String.CASE_INSENSITIVE_ORDER));
        return skins;
    }
    
    /**
     * Get a skin by ID
     */
    @Nullable
    public SkinData getSkin(String skinId) {
        SkinMetadata meta = loadedSkins.get(skinId);
        return meta != null ? meta.toSkinData() : null;
    }
    
    /**
     * Get all metadata
     */
    public Collection<SkinMetadata> getAllMetadata() {
        return Collections.unmodifiableCollection(loadedSkins.values());
    }
    
    /**
     * Get metadata by ID
     */
    @Nullable
    public SkinMetadata getMetadata(String id) {
        return loadedSkins.get(id);
    }
    
    /**
     * Find skin by name
     */
    @Nullable
    private SkinMetadata findByName(String name) {
        for (SkinMetadata meta : loadedSkins.values()) {
            if (meta.getName().equalsIgnoreCase(name) || 
                meta.generateId().equals(name)) {
                return meta;
            }
        }
        return null;
    }
    
    /**
     * Check if skin is currently applied
     */
    public boolean isApplied(String skinId) {
        return selectedSkin != null && selectedSkin.generateId().equals(skinId);
    }

    /**
     * Toggle favorite status for a skin
     */
    public boolean toggleFavorite(String skinId) {
        SkinMetadata meta = loadedSkins.get(skinId);
        if (meta == null) {
            BBTSkin.LOGGER.warn("[FAV-DEBUG] toggleFavorite: skinId={} NOT FOUND in loadedSkins (size={})",
                    skinId, loadedSkins.size());
            return false;
        }

        boolean wasFav = meta.isFavorite();
        meta.setFavorite(!wasFav);
        try {
            meta.save();
            BBTSkin.LOGGER.info("[FAV-DEBUG] toggleFavorite: skinId={} name={} {} -> {} (saved OK)",
                    skinId, meta.getName(), wasFav, meta.isFavorite());
        } catch (IOException e) {
            BBTSkin.LOGGER.warn("[FAV-DEBUG] toggleFavorite: SAVE FAILED skinId={} name={}",
                    skinId, meta.getName(), e);
            return false;
        }
        return true;
    }

    /**
     * Check if a skin is favorited
     */
    public boolean isFavorite(String skinId) {
        SkinMetadata meta = loadedSkins.get(skinId);
        boolean result = meta != null && meta.isFavorite();
        return result;
    }
    
    /**
     * Validate skin dimensions
     */
    private boolean isValidSkinDimensions(int width, int height) {
        // Standard: 64x64, 64x32
        if (width == 64 && (height == 64 || height == 32)) return true;
        
        // High-res: multiples (128x128, 256x256, 512x512, etc.)
        if (width == height && width >= 64 && width <= 8192) {
            return (width & (width - 1)) == 0; // Is power of 2
        }
        
        // Allow non-power-of-2 but valid aspect ratio
        if (width >= 64 && height >= 32 && width == height * 2) return true;
        if (width >= 64 && height >= 64 && width == height) return true;
        
        return false;
    }
    
    /**
     * Sanitize filename
     */
    private String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_")
                   .replaceAll("_+", "_")
                   .replaceAll("^_|_$", "");
    }
    
    /**
     * Get skins directory path
     */
    public Path getSkinsDirectory() {
        return skinsDirectory;
    }
    
    /**
     * Refresh/reload all skins from disk
     */
    public void refresh() {
        loadLocalSkins();
    }
    
    /**
     * Save a SkinData object (supports voice texture)
     */
    public boolean saveSkin(SkinData skin) {
        if (skin == null || skin.getImageData() == null) {
            BBTSkin.LOGGER.warn("Cannot save skin: null or no image data");
            return false;
        }
        
        try {
            // Generate safe filename
            String safeName = sanitizeFilename(skin.getName());
            Path skinFile = skinsDirectory.resolve(safeName + ".png");
            Path metaFile = skinsDirectory.resolve(safeName + ".meta");
            
            // Handle duplicates
            int counter = 1;
            while (Files.exists(skinFile) || Files.exists(metaFile)) {
                safeName = sanitizeFilename(skin.getName()) + "_" + counter;
                skinFile = skinsDirectory.resolve(safeName + ".png");
                metaFile = skinsDirectory.resolve(safeName + ".meta");
                counter++;
            }
            
            // Write main skin file
            Files.write(skinFile, skin.getImageData());
            
            // Write mouth-open texture if present
            Path mouthOpenFile = null;
            if (skin.hasVoiceTexture() && skin.getMouthOpenData() != null) {
                mouthOpenFile = skinsDirectory.resolve(safeName + "_mouth.png");
                Files.write(mouthOpenFile, skin.getMouthOpenData());
                BBTSkin.LOGGER.info("Saved voice texture: {}", mouthOpenFile);
            }
            
            // Get player info
            String playerName = "Unknown";
            String playerUuid = "";
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                playerName = mc.player.getName().getString();
                playerUuid = mc.player.getUUID().toString();
            } else if (mc.getUser() != null) {
                playerName = mc.getUser().getName();
                playerUuid = mc.getUser().getProfileId().toString();
            }
            
            // Create metadata with voice texture info
            SkinMetadata meta = SkinMetadata.create(skin.getName(), skinFile, skin.isSlim(),
                    playerName, playerUuid, skin.getWidth(), skin.getHeight());
            
            // Set voice texture path in metadata
            if (mouthOpenFile != null) {
                meta.setMouthOpenPath(mouthOpenFile);
            }
            
            meta.save(metaFile);
            
            // Add to loaded skins
            String id = meta.generateId();
            loadedSkins.put(id, meta);
            
            BBTSkin.LOGGER.info("Saved skin: {} (voice={})", skin.getName(), skin.hasVoiceTexture());
            
            return true;
            
        } catch (Exception e) {
            BBTSkin.LOGGER.error("Failed to save skin", e);
            return false;
        }
    }
}
