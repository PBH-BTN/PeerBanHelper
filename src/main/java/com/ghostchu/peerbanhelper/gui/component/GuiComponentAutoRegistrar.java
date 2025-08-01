package com.ghostchu.peerbanhelper.gui.component;

import com.ghostchu.peerbanhelper.gui.component.impl.BasicStatisticsComponent;
import com.ghostchu.peerbanhelper.gui.component.impl.SystemInfoComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * Automatically registers GUI components when the application context is ready
 */
@Slf4j
@Component
public class GuiComponentAutoRegistrar implements ApplicationListener<ContextRefreshedEvent> {
    
    private final GuiComponentRegistry registry;
    private final BasicStatisticsComponent basicStatisticsComponent;
    private final SystemInfoComponent systemInfoComponent;
    
    public GuiComponentAutoRegistrar(GuiComponentRegistry registry,
                                   BasicStatisticsComponent basicStatisticsComponent,
                                   SystemInfoComponent systemInfoComponent) {
        this.registry = registry;
        this.basicStatisticsComponent = basicStatisticsComponent;
        this.systemInfoComponent = systemInfoComponent;
    }
    
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        log.info("Auto-registering GUI components...");
        
        // Register components
        registry.registerComponent(basicStatisticsComponent);
        registry.registerComponent(systemInfoComponent);
        
        log.info("GUI component auto-registration completed");
    }
}