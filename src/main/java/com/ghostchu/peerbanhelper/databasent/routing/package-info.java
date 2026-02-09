/**
 * 数据源读写分离路由包
 * 
 * <p>本包实现了数据库读写数据源的智能路由，充分利用 SQLite 的多读单写性能特性。</p>
 * 
 * <h2>核心特性</h2>
 * <ul>
 *   <li><b>虚拟线程兼容</b>：使用 Java 21 的 ScopedValue 而非 ThreadLocal，完美支持虚拟线程</li>
 *   <li><b>多层优先级策略</b>：手动 API > 显式注解 > @Transactional > SQL 自动检测 > 默认值</li>
 *   <li><b>嵌套安全</b>：防止 WRITE -> READ 降级，避免 SQLite 死锁</li>
 *   <li><b>透明路由</b>：自动根据上下文选择合适的数据源</li>
 * </ul>
 * 
 * <h2>SQLite 性能优化</h2>
 * <ul>
 *   <li><b>READ 数据源</b>：多连接池（CPU 核心数），FULLMUTEX 模式，支持并发读</li>
 *   <li><b>WRITE 数据源</b>：单连接池，NOMUTEX 模式，串行化写入</li>
 *   <li><b>WAL 模式</b>：Write-Ahead Logging，读写不互斥</li>
 * </ul>
 * 
 * <h2>使用方式</h2>
 * 
 * <h3>1. 注解方式（推荐）</h3>
 * 
 * <h4>1.1 使用 @ReadDataSource / @WriteDataSource</h4>
 * <pre>{@code
 * @Service
 * public class UserService extends ServiceImpl<UserMapper, UserEntity> {
 *     
 *     // 显式标记只读查询
 *     @ReadDataSource
 *     public List<UserEntity> findAllUsers() {
 *         return baseMapper.selectList(null);
 *     }
 *     
 *     // 显式标记写入操作
 *     @WriteDataSource
 *     public int createUser(UserEntity user) {
 *         return baseMapper.insert(user);
 *     }
 *     
 *     // 类级别注解 - 所有方法默认使用 READ
 *     @ReadDataSource
 *     public int countUsers() {
 *         return baseMapper.selectCount(null);
 *     }
 * }
 * }</pre>
 * 
 * <h4>1.2 使用 @Transactional</h4>
 * <pre>{@code
 * @Service
 * public class OrderService {
 *     
 *     // readOnly=true 自动路由到 READ 数据源
 *     @Transactional(readOnly = true)
 *     public List<OrderEntity> findOrders(Long userId) {
 *         return orderMapper.selectList(
 *             Wrappers.<OrderEntity>lambdaQuery()
 *                 .eq(OrderEntity::getUserId, userId)
 *         );
 *     }
 *     
 *     // readOnly=false（默认）自动路由到 WRITE 数据源
 *     @Transactional
 *     public void createOrder(OrderEntity order) {
 *         orderMapper.insert(order);
 *         orderItemMapper.insertBatch(order.getItems());
 *     }
 * }
 * }</pre>
 * 
 * <h3>2. 编程方式 - 自定义 TransactionTemplate</h3>
 * 
 * <pre>{@code
 * @Service
 * public class DataService {
 *     
 *     @Autowired
 *     private ReadOnlyTransactionTemplate readOnlyTransactionTemplate;
 *     
 *     @Autowired
 *     private WriteTransactionTemplate writeTransactionTemplate;
 *     
 *     public List<Entity> queryData() {
 *         // 使用只读事务模板，自动路由到 READ 数据源
 *         return readOnlyTransactionTemplate.execute(status -> {
 *             return baseMapper.selectList(null);
 *         });
 *     }
 *     
 *     public void updateData(List<Entity> entities) {
 *         // 使用写入事务模板，自动路由到 WRITE 数据源
 *         writeTransactionTemplate.execute(status -> {
 *             baseMapper.delete(null);
 *             return baseMapper.insert(entities).size();
 *         });
 *     }
 * }
 * }</pre>
 * 
 * <h3>3. 手动路由 API（最高优先级）</h3>
 * 
 * <pre>{@code
 * @Service
 * public class AdvancedService {
 *     
 *     public void complexOperation() {
 *         // 强制使用 READ 数据源
 *         List<Entity> data = DataSourceRouter.executeWithRead(() -> 
 *             baseMapper.selectList(null)
 *         );
 *         
 *         // 强制使用 WRITE 数据源
 *         DataSourceRouter.executeWithWrite(() -> {
 *             data.forEach(e -> e.setProcessed(true));
 *             baseMapper.updateBatch(data);
 *         });
 *         
 *         // 检查当前数据源
 *         if (DataSourceRouter.isReadContext()) {
 *             log.info("Currently using READ datasource");
 *         }
 *     }
 *     
 *     // 在注解无法生效的场景使用
 *     public void dynamicRouting(boolean readonly) {
 *         if (readonly) {
 *             DataSourceRouter.executeWithRead(() -> performQuery());
 *         } else {
 *             DataSourceRouter.executeWithWrite(() -> performUpdate());
 *         }
 *     }
 * }
 * }</pre>
 * 
 * <h3>4. 自动检测（兜底策略）</h3>
 * 
 * <p>当没有任何显式控制时，系统会自动根据 SQL 类型选择数据源：</p>
 * <pre>{@code
 * @Service
 * public class PlainService extends ServiceImpl<UserMapper, UserEntity> {
 *     
 *     // 自动检测为 SELECT -> 使用 READ 数据源
 *     public List<UserEntity> list() {
 *         return baseMapper.selectList(null);
 *     }
 *     
 *     // 自动检测为 INSERT -> 使用 WRITE 数据源
 *     public boolean save(UserEntity entity) {
 *         return baseMapper.insert(entity) > 0;
 *     }
 *     
 *     // 自动检测为 UPDATE -> 使用 WRITE 数据源
 *     public boolean update(UserEntity entity) {
 *         return baseMapper.updateById(entity) > 0;
 *     }
 * }
 * }</pre>
 * 
 * <h2>嵌套场景处理</h2>
 * 
 * <h3>场景 1：WRITE 中嵌套 READ（安全）</h3>
 * <pre>{@code
 * @WriteDataSource
 * public void outerWrite() {
 *     // 外层使用 WRITE
 *     baseMapper.insert(entity);
 *     
 *     // 内层请求 READ，但实际保持 WRITE（防止死锁）
 *     @ReadDataSource
 *     innerRead();  // 仍然使用 WRITE 数据源
 * }
 * }</pre>
 * 
 * <h3>场景 2：READ 中嵌套 WRITE（允许）</h3>
 * <pre>{@code
 * @ReadDataSource
 * public void outerRead() {
 *     // 外层使用 READ
 *     List<Entity> data = baseMapper.selectList(null);
 *     
 *     // 内层提升到 WRITE（允许）
 *     @WriteDataSource
 *     innerWrite(data);  // 切换到 WRITE 数据源
 * }
 * }</pre>
 * 
 * <h3>场景 3：@Transactional 嵌套</h3>
 * <pre>{@code
 * @Transactional  // 默认 WRITE
 * public void outerTransaction() {
 *     baseMapper.insert(entity);
 *     
 *     // 内层声明只读，但保持 WRITE（事务一致性）
 *     @Transactional(readOnly = true)
 *     innerReadOnlyTransaction();  // 仍然使用 WRITE
 * }
 * }</pre>
 * 
 * <h2>优先级示例</h2>
 * 
 * <pre>{@code
 * @ReadDataSource  // 优先级 2
 * @Transactional   // 优先级 3
 * public void method1() {
 *     // 实际使用 @ReadDataSource（优先级更高）
 * }
 * 
 * @WriteDataSource  // 优先级 2
 * public void method2() {
 *     // 手动 API 优先级最高
 *     DataSourceRouter.executeWithRead(() -> {
 *         // 实际使用 READ（手动 API 覆盖注解）
 *         baseMapper.selectList(null);
 *     });
 * }
 * 
 * public void method3() {
 *     // 没有任何控制，使用 SQL 自动检测
 *     baseMapper.selectList(null);  // 自动使用 READ
 *     baseMapper.insert(entity);     // 自动使用 WRITE
 * }
 * }</pre>
 * 
 * <h2>最佳实践</h2>
 * 
 * <ol>
 *   <li><b>查询方法</b>：使用 {@code @ReadDataSource} 或 {@code @Transactional(readOnly=true)}</li>
 *   <li><b>写入方法</b>：使用 {@code @WriteDataSource} 或 {@code @Transactional}</li>
 *   <li><b>复杂事务</b>：使用 {@code WriteTransactionTemplate}，避免在事务中混合读写</li>
 *   <li><b>只读事务</b>：使用 {@code ReadOnlyTransactionTemplate} 或 {@code @Transactional(readOnly=true)}</li>
 *   <li><b>动态场景</b>：使用 {@code DataSourceRouter} API</li>
 *   <li><b>简单查询</b>：信任自动检测，无需显式标记</li>
 * </ol>
 * 
 * <h2>性能优化建议</h2>
 * 
 * <ul>
 *   <li>大量查询操作使用 READ 数据源，充分利用多连接并发</li>
 *   <li>批量写入操作使用 WRITE 数据源，避免连接池竞争</li>
 *   <li>长时间查询不要开启事务，使用 {@code @ReadDataSource} 即可</li>
 *   <li>短事务原则：尽快提交，减少锁持有时间</li>
 * </ul>
 * 
 * <h2>故障排查</h2>
 * 
 * <h3>启用 DEBUG 日志</h3>
 * <pre>
 * logging.level.com.ghostchu.peerbanhelper.databasent.routing=DEBUG
 * </pre>
 * 
 * <h3>启用 TRACE 日志（查看每次路由）</h3>
 * <pre>
 * logging.level.com.ghostchu.peerbanhelper.databasent.routing=TRACE
 * </pre>
 * 
 * <h3>检查当前数据源</h3>
 * <pre>{@code
 * DataSourceType current = DataSourceRouter.getCurrentDataSourceType();
 * log.info("Current datasource: {}", current);
 * }</pre>
 * 
 * @author PeerBanHelper Team
 * @since 9.3.0
 */
package com.ghostchu.peerbanhelper.databasent.routing;
