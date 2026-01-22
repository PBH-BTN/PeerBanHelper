package com.ghostchu.peerbanhelper.configuration;

import com.ghostchu.peerbanhelper.databasent.DatabaseDriver;
import org.jetbrains.annotations.NotNull;
import org.mybatis.spring.mapper.ClassPathMapperScanner;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;

@Configuration
public class DynamicMapperConfig implements BeanDefinitionRegistryPostProcessor {
    private final DatabaseDriver databaseDriver;

    public DynamicMapperConfig(@Autowired DatabaseDriver databaseDriver) {
        this.databaseDriver = databaseDriver;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(@NotNull BeanDefinitionRegistry registry) throws BeansException {
        ClassPathMapperScanner scanner = new ClassPathMapperScanner(registry, new StandardEnvironment());
        scanner.doScan(databaseDriver.getMapperPackagePath());
    }
}