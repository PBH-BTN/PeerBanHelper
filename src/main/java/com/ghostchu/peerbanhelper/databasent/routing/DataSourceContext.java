package com.ghostchu.peerbanhelper.databasent.routing;

import lombok.extern.slf4j.Slf4j;

/**
 * 数据源上下文管理器，使用 ScopedValue 实现虚拟线程安全的上下文传递
 * 
 * <p>优先级策略（从高到低）：</p>
 * <ol>
 *   <li>手动 API 调用（DataSourceRouter）- 最高优先级</li>
 *   <li>显式注解（@ReadDataSource / @WriteDataSource）</li>
 *   <li>事务注解（@Transactional）</li>
 *   <li>SQL 类型自动检测（MyBatis 拦截器）</li>
 *   <li>默认值 - READ 数据源</li>
 * </ol>
 * 
 * <p>嵌套规则：</p>
 * <ul>
 *   <li>WRITE 上下文中不允许降级到 READ（防止 SQLite 死锁）</li>
 *   <li>READ 上下文中可以提升到 WRITE</li>
 * </ul>
 */
@Slf4j
public class DataSourceContext {
    
    /**
     * 使用 ScopedValue 存储当前数据源类型，确保虚拟线程安全
     * ScopedValue 是 Java 21+ 的特性，专为虚拟线程设计
     */
    private static final ScopedValue<DataSourceType> DATASOURCE_TYPE = ScopedValue.newInstance();
    
    /**
     * 获取当前上下文的数据源类型
     * 
     * @return 当前数据源类型，如果未设置则返回默认值 READ
     */
    public static DataSourceType getDataSourceType() {
        return DATASOURCE_TYPE.orElse(DataSourceType.READ);
    }
    
    /**
     * 检查是否已经设置了数据源类型
     * 
     * @return 如果已设置返回 true，否则返回 false
     */
    public static boolean isBound() {
        return DATASOURCE_TYPE.isBound();
    }
    
    /**
     * 获取 ScopedValue 实例，供外部使用 ScopedValue.where() 设置上下文
     * 
     * @return ScopedValue 实例
     */
    public static ScopedValue<DataSourceType> getScopedValue() {
        return DATASOURCE_TYPE;
    }
    
    /**
     * 验证数据源切换是否合法
     * 防止从 WRITE 降级到 READ（可能导致 SQLite 死锁）
     * 
     * @param newType 新的数据源类型
     * @return 如果切换合法返回 true，否则返回 false
     */
    public static boolean isValidSwitch(DataSourceType newType) {
        if (!DATASOURCE_TYPE.isBound()) {
            // 未绑定时，任何切换都合法
            return true;
        }
        
        DataSourceType current = DATASOURCE_TYPE.get();
        
        // 从 WRITE 降级到 READ 是不允许的
        if (current == DataSourceType.WRITE && newType == DataSourceType.READ) {
            log.warn("Prevented illegal datasource switch from WRITE to READ in nested context");
            return false;
        }
        
        return true;
    }
    
    /**
     * 获取有效的数据源类型（考虑嵌套规则）
     * 如果尝试非法切换，则保持当前类型
     * 
     * @param requestedType 请求的数据源类型
     * @return 实际应该使用的数据源类型
     */
    public static DataSourceType getEffectiveType(DataSourceType requestedType) {
        if (!DATASOURCE_TYPE.isBound()) {
            return requestedType;
        }
        
        DataSourceType current = DATASOURCE_TYPE.get();
        
        // 如果当前是 WRITE 且请求 READ，保持 WRITE
        if (current == DataSourceType.WRITE && requestedType == DataSourceType.READ) {
            log.debug("Keeping WRITE datasource due to nesting rules (requested: READ)");
            return DataSourceType.WRITE;
        }
        
        return requestedType;
    }
}
