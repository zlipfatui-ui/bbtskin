package com.bbt.skin.client.gui.input;

import com.bbt.skin.BBTSkin;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWDropCallback;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Handles file drag & drop functionality using GLFW
 */
public class DragDropHandler {
    
    private final long windowHandle;
    private Consumer<Path> dropCallback;
    private GLFWDropCallback glfwCallback;
    
    public DragDropHandler(long windowHandle) {
        this.windowHandle = windowHandle;
        setupCallback();
    }
    
    private void setupCallback() {
        // Store reference to prevent garbage collection
        glfwCallback = GLFWDropCallback.create((window, count, names) -> {
            if (dropCallback != null && count > 0) {
                try {
                    // Get the first dropped file
                    String filePath = GLFWDropCallback.getName(names, 0);
                    if (filePath != null) {
                        Path path = Path.of(filePath);
                        BBTSkin.LOGGER.info("File dropped: {}", path);
                        dropCallback.accept(path);
                    }
                } catch (Exception e) {
                    BBTSkin.LOGGER.error("Error handling dropped file", e);
                }
            }
        });
        
        GLFW.glfwSetDropCallback(windowHandle, glfwCallback);
    }
    
    /**
     * Set the callback to be called when a file is dropped
     */
    public void setDropCallback(Consumer<Path> callback) {
        this.dropCallback = callback;
    }
    
    /**
     * Clean up the callback when no longer needed
     */
    public void cleanup() {
        if (glfwCallback != null) {
            // Remove the callback
            GLFW.glfwSetDropCallback(windowHandle, null);
            glfwCallback.free();
            glfwCallback = null;
        }
        dropCallback = null;
    }
}
