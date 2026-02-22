package com.bbt.skin.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Custom styled checkbox for BBTSkin (Forge)
 */
public class BBTCheckbox extends AbstractWidget {
    
    private static final int BOX_SIZE = 16;
    private static final int COLOR_BOX_BG = 0xFF16213E;
    private static final int COLOR_BOX_BORDER = 0xFF0F3460;
    private static final int COLOR_BOX_BORDER_HOVER = 0xFFE94560;
    private static final int COLOR_CHECK = 0xFFE94560;
    private static final int COLOR_TEXT = 0xFFEEEEEE;
    
    private boolean checked;
    private Consumer<Boolean> callback;
    private float checkAnimation = 0f;
    private long lastRenderTime = 0;
    
    public BBTCheckbox(int x, int y, int width, int height, Component message, boolean checked) {
        super(x, y, width, height, message);
        this.checked = checked;
        this.checkAnimation = checked ? 1f : 0f;
    }
    
    public void setCallback(Consumer<Boolean> callback) {
        this.callback = callback;
    }
    
    public boolean isChecked() {
        return checked;
    }
    
    public void setChecked(boolean checked) {
        this.checked = checked;
    }
    
    @Override
    public void onClick(double mouseX, double mouseY) {
        this.checked = !this.checked;
        if (callback != null) {
            callback.accept(this.checked);
        }
    }
    
    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        
        updateAnimation();
        
        int boxX = this.getX();
        int boxY = this.getY() + (this.height - BOX_SIZE) / 2;
        
        drawCheckbox(graphics, boxX, boxY, this.isHovered());
        
        int textX = boxX + BOX_SIZE + 8;
        int textY = this.getY() + (this.height - 8) / 2;
        graphics.drawString(font, this.getMessage(), textX, textY, COLOR_TEXT, false);
    }
    
    private void drawCheckbox(GuiGraphics graphics, int x, int y, boolean hovered) {
        graphics.fill(x, y, x + BOX_SIZE, y + BOX_SIZE, COLOR_BOX_BG);
        
        int borderColor = hovered ? COLOR_BOX_BORDER_HOVER : COLOR_BOX_BORDER;
        drawBorder(graphics, x, y, BOX_SIZE, BOX_SIZE, borderColor);
        
        if (checkAnimation > 0) {
            drawCheckMark(graphics, x, y, checkAnimation);
        }
    }
    
    private void drawBorder(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }
    
    private void drawCheckMark(GuiGraphics graphics, int x, int y, float progress) {
        int checkColor = COLOR_CHECK;
        int padding = 3;
        int checkSize = BOX_SIZE - padding * 2;
        
        int animatedSize = (int)(checkSize * progress);
        int offset = (checkSize - animatedSize) / 2;
        
        int fillX = x + padding + offset;
        int fillY = y + padding + offset;
        graphics.fill(fillX, fillY, fillX + animatedSize, fillY + animatedSize, checkColor);
        
        if (progress > 0.8f) {
            int cx = x + BOX_SIZE / 2;
            int cy = y + BOX_SIZE / 2;
            int lineColor = 0xFFFFFFFF;
            
            for (int i = 0; i < 3; i++) {
                graphics.fill(cx - 4 + i, cy + i - 1, cx - 3 + i, cy + i + 1, lineColor);
            }
            
            for (int i = 0; i < 5; i++) {
                graphics.fill(cx - 1 + i, cy + 1 - i, cx + i, cy + 3 - i, lineColor);
            }
        }
    }
    
    private void updateAnimation() {
        long now = System.currentTimeMillis();
        if (lastRenderTime == 0) {
            lastRenderTime = now;
            return;
        }
        
        float deltaSeconds = (now - lastRenderTime) / 1000f;
        lastRenderTime = now;
        
        float target = checked ? 1f : 0f;
        float speed = 10f;
        
        if (checkAnimation < target) {
            checkAnimation = Math.min(target, checkAnimation + deltaSeconds * speed);
        } else if (checkAnimation > target) {
            checkAnimation = Math.max(target, checkAnimation - deltaSeconds * speed);
        }
    }
    
    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
