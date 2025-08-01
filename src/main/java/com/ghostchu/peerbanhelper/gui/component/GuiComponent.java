package com.ghostchu.peerbanhelper.gui.component;

import javax.swing.*;

/**
 * Interface for GUI components that can be registered with the GUI system.
 * Each component provides a panel that can be embedded in the main UI.
 */
public interface GuiComponent {
    
    /**
     * Get the unique identifier for this component
     * @return Component ID
     */
    String getComponentId();
    
    /**
     * Get the display name for this component
     * @return Display name
     */
    String getDisplayName();
    
    /**
     * Get the Swing panel for this component
     * @return JPanel containing the component UI
     */
    JPanel getPanel();
    
    /**
     * Initialize the component with required services
     * @param serviceProvider Service provider for dependency injection
     */
    void initialize(GuiServiceProvider serviceProvider);
    
    /**
     * Update the component data (called periodically)
     */
    void updateData();
    
    /**
     * Clean up resources when component is destroyed
     */
    default void dispose() {
        // Default implementation does nothing
    }
    
    /**
     * Get the preferred position in the UI (lower numbers appear first)
     * @return Display order priority
     */
    default int getDisplayOrder() {
        return 100;
    }
    
    /**
     * Whether this component should be enabled by default
     * @return true if enabled by default
     */
    default boolean isEnabledByDefault() {
        return true;
    }
}