package com.bbt.skin.common.data;

import com.bbt.skin.BBTSkin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

/**
 * NexSkins-compatible metadata handler using .meta (Java Properties) format
 * 
 * Format:
 * #BBTSkin Metadata
 * #Thu Jan 22 19:35:50 ICT 2026
 * name=My Skin
 * uploaded_by=PlayerName
 * uploaded_uuid=898da750-8818-40f0-9da4-ea6822260b30
 * slim=true
 * checksum=76884ac0ca5d8c6491a587d7bffd99560ab060b11fc823b51517482a37bfc08d
 * timestamp=1769085350630
 * selected=true
 */
public class SkinMetadata {
    
    private static final String HEADER = "BBTSkin Metadata";
    
    // Metadata fields
    private String name;
    private String uploadedBy;
    private String uploadedUuid;
    private boolean slim;
    private String checksum;
    private long timestamp;
    private boolean selected;
    private boolean favorite;
    private int width;
    private int height;
    
    // Voice texture support
    private Path mouthOpenPath;
    
    // File reference
    private Path metaFilePath;
    private Path skinFilePath;
    
    public SkinMetadata() {
        this.timestamp = System.currentTimeMillis();
        this.selected = false;
        this.favorite = false;
        this.slim = false;
        this.width = 64;
        this.height = 64;
    }
    
    /**
     * Load metadata from a .meta file
     */
    public static SkinMetadata load(Path metaFile) throws IOException {
        if (!Files.exists(metaFile)) {
            throw new IOException("Meta file does not exist: " + metaFile);
        }
        
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(metaFile);
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            props.load(reader);
        }
        
        SkinMetadata meta = new SkinMetadata();
        meta.metaFilePath = metaFile;
        
        // Parse properties
        meta.name = props.getProperty("name", metaFile.getFileName().toString().replace(".meta", ""));
        meta.uploadedBy = props.getProperty("uploaded_by", "Unknown");
        meta.uploadedUuid = props.getProperty("uploaded_uuid", "");
        meta.slim = Boolean.parseBoolean(props.getProperty("slim", "false"));
        meta.checksum = props.getProperty("checksum", "");
        meta.timestamp = parseLong(props.getProperty("timestamp", "0"));
        meta.selected = Boolean.parseBoolean(props.getProperty("selected", "false"));
        meta.favorite = Boolean.parseBoolean(props.getProperty("favorite", "false"));
        meta.width = parseInt(props.getProperty("width", "64"));
        meta.height = parseInt(props.getProperty("height", "64"));
        
        // Find associated skin file
        String baseName = metaFile.getFileName().toString().replace(".meta", "");
        Path skinFile = metaFile.getParent().resolve(baseName + ".png");
        if (Files.exists(skinFile)) {
            meta.skinFilePath = skinFile;
        }
        
        // Find mouth-open texture file
        String mouthOpenFileName = props.getProperty("mouth_open", "");
        if (!mouthOpenFileName.isEmpty()) {
            Path mouthOpenFile = metaFile.getParent().resolve(mouthOpenFileName);
            if (Files.exists(mouthOpenFile)) {
                meta.mouthOpenPath = mouthOpenFile;
            }
        } else {
            // Try default naming convention
            Path mouthOpenFile = metaFile.getParent().resolve(baseName + "_mouth.png");
            if (Files.exists(mouthOpenFile)) {
                meta.mouthOpenPath = mouthOpenFile;
            }
        }
        
        return meta;
    }
    
    /**
     * Save metadata to a .meta file
     */
    public void save() throws IOException {
        if (metaFilePath == null) {
            throw new IOException("No meta file path set");
        }
        save(metaFilePath);
    }
    
    /**
     * Save metadata to specified path
     */
    public void save(Path path) throws IOException {
        Properties props = new Properties();
        
        props.setProperty("name", name != null ? name : "");
        props.setProperty("uploaded_by", uploadedBy != null ? uploadedBy : "");
        props.setProperty("uploaded_uuid", uploadedUuid != null ? uploadedUuid : "");
        props.setProperty("slim", String.valueOf(slim));
        props.setProperty("checksum", checksum != null ? checksum : "");
        props.setProperty("timestamp", String.valueOf(timestamp));
        props.setProperty("selected", String.valueOf(selected));
        props.setProperty("width", String.valueOf(width));
        props.setProperty("height", String.valueOf(height));
        
        // Create parent directories if needed
        Files.createDirectories(path.getParent());
        
        // Write with custom header
        try (OutputStream out = Files.newOutputStream(path);
             Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            
            // Write header
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy");
            writer.write("#" + HEADER + "\n");
            writer.write("#" + dateFormat.format(new Date()) + "\n");
            
            // Write properties manually to control order
            writer.write("name=" + escape(name) + "\n");
            writer.write("uploaded_by=" + escape(uploadedBy) + "\n");
            writer.write("uploaded_uuid=" + escape(uploadedUuid) + "\n");
            writer.write("slim=" + slim + "\n");
            writer.write("width=" + width + "\n");
            writer.write("height=" + height + "\n");
            writer.write("checksum=" + escape(checksum) + "\n");
            writer.write("timestamp=" + timestamp + "\n");
            writer.write("selected=" + selected + "\n");
            if (favorite) {
                writer.write("favorite=" + favorite + "\n");
            }

            // Voice texture
            if (mouthOpenPath != null) {
                writer.write("mouth_open=" + escape(mouthOpenPath.getFileName().toString()) + "\n");
            }
        }
        
        this.metaFilePath = path;
        BBTSkin.LOGGER.debug("Saved metadata to: {}", path);
    }
    
    /**
     * Calculate SHA-256 checksum of skin file
     */
    public static String calculateChecksum(Path skinFile) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(skinFile);
            byte[] hash = digest.digest(fileBytes);
            
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IOException("Failed to calculate checksum", e);
        }
    }
    
    /**
     * Create metadata for a new skin
     */
    public static SkinMetadata create(String name, Path skinFile, boolean slim, 
                                       String playerName, String playerUuid,
                                       int width, int height) throws IOException {
        SkinMetadata meta = new SkinMetadata();
        meta.name = name;
        meta.skinFilePath = skinFile;
        meta.slim = slim;
        meta.uploadedBy = playerName;
        meta.uploadedUuid = playerUuid;
        meta.width = width;
        meta.height = height;
        meta.timestamp = System.currentTimeMillis();
        meta.selected = false;
        
        if (Files.exists(skinFile)) {
            meta.checksum = calculateChecksum(skinFile);
        }
        
        // Set meta file path
        String baseName = skinFile.getFileName().toString().replace(".png", "");
        meta.metaFilePath = skinFile.getParent().resolve(baseName + ".meta");
        
        return meta;
    }
    
    /**
     * Convert to SkinData for compatibility
     */
    public SkinData toSkinData() {
        SkinData.Builder builder = new SkinData.Builder()
                .id(generateId())
                .name(name)
                .width(width)
                .height(height)
                .slim(slim)
                .createdAt(timestamp)
                .ownerUUID(uploadedUuid);
        
        // Load main image data if available
        if (skinFilePath != null && Files.exists(skinFilePath)) {
            try {
                builder.imageData(Files.readAllBytes(skinFilePath));
            } catch (IOException e) {
                BBTSkin.LOGGER.warn("Failed to load skin image: {}", skinFilePath, e);
            }
        }
        
        // Load mouth-open texture if available
        if (mouthOpenPath != null && Files.exists(mouthOpenPath)) {
            try {
                builder.mouthOpenData(Files.readAllBytes(mouthOpenPath));
            } catch (IOException e) {
                BBTSkin.LOGGER.warn("Failed to load mouth-open texture: {}", mouthOpenPath, e);
            }
        }
        
        return builder.build();
    }
    
    /**
     * Generate unique ID from checksum or filename
     */
    public String generateId() {
        if (checksum != null && !checksum.isEmpty()) {
            return checksum.substring(0, Math.min(16, checksum.length()));
        }
        if (metaFilePath != null) {
            return metaFilePath.getFileName().toString().replace(".meta", "");
        }
        return String.valueOf(System.currentTimeMillis());
    }
    
    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("=", "\\=")
                   .replace(":", "\\:");
    }
    
    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 64;
        }
    }
    
    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
    
    public String getUploadedUuid() { return uploadedUuid; }
    public void setUploadedUuid(String uploadedUuid) { this.uploadedUuid = uploadedUuid; }
    
    public boolean isSlim() { return slim; }
    public void setSlim(boolean slim) { this.slim = slim; }
    
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }

    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }
    
    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
    
    public Path getMetaFilePath() { return metaFilePath; }
    public void setMetaFilePath(Path metaFilePath) { this.metaFilePath = metaFilePath; }
    
    public Path getSkinFilePath() { return skinFilePath; }
    public void setSkinFilePath(Path skinFilePath) { this.skinFilePath = skinFilePath; }
    
    public Path getMouthOpenPath() { return mouthOpenPath; }
    public void setMouthOpenPath(Path mouthOpenPath) { this.mouthOpenPath = mouthOpenPath; }
    
    public boolean hasMouthOpenTexture() { return mouthOpenPath != null && Files.exists(mouthOpenPath); }
    
    @Override
    public String toString() {
        String voice = hasMouthOpenTexture() ? ", voice=true" : "";
        return String.format("SkinMetadata{name='%s', slim=%s, selected=%s, %dx%d%s}", 
                name, slim, selected, width, height, voice);
    }
}
