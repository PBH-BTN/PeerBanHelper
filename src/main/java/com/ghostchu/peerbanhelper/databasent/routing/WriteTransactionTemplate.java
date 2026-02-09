package com.ghostchu.peerbanhelper.databasent.routing;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 写入事务模板，自动路由到 WRITE 数据源
 * 
 * <p>用于编程式写入事务，确保所有写操作使用 WRITE 数据源（SQLite 单连接池）</p>
 * 
 * <p>使用示例：</p>
 * <pre>{@code
 * @Autowired
 * private WriteTransactionTemplate writeTransactionTemplate;
 * 
 * Integer count = writeTransactionTemplate.execute(status -> {
 *     baseMapper.delete(null);
 *     return baseMapper.insert(entityList).size();
 * });
 * }</pre>
 * 
 * <p>这是默认的事务模板行为，与原有的 TransactionTemplate 等效，但显式标记为写入</p>
 */
@Slf4j
public class WriteTransactionTemplate extends TransactionTemplate {
    
    /**
     * 构造函数
     * 
     * @param transactionManager 事务管理器
     */
    public WriteTransactionTemplate(@NotNull PlatformTransactionManager transactionManager) {
        super(transactionManager);
        // 设置为写入事务（readOnly = false）
        setReadOnly(false);
        // 设置传播行为为 REQUIRED（默认）
        setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }
    
    /**
     * 在 WRITE 数据源上下文中执行事务
     */
    @Override
    public <T> T execute(@NotNull TransactionCallback<T> action) {
        // 写事务始终使用 WRITE 数据源
        DataSourceType effectiveType = DataSourceType.WRITE;
        
        // 如果已经在 WRITE 数据源上下文中，直接执行
        if (DataSourceContext.isBound() && DataSourceContext.getDataSourceType() == DataSourceType.WRITE) {
            return super.execute(action);
        }
        
        if (log.isDebugEnabled()) {
            log.debug("WriteTransactionTemplate executing with WRITE datasource");
        }
        
        // 使用 ScopedValue 设置数据源上下文并执行事务
        try {
            return ScopedValue.where(DataSourceContext.getScopedValue(), effectiveType)
                              .call(() -> WriteTransactionTemplate.super.execute(action));
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Transaction execution failed", e);
        }
    }
}
