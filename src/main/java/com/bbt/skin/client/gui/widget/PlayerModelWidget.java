package com.bbt.skin.client.gui.widget;

import com.bbt.skin.BBTSkin;
import com.bbt.skin.common.data.SkinData;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

/**
 * Widget for rendering a player model preview (Forge 1.20.1)
 * Uses direct model rendering to avoid EMF/ETF/Figura mod conflicts
 */
public class PlayerModelWidget extends AbstractWidget {
    
    private static final int COLOR_BACKGROUND = 0xFF0A0A15;
    private static final int COLOR_GRID = 0xFF1A1A2E;
    
    // Zoom settings
    private static final float MIN_ZOOM = 0.6f;
    private static final float MAX_ZOOM = 1.8f;
    private static final float DEFAULT_ZOOM = 1.0f;
    private static final float ZOOM_STEP = 0.1f;
    
    // View state
    private float rotationY = 20f;
    private float rotationX = -10f;
    private float targetRotationY = 20f;
    private float targetRotationX = -10f;
    private float zoom = DEFAULT_ZOOM;
    private float targetZoom = DEFAULT_ZOOM;
    
    // Interaction state
    private boolean isDragging = false;
    private double lastMouseX, lastMouseY;
    private boolean autoRotate = true;
    private float autoRotateAngle = 0f;
    
    // Preview texture
    @Nullable
    private ResourceLocation previewTexture;
    @Nullable
    private DynamicTexture dynamicTexture;
    private boolean isSlimModel = false;
    private long textureVersion = 0;
    
    // Player models for direct rendering
    @Nullable
    private PlayerModel<?> playerModel;
    @Nullable
    private PlayerModel<?> playerModelSlim;
    
    // Skin dimensions
    private int skinWidth = 64;
    private int skinHeight = 64;
    
    // Static preview texture for mixin access
    private static ResourceLocation currentPreviewTexture = null;
    private static boolean previewSlim = false;
    
    public PlayerModelWidget(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
        initModels();
    }
    
    private void initModels() {
        try {
            Minecraft mc = Minecraft.getInstance();
            playerModel = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
            playerModelSlim = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
            BBTSkin.LOGGER.debug("Player models initialized for preview");
        } catch (Exception e) {
            BBTSkin.LOGGER.warn("Failed to initialize player models", e);
        }
    }
    
    // ===== PUBLIC API =====
    
    public void setSkin(SkinData skinData) {
        if (skinData == null) {
            clearSkin();
            return;
        }
        
        this.isSlimModel = skinData.isSlim();
        this.skinWidth = skinData.getWidth();
        this.skinHeight = skinData.getHeight();
        previewSlim = skinData.isSlim();
        
        try {
            byte[] imageData = skinData.getImageData();
            if (imageData != null) {
                loadTextureFromBytes(imageData);
            }
        } catch (Exception e) {
            BBTSkin.LOGGER.error("Failed to load preview skin texture", e);
        }
    }
    
    public void setSkinFromImage(BufferedImage image, boolean slim) {
        this.isSlimModel = slim;
        this.skinWidth = image.getWidth();
        this.skinHeight = image.getHeight();
        previewSlim = slim;
        
        try {
            loadTextureFromImage(image);
        } catch (Exception e) {
            BBTSkin.LOGGER.error("Failed to load preview from image", e);
        }
    }
    
    public void setSlimModel(boolean slim) {
        this.isSlimModel = slim;
        previewSlim = slim;
    }
    
    public void clearSkin() {
        releaseTexture();
        currentPreviewTexture = null;
    }
    
    // ===== ZOOM CONTROLS =====
    
    public void zoomIn() {
        targetZoom = Math.min(MAX_ZOOM, targetZoom + ZOOM_STEP);
    }
    
    public void zoomOut() {
        targetZoom = Math.max(MIN_ZOOM, targetZoom - ZOOM_STEP);
    }
    
    public void resetView() {
        targetRotationY = 20f;
        targetRotationX = -10f;
        targetZoom = DEFAULT_ZOOM;
        autoRotate = true;
        autoRotateAngle = 0f;
    }
    
    public void setZoom(float zoom) {
        this.targetZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
    }
    
    public float getZoom() {
        return zoom;
    }
    
    // ===== TEXTURE MANAGEMENT =====
    
    private void releaseTexture() {
        if (previewTexture != null) {
            Minecraft.getInstance().getTextureManager().release(previewTexture);
            previewTexture = null;
        }
        if (dynamicTexture != null) {
            dynamicTexture.close();
            dynamicTexture = null;
        }
    }
    
    private void loadTextureFromBytes(byte[] imageData) throws Exception {
        releaseTexture();
        
        NativeImage image = NativeImage.read(new ByteArrayInputStream(imageData));
        dynamicTexture = new DynamicTexture(image);
        
        textureVersion++;
        String uniqueName = "bbtskin_preview_" + System.currentTimeMillis() + "_" + textureVersion;
        previewTexture = Minecraft.getInstance().getTextureManager()
                .register(uniqueName, dynamicTexture);
        
        currentPreviewTexture = previewTexture;
    }
    
    private void loadTextureFromImage(BufferedImage image) throws Exception {
        releaseTexture();
        
        NativeImage nativeImage = new NativeImage(image.getWidth(), image.getHeight(), true);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                nativeImage.setPixelRGBA(x, y, abgr);
            }
        }
        
        dynamicTexture = new DynamicTexture(nativeImage);
        
        textureVersion++;
        String uniqueName = "bbtskin_preview_" + System.currentTimeMillis() + "_" + textureVersion;
        previewTexture = Minecraft.getInstance().getTextureManager()
                .register(uniqueName, dynamicTexture);
        
        currentPreviewTexture = previewTexture;
    }
    
    // ===== STATIC ACCESSORS =====
    
    @Nullable
    public static ResourceLocation getCurrentPreviewTexture() {
        return currentPreviewTexture;
    }
    
    public static boolean isPreviewSlim() {
        return previewSlim;
    }
    
    public static boolean hasActivePreview() {
        return currentPreviewTexture != null;
    }
    
    // ===== RENDERING =====
    
    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        updateAnimations(delta);
        drawBackground(graphics);
        
        if (previewTexture == null) {
            drawModelPlaceholder(graphics);
            return;
        }
        
        try {
            render3DModel(graphics, delta);
        } catch (Exception e) {
            BBTSkin.LOGGER.warn("3D preview failed: {}", e.getMessage());
            drawModelPlaceholder(graphics);
        }
        
        drawZoomIndicator(graphics);
    }
    
    private void updateAnimations(float delta) {
        // Smooth interpolation
        float lerpSpeed = 0.15f;
        rotationY += (targetRotationY - rotationY) * lerpSpeed;
        rotationX += (targetRotationX - rotationX) * lerpSpeed;
        zoom += (targetZoom - zoom) * lerpSpeed;
        
        // Slow auto-rotate when not dragging
        if (autoRotate && !isDragging) {
            autoRotateAngle += delta * 0.3f;
            if (autoRotateAngle > 360f) autoRotateAngle -= 360f;
        }
    }
    
    private void drawBackground(GuiGraphics graphics) {
        int x = getX();
        int y = getY();
        int w = width;
        int h = height;
        
        graphics.fill(x, y, x + w, y + h, COLOR_BACKGROUND);
        
        int gridSize = 20;
        for (int gx = 0; gx < w; gx += gridSize) {
            graphics.fill(x + gx, y, x + gx + 1, y + h, COLOR_GRID);
        }
        for (int gy = 0; gy < h; gy += gridSize) {
            graphics.fill(x, y + gy, x + w, y + gy + 1, COLOR_GRID);
        }
        
        for (int i = 0; i < 30; i++) {
            int alpha = (int)(40 * (1 - i / 30f));
            graphics.fill(x, y + h - 30 + i, x + w, y + h - 29 + i, (alpha << 24) | 0x000000);
        }
    }
    
    private void drawZoomIndicator(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        int x = getX() + 5;
        int y = getY() + height - 15;
        String zoomText = String.format("%.0f%%", zoom * 100);
        graphics.drawString(mc.font, zoomText, x, y, 0x88FFFFFF, false);
    }
    
    /**
     * Render 3D player model using direct PlayerModel rendering.
     * This bypasses entity rendering hooks from EMF/ETF/Figura mods.
     * 
     * Key insight: Use POSITIVE Y scale (not negative) because EMF/ETF
     * already applies coordinate transforms. Negative Y would double-flip.
     */
    private void render3DModel(GuiGraphics graphics, float delta) {
        if (previewTexture == null) return;
        
        PlayerModel<?> model = isSlimModel ? playerModelSlim : playerModel;
        if (model == null) return;
        
        Minecraft mc = Minecraft.getInstance();
        
        // Scissor clipping
        double guiScale = mc.getWindow().getGuiScale();
        int scissorX = (int)(getX() * guiScale);
        int scissorY = (int)((mc.getWindow().getGuiScaledHeight() - getY() - height) * guiScale);
        int scissorW = (int)(width * guiScale);
        int scissorH = (int)(height * guiScale);
        RenderSystem.enableScissor(scissorX, scissorY, scissorW, scissorH);
        
        // Position - center of widget
        int centerX = getX() + width / 2;
        int centerY = getY() + (int)(height * 0.5f);
        
        // Scale - fills ~70% of preview height at 100% zoom
        float baseScale = height * 0.4f;
        float scale = baseScale * zoom;
        
        // Rotation - Y for horizontal spin, X for vertical tilt
        float yRot = autoRotate && !isDragging ? autoRotateAngle * 15f : rotationY;
        float xRot = rotationX;
        
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        
        // Transform: translate, scale (positive Y), rotate
        poseStack.translate(centerX, centerY, 50.0f);
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.XP.rotationDegrees(xRot));  // Vertical tilt
        poseStack.mulPose(Axis.YP.rotationDegrees(yRot));  // Horizontal spin
        
        // Setup lighting
        Lighting.setupForEntityInInventory();
        
        // Setup model state
        model.young = false;
        model.crouching = false;
        model.riding = false;
        model.leftArmPose = net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;
        model.rightArmPose = net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;
        
        // Reset part rotations
        model.head.xRot = 0; model.head.yRot = 0; model.head.zRot = 0;
        model.hat.xRot = 0; model.hat.yRot = 0; model.hat.zRot = 0;
        model.body.xRot = 0; model.body.yRot = 0; model.body.zRot = 0;
        model.rightArm.xRot = 0; model.rightArm.yRot = 0; model.rightArm.zRot = 0;
        model.leftArm.xRot = 0; model.leftArm.yRot = 0; model.leftArm.zRot = 0;
        model.rightLeg.xRot = 0; model.rightLeg.yRot = 0; model.rightLeg.zRot = 0;
        model.leftLeg.xRot = 0; model.leftLeg.yRot = 0; model.leftLeg.zRot = 0;
        
        // Ensure visibility
        model.head.visible = true;
        model.hat.visible = true;
        model.body.visible = true;
        model.leftArm.visible = true;
        model.rightArm.visible = true;
        model.leftLeg.visible = true;
        model.rightLeg.visible = true;
        
        // Render
        try {
            MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
            RenderType renderType = RenderType.entityTranslucent(previewTexture);
            var vertexConsumer = bufferSource.getBuffer(renderType);
            
            model.renderToBuffer(poseStack, vertexConsumer, 0xF000F0, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
            bufferSource.endBatch();
            
        } catch (Exception e) {
            poseStack.popPose();
            RenderSystem.disableScissor();
            Lighting.setupFor3DItems();
            throw e;
        }
        
        poseStack.popPose();
        RenderSystem.disableScissor();
        Lighting.setupFor3DItems();
    }
    
    private void drawModelPlaceholder(GuiGraphics graphics) {
        int x = getX();
        int y = getY();
        int w = width;
        int h = height;
        int centerX = x + w / 2;
        int centerY = y + h / 2;
        
        int silhouetteColor = 0xFF333344;
        
        int headSize = (int)(20 * zoom);
        int bodyHeight = (int)(35 * zoom);
        int armWidth = (int)(8 * zoom);
        int armHeight = (int)(28 * zoom);
        int legWidth = (int)(8 * zoom);
        int legHeight = (int)(30 * zoom);
        
        int figureTop = centerY - (headSize + bodyHeight + legHeight) / 2;
        
        // Head
        graphics.fill(centerX - headSize/2, figureTop, centerX + headSize/2, figureTop + headSize, silhouetteColor);
        // Body
        graphics.fill(centerX - 12, figureTop + headSize, centerX + 12, figureTop + headSize + bodyHeight, silhouetteColor);
        // Arms
        graphics.fill(centerX - 12 - armWidth, figureTop + headSize + 2, centerX - 12, figureTop + headSize + armHeight, silhouetteColor);
        graphics.fill(centerX + 12, figureTop + headSize + 2, centerX + 12 + armWidth, figureTop + headSize + armHeight, silhouetteColor);
        // Legs
        graphics.fill(centerX - legWidth - 2, figureTop + headSize + bodyHeight, centerX - 2, figureTop + headSize + bodyHeight + legHeight, silhouetteColor);
        graphics.fill(centerX + 2, figureTop + headSize + bodyHeight, centerX + legWidth + 2, figureTop + headSize + bodyHeight + legHeight, silhouetteColor);
        
        // Hint text
        Minecraft mc = Minecraft.getInstance();
        String hint = "Select a skin";
        int hintWidth = mc.font.width(hint);
        graphics.drawString(mc.font, hint, centerX - hintWidth / 2, y + h - 20, 0x66FFFFFF, false);
    }
    
    // ===== INPUT HANDLING =====
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isMouseOver(mouseX, mouseY)) {
            if (button == 0) {  // Left click - start drag
                isDragging = true;
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                autoRotate = false;
                return true;
            } else if (button == 2) {  // Middle click - reset view
                resetView();
                return true;
            }
        }
        return false;
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDragging = false;
            return true;
        }
        return false;
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDragging && button == 0) {
            float dx = (float)(mouseX - lastMouseX);
            float dy = (float)(mouseY - lastMouseY);
            
            targetRotationY += dx * 0.8f;
            targetRotationX = Math.max(-60f, Math.min(60f, targetRotationX + dy * 0.5f));
            
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return false;
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isMouseOver(mouseX, mouseY)) {
            if (delta > 0) {
                zoomIn();
            } else if (delta < 0) {
                zoomOut();
            }
            return true;
        }
        return false;
    }
    
    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
    
    public void close() {
        clearSkin();
    }
}
