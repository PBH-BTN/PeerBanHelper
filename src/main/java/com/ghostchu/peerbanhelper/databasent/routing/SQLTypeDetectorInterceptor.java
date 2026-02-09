package com.ghostchu.peerbanhelper.databasent.routing;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;

/**
 * MyBatis SQL 类型检测拦截器
 * 
 * <p>作为最后的兜底策略，当没有任何显式的数据源控制时，
 * 根据 SQL 命令类型自动选择数据源：</p>
 * <ul>
 *   <li>SELECT -> READ 数据源</li>
 *   <li>INSERT/UPDATE/DELETE -> WRITE 数据源</li>
 * </ul>
 * 
 * <p>优先级：最低 - 仅在手动 API、注解、@Transactional 都未设置时生效</p>
 * 
 * <p>拦截的方法：</p>
 * <ul>
 *   <li>Executor.update - 所有写操作（INSERT/UPDATE/DELETE）</li>
 *   <li>Executor.query - 所有查询操作（SELECT）</li>
 * </ul>
 */
@Component
@Intercepts({
    @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
    @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
@Slf4j
public class SQLTypeDetectorInterceptor implements Interceptor {
    
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 如果已经设置了数据源上下文，直接执行（优先级更高的策略已经生效）
        if (DataSourceContext.isBound()) {
            if (log.isTraceEnabled()) {
                log.trace("DataSource already bound to {}, skipping SQL type detection", 
                         DataSourceContext.getDataSourceType());
            }
            return invocation.proceed();
        }
        
        // 获取 MappedStatement 以确定 SQL 类型
        MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
        SqlCommandType sqlCommandType = mappedStatement.getSqlCommandType();
        
        // 根据 SQL 类型确定数据源
        DataSourceType dataSourceType = determinDataSourceType(sqlCommandType);
        
        if (log.isDebugEnabled()) {
            log.debug("Auto-detected SQL type {} for statement [{}], routing to {} datasource",
                     sqlCommandType, mappedStatement.getId(), dataSourceType);
        }
        
        // 使用 ScopedValue 设置数据源上下文并执行 SQL
        return ScopedValue.where(DataSourceContext.getScopedValue(), dataSourceType)
                          .call(() -> {
                              try {
                                  return invocation.proceed();
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
    }
    
    /**
     * 根据 SQL 命令类型确定数据源类型
     * 
     * @param sqlCommandType SQL 命令类型
     * @return 对应的数据源类型
     */
    private DataSourceType determinDataSourceType(SqlCommandType sqlCommandType) {
        return switch (sqlCommandType) {
            case SELECT -> DataSourceType.READ;
            case INSERT, UPDATE, DELETE -> DataSourceType.WRITE;
            // FLUSH 和 UNKNOWN 默认使用 WRITE 以保证安全性
            case FLUSH, UNKNOWN -> {
                if (log.isDebugEnabled()) {
                    log.debug("Encountered {} SQL command, defaulting to WRITE datasource for safety", sqlCommandType);
                }
                yield DataSourceType.WRITE;
            }
        };
    }
}
