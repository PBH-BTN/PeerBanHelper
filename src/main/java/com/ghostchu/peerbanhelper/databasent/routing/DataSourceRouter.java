package com.ghostchu.peerbanhelper.databasent.routing;

import java.util.concurrent.Callable;

import lombok.extern.slf4j.Slf4j;

/**
 * 手动数据源路由 API，提供编程式的数据源切换能力
 * 
 * <p>适用场景：</p>
 * <ul>
 *   <li>注解无法生效的地方（如动态代理之外的代码）</li>
 *   <li>需要在运行时动态决定使用哪个数据源</li>
 *   <li>复杂的嵌套场景需要显式控制</li>
 * </ul>
 * 
 * <p>使用示例：</p>
 * <pre>{@code
 * // 执行只读操作
 * List<Entity> result = DataSourceRouter.executeWithRead(() -> 
 *     baseMapper.selectList(null)
 * );
 * 
 * // 执行写入操作
 * DataSourceRouter.executeWithWrite(() -> 
 *     baseMapper.insert(entity)
 * );
 * 
 * // 检查当前数据源
 * DataSourceType current = DataSourceRouter.getCurrentDataSourceType();
 * }</pre>
 */
@Slf4j
public class DataSourceRouter {
    
    /**
     * 使用 READ 数据源执行操作（无返回值）
     * 
     * @param runnable 要执行的操作
     */
    public static void executeWithRead(Runnable runnable) {
        DataSourceType effectiveType = DataSourceContext.getEffectiveType(DataSourceType.READ);
        
        if (log.isDebugEnabled() && effectiveType != DataSourceType.READ) {
            log.debug("Requested READ but using {} due to nesting rules", effectiveType);
        }
        
        ScopedValue.where(DataSourceContext.getScopedValue(), effectiveType)
                   .run(runnable);
    }
    
    /**
     * 使用 READ 数据源执行操作（有返回值）
     * 
     * @param callable 要执行的操作
     * @param <T> 返回值类型
     * @return 操作结果
     * @throws Exception 操作过程中的异常
     */
    public static <T> T executeWithRead(Callable<T> callable) throws Exception {
        DataSourceType effectiveType = DataSourceContext.getEffectiveType(DataSourceType.READ);
        
        if (log.isDebugEnabled() && effectiveType != DataSourceType.READ) {
            log.debug("Requested READ but using {} due to nesting rules", effectiveType);
        }
        
        return ScopedValue.where(DataSourceContext.getScopedValue(), effectiveType)
                          .call(() -> {
                              try {
                                  return callable.call();
                              } catch (Exception e) {
                                  throw e;
                              }
                          });
    }
    
    /**
     * 使用 WRITE 数据源执行操作（无返回值）
     * 
     * @param runnable 要执行的操作
     */
    public static void executeWithWrite(Runnable runnable) {
        ScopedValue.where(DataSourceContext.getScopedValue(), DataSourceType.WRITE)
                   .run(runnable);
    }
    
    /**
     * 使用 WRITE 数据源执行操作（有返回值）
     * 
     * @param callable 要执行的操作
     * @param <T> 返回值类型
     * @return 操作结果
     * @throws Exception 操作过程中的异常
     */
    public static <T> T executeWithWrite(Callable<T> callable) throws Exception {
        return ScopedValue.where(DataSourceContext.getScopedValue(), DataSourceType.WRITE)
                          .call(() -> {
                              try {
                                  return callable.call();
                              } catch (Exception e) {
                                  throw e;
                              }
                          });
    }
    
    /**
     * 获取当前上下文的数据源类型
     * 
     * @return 当前数据源类型
     */
    public static DataSourceType getCurrentDataSourceType() {
        return DataSourceContext.getDataSourceType();
    }
    
    /**
     * 检查当前是否在 READ 数据源上下文中
     * 
     * @return 如果是 READ 返回 true
     */
    public static boolean isReadContext() {
        return DataSourceContext.getDataSourceType() == DataSourceType.READ;
    }
    
    /**
     * 检查当前是否在 WRITE 数据源上下文中
     * 
     * @return 如果是 WRITE 返回 true
     */
    public static boolean isWriteContext() {
        return DataSourceContext.getDataSourceType() == DataSourceType.WRITE;
    }
}
