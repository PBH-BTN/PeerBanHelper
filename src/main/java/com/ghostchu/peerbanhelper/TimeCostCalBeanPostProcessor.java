package com.ghostchu.peerbanhelper;

import com.google.common.collect.Maps;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.stereotype.Service;

import java.beans.PropertyDescriptor;
import java.util.Map;

@Service
public class TimeCostCalBeanPostProcessor implements InstantiationAwareBeanPostProcessor {
    private Map<String, Long> costMap = Maps.newConcurrentMap();

    @Override
    public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
        if (!costMap.containsKey(beanName)) {
            costMap.put(beanName, System.currentTimeMillis());
        }
        return null;
    }
    @Override
    public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
        return true;
    }
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
         if (costMap.containsKey(beanName)) {
            Long start = costMap.get(beanName);
            long cost = System.currentTimeMillis() - start;
            System.out.println("bean: " + beanName + "\ttime: " + cost + "ms");
        }
         return bean;
    }
}