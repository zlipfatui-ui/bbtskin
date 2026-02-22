package com.bbt.skin.client.gui.screen;

import com.bbt.skin.BBTSkin;
import com.bbt.skin.client.BBTSkinClient;
import com.bbt.skin.client.gui.widget.BBTButton;
import com.bbt.skin.client.gui.widget.PlayerModelWidget;
import com.bbt.skin.client.gui.widget.SkinListWidget;
import com.bbt.skin.common.data.SkinData;
import com.bbt.skin.common.data.SkinManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Main skin selection screen with responsive layout
 * Properly handles all GUI scales and screen sizes
 */
public class SkinSelectionScreen extends Screen {
    
    // Colors - Glass morphism theme
    private static final int COLOR_BACKGROUND = 0xFF0A0A15;
    private static final int COLOR_PANEL = 0xE6161625;
    private static final int COLOR_BORDER = 0xFF0F3460;
    private static final int COLOR_ACCENT = 0xFFE94560;
    private static final int COLOR_TEXT = 0xFFEEEEEE;
    private static final int COLOR_TEXT_DIM = 0xFFAAAAAA;
    private static final int COLOR_SUCCESS = 0xFF22C55E;
    
    // Layout constants
    private static final int PADDING = 12;
    private static final int TITLE_HEIGHT = 32;
    private static final int BUTTON_HEIGHT = 24;
    private static final int BUTTON_SPACING = 8;
    private static final int PANEL_GAP = 10;
    
    // Layout bounds (computed in init)
    private int leftPanelX, leftPanelY, leftPanelWidth, leftPanelHeight;
    private int rightPanelX, rightPanelY, rightPanelWidth, rightPanelHeight;
    private int buttonAreaY;
    
    // Colors
    private static final int COLOR_FAVORITE = 0xFFFFD700;
    private static final int COLOR_FAVORITE_DIM = 0xFF666655;

    // Widgets
    private EditBox searchBox;
    private SkinListWidget skinList;
    private PlayerModelWidget playerModel;
    private BBTButton importButton;
    private BBTButton deleteButton;
    private BBTButton resetButton;
    private BBTButton applyButton;
    private BBTButton favFilterButton;

    // State
    @Nullable
    private SkinData selectedSkin = null;
    private boolean favoritesOnly = false;
    
    public SkinSelectionScreen() {
        super(Component.translatable("gui.bbtskin.skin_selection"));
    }
    
    @Override
    protected void init() {
        super.init();
        BBTSkin.LOGGER.info("[BBTSkin v1.3.0-patch] SkinSelectionScreen.init() called, screen={}x{}", width, height);

        computeLayout();
        clearWidgets();
        
        createSearchBox();
        createSkinList();
        createPlayerModel();
        createButtons();
        
        refreshSkinList();
    }
    
    /**
     * Compute responsive layout based on screen dimensions
     */
    private void computeLayout() {
        int contentY = TITLE_HEIGHT + PADDING;
        int buttonAreaHeight = BUTTON_HEIGHT + PADDING * 2;
        int contentHeight = height - contentY - buttonAreaHeight - PADDING;
        buttonAreaY = height - buttonAreaHeight;

        // Calculate panel widths - left panel ~38%, right panel ~32%, with explicit gap
        int totalWidth = width - PADDING * 2 - PANEL_GAP;
        leftPanelWidth = Math.max(180, Math.min(380, (int)(totalWidth * 0.38)));
        rightPanelWidth = Math.max(160, Math.min(320, (int)(totalWidth * 0.32)));

        // Panel positions
        leftPanelX = PADDING;
        leftPanelY = contentY;
        leftPanelHeight = contentHeight;

        rightPanelX = width - rightPanelWidth - PADDING;
        rightPanelY = contentY;
        rightPanelHeight = contentHeight;
    }
    
    private void createSearchBox() {
        int favBtnWidth = 20;
        int gap = 4;
        int searchWidth = leftPanelWidth - PADDING * 2 - favBtnWidth - gap;
        int searchX = leftPanelX + PADDING;
        int searchY = leftPanelY + PADDING;

        searchBox = new EditBox(font, searchX, searchY, searchWidth, 18,
                Component.translatable("gui.bbtskin.search.placeholder"));
        searchBox.setHint(Component.translatable("gui.bbtskin.search.placeholder"));
        searchBox.setMaxLength(50);
        searchBox.setResponder(this::onSearchChanged);
        addRenderableWidget(searchBox);

        // Favorites filter toggle button
        favFilterButton = new BBTButton(
                searchX + searchWidth + gap, searchY - 1, favBtnWidth, 20,
                Component.literal(favoritesOnly ? "\u2605" : "\u2606"),
                btn -> toggleFavoritesFilter(),
                BBTButton.Style.GHOST
        );
        addRenderableWidget(favFilterButton);
    }
    
    private void createSkinList() {
        int listX = leftPanelX + PADDING;
        int listY = leftPanelY + PADDING + 28;
        int listWidth = leftPanelWidth - PADDING * 2;
        int listHeight = leftPanelHeight - PADDING * 2 - 28;
        
        skinList = new SkinListWidget(minecraft, listWidth, listHeight, listX, listY, 38);
        skinList.setSelectionCallback(this::onSkinSelected);
        addRenderableWidget(skinList);
    }
    
    private void createPlayerModel() {
        int modelSize = Math.min(rightPanelWidth - PADDING * 2, rightPanelHeight - 100);
        modelSize = Math.max(100, modelSize);
        
        int modelX = rightPanelX + (rightPanelWidth - modelSize) / 2;
        int modelY = rightPanelY + PADDING;
        
        playerModel = new PlayerModelWidget(modelX, modelY, modelSize, modelSize);
        addRenderableWidget(playerModel);
    }
    
    private void createButtons() {
        // Left panel buttons - centered within panel
        int buttonWidth = (leftPanelWidth - PADDING * 2 - BUTTON_SPACING * 2) / 3;
        buttonWidth = Math.max(50, Math.min(100, buttonWidth));

        int totalButtonsWidth = buttonWidth * 3 + BUTTON_SPACING * 2;
        int buttonsStartX = leftPanelX + (leftPanelWidth - totalButtonsWidth) / 2;
        int buttonY = buttonAreaY + PADDING;
        
        importButton = new BBTButton(
                buttonsStartX, buttonY, buttonWidth, BUTTON_HEIGHT,
                Component.translatable("gui.bbtskin.import"),
                btn -> openImportScreen(),
                BBTButton.Style.PRIMARY
        );
        addRenderableWidget(importButton);
        
        deleteButton = new BBTButton(
                buttonsStartX + buttonWidth + BUTTON_SPACING, buttonY, buttonWidth, BUTTON_HEIGHT,
                Component.translatable("gui.bbtskin.delete"),
                btn -> deleteSkin(),
                BBTButton.Style.DANGER
        );
        deleteButton.active = false;
        addRenderableWidget(deleteButton);
        
        resetButton = new BBTButton(
                buttonsStartX + (buttonWidth + BUTTON_SPACING) * 2, buttonY, buttonWidth, BUTTON_HEIGHT,
                Component.translatable("gui.bbtskin.reset"),
                btn -> resetSkin(),
                BBTButton.Style.SECONDARY
        );
        addRenderableWidget(resetButton);
        
        // Right panel apply button
        int applyWidth = rightPanelWidth - PADDING * 2;
        int applyX = rightPanelX + PADDING;
        
        applyButton = new BBTButton(
                applyX, buttonY, applyWidth, BUTTON_HEIGHT,
                Component.translatable("gui.bbtskin.apply"),
                btn -> applySkin(),
                BBTButton.Style.SUCCESS
        );
        applyButton.active = false;
        addRenderableWidget(applyButton);
    }
    
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // 1. Fill entire screen with solid background (replaces dirt)
        graphics.fill(0, 0, width, height, COLOR_BACKGROUND);
        
        // 2. Draw panels
        drawLeftPanel(graphics);
        drawRightPanel(graphics);
        
        // 3. Draw title
        drawTitle(graphics);
        
        // 4. Draw skin info
        drawSkinInfo(graphics);
        
        // 5. Render widgets
        super.render(graphics, mouseX, mouseY, delta);
    }
    
    private void drawTitle(GuiGraphics graphics) {
        String title = "BBTSkin";
        String subtitle = " - Skin Selection [v1.3.0-patch]";

        int titleWidth = font.width(title);
        int subtitleWidth = font.width(subtitle);
        int totalTitleWidth = titleWidth + subtitleWidth;
        int titleX = (width - totalTitleWidth) / 2;
        int titleY = TITLE_HEIGHT / 2 - 4;

        graphics.drawString(font, title, titleX, titleY, COLOR_ACCENT, true);
        graphics.drawString(font, subtitle, titleX + titleWidth, titleY, COLOR_TEXT, true);

        // Subtle separator line below title
        int sepY = TITLE_HEIGHT + PADDING / 2;
        graphics.fill(PADDING, sepY, width - PADDING, sepY + 1, 0x33FFFFFF);
    }
    
    private void drawLeftPanel(GuiGraphics graphics) {
        graphics.fill(leftPanelX, leftPanelY, 
                leftPanelX + leftPanelWidth, leftPanelY + leftPanelHeight, 
                COLOR_PANEL);
        drawBorder(graphics, leftPanelX, leftPanelY, leftPanelWidth, leftPanelHeight, COLOR_BORDER);
    }
    
    private void drawRightPanel(GuiGraphics graphics) {
        graphics.fill(rightPanelX, rightPanelY, 
                rightPanelX + rightPanelWidth, rightPanelY + rightPanelHeight, 
                COLOR_PANEL);
        drawBorder(graphics, rightPanelX, rightPanelY, rightPanelWidth, rightPanelHeight, COLOR_BORDER);
    }
    
    private void drawBorder(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }
    
    private void drawSkinInfo(GuiGraphics graphics) {
        int infoX = rightPanelX + PADDING;
        int infoY = rightPanelY + playerModel.getHeight() + PADDING * 2;
        int infoWidth = rightPanelWidth - PADDING * 2;

        // Subtle divider between model and info
        graphics.fill(infoX, infoY, infoX + infoWidth, infoY + 1, 0x22FFFFFF);
        infoY += 8;

        // Clear area
        graphics.fill(infoX, infoY, infoX + infoWidth, infoY + 50, COLOR_PANEL);

        if (selectedSkin != null) {
            String name = selectedSkin.getName();
            if (font.width(name) > infoWidth) {
                name = font.plainSubstrByWidth(name, infoWidth - 10) + "...";
            }
            graphics.drawString(font, name, infoX, infoY, COLOR_TEXT, true);

            String details = String.format("%dx%d | %s",
                    selectedSkin.getWidth(),
                    selectedSkin.getHeight(),
                    selectedSkin.isSlim() ? "Slim" : "Classic"
            );
            graphics.drawString(font, details, infoX, infoY + 14, COLOR_TEXT_DIM, false);

            if (isCurrentSkinApplied()) {
                graphics.drawString(font, "\u2713 Applied", infoX, infoY + 28, COLOR_SUCCESS, false);
            }
        } else {
            graphics.drawString(font, "No Skin Selected", infoX, infoY, COLOR_TEXT_DIM, false);
            graphics.drawString(font, "Select from list", infoX, infoY + 14, COLOR_TEXT_DIM, false);
        }
    }
    
    private void onSearchChanged(String search) {
        if (skinList != null) {
            skinList.setFilter(search);
        }
    }

    private void toggleFavoritesFilter() {
        favoritesOnly = !favoritesOnly;
        if (skinList != null) {
            skinList.setFavoritesOnly(favoritesOnly);
        }
        if (favFilterButton != null) {
            favFilterButton.setMessage(Component.literal(favoritesOnly ? "\u2605" : "\u2606"));
        }
    }
    
    private void onSkinSelected(@Nullable SkinData skin) {
        this.selectedSkin = skin;
        
        boolean hasSkin = skin != null;
        deleteButton.active = hasSkin && !isCurrentSkinApplied();
        applyButton.active = hasSkin;
        
        if (playerModel != null) {
            if (skin != null) {
                playerModel.setSkin(skin);
            } else {
                playerModel.clearSkin();
            }
        }
    }
    
    private boolean isCurrentSkinApplied() {
        if (selectedSkin == null) return false;
        SkinManager manager = BBTSkinClient.getInstance().getSkinManager();
        SkinData applied = manager.getAppliedSkin();
        return applied != null && applied.getId().equals(selectedSkin.getId());
    }
    
    public void refreshSkinList() {
        if (skinList != null) {
            SkinManager manager = BBTSkinClient.getInstance().getSkinManager();
            List<SkinData> skins = manager.getAllSkins();
            skinList.setSkins(skins);
            
            if (selectedSkin != null) {
                skinList.selectSkin(selectedSkin.getId());
            } else {
                SkinData applied = manager.getAppliedSkin();
                if (applied != null) {
                    skinList.selectSkin(applied.getId());
                    onSkinSelected(applied);
                }
            }
        }
    }
    
    private void openImportScreen() {
        minecraft.setScreen(new SkinImportScreen(this));
    }
    
    private void deleteSkin() {
        if (selectedSkin == null || isCurrentSkinApplied()) return;
        
        SkinManager manager = BBTSkinClient.getInstance().getSkinManager();
        if (manager.deleteSkin(selectedSkin.getId())) {
            selectedSkin = null;
            refreshSkinList();
        }
    }
    
    private void resetSkin() {
        SkinManager manager = BBTSkinClient.getInstance().getSkinManager();
        manager.clearAppliedSkin();
        BBTSkinClient.getInstance().getTextureManager().unloadLocalSkin();
        BBTSkinClient.sendSkinReset();
        refreshSkinList();
    }
    
    private void applySkin() {
        if (selectedSkin == null) return;
        
        SkinManager manager = BBTSkinClient.getInstance().getSkinManager();
        manager.setAppliedSkin(selectedSkin);
        BBTSkinClient.getInstance().getTextureManager().loadLocalSkin(selectedSkin);
        BBTSkinClient.syncCurrentSkin();
        deleteButton.active = false;
        refreshSkinList();
    }
    
    @Override
    public void resize(net.minecraft.client.Minecraft mc, int newWidth, int newHeight) {
        String searchText = searchBox != null ? searchBox.getValue() : "";
        SkinData prevSelected = selectedSkin;
        boolean prevFavOnly = favoritesOnly;

        super.resize(mc, newWidth, newHeight);

        if (searchBox != null && !searchText.isEmpty()) {
            searchBox.setValue(searchText);
        }
        if (prevFavOnly && skinList != null) {
            skinList.setFavoritesOnly(true);
        }
        if (prevSelected != null && skinList != null) {
            skinList.selectSkin(prevSelected.getId());
        }
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    @Override
    public void onClose() {
        // Clear preview texture when closing screen
        if (playerModel != null) {
            playerModel.close();
        }
        minecraft.setScreen(null);
    }
    
    @Override
    public void removed() {
        super.removed();
        // Also clear on removed (called when screen changes)
        if (playerModel != null) {
            playerModel.close();
        }
    }
}
