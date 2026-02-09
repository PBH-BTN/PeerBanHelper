package com.ghostchu.peerbanhelper.databasent.routing;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

/**
 * @Transactional 事务注解拦截切面
 * 
 * <p>根据 @Transactional 的 readOnly 属性自动路由到对应的数据源：</p>
 * <ul>
 *   <li>readOnly = true  -> READ 数据源</li>
 *   <li>readOnly = false 或未设置 -> WRITE 数据源（默认）</li>
 * </ul>
 * 
 * <p>优先级：Order(0) - 在 Spring 事务切面之前执行，确保事务开启前数据源已切换</p>
 */
@Aspect
@Component
@Order(0)
@Slf4j
public class TransactionalDataSourceAspect {
    
    /**
     * 定义 @Transactional 切入点
     */
    @Pointcut("@annotation(org.springframework.transaction.annotation.Transactional)")
    public void transactionalPointcut() {
    }
    
    /**
     * 环绕通知 - 在事务开启前设置数据源
     */
    @Around("transactionalPointcut()")
    public Object aroundTransactional(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        // 获取 @Transactional 注解
        Transactional transactional = method.getAnnotation(Transactional.class);
        if (transactional == null) {
            // 如果方法上没有，尝试从类上获取
            transactional = joinPoint.getTarget().getClass().getAnnotation(Transactional.class);
        }
        
        if (transactional == null) {
            // 理论上不会发生，因为切入点已经限制了
            return joinPoint.proceed();
        }
        
        // 根据 readOnly 属性决定数据源类型
        // readOnly=true 使用 READ，否则使用 WRITE（默认）
        DataSourceType requestedType = transactional.readOnly() ? DataSourceType.READ : DataSourceType.WRITE;
        
        // 获取有效的数据源类型（考虑嵌套规则）
        DataSourceType effectiveType = DataSourceContext.getEffectiveType(requestedType);
        
        // 如果已经在相同的数据源上下文中，直接执行
        if (DataSourceContext.isBound() && DataSourceContext.getDataSourceType() == effectiveType) {
            if (log.isTraceEnabled()) {
                log.trace("Already in {} datasource context for transaction, proceeding directly", effectiveType);
            }
            return joinPoint.proceed();
        }
        
        // 记录事务数据源选择
        if (log.isDebugEnabled()) {
            String methodName = method.getDeclaringClass().getSimpleName() + "." + method.getName();
            if (effectiveType != requestedType) {
                log.debug("Transaction [{}] requested {} (readOnly={}) but using {} due to nesting rules", 
                         methodName, requestedType, transactional.readOnly(), effectiveType);
            } else {
                log.debug("Transaction [{}] using {} datasource (readOnly={})", 
                         methodName, effectiveType, transactional.readOnly());
            }
        }
        
        // 使用 ScopedValue 设置数据源上下文并执行事务
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
}
