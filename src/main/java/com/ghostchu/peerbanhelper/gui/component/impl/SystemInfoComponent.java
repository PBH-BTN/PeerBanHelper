package com.ghostchu.peerbanhelper.gui.component.impl;

import com.ghostchu.peerbanhelper.gui.component.GuiComponent;
import com.ghostchu.peerbanhelper.gui.component.GuiServiceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.text.DecimalFormat;

/**
 * GUI component that displays system and runtime information
 */
@Slf4j
@Component
public class SystemInfoComponent implements GuiComponent {
    
    private JPanel panel;
    private JLabel javaVersionLabel;
    private JLabel javaVendorLabel;
    private JLabel osInfoLabel;
    private JLabel uptimeLabel;
    private JLabel usedMemoryLabel;
    private JLabel maxMemoryLabel;
    private JLabel freeMemoryLabel;
    private JLabel totalMemoryLabel;
    private JLabel memoryUsageLabel;
    private JProgressBar memoryProgressBar;
    
    private final DecimalFormat percentFormat = new DecimalFormat("#0.0%");
    private final Runtime runtime = Runtime.getRuntime();
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
    
    @Override
    public String getComponentId() {
        return "system-info";
    }
    
    @Override
    public String getDisplayName() {
        return "程序运行信息";
    }
    
    @Override
    public JPanel getPanel() {
        if (panel == null) {
            createPanel();
        }
        return panel;
    }
    
    @Override
    public void initialize(GuiServiceProvider serviceProvider) {
        // No external services needed for system info
        updateData();
    }
    
    @Override
    public void updateData() {
        SwingUtilities.invokeLater(() -> {
            try {
                // Update runtime information
                long uptimeMs = runtimeBean.getUptime();
                uptimeLabel.setText(formatUptime(uptimeMs));
                
                // Update memory information
                long maxMemory = runtime.maxMemory();
                long totalMemory = runtime.totalMemory();
                long freeMemory = runtime.freeMemory();
                long usedMemory = totalMemory - freeMemory;
                
                maxMemoryLabel.setText(formatBytes(maxMemory));
                totalMemoryLabel.setText(formatBytes(totalMemory));
                freeMemoryLabel.setText(formatBytes(freeMemory));
                usedMemoryLabel.setText(formatBytes(usedMemory));
                
                // Calculate and update memory usage percentage
                double memoryUsagePercent = (double) usedMemory / maxMemory;
                memoryUsageLabel.setText(percentFormat.format(memoryUsagePercent));
                memoryProgressBar.setValue((int) (memoryUsagePercent * 100));
                
                // Color the progress bar based on usage
                if (memoryUsagePercent > 0.9) {
                    memoryProgressBar.setForeground(Color.RED);
                } else if (memoryUsagePercent > 0.75) {
                    memoryProgressBar.setForeground(Color.ORANGE);
                } else {
                    memoryProgressBar.setForeground(Color.GREEN);
                }
                
            } catch (Exception e) {
                log.error("Error updating system info", e);
            }
        });
    }
    
    @Override
    public int getDisplayOrder() {
        return 20; // Display after basic statistics
    }
    
    private void createPanel() {
        panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("程序运行信息"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Java Version
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Java版本:"), gbc);
        gbc.gridx = 1;
        javaVersionLabel = new JLabel(System.getProperty("java.version"));
        panel.add(javaVersionLabel, gbc);
        
        // Java Vendor
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Java厂商:"), gbc);
        gbc.gridx = 1;
        javaVendorLabel = new JLabel(System.getProperty("java.vendor"));
        panel.add(javaVendorLabel, gbc);
        
        // OS Info
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("操作系统:"), gbc);
        gbc.gridx = 1;
        String osInfo = System.getProperty("os.name") + " " + 
                       System.getProperty("os.version") + " " + 
                       System.getProperty("os.arch");
        osInfoLabel = new JLabel(osInfo);
        panel.add(osInfoLabel, gbc);
        
        // Uptime
        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(new JLabel("运行时间:"), gbc);
        gbc.gridx = 1;
        uptimeLabel = new JLabel("0s");
        panel.add(uptimeLabel, gbc);
        
        // Add separator
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JSeparator(), gbc);
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        
        // Memory section title
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        JLabel memoryTitle = new JLabel("内存使用情况");
        memoryTitle.setFont(memoryTitle.getFont().deriveFont(Font.BOLD));
        panel.add(memoryTitle, gbc);
        gbc.gridwidth = 1;
        
        // Used Memory
        gbc.gridx = 0; gbc.gridy = 6;
        panel.add(new JLabel("已用内存:"), gbc);
        gbc.gridx = 1;
        usedMemoryLabel = new JLabel("0 B");
        panel.add(usedMemoryLabel, gbc);
        
        // Max Memory
        gbc.gridx = 0; gbc.gridy = 7;
        panel.add(new JLabel("最大内存:"), gbc);
        gbc.gridx = 1;
        maxMemoryLabel = new JLabel("0 B");
        panel.add(maxMemoryLabel, gbc);
        
        // Total Memory
        gbc.gridx = 0; gbc.gridy = 8;
        panel.add(new JLabel("已分配内存:"), gbc);
        gbc.gridx = 1;
        totalMemoryLabel = new JLabel("0 B");
        panel.add(totalMemoryLabel, gbc);
        
        // Free Memory
        gbc.gridx = 0; gbc.gridy = 9;
        panel.add(new JLabel("空闲内存:"), gbc);
        gbc.gridx = 1;
        freeMemoryLabel = new JLabel("0 B");
        panel.add(freeMemoryLabel, gbc);
        
        // Memory Usage Percentage
        gbc.gridx = 0; gbc.gridy = 10;
        panel.add(new JLabel("内存使用率:"), gbc);
        gbc.gridx = 1;
        memoryUsageLabel = new JLabel("0.0%");
        panel.add(memoryUsageLabel, gbc);
        
        // Memory Progress Bar
        gbc.gridx = 0; gbc.gridy = 11; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        memoryProgressBar = new JProgressBar(0, 100);
        memoryProgressBar.setStringPainted(true);
        memoryProgressBar.setString("内存使用率");
        panel.add(memoryProgressBar, gbc);
    }
    
    private String formatUptime(long uptimeMs) {
        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        seconds = seconds % 60;
        minutes = minutes % 60;
        hours = hours % 24;
        
        if (days > 0) {
            return String.format("%d天 %d小时 %d分钟", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%d小时 %d分钟", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%d分钟 %d秒", minutes, seconds);
        } else {
            return String.format("%d秒", seconds);
        }
    }
    
    private String formatBytes(long bytes) {
        if (bytes == 0) return "0 B";
        
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = (int) (Math.log(bytes) / Math.log(1024));
        unitIndex = Math.min(unitIndex, units.length - 1);
        
        double size = bytes / Math.pow(1024, unitIndex);
        DecimalFormat df = new DecimalFormat("#.##");
        return df.format(size) + " " + units[unitIndex];
    }
}