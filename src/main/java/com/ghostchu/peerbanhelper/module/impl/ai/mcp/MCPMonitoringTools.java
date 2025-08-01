package com.ghostchu.peerbanhelper.module.impl.ai.mcp;

import com.ghostchu.peerbanhelper.module.FeatureModule;
import com.ghostchu.peerbanhelper.module.ModuleManagerImpl;
import com.ghostchu.peerbanhelper.module.impl.background.BackgroundModule;
import com.ghostchu.peerbanhelper.module.impl.monitor.ActiveMonitoringModule;
import com.ghostchu.peerbanhelper.module.impl.monitor.SwarmTrackingModule;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP tool provider for monitoring, background tasks, and system metrics
 */
@Slf4j
@Component
public class MCPMonitoringTools implements MCPToolProvider {
    
    @Autowired
    private ModuleManagerImpl moduleManager;

    @Override
    public void registerTools(MCPToolsRegistry registry) {
        log.debug("Registering monitoring and background task tools for MCP");
        
        McpSchema.JsonSchema noInput = new McpSchema.JsonSchema(
                "object",
                Map.of(),
                List.of(),
                null,
                Map.of(),
                Map.of()
        );

        // Monitoring and Background Task Tools
        registry.registerTool("get_background_tasks", "Get information about background tasks and modules", noInput, this::handleGetBackgroundTasks);
        registry.registerTool("get_monitoring_status", "Get status of monitoring modules", noInput, this::handleGetMonitoringStatus);
        registry.registerTool("get_detailed_system_metrics", "Get detailed JVM and system performance metrics", noInput, this::handleGetDetailedSystemMetrics);
        registry.registerTool("get_swarm_tracking_info", "Get swarm tracking and peer monitoring information", noInput, this::handleGetSwarmTrackingInfo);
        registry.registerTool("get_active_monitoring_info", "Get active monitoring module information", noInput, this::handleGetActiveMonitoringInfo);
    }
    
    @Override
    public String getProviderName() {
        return "MonitoringTools";
    }

    private Map<String, Object> handleGetBackgroundTasks(Map<String, Object> arguments) {
        try {
            StringBuilder backgroundInfo = new StringBuilder();
            backgroundInfo.append("Background Tasks and Modules:\n\n");
            
            // Find background module
            Optional<FeatureModule> backgroundModuleOpt = moduleManager.getModules().stream()
                    .filter(m -> m instanceof BackgroundModule)
                    .findFirst();
            
            if (backgroundModuleOpt.isPresent()) {
                BackgroundModule backgroundModule = (BackgroundModule) backgroundModuleOpt.get();
                backgroundInfo.append("Background Module:\n");
                backgroundInfo.append("- Status: ").append(backgroundModule.isEnabled() ? "✓ Running" : "✗ Stopped").append("\n");
                backgroundInfo.append("- Name: ").append(backgroundModule.getName()).append("\n");
                backgroundInfo.append("- Config: ").append(backgroundModule.getConfigName()).append("\n");
                backgroundInfo.append("- Description: Manages background tasks and periodic operations\n\n");
            } else {
                backgroundInfo.append("Background Module: Not found or not loaded\n\n");
            }
            
            // List all modules that might have background tasks
            backgroundInfo.append("Modules with Potential Background Tasks:\n");
            for (FeatureModule module : moduleManager.getModules()) {
                String className = module.getClass().getSimpleName();
                if (className.contains("Background") || className.contains("Monitor") || 
                    className.contains("Task") || className.contains("Scheduler")) {
                    backgroundInfo.append("• ").append(module.getName())
                                 .append(" (").append(className).append(") - ")
                                 .append(module.isEnabled() ? "Active" : "Inactive").append("\n");
                }
            }
            
            // JVM Thread information
            backgroundInfo.append("\nJVM Thread Information:\n");
            backgroundInfo.append("- Active Threads: ").append(Thread.activeCount()).append("\n");
            backgroundInfo.append("- Thread Groups: ").append(Thread.currentThread().getThreadGroup().activeGroupCount()).append("\n");
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", backgroundInfo.toString()
            )));
        } catch (Exception e) {
            return createErrorResponse("Error getting background tasks: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetMonitoringStatus(Map<String, Object> arguments) {
        try {
            StringBuilder monitoringStatus = new StringBuilder();
            monitoringStatus.append("Monitoring Modules Status:\n\n");
            
            // Active Monitoring Module
            Optional<FeatureModule> activeMonitoringOpt = moduleManager.getModules().stream()
                    .filter(m -> m instanceof ActiveMonitoringModule)
                    .findFirst();
            
            if (activeMonitoringOpt.isPresent()) {
                ActiveMonitoringModule activeMonitoring = (ActiveMonitoringModule) activeMonitoringOpt.get();
                monitoringStatus.append("Active Monitoring Module:\n");
                monitoringStatus.append("- Status: ").append(activeMonitoring.isEnabled() ? "✓ Active" : "✗ Inactive").append("\n");
                monitoringStatus.append("- Name: ").append(activeMonitoring.getName()).append("\n");
                monitoringStatus.append("- Config: ").append(activeMonitoring.getConfigName()).append("\n");
                monitoringStatus.append("- Description: Real-time peer activity monitoring\n\n");
            }
            
            // Swarm Tracking Module
            Optional<FeatureModule> swarmTrackingOpt = moduleManager.getModules().stream()
                    .filter(m -> m instanceof SwarmTrackingModule)
                    .findFirst();
            
            if (swarmTrackingOpt.isPresent()) {
                SwarmTrackingModule swarmTracking = (SwarmTrackingModule) swarmTrackingOpt.get();
                monitoringStatus.append("Swarm Tracking Module:\n");
                monitoringStatus.append("- Status: ").append(swarmTracking.isEnabled() ? "✓ Active" : "✗ Inactive").append("\n");
                monitoringStatus.append("- Name: ").append(swarmTracking.getName()).append("\n");
                monitoringStatus.append("- Config: ").append(swarmTracking.getConfigName()).append("\n");
                monitoringStatus.append("- Description: Tracks torrent swarms and peer behavior\n\n");
            }
            
            // Count all monitoring-related modules
            long monitoringModules = moduleManager.getModules().stream()
                    .filter(m -> m.getName().toLowerCase().contains("monitor") || 
                               m.getClass().getSimpleName().toLowerCase().contains("monitor"))
                    .count();
            
            monitoringStatus.append("Summary:\n");
            monitoringStatus.append("- Total Monitoring Modules: ").append(monitoringModules).append("\n");
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", monitoringStatus.toString()
            )));
        } catch (Exception e) {
            return createErrorResponse("Error getting monitoring status: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetDetailedSystemMetrics(Map<String, Object> arguments) {
        try {
            StringBuilder metrics = new StringBuilder();
            metrics.append("Detailed System Metrics:\n\n");
            
            // Memory Information
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            Runtime runtime = Runtime.getRuntime();
            
            metrics.append("Memory Information:\n");
            metrics.append("- Heap Memory Used: ").append(memoryBean.getHeapMemoryUsage().getUsed() / 1024 / 1024).append(" MB\n");
            metrics.append("- Heap Memory Max: ").append(memoryBean.getHeapMemoryUsage().getMax() / 1024 / 1024).append(" MB\n");
            metrics.append("- Non-Heap Memory Used: ").append(memoryBean.getNonHeapMemoryUsage().getUsed() / 1024 / 1024).append(" MB\n");
            metrics.append("- Non-Heap Memory Max: ").append(memoryBean.getNonHeapMemoryUsage().getMax() / 1024 / 1024).append(" MB\n");
            metrics.append("- Runtime Max Memory: ").append(runtime.maxMemory() / 1024 / 1024).append(" MB\n");
            metrics.append("- Runtime Total Memory: ").append(runtime.totalMemory() / 1024 / 1024).append(" MB\n");
            metrics.append("- Runtime Free Memory: ").append(runtime.freeMemory() / 1024 / 1024).append(" MB\n\n");
            
            // Thread Information
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            metrics.append("Thread Information:\n");
            metrics.append("- Current Thread Count: ").append(threadBean.getThreadCount()).append("\n");
            metrics.append("- Peak Thread Count: ").append(threadBean.getPeakThreadCount()).append("\n");
            metrics.append("- Total Started Threads: ").append(threadBean.getTotalStartedThreadCount()).append("\n");
            metrics.append("- Daemon Thread Count: ").append(threadBean.getDaemonThreadCount()).append("\n\n");
            
            // Garbage Collection Information
            metrics.append("Garbage Collection:\n");
            for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
                metrics.append("- ").append(gcBean.getName()).append(":\n");
                metrics.append("  Collection Count: ").append(gcBean.getCollectionCount()).append("\n");
                metrics.append("  Collection Time: ").append(gcBean.getCollectionTime()).append(" ms\n");
            }
            
            // System Information
            metrics.append("\nSystem Information:\n");
            metrics.append("- Available Processors: ").append(runtime.availableProcessors()).append("\n");
            metrics.append("- System Load Average: ").append(ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage()).append("\n");
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", metrics.toString()
            )));
        } catch (Exception e) {
            return createErrorResponse("Error getting detailed system metrics: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetSwarmTrackingInfo(Map<String, Object> arguments) {
        try {
            Optional<FeatureModule> swarmTrackingOpt = moduleManager.getModules().stream()
                    .filter(m -> m instanceof SwarmTrackingModule)
                    .findFirst();
            
            if (swarmTrackingOpt.isEmpty()) {
                return createSuccessResponse(List.of(Map.of(
                        "type", "text",
                        "text", "Swarm Tracking Module: Not available or not loaded"
                )));
            }
            
            SwarmTrackingModule swarmTracking = (SwarmTrackingModule) swarmTrackingOpt.get();
            
            StringBuilder info = new StringBuilder();
            info.append("Swarm Tracking Module Information:\n\n");
            info.append("Status: ").append(swarmTracking.isEnabled() ? "✓ Active" : "✗ Inactive").append("\n");
            info.append("Name: ").append(swarmTracking.getName()).append("\n");
            info.append("Config: ").append(swarmTracking.getConfigName()).append("\n");
            info.append("Configurable: ").append(swarmTracking.isConfigurable() ? "Yes" : "No").append("\n\n");
            
            info.append("Features:\n");
            info.append("- Tracks torrent swarm activity\n");
            info.append("- Monitors peer behavior patterns\n");
            info.append("- Provides swarm statistics\n");
            info.append("- Integrates with other monitoring modules\n\n");
            
            info.append("Note: Detailed swarm data requires access to the SwarmTracking WebAPI endpoints.");
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", info.toString()
            )));
        } catch (Exception e) {
            return createErrorResponse("Error getting swarm tracking info: " + e.getMessage());
        }
    }

    private Map<String, Object> handleGetActiveMonitoringInfo(Map<String, Object> arguments) {
        try {
            Optional<FeatureModule> activeMonitoringOpt = moduleManager.getModules().stream()
                    .filter(m -> m instanceof ActiveMonitoringModule)
                    .findFirst();
            
            if (activeMonitoringOpt.isEmpty()) {
                return createSuccessResponse(List.of(Map.of(
                        "type", "text",
                        "text", "Active Monitoring Module: Not available or not loaded"
                )));
            }
            
            ActiveMonitoringModule activeMonitoring = (ActiveMonitoringModule) activeMonitoringOpt.get();
            
            StringBuilder info = new StringBuilder();
            info.append("Active Monitoring Module Information:\n\n");
            info.append("Status: ").append(activeMonitoring.isEnabled() ? "✓ Active" : "✗ Inactive").append("\n");
            info.append("Name: ").append(activeMonitoring.getName()).append("\n");
            info.append("Config: ").append(activeMonitoring.getConfigName()).append("\n");
            info.append("Configurable: ").append(activeMonitoring.isConfigurable() ? "Yes" : "No").append("\n\n");
            
            info.append("Features:\n");
            info.append("- Real-time peer monitoring\n");
            info.append("- Active connection tracking\n");
            info.append("- Performance metrics collection\n");
            info.append("- Alert generation for suspicious activity\n\n");
            
            info.append("Note: Detailed monitoring data requires access to the ActiveMonitoring WebAPI endpoints.");
            
            return createSuccessResponse(List.of(Map.of(
                    "type", "text",
                    "text", info.toString()
            )));
        } catch (Exception e) {
            return createErrorResponse("Error getting active monitoring info: " + e.getMessage());
        }
    }
}