package com.bbt.skin.client.gui.screen;

import com.bbt.skin.BBTSkin;
import com.bbt.skin.client.BBTSkinClient;
import com.bbt.skin.client.gui.input.DragDropHandler;
import com.bbt.skin.client.gui.widget.BBTButton;
import com.bbt.skin.client.gui.widget.BBTCheckbox;
import com.bbt.skin.client.gui.widget.PlayerModelWidget;
import com.bbt.skin.client.voice.PlasmoVoiceCompat;
import com.bbt.skin.common.data.SkinData;
import com.bbt.skin.common.data.SkinManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Simple skin import screen
 * - Browse Skin File (default/closed mouth)
 * - Preset Name
 * - Slim model checkbox
 * - Mouth Open checkbox (optional feature)
 * - Browse Open Mouth File (only when Mouth Open is checked)
 */
public class SkinImportScreen extends Screen {
    
    // Colors
    private static final int COLOR_BACKGROUND = 0xFF0A0A15;
    private static final int COLOR_PANEL = 0xE616213E;
    private static final int COLOR_BORDER = 0xFF0F3460;
    private static final int COLOR_ACCENT = 0xFFE94560;
    private static final int COLOR_TEXT = 0xFFEEEEEE;
    private static final int COLOR_TEXT_DIM = 0xFFAAAAAA;
    private static final int COLOR_SUCCESS = 0xFF22C55E;
    private static final int COLOR_ERROR = 0xFFEF4444;
    
    private static final int PADDING = 20;
    
    @Nullable private final SkinSelectionScreen parentScreen;
    
    // Panel bounds
    private int panelX, panelY, panelWidth, panelHeight;
    private int previewX, previewY, previewSize;
    
    // Widgets
    private BBTButton browseSkinButton;
    private EditBox presetNameField;
    private BBTCheckbox slimCheckbox;
    private BBTCheckbox mouthOpenCheckbox;
    private BBTButton browseOpenMouthButton;
    private PlayerModelWidget previewModel;
    private BBTButton importButton;
    private BBTButton cancelButton;
    
    // Zoom controls
    private BBTButton zoomInButton;
    private BBTButton zoomOutButton;
    private BBTButton resetViewButton;
    
    // State - Main skin (default/closed mouth)
    @Nullable private Path skinFilePath;
    @Nullable private BufferedImage skinImage;
    @Nullable private byte[] skinData;
    
    // State - Open mouth texture (optional)
    @Nullable private Path openMouthPath;
    @Nullable private BufferedImage openMouthImage;
    @Nullable private byte[] openMouthData;
    
    // UI state
    private boolean isProcessing = false;
    private String statusMessage = "";
    private int statusColor = COLOR_TEXT;
    
    // Plasmo Voice
    private final boolean plasmoVoiceAvailable;
    
    // Drag & Drop
    @Nullable private DragDropHandler dragDropHandler;
    
    public SkinImportScreen(@Nullable SkinSelectionScreen parent) {
        super(Component.translatable("gui.bbtskin.import_skin"));
        this.parentScreen = parent;
        PlasmoVoiceCompat.init();
        this.plasmoVoiceAvailable = PlasmoVoiceCompat.isAvailable();
    }
    
    @Override
    protected void init() {
        super.init();
        computeLayout();
        clearWidgets();
        createWidgets();
        setupDragAndDrop();
    }
    
    private void setupDragAndDrop() {
        // Clean up any existing handler
        if (dragDropHandler != null) {
            dragDropHandler.cleanup();
        }
        
        // Create new handler for file drag & drop
        long windowHandle = Minecraft.getInstance().getWindow().getWindow();
        dragDropHandler = new DragDropHandler(windowHandle);
        dragDropHandler.setDropCallback(path -> {
            String fileName = path.getFileName().toString().toLowerCase();
            if (fileName.endsWith(".png")) {
                BBTSkin.LOGGER.info("Skin file dropped: {}", path);
                // Run on main thread
                minecraft.execute(() -> loadSkinFile(path));
            } else {
                // Run on main thread
                minecraft.execute(() -> {
                    statusMessage = "Only PNG files are supported";
                    statusColor = COLOR_ERROR;
                });
            }
        });
    }
    
    private void computeLayout() {
        panelWidth = Math.min(520, width - PADDING * 2);
        panelHeight = Math.min(380, height - PADDING * 2);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        
        previewSize = Math.min(140, panelHeight - 140);
        previewX = panelX + panelWidth - previewSize - PADDING;
        previewY = panelY + 50;
    }
    
    private void createWidgets() {
        int controlX = panelX + PADDING;
        int controlWidth = panelWidth - previewSize - PADDING * 4;
        int currentY = panelY + 50;
        
        // ===== BROWSE SKIN FILE =====
        browseSkinButton = new BBTButton(
                controlX, currentY, controlWidth, 26,
                Component.literal("Browse Skin File"),
                btn -> browseSkinFile(),
                BBTButton.Style.PRIMARY
        );
        addRenderableWidget(browseSkinButton);
        currentY += 50;  // More spacing after browse button
        
        // ===== PRESET NAME =====
        presetNameField = new EditBox(font, controlX, currentY, controlWidth, 20,
                Component.literal("Preset Name"));
        presetNameField.setHint(Component.literal("Enter preset name..."));
        presetNameField.setMaxLength(64);
        addRenderableWidget(presetNameField);
        currentY += 36;  // More spacing after name field
        
        // ===== SLIM MODEL CHECKBOX =====
        slimCheckbox = new BBTCheckbox(
                controlX, currentY, controlWidth, 18,
                Component.literal("Slim model (3px arms)"),
                false
        );
        slimCheckbox.setCallback(checked -> {
            if (previewModel != null) {
                previewModel.setSlimModel(checked);
            }
        });
        addRenderableWidget(slimCheckbox);
        currentY += 30;  // Slightly more spacing
        
        // ===== MOUTH OPEN CHECKBOX =====
        String mouthLabel = plasmoVoiceAvailable ? 
                "Mouth Open (Plasmo Voice)" : 
                "Mouth Open (requires Plasmo Voice)";
        mouthOpenCheckbox = new BBTCheckbox(
                controlX, currentY, controlWidth, 18,
                Component.literal(mouthLabel),
                false
        );
        mouthOpenCheckbox.active = plasmoVoiceAvailable;
        addRenderableWidget(mouthOpenCheckbox);
        currentY += 28;
        
        // ===== BROWSE OPEN MOUTH FILE (initially hidden/inactive) =====
        browseOpenMouthButton = new BBTButton(
                controlX + 20, currentY, controlWidth - 20, 24,
                Component.literal("Browse Open Mouth File"),
                btn -> browseOpenMouthFile(),
                BBTButton.Style.SECONDARY
        );
        browseOpenMouthButton.active = false;
        browseOpenMouthButton.visible = false;
        addRenderableWidget(browseOpenMouthButton);
        
        // ===== PREVIEW =====
        previewModel = new PlayerModelWidget(previewX, previewY, previewSize, previewSize);
        addRenderableWidget(previewModel);
        
        // ===== ZOOM CONTROLS =====
        int zoomY = previewY + previewSize + 5;
        int btnW = 28;
        
        zoomOutButton = new BBTButton(previewX, zoomY, btnW, 18, 
                Component.literal("-"), btn -> previewModel.zoomOut(), BBTButton.Style.SECONDARY);
        addRenderableWidget(zoomOutButton);
        
        resetViewButton = new BBTButton(previewX + btnW + 4, zoomY, 50, 18, 
                Component.literal("Reset"), btn -> previewModel.resetView(), BBTButton.Style.SECONDARY);
        addRenderableWidget(resetViewButton);
        
        zoomInButton = new BBTButton(previewX + btnW + 58, zoomY, btnW, 18, 
                Component.literal("+"), btn -> previewModel.zoomIn(), BBTButton.Style.SECONDARY);
        addRenderableWidget(zoomInButton);
        
        // ===== BOTTOM BUTTONS =====
        int buttonWidth = 100;
        int buttonSpacing = 20;
        int buttonY = panelY + panelHeight - 42;
        int buttonsX = panelX + (panelWidth - buttonWidth * 2 - buttonSpacing) / 2;
        
        importButton = new BBTButton(
                buttonsX, buttonY, buttonWidth, 28,
                Component.literal("Import"),
                btn -> importSkin(),
                BBTButton.Style.SUCCESS
        );
        importButton.active = false;
        addRenderableWidget(importButton);
        
        cancelButton = new BBTButton(
                buttonsX + buttonWidth + buttonSpacing, buttonY, buttonWidth, 28,
                Component.literal("Cancel"),
                btn -> onClose(),
                BBTButton.Style.SECONDARY
        );
        addRenderableWidget(cancelButton);
    }
    
    @Override
    public void tick() {
        super.tick();
        
        // Show/hide open mouth button based on checkbox
        boolean showOpenMouth = mouthOpenCheckbox.isChecked() && plasmoVoiceAvailable;
        browseOpenMouthButton.visible = showOpenMouth;
        browseOpenMouthButton.active = showOpenMouth && skinFilePath != null;
        
        // Update import button
        importButton.active = canImport();
    }
    
    private boolean canImport() {
        if (isProcessing) return false;
        if (skinFilePath == null || skinImage == null) return false;
        
        // If mouth open is checked, require open mouth file
        if (mouthOpenCheckbox.isChecked() && plasmoVoiceAvailable) {
            if (openMouthPath == null || openMouthImage == null) return false;
        }
        
        return true;
    }
    
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Background
        graphics.fill(0, 0, width, height, COLOR_BACKGROUND);
        
        // Panel
        drawPanel(graphics);
        
        // Title
        graphics.drawCenteredString(font, "Import Skin", width / 2, panelY + 15, COLOR_ACCENT);
        
        // Labels
        drawLabels(graphics);
        
        // File status
        drawFileStatus(graphics);
        
        // Status message
        if (!statusMessage.isEmpty()) {
            int msgWidth = font.width(statusMessage);
            graphics.drawString(font, statusMessage,
                    panelX + (panelWidth - msgWidth) / 2,
                    panelY + panelHeight - 65, statusColor, true);
        }
        
        super.render(graphics, mouseX, mouseY, delta);
    }
    
    private void drawPanel(GuiGraphics graphics) {
        // Main panel
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, COLOR_PANEL);
        drawBorder(graphics, panelX, panelY, panelWidth, panelHeight, COLOR_BORDER);
        
        // Preview border
        int pad = 4;
        graphics.fill(previewX - pad, previewY - pad, 
                previewX + previewSize + pad, previewY + previewSize + pad, 0xFF12162B);
        drawBorder(graphics, previewX - pad, previewY - pad, 
                previewSize + pad * 2, previewSize + pad * 2, COLOR_BORDER);
    }
    
    private void drawBorder(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }
    
    private void drawLabels(GuiGraphics graphics) {
        int labelX = panelX + PADDING;
        graphics.drawString(font, "Skin File:", labelX, panelY + 38, COLOR_TEXT_DIM, false);
        graphics.drawString(font, "Preset Name:", labelX, panelY + 88, COLOR_TEXT_DIM, false);
        
        // Drag & drop hint (only show when no skin loaded)
        if (skinFilePath == null) {
            String hint = "or drag & drop PNG file";
            int hintWidth = font.width(hint);
            int hintX = previewX + (previewSize - hintWidth) / 2;
            int hintY = previewY + previewSize / 2 + 20;
            graphics.drawString(font, hint, hintX, hintY, 0x66FFFFFF, false);
        }
    }
    
    private void drawFileStatus(GuiGraphics graphics) {
        int statusX = previewX;
        int statusY = previewY + previewSize + 28;
        
        // Main skin status
        if (skinFilePath != null) {
            String filename = skinFilePath.getFileName().toString();
            if (filename.length() > 12) filename = filename.substring(0, 9) + "...";
            graphics.drawCenteredString(font, "✓ " + filename, statusX + previewSize/2, statusY, COLOR_SUCCESS);
            
            if (skinImage != null) {
                String dims = skinImage.getWidth() + "x" + skinImage.getHeight();
                graphics.drawCenteredString(font, dims, statusX + previewSize/2, statusY + 11, COLOR_TEXT_DIM);
            }
        } else {
            graphics.drawCenteredString(font, "No file", statusX + previewSize/2, statusY, COLOR_TEXT_DIM);
        }
        
        // Open mouth status (if checkbox checked)
        if (mouthOpenCheckbox.isChecked() && plasmoVoiceAvailable) {
            int openStatusY = statusY + 24;
            if (openMouthPath != null) {
                graphics.drawCenteredString(font, "✓ Mouth Open", statusX + previewSize/2, openStatusY, COLOR_SUCCESS);
            } else {
                graphics.drawCenteredString(font, "○ Select mouth", statusX + previewSize/2, openStatusY, COLOR_TEXT_DIM);
            }
        }
    }
    
    // ===== FILE BROWSING =====
    
    private void browseSkinFile() {
        if (isProcessing) return;
        
        CompletableFuture.runAsync(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(1);
                filters.put(stack.UTF8("*.png"));
                filters.flip();
                
                String result = TinyFileDialogs.tinyfd_openFileDialog(
                        "Select Skin File", "", filters, "PNG Images (*.png)", false);
                
                if (result != null) {
                    Path path = Path.of(result);
                    minecraft.execute(() -> loadSkinFile(path));
                }
            } catch (Exception e) {
                BBTSkin.LOGGER.error("File browser error", e);
            }
        });
    }
    
    private void browseOpenMouthFile() {
        if (isProcessing || !plasmoVoiceAvailable) return;
        
        CompletableFuture.runAsync(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(1);
                filters.put(stack.UTF8("*.png"));
                filters.flip();
                
                String result = TinyFileDialogs.tinyfd_openFileDialog(
                        "Select Open Mouth Texture", "", filters, "PNG Images (*.png)", false);
                
                if (result != null) {
                    Path path = Path.of(result);
                    minecraft.execute(() -> loadOpenMouthFile(path));
                }
            } catch (Exception e) {
                BBTSkin.LOGGER.error("File browser error", e);
            }
        });
    }
    
    private void loadSkinFile(Path path) {
        if (!path.toString().toLowerCase().endsWith(".png")) {
            showStatus("Invalid format - PNG only", COLOR_ERROR);
            return;
        }
        
        try {
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) {
                showStatus("Failed to read image", COLOR_ERROR);
                return;
            }
            
            int w = image.getWidth();
            int h = image.getHeight();
            
            if (!isValidSkinSize(w, h)) {
                showStatus("Invalid size: " + w + "x" + h, COLOR_ERROR);
                return;
            }
            
            skinFilePath = path;
            skinImage = image;
            skinData = Files.readAllBytes(path);
            
            // Auto-fill preset name if empty
            if (presetNameField.getValue().isEmpty()) {
                String name = path.getFileName().toString().replace(".png", "");
                presetNameField.setValue(name);
            }
            
            // Update preview
            updatePreview();
            
            showStatus("Loaded: " + w + "x" + h, COLOR_SUCCESS);
            
        } catch (Exception e) {
            BBTSkin.LOGGER.error("Failed to load skin file", e);
            showStatus("Error loading file", COLOR_ERROR);
        }
    }
    
    private void loadOpenMouthFile(Path path) {
        if (!path.toString().toLowerCase().endsWith(".png")) {
            showStatus("Invalid format - PNG only", COLOR_ERROR);
            return;
        }
        
        try {
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) {
                showStatus("Failed to read image", COLOR_ERROR);
                return;
            }
            
            // Validate size matches main skin
            if (skinImage != null) {
                if (image.getWidth() != skinImage.getWidth() ||
                    image.getHeight() != skinImage.getHeight()) {
                    showStatus("Size must match main skin!", COLOR_ERROR);
                    return;
                }
            }
            
            openMouthPath = path;
            openMouthImage = image;
            openMouthData = Files.readAllBytes(path);
            
            showStatus("Mouth texture loaded", COLOR_SUCCESS);
            
        } catch (Exception e) {
            BBTSkin.LOGGER.error("Failed to load open mouth file", e);
            showStatus("Error loading file", COLOR_ERROR);
        }
    }
    
    private void updatePreview() {
        if (skinImage != null && previewModel != null) {
            previewModel.setSkinFromImage(skinImage, slimCheckbox.isChecked());
        }
    }
    
    private boolean isValidSkinSize(int w, int h) {
        if (w == 64 && (h == 64 || h == 32)) return true;
        if (w == h && w >= 64 && w <= 8192 && (w & (w - 1)) == 0) return true;
        return false;
    }
    
    private void showStatus(String message, int color) {
        statusMessage = message;
        statusColor = color;
    }
    
    // ===== IMPORT =====
    
    private void importSkin() {
        if (!canImport()) return;
        
        // Get or generate preset name
        String presetName = presetNameField.getValue().trim();
        if (presetName.isEmpty()) {
            presetName = skinFilePath.getFileName().toString().replace(".png", "");
        }
        
        isProcessing = true;
        importButton.active = false;
        showStatus("Importing...", COLOR_TEXT);
        
        try {
            SkinManager manager = BBTSkinClient.getInstance().getSkinManager();
            
            // Build skin data
            SkinData.Builder builder = new SkinData.Builder()
                    .name(presetName)
                    .width(skinImage.getWidth())
                    .height(skinImage.getHeight())
                    .slim(slimCheckbox.isChecked())
                    .imageData(skinData);
            
            // Add open mouth texture if enabled and present
            if (mouthOpenCheckbox.isChecked() && openMouthData != null && plasmoVoiceAvailable) {
                builder.mouthOpenData(openMouthData);
                BBTSkin.LOGGER.info("Importing '{}' with mouth-open texture", presetName);
            }
            
            SkinData skin = builder.build();
            boolean saved = manager.saveSkin(skin);
            
            if (saved) {
                showStatus("Imported successfully!", COLOR_SUCCESS);
                
                // Return to parent
                minecraft.execute(() -> {
                    if (parentScreen != null) {
                        parentScreen.refreshSkinList();
                        minecraft.setScreen(parentScreen);
                    } else {
                        minecraft.setScreen(new SkinSelectionScreen());
                    }
                });
            } else {
                showStatus("Import failed", COLOR_ERROR);
                importButton.active = true;
            }
            
        } catch (Exception e) {
            BBTSkin.LOGGER.error("Import error", e);
            showStatus("Error: " + e.getMessage(), COLOR_ERROR);
            importButton.active = true;
        }
        
        isProcessing = false;
    }
    
    @Override
    public void removed() {
        super.removed();
        if (previewModel != null) {
            previewModel.close();
        }
        if (dragDropHandler != null) {
            dragDropHandler.cleanup();
            dragDropHandler = null;
        }
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    @Override
    public void onClose() {
        if (previewModel != null) {
            previewModel.close();
        }
        if (parentScreen != null) {
            minecraft.setScreen(parentScreen);
        } else {
            minecraft.setScreen(null);
        }
    }
}
