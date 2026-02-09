package com.ghostchu.peerbanhelper.databasent.routing;

import com.ghostchu.peerbanhelper.databasent.DatabaseDriver;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 动态路由数据源，根据上下文自动选择读或写数据源
 * 
 * <p>基于 Spring 的 AbstractRoutingDataSource 实现，
 * 通过 DataSourceContext 获取当前应该使用的数据源类型</p>
 * 
 * <p>路由策略：</p>
 * <ul>
 *   <li>默认使用 READ 数据源（高并发查询场景）</li>
 *   <li>写操作自动切换到 WRITE 数据源</li>
 *   <li>事务中默认使用 WRITE 数据源</li>
 *   <li>支持 @Transactional(readOnly=true) 路由到 READ</li>
 * </ul>
 */
@Slf4j
public class RoutingDataSource extends AbstractRoutingDataSource {
    
    private final DatabaseDriver databaseDriver;
    
    /**
     * 构造函数，初始化读写数据源映射
     * 
     * @param databaseDriver 数据库驱动，提供 READ 和 WRITE 数据源
     */
    public RoutingDataSource(@NotNull DatabaseDriver databaseDriver) {
        this.databaseDriver = databaseDriver;
        
        // 设置数据源映射
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceType.READ, databaseDriver.getReadDataSource());
        targetDataSources.put(DataSourceType.WRITE, databaseDriver.getWriteDataSource());
        
        setTargetDataSources(targetDataSources);
        
        // 设置默认数据源为 READ（高并发查询优化）
        setDefaultTargetDataSource(databaseDriver.getReadDataSource());
        
        // 初始化数据源映射
        afterPropertiesSet();
        
        log.info("RoutingDataSource initialized with READ (default) and WRITE datasources");
    }
    
    /**
     * 确定当前应该使用的数据源 key
     * 
     * @return 数据源类型（READ 或 WRITE）
     */
    @Override
    protected Object determineCurrentLookupKey() {
        DataSourceType type = DataSourceContext.getDataSourceType();
        
        if (log.isTraceEnabled()) {
            log.trace("Routing to {} datasource", type);
        }
        
        return type;
    }
    
    /**
     * 获取当前实际使用的数据源
     * （用于调试和监控）
     * 
     * @return 当前数据源实例
     */
    public DataSource getCurrentDataSource() {
        return determineTargetDataSource();
    }
    
    /**
     * 获取 READ 数据源
     * 
     * @return READ 数据源实例
     */
    public DataSource getReadDataSource() {
        return databaseDriver.getReadDataSource();
    }
    
    /**
     * 获取 WRITE 数据源
     * 
     * @return WRITE 数据源实例
     */
    public DataSource getWriteDataSource() {
        return databaseDriver.getWriteDataSource();
    }
}
