package com.ghostchu.peerbanhelper.databasent.routing;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 只读事务模板，自动路由到 READ 数据源
 * 
 * <p>用于编程式只读事务，充分利用 SQLite 的多读性能</p>
 * 
 * <p>使用示例：</p>
 * <pre>{@code
 * @Autowired
 * private ReadOnlyTransactionTemplate readOnlyTransactionTemplate;
 * 
 * List<Entity> result = readOnlyTransactionTemplate.execute(status -> {
 *     return baseMapper.selectList(null);
 * });
 * }</pre>
 * 
 * <p>注意：如果在 WRITE 上下文中嵌套调用，会自动保持 WRITE 以防止死锁</p>
 */
@Slf4j
public class ReadOnlyTransactionTemplate extends TransactionTemplate {
    
    /**
     * 构造函数
     * 
     * @param transactionManager 事务管理器
     */
    public ReadOnlyTransactionTemplate(@NotNull PlatformTransactionManager transactionManager) {
        super(transactionManager);
        // 设置为只读事务
        setReadOnly(true);
        // 设置传播行为为 REQUIRED（默认）
        setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }
    
    /**
     * 在 READ 数据源上下文中执行事务
     */
    @Override
    public <T> T execute(@NotNull TransactionCallback<T> action) {
        // 获取有效的数据源类型（考虑嵌套规则）
        DataSourceType effectiveType = DataSourceContext.getEffectiveType(DataSourceType.READ);
        
        if (log.isDebugEnabled() && effectiveType != DataSourceType.READ) {
            log.debug("ReadOnlyTransactionTemplate requested READ but using {} due to nesting rules", effectiveType);
        }
        
        // 如果已经在相同的数据源上下文中，直接执行
        if (DataSourceContext.isBound() && DataSourceContext.getDataSourceType() == effectiveType) {
            return super.execute(action);
        }
        
        // 使用 ScopedValue 设置数据源上下文并执行事务
        try {
            return ScopedValue.where(DataSourceContext.getScopedValue(), effectiveType)
                              .call(() -> ReadOnlyTransactionTemplate.super.execute(action));
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Transaction execution failed", e);
        }
    }
}
