package com.ghostchu.peerbanhelper.util;

import com.ghostchu.peerbanhelper.util.observable.ReportGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class BeanCreationTimeMonitor implements BeanPostProcessor, ReportGenerator {

    private final Map<String, Long> startTimes = new HashMap<>();

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        startTimes.put(beanName, System.currentTimeMillis());
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Long startTime = startTimes.remove(beanName);
        if (startTime != null) {
            long initializationTime = System.currentTimeMillis() - startTime;
            log.debug("Bean '{}' initialized in {}ms", beanName, initializationTime);
        }
        return bean;
    }

    @Override
    public Map<String, Object> createReportJsonObject() {
        return Map.of("startTimes", startTimes);
    }
}