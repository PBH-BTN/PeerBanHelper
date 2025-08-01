package com.ghostchu.peerbanhelper.gui.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for GUI components that provides a more extensible alternative to the bridge pattern.
 * Components can register themselves and access required services through the service provider.
 */
@Slf4j
@Component
public class GuiComponentRegistry implements GuiServiceProvider {
    
    private final Map<String, GuiComponent> registeredComponents = new ConcurrentHashMap<>();
    private final ApplicationContext applicationContext;
    
    public GuiComponentRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    
    /**
     * Register a GUI component
     * @param component The component to register
     */
    public void registerComponent(GuiComponent component) {
        String componentId = component.getComponentId();
        if (registeredComponents.containsKey(componentId)) {
            log.warn("Component with ID '{}' is already registered, replacing", componentId);
        }
        
        registeredComponents.put(componentId, component);
        log.info("Registered GUI component: {} ({})", component.getDisplayName(), componentId);
        
        // Initialize the component with services
        try {
            component.initialize(this);
        } catch (Exception e) {
            log.error("Failed to initialize GUI component: {}", componentId, e);
        }
    }
    
    /**
     * Unregister a GUI component
     * @param componentId The ID of the component to unregister
     */
    public void unregisterComponent(String componentId) {
        GuiComponent component = registeredComponents.remove(componentId);
        if (component != null) {
            try {
                component.dispose();
            } catch (Exception e) {
                log.error("Error disposing component: {}", componentId, e);
            }
            log.info("Unregistered GUI component: {}", componentId);
        }
    }
    
    /**
     * Get a registered component by ID
     * @param componentId The component ID
     * @return The component, or null if not found
     */
    public GuiComponent getComponent(String componentId) {
        return registeredComponents.get(componentId);
    }
    
    /**
     * Get all registered components, sorted by display order
     * @return List of components sorted by display order
     */
    public List<GuiComponent> getAllComponents() {
        return registeredComponents.values().stream()
                .sorted(Comparator.comparingInt(GuiComponent::getDisplayOrder))
                .toList();
    }
    
    /**
     * Get enabled components, sorted by display order
     * @return List of enabled components sorted by display order
     */
    public List<GuiComponent> getEnabledComponents() {
        return registeredComponents.values().stream()
                .filter(GuiComponent::isEnabledByDefault)
                .sorted(Comparator.comparingInt(GuiComponent::getDisplayOrder))
                .toList();
    }
    
    /**
     * Update all registered components
     */
    public void updateAllComponents() {
        for (GuiComponent component : registeredComponents.values()) {
            try {
                component.updateData();
            } catch (Exception e) {
                log.error("Error updating component: {}", component.getComponentId(), e);
            }
        }
    }
    
    @Override
    public <T> T getService(Class<T> serviceClass) {
        try {
            return applicationContext.getBean(serviceClass);
        } catch (Exception e) {
            log.debug("Service {} not available: {}", serviceClass.getSimpleName(), e.getMessage());
            return null;
        }
    }
    
    /**
     * Dispose all components and clear registry
     */
    public void dispose() {
        for (GuiComponent component : new ArrayList<>(registeredComponents.values())) {
            unregisterComponent(component.getComponentId());
        }
    }
}