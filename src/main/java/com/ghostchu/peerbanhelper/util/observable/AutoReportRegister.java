package com.ghostchu.peerbanhelper.util.observable;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class AutoReportRegister {
    public AutoReportRegister(@Autowired List<ReportGenerator> autoDiscovery, @Autowired ReportManager reportManager) {
        log.debug("Preparing AutoReportRegister");
        autoDiscovery.forEach(report -> {
            log.debug("Registering report generator: {}", report.getClass().getName());
            reportManager.register(report.getClass().getSimpleName(), report);
        });
    }
}
