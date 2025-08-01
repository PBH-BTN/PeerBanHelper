package com.ghostchu.peerbanhelper.gui.component.impl;

import com.ghostchu.peerbanhelper.DownloaderServer;
import com.ghostchu.peerbanhelper.database.dao.impl.tmp.TrackedSwarmDao;
import com.ghostchu.peerbanhelper.gui.component.GuiComponent;
import com.ghostchu.peerbanhelper.gui.component.GuiServiceProvider;
import com.ghostchu.peerbanhelper.metric.BasicMetrics;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.sql.SQLException;
import java.text.DecimalFormat;

/**
 * GUI component that displays basic statistics similar to the WebUI dashboard
 */
@Slf4j
@Component
public class BasicStatisticsComponent implements GuiComponent {
    
    private JPanel panel;
    private JLabel checkCounterLabel;
    private JLabel banCounterLabel;
    private JLabel unbanCounterLabel;
    private JLabel banlistCounterLabel;
    private JLabel bannedIpCounterLabel;
    private JLabel wastedTrafficLabel;
    private JLabel trackedSwarmCountLabel;
    private JLabel blockRateLabel;
    
    private BasicMetrics metrics;
    private DownloaderServer downloaderServer;
    private TrackedSwarmDao trackedSwarmDao;
    
    private final DecimalFormat percentFormat = new DecimalFormat("#0.00%");
    private final DecimalFormat numberFormat = new DecimalFormat("#,###");
    
    @Override
    public String getComponentId() {
        return "basic-statistics";
    }
    
    @Override
    public String getDisplayName() {
        return "基本统计信息";
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
        this.metrics = serviceProvider.getService(BasicMetrics.class);
        this.downloaderServer = serviceProvider.getService(DownloaderServer.class);
        this.trackedSwarmDao = serviceProvider.getService(TrackedSwarmDao.class);
        
        // Initial data update
        updateData();
    }
    
    @Override
    public void updateData() {
        if (metrics == null || downloaderServer == null) {
            return;
        }
        
        SwingUtilities.invokeLater(() -> {
            try {
                // Update basic counters
                checkCounterLabel.setText(numberFormat.format(metrics.getCheckCounter()));
                banCounterLabel.setText(numberFormat.format(metrics.getPeerBanCounter()));
                unbanCounterLabel.setText(numberFormat.format(metrics.getPeerUnbanCounter()));
                
                // Update banlist info
                int banlistSize = downloaderServer.getBannedPeers().size();
                banlistCounterLabel.setText(numberFormat.format(banlistSize));
                
                long bannedIpCount = downloaderServer.getBannedPeers().keySet().stream()
                        .map(PeerAddress::getIp)
                        .distinct()
                        .count();
                bannedIpCounterLabel.setText(numberFormat.format(bannedIpCount));
                
                // Update wasted traffic
                wastedTrafficLabel.setText(formatBytes(metrics.getWastedTraffic()));
                
                // Update tracked swarm count and block rate
                if (trackedSwarmDao != null) {
                    try {
                        long trackedPeers = trackedSwarmDao.countOf();
                        trackedSwarmCountLabel.setText(numberFormat.format(trackedPeers));
                        
                        if (trackedPeers > 0) {
                            double blockRate = (double) metrics.getPeerBanCounter() / trackedPeers;
                            blockRateLabel.setText(percentFormat.format(blockRate));
                        } else {
                            blockRateLabel.setText("0.00%");
                        }
                    } catch (SQLException e) {
                        trackedSwarmCountLabel.setText("Error");
                        blockRateLabel.setText("Error");
                        log.error("Failed to query tracked swarm count", e);
                    }
                } else {
                    trackedSwarmCountLabel.setText("N/A");
                    blockRateLabel.setText("N/A");
                }
                
            } catch (Exception e) {
                log.error("Error updating basic statistics", e);
            }
        });
    }
    
    @Override
    public int getDisplayOrder() {
        return 10; // Display first
    }
    
    private void createPanel() {
        panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("当前状态"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Row 0: Check Counter
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("检查次数:"), gbc);
        gbc.gridx = 1;
        checkCounterLabel = new JLabel("0");
        panel.add(checkCounterLabel, gbc);
        
        // Row 1: Ban Counter
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("封禁次数:"), gbc);
        gbc.gridx = 1;
        banCounterLabel = new JLabel("0");
        panel.add(banCounterLabel, gbc);
        
        // Row 2: Unban Counter
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("解封次数:"), gbc);
        gbc.gridx = 1;
        unbanCounterLabel = new JLabel("0");
        panel.add(unbanCounterLabel, gbc);
        
        // Row 3: Banlist Counter
        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(new JLabel("封禁列表大小:"), gbc);
        gbc.gridx = 1;
        banlistCounterLabel = new JLabel("0");
        panel.add(banlistCounterLabel, gbc);
        
        // Row 4: Banned IP Counter
        gbc.gridx = 0; gbc.gridy = 4;
        panel.add(new JLabel("封禁IP数量:"), gbc);
        gbc.gridx = 1;
        bannedIpCounterLabel = new JLabel("0");
        panel.add(bannedIpCounterLabel, gbc);
        
        // Row 5: Wasted Traffic
        gbc.gridx = 0; gbc.gridy = 5;
        panel.add(new JLabel("节省流量:"), gbc);
        gbc.gridx = 1;
        wastedTrafficLabel = new JLabel("0 B");
        panel.add(wastedTrafficLabel, gbc);
        
        // Row 6: Tracked Swarm Count
        gbc.gridx = 0; gbc.gridy = 6;
        panel.add(new JLabel("跟踪Peer数量:"), gbc);
        gbc.gridx = 1;
        trackedSwarmCountLabel = new JLabel("0");
        panel.add(trackedSwarmCountLabel, gbc);
        
        // Row 7: Block Rate
        gbc.gridx = 0; gbc.gridy = 7;
        panel.add(new JLabel("Peer封禁率:"), gbc);
        gbc.gridx = 1;
        blockRateLabel = new JLabel("0.00%");
        panel.add(blockRateLabel, gbc);
    }
    
    private String formatBytes(long bytes) {
        if (bytes == 0) return "0 B";
        
        String[] units = {"B", "KB", "MB", "GB", "TB", "PB"};
        int unitIndex = (int) (Math.log(bytes) / Math.log(1024));
        unitIndex = Math.min(unitIndex, units.length - 1);
        
        double size = bytes / Math.pow(1024, unitIndex);
        DecimalFormat df = new DecimalFormat("#.##");
        return df.format(size) + " " + units[unitIndex];
    }
}