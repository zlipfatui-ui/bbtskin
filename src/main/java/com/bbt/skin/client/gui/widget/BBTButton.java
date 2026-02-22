package com.bbt.skin.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Custom styled button for BBTSkin (Forge)
 */
public class BBTButton extends Button {
    
    public enum Style {
        PRIMARY(0xFFE94560, 0xFFFF6B6B, 0xFFCB3A4F),
        SECONDARY(0xFF3D5A80, 0xFF5C7FA2, 0xFF2D4A6E),
        SUCCESS(0xFF22C55E, 0xFF4ADE80, 0xFF16A34A),
        DANGER(0xFFEF4444, 0xFFF87171, 0xFFDC2626),
        GHOST(0x00000000, 0x33FFFFFF, 0x22FFFFFF);
        
        public final int normal;
        public final int hover;
        public final int pressed;
        
        Style(int normal, int hover, int pressed) {
            this.normal = normal;
            this.hover = hover;
            this.pressed = pressed;
        }
    }
    
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_DISABLED = 0xFF888888;
    private static final int COLOR_DISABLED = 0xFF444444;
    
    private final Style style;
    private float hoverProgress = 0f;
    private long lastRenderTime = 0;
    
    public BBTButton(int x, int y, int width, int height, Component message, OnPress onPress, Style style) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.style = style;
    }
    
    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        
        updateHoverAnimation();
        
        int bgColor;
        int textColor;
        
        if (!this.active) {
            bgColor = COLOR_DISABLED;
            textColor = COLOR_TEXT_DISABLED;
        } else if (this.isHovered()) {
            bgColor = interpolateColor(style.normal, style.hover, hoverProgress);
            textColor = COLOR_TEXT;
        } else {
            bgColor = style.normal;
            textColor = COLOR_TEXT;
        }
        
        drawButtonBackground(graphics, bgColor);
        
        int textX = this.getX() + (this.width - font.width(this.getMessage())) / 2;
        int textY = this.getY() + (this.height - 8) / 2;
        graphics.drawString(font, this.getMessage(), textX, textY, textColor, true);
    }
    
    private void drawButtonBackground(GuiGraphics graphics, int color) {
        int x = this.getX();
        int y = this.getY();
        int w = this.width;
        int h = this.height;
        
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
        
        int cornerColor = 0xFF1A1A2E;
        graphics.fill(x, y, x + 1, y + 1, cornerColor);
        graphics.fill(x + w - 1, y, x + w, y + 1, cornerColor);
        graphics.fill(x, y + h - 1, x + 1, y + h, cornerColor);
        graphics.fill(x + w - 1, y + h - 1, x + w, y + h, cornerColor);
        
        if (this.active && hoverProgress > 0.5f) {
            int highlightAlpha = (int)(40 * hoverProgress);
            int highlight = (highlightAlpha << 24) | 0xFFFFFF;
            graphics.fill(x + 2, y + 1, x + w - 2, y + 2, highlight);
        }
        
        int borderColor = adjustBrightness(color, this.active ? 0.7f : 0.5f);
        drawBorder(graphics, x, y, w, h, borderColor);
        
        if (!this.isHovered() && this.active) {
            int shadowColor = 0x22000000;
            graphics.fill(x + 2, y + h, x + w - 2, y + h + 1, shadowColor);
        }
    }
    
    private void drawBorder(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x + 1, y, x + w - 1, y + 1, color);
        graphics.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        graphics.fill(x, y + 1, x + 1, y + h - 1, color);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }
    
    private void updateHoverAnimation() {
        long now = System.currentTimeMillis();
        if (lastRenderTime == 0) {
            lastRenderTime = now;
            return;
        }
        
        float deltaSeconds = (now - lastRenderTime) / 1000f;
        lastRenderTime = now;
        
        float targetHover = this.isHovered() && this.active ? 1f : 0f;
        float animSpeed = 8f;
        
        if (hoverProgress < targetHover) {
            hoverProgress = Math.min(targetHover, hoverProgress + deltaSeconds * animSpeed);
        } else if (hoverProgress > targetHover) {
            hoverProgress = Math.max(targetHover, hoverProgress - deltaSeconds * animSpeed);
        }
    }
    
    private int interpolateColor(int color1, int color2, float progress) {
        int a1 = (color1 >> 24) & 0xFF, a2 = (color2 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF, r2 = (color2 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF, g2 = (color2 >> 8) & 0xFF;
        int b1 = color1 & 0xFF, b2 = color2 & 0xFF;
        
        return ((int)(a1 + (a2 - a1) * progress) << 24) |
               ((int)(r1 + (r2 - r1) * progress) << 16) |
               ((int)(g1 + (g2 - g1) * progress) << 8) |
               (int)(b1 + (b2 - b1) * progress);
    }
    
    private int adjustBrightness(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (int)(((color >> 16) & 0xFF) * factor);
        int g = (int)(((color >> 8) & 0xFF) * factor);
        int b = (int)((color & 0xFF) * factor);
        
        return (a << 24) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }
    
    @Override
    public void onClick(double mouseX, double mouseY) {
        if (this.active) {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
            );
        }
        super.onClick(mouseX, mouseY);
    }
}
