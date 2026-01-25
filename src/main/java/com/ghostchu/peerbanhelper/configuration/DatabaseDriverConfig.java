package com.ghostchu.peerbanhelper.configuration;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.databasent.DatabaseDriver;
import com.ghostchu.peerbanhelper.databasent.driver.h2.H2DatabaseDriver;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.jetbrains.annotations.NotNull;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@Slf4j
@Configuration
@MapperScan("com.ghostchu.peerbanhelper.databasent.mapper.java")
public class DatabaseDriverConfig {

    public static DatabaseDriver databaseDriver;

    @Bean
    public DatabaseDriver loadDriver() throws Exception {
        log.info("Please wait, loading database driver...");
        var section = Main.getMainConfig().getConfigurationSection("database");
        if (section == null) throw new IllegalStateException("Database configuration section is missing!");
        int databaseTypeId = section.getInt("type");
        var driver = switch (databaseTypeId) {
            // case 1 -> new PostgresDatabaseDriver(section);
            // case 2 -> new MySQLDatabaseDriver(section);
            default -> new H2DatabaseDriver(section);
        };
        log.info("Database driver loaded: {}", driver.getType().name());
        databaseDriver = driver;
        return driver;
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(@NotNull DatabaseDriver databaseDriver) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        DbType dbType = switch (databaseDriver.getType()) {
            case H2 -> DbType.H2;
            case POSTGRES -> DbType.POSTGRE_SQL;
            case MYSQL -> DbType.MYSQL;
        };
        var pagination = new PaginationInnerInterceptor(dbType);
        pagination.setMaxLimit(1000L);
        //interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor()); // 防止全表更新与删除插件
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(pagination); // 如果配置多个插件, 切记分页最后添加
        // 如果有多数据源可以不配具体类型, 否则都建议配上具体的 DbType
        return interceptor;
    }


    @Bean
    public SqlSessionFactory sqlSessionFactory(MybatisPlusInterceptor interceptor, DatabaseDriver driver) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        // DatabaseDriver 作为 Bean 注入，从中获取 DataSource，而不是依赖 Spring 上下文中的 DataSource Bean
        factoryBean.setDataSource(driver.getDataSource());
        factoryBean.setPlugins(interceptor);
        //factoryBean.setTypeHandlers(new OffsetDateTimeTypeHandlerForwarder()); // 注册 OffsetDateTime 类型处理器

        // 关键逻辑：根据 Driver 提供的路径（例如 "mapper/mysql/**/*.xml"）动态构建 XML 扫描路径
        // 结果如: "classpath*:mapper/mysql/**/*.xml"
        // 这样就只加载特定数据库的 SQL XML，避免了 MySQL 和 Postgres 的 XML 同时被加载产生的冲突
        String xmlPath = "classpath*:" + driver.getMapperXmlPath();
        log.info("Loading Mapper XMLs from: {}", xmlPath);

        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources(xmlPath));

        return factoryBean.getObject();
    }
}
