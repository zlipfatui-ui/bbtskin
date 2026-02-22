package com.bbt.skin.client.gui.widget;

import com.bbt.skin.BBTSkin;
import com.bbt.skin.client.BBTSkinClient;
import com.bbt.skin.common.data.SkinData;
import com.bbt.skin.common.data.SkinManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Scrollable list widget for displaying skins (Forge 1.20.1)
 * - No Minecraft dirt background
 * - Hidden scrollbar (but scrolling still works)
 * - Full-width row highlights
 */
public class SkinListWidget extends ObjectSelectionList<SkinListWidget.SkinEntry> {
    
    // Colors
    private static final int COLOR_BACKGROUND = 0xFF0A0A15;
    private static final int COLOR_SELECTED = 0x66E94560;
    private static final int COLOR_BORDER = 0xFF0F3460;
    private static final int COLOR_TEXT = 0xFFEEEEEE;
    private static final int COLOR_TEXT_DIM = 0xFFAAAAAA;
    private static final int COLOR_ACCENT = 0xFFE94560;
    private static final int COLOR_BADGE = 0xFF22C55E;
    private static final int COLOR_FAVORITE = 0xFFFFD700;
    private static final int COLOR_FAVORITE_DIM = 0xFF666655;
    private static final int STAR_CLICK_WIDTH = 14;

    private List<SkinData> allSkins = new ArrayList<>();
    private String filter = "";
    private boolean favoritesOnly = false;
    private Consumer<SkinData> selectionCallback;
    
    // Store our actual bounds
    private final int listX;
    private final int listWidth;
    
    public SkinListWidget(Minecraft minecraft, int width, int height, int x, int y, int itemHeight) {
        super(minecraft, width, height, y, y + height, itemHeight);
        this.listX = x;
        this.listWidth = width;
        
        // Disable default background rendering
        this.setRenderBackground(false);
        this.setRenderTopAndBottom(false);
    }
    
    public void setSkins(List<SkinData> skins) {
        this.allSkins = new ArrayList<>(skins);
        refreshEntries();
    }
    
    public void setFilter(String filter) {
        this.filter = filter.toLowerCase().trim();
        refreshEntries();
    }

    public void setFavoritesOnly(boolean favoritesOnly) {
        this.favoritesOnly = favoritesOnly;
        refreshEntries();
    }

    public boolean isFavoritesOnly() {
        return favoritesOnly;
    }
    
    public void setSelectionCallback(Consumer<SkinData> callback) {
        this.selectionCallback = callback;
    }
    
    public void selectSkin(String skinId) {
        for (int i = 0; i < this.children().size(); i++) {
            SkinEntry entry = this.children().get(i);
            if (entry.skin.getId().equals(skinId)) {
                this.setSelected(entry);
                this.ensureVisible(entry);
                break;
            }
        }
    }
    
    private void refreshEntries() {
        this.clearEntries();
        SkinManager manager = BBTSkinClient.getInstance() != null
                ? BBTSkinClient.getInstance().getSkinManager() : null;
        for (SkinData skin : allSkins) {
            if (!filter.isEmpty() && !skin.getName().toLowerCase().contains(filter)) {
                continue;
            }
            if (favoritesOnly && manager != null && !manager.isFavorite(skin.getId())) {
                continue;
            }
            this.addEntry(new SkinEntry(skin));
        }
    }
    
    @Override
    public void setSelected(@Nullable SkinEntry entry) {
        super.setSelected(entry);
        if (selectionCallback != null) {
            selectionCallback.accept(entry != null ? entry.skin : null);
        }
    }
    
    // ========== CRITICAL: Override these to use FULL width (no scrollbar reservation) ==========
    
    @Override
    public int getRowWidth() {
        // Use full width minus small padding for borders
        return this.listWidth - 4;
    }
    
    @Override
    public int getRowLeft() {
        return this.listX + 2;
    }
    
    @Override
    protected int getScrollbarPosition() {
        // Put scrollbar off-screen (we'll hide it anyway)
        return this.listX + this.listWidth + 100;
    }
    
    // ========== Override rendering to hide scrollbar and use our background ==========
    
    @Override
    protected void renderBackground(GuiGraphics graphics) {
        // Don't call super - no dirt background
    }
    
    @Override
    protected void renderDecorations(GuiGraphics graphics, int mouseX, int mouseY) {
        // Don't render scrollbar or any decorations
    }
    
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // 1. Draw our solid background
        graphics.fill(this.listX, this.y0, this.listX + this.listWidth, this.y1, COLOR_BACKGROUND);
        
        // 2. Enable scissor to clip content to our bounds
        graphics.enableScissor(this.listX + 1, this.y0 + 1, this.listX + this.listWidth - 1, this.y1 - 1);
        
        // 3. Render list content (entries only, no scrollbar due to our overrides)
        this.renderListItems(graphics, mouseX, mouseY, delta);
        
        // 4. Disable scissor
        graphics.disableScissor();
        
        // 5. Draw border
        drawBorder(graphics, this.listX, this.y0, this.listWidth, this.y1 - this.y0, COLOR_BORDER);
    }
    
    /**
     * Render list items manually to avoid parent's scrollbar rendering
     */
    private void renderListItems(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int itemCount = this.getItemCount();
        int rowWidth = this.getRowWidth();
        int rowLeft = this.getRowLeft();
        
        for (int i = 0; i < itemCount; i++) {
            int rowTop = this.getRowTop(i);
            int rowBottom = rowTop + this.itemHeight;
            
            // Skip items outside visible area
            if (rowBottom < this.y0 || rowTop > this.y1) {
                continue;
            }
            
            SkinEntry entry = this.getEntry(i);
            boolean isHovered = this.isMouseOver(mouseX, mouseY) && 
                    mouseY >= rowTop && mouseY < rowBottom &&
                    mouseX >= this.listX && mouseX < this.listX + this.listWidth;
            
            entry.render(graphics, i, rowTop, rowLeft, rowWidth, this.itemHeight - 4, 
                    mouseX, mouseY, isHovered, delta);
        }
    }
    
    private void drawBorder(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);           // Top
        graphics.fill(x, y + h - 1, x + w, y + h, color);   // Bottom
        graphics.fill(x, y, x + 1, y + h, color);           // Left
        graphics.fill(x + w - 1, y, x + w, y + h, color);   // Right
    }
    
    // ========== Override mouseClicked for proper hit detection ==========

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.isMouseOver(mouseX, mouseY)) return false;
        if (button != 0) return false;

        // Find which entry was clicked using our actual bounds
        for (int i = 0; i < this.getItemCount(); i++) {
            int rowTop = this.getRowTop(i);
            int rowBottom = rowTop + this.itemHeight;
            if (mouseY >= rowTop && mouseY < rowBottom) {
                SkinEntry entry = this.getEntry(i);
                entry.mouseClicked(mouseX, mouseY, button);
                return true;
            }
        }
        return false;
    }

    // ========== Keep mouse scroll working ==========

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // Check if mouse is within our bounds
        if (mouseX >= this.listX && mouseX < this.listX + this.listWidth &&
            mouseY >= this.y0 && mouseY < this.y1) {
            this.setScrollAmount(this.getScrollAmount() - delta * this.itemHeight / 2.0);
            return true;
        }
        return false;
    }
    
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= this.listX && mouseX < this.listX + this.listWidth &&
               mouseY >= this.y0 && mouseY < this.y1;
    }
    
    /**
     * Get our actual list X position
     */
    public int getListX() {
        return this.listX;
    }
    
    /**
     * Get our actual list width
     */
    public int getListWidth() {
        return this.listWidth;
    }
    
    /**
     * Entry class for skin list items
     */
    public class SkinEntry extends ObjectSelectionList.Entry<SkinEntry> {
        
        private final SkinData skin;
        private float hoverProgress = 0f;
        private long lastRenderTime = 0;
        
        public SkinEntry(SkinData skin) {
            this.skin = skin;
        }
        
        @Override
        public Component getNarration() {
            return Component.literal(skin.getName());
        }
        
        @Override
        public void render(GuiGraphics graphics, int index, int top, int left,
                          int width, int height, int mouseX, int mouseY,
                          boolean hovered, float delta) {

            updateAnimation(hovered);

            Minecraft mc = Minecraft.getInstance();
            boolean isSelected = SkinListWidget.this.getSelected() == this;
            boolean isApplied = isAppliedSkin();

            // Consistent padding constants
            int PAD = 6;
            int RIGHT_PAD = 8;

            // Use FULL panel width (from listX to listX + listWidth, with small margin)
            int fullLeft = SkinListWidget.this.listX + 2;
            int fullRight = SkinListWidget.this.listX + SkinListWidget.this.listWidth - 2;

            // Entry background - spans full width
            if (isSelected) {
                graphics.fill(fullLeft, top, fullRight, top + height, COLOR_SELECTED);
                // Left accent bar
                graphics.fill(fullLeft, top, fullLeft + 3, top + height, COLOR_ACCENT);
            } else if (hovered || hoverProgress > 0) {
                int alpha = (int)(40 * hoverProgress);
                graphics.fill(fullLeft, top, fullRight, top + height, (alpha << 24) | 0xFFFFFF);
            }

            // Text edges
            int textLeft = fullLeft + PAD + 4; // extra 4 for accent bar clearance
            int textRight = fullRight - RIGHT_PAD;

            // Two rows of text (9px font each), vertically centered in row
            int lineHeight = 9;
            int lineGap = 4;
            int textBlockHeight = lineHeight * 2 + lineGap;
            int row1Y = top + (height - textBlockHeight) / 2;
            int row2Y = row1Y + lineHeight + lineGap;

            // === ROW 1: Name (left) | [✓ Applied] [★/☆] (right, star always rightmost) ===

            // Star (always rightmost on row 1 baseline)
            boolean isFav = isFavoriteSkin();
            String star = isFav ? "\u2605" : "\u2606";
            int starColor = isFav ? COLOR_FAVORITE : COLOR_FAVORITE_DIM;
            int starWidth = mc.font.width(star);
            int starX = textRight - starWidth;
            graphics.drawString(mc.font, star, starX, row1Y, starColor, false);

            // Available width for name (and badge) — everything left of the star
            int row1RightEdge = starX - 4; // 4px gap before star

            // Applied badge (right-aligned on row 1, left of star)
            if (isApplied) {
                String badge = "\u2713 Applied";
                int badgeWidth = mc.font.width(badge);
                int badgeX = row1RightEdge - badgeWidth;
                graphics.drawString(mc.font, badge, badgeX, row1Y, COLOR_BADGE, false);
                row1RightEdge = badgeX - 4; // shrink available name space
            }

            // Name (left-aligned on row 1, truncated if needed)
            String name = skin.getName();
            int maxNameWidth = row1RightEdge - textLeft;
            if (maxNameWidth > 0) {
                if (mc.font.width(name) > maxNameWidth) {
                    name = mc.font.plainSubstrByWidth(name, maxNameWidth - mc.font.width("...")) + "...";
                }
                graphics.drawString(mc.font, name, textLeft, row1Y, COLOR_TEXT, false);
            }

            // === ROW 2: WxH (left) | Slim/Classic (right-aligned to textRight) ===

            String dims = skin.getWidth() + "x" + skin.getHeight();
            graphics.drawString(mc.font, dims, textLeft, row2Y, COLOR_TEXT_DIM, false);

            String modelType = skin.isSlim() ? "Slim" : "Classic";
            int modelTypeWidth = mc.font.width(modelType);
            graphics.drawString(mc.font, modelType, textRight - modelTypeWidth, row2Y, COLOR_TEXT_DIM, false);

            // Bottom separator line
            graphics.fill(fullLeft + 5, top + height - 1, fullRight - 5, top + height, 0x22FFFFFF);
        }
        
        private boolean isAppliedSkin() {
            BBTSkinClient client = BBTSkinClient.getInstance();
            if (client == null) return false;
            SkinData applied = client.getSkinManager().getAppliedSkin();
            return applied != null && applied.getId().equals(skin.getId());
        }

        private boolean isFavoriteSkin() {
            BBTSkinClient client = BBTSkinClient.getInstance();
            if (client == null) return false;
            return client.getSkinManager().isFavorite(skin.getId());
        }
        
        private void updateAnimation(boolean hovered) {
            long now = System.currentTimeMillis();
            if (lastRenderTime == 0) {
                lastRenderTime = now;
                return;
            }
            
            float deltaSeconds = (now - lastRenderTime) / 1000f;
            lastRenderTime = now;
            
            float target = hovered ? 1f : 0f;
            float speed = 8f;
            
            if (hoverProgress < target) {
                hoverProgress = Math.min(target, hoverProgress + deltaSeconds * speed);
            } else if (hoverProgress > target) {
                hoverProgress = Math.max(target, hoverProgress - deltaSeconds * speed);
            }
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                // Star hit zone: far right of the entry row
                int fullRight = SkinListWidget.this.listX + SkinListWidget.this.listWidth - 2;
                int starZoneLeft = fullRight - STAR_CLICK_WIDTH - 6;

                BBTSkin.LOGGER.info("[FAV-DEBUG] mouseClicked skinId={} mouseX={} starZone=[{},{}]",
                        skin.getId(), (int) mouseX, starZoneLeft, fullRight);

                if (mouseX >= starZoneLeft && mouseX <= fullRight) {
                    BBTSkinClient client = BBTSkinClient.getInstance();
                    if (client != null) {
                        boolean result = client.getSkinManager().toggleFavorite(skin.getId());
                        BBTSkin.LOGGER.info("[FAV-DEBUG] toggleFavorite skinId={} result={} nowFav={}",
                                skin.getId(), result,
                                client.getSkinManager().isFavorite(skin.getId()));
                    }
                    return true;
                }
                SkinListWidget.this.setSelected(this);
                return true;
            }
            return false;
        }
        
        public SkinData getSkin() {
            return skin;
        }
    }
}
