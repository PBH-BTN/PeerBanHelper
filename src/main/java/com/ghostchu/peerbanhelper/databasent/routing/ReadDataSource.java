package com.ghostchu.peerbanhelper.databasent.routing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 显式标记方法或类使用 READ 数据源
 * 
 * <p>用于只读查询操作，充分利用 SQLite 的多读性能</p>
 * 
 * <p>使用示例：</p>
 * <pre>{@code
 * @ReadDataSource
 * public List<Entity> findAll() {
 *     return baseMapper.selectList(null);
 * }
 * }</pre>
 * 
 * <p>注意：在 WRITE 上下文中（如事务中），此注解会被忽略以防止死锁</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ReadDataSource {
}
