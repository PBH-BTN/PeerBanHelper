package com.ghostchu.peerbanhelper.databasent.routing;

import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 自定义数据源注解 AOP 切面
 * 
 * <p>拦截 @ReadDataSource 和 @WriteDataSource 注解，
 * 在方法执行前设置对应的数据源上下文</p>
 * 
 * <p>优先级：Order(1) - 高于事务切面，低于手动 API</p>
 */
@Aspect
@Component
@Order(1)
@Slf4j
public class DataSourceAspect {
    
    /**
     * 定义 @ReadDataSource 切入点
     */
    @Pointcut("@annotation(com.ghostchu.peerbanhelper.databasent.routing.ReadDataSource) || " +
              "@within(com.ghostchu.peerbanhelper.databasent.routing.ReadDataSource)")
    public void readDataSourcePointcut() {
    }
    
    /**
     * 定义 @WriteDataSource 切入点
     */
    @Pointcut("@annotation(com.ghostchu.peerbanhelper.databasent.routing.WriteDataSource) || " +
              "@within(com.ghostchu.peerbanhelper.databasent.routing.WriteDataSource)")
    public void writeDataSourcePointcut() {
    }
    
    /**
     * 环绕通知 - 处理 @ReadDataSource
     */
    @Around("readDataSourcePointcut()")
    public Object aroundReadDataSource(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithDataSource(joinPoint, DataSourceType.READ);
    }
    
    /**
     * 环绕通知 - 处理 @WriteDataSource
     */
    @Around("writeDataSourcePointcut()")
    public Object aroundWriteDataSource(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithDataSource(joinPoint, DataSourceType.WRITE);
    }
    
    /**
     * 使用指定的数据源类型执行方法
     * 
     * @param joinPoint 切入点
     * @param requestedType 请求的数据源类型
     * @return 方法执行结果
     * @throws Throwable 方法执行异常
     */
    private Object executeWithDataSource(ProceedingJoinPoint joinPoint, DataSourceType requestedType) throws Throwable {
        // 获取有效的数据源类型（考虑嵌套规则）
        DataSourceType effectiveType = DataSourceContext.getEffectiveType(requestedType);
        
        // 如果已经在相同的数据源上下文中，直接执行
        if (DataSourceContext.isBound() && DataSourceContext.getDataSourceType() == effectiveType) {
            if (log.isTraceEnabled()) {
                log.trace("Already in {} datasource context, proceeding directly", effectiveType);
            }
            return joinPoint.proceed();
        }
        
        // 记录数据源切换（仅在实际切换时）
        if (log.isDebugEnabled()) {
            String methodName = getMethodName(joinPoint);
            if (effectiveType != requestedType) {
                log.debug("Method [{}] requested {} but using {} due to nesting rules", 
                         methodName, requestedType, effectiveType);
            } else {
                log.debug("Method [{}] using {} datasource", methodName, effectiveType);
            }
        }
        
        // 使用 ScopedValue 设置数据源上下文并执行方法
        try {
            return ScopedValue.where(DataSourceContext.getScopedValue(), effectiveType)
                              .call(() -> {
                                  try {
                                      return joinPoint.proceed();
                                  } catch (Throwable e) {
                                      // 将受检异常包装为非受检异常
                                      if (e instanceof RuntimeException) {
                                          throw (RuntimeException) e;
                                      } else if (e instanceof Error) {
                                          throw (Error) e;
                                      } else {
                                          throw new RuntimeException(e);
                                      }
                                  }
                              });
        } catch (Exception e) {
            // 如果是包装的异常，解包
            if (e instanceof RuntimeException && e.getCause() != null) {
                throw e.getCause();
            }
            throw e;
        }
    }
    
    /**
     * 获取方法名（用于日志）
     */
    private String getMethodName(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }
}
