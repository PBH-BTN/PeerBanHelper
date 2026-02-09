package com.ghostchu.peerbanhelper.databasent.routing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 显式标记方法或类使用 WRITE 数据源
 * 
 * <p>用于写入操作（INSERT/UPDATE/DELETE）</p>
 * 
 * <p>使用示例：</p>
 * <pre>{@code
 * @WriteDataSource
 * public int insert(Entity entity) {
 *     return baseMapper.insert(entity);
 * }
 * }</pre>
 * 
 * <p>对于 SQLite，WRITE 操作使用单连接池确保写入串行化</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface WriteDataSource {
}
