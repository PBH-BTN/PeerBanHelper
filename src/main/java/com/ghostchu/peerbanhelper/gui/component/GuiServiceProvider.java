package com.ghostchu.peerbanhelper.gui.component;

/**
 * Service provider interface for GUI components to access required services.
 * This replaces the monolithic bridge pattern with a more modular approach.
 */
public interface GuiServiceProvider {
    
    /**
     * Get a service by its class type
     * @param serviceClass The class of the service to retrieve
     * @param <T> Service type
     * @return The service instance, or null if not available
     */
    <T> T getService(Class<T> serviceClass);
    
    /**
     * Check if a service is available
     * @param serviceClass The class of the service to check
     * @return true if the service is available
     */
    default boolean hasService(Class<?> serviceClass) {
        return getService(serviceClass) != null;
    }
}