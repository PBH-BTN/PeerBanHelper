package com.ghostchu.peerbanhelper.configuration;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.databasent.DatabaseDriver;
import com.ghostchu.peerbanhelper.databasent.MultiDbExplainInterceptor;
import com.ghostchu.peerbanhelper.databasent.driver.common.BasicIPAddressTypeHandler;
import com.ghostchu.peerbanhelper.databasent.driver.common.BasicInetTypeHandler;
import com.ghostchu.peerbanhelper.databasent.driver.h2.H2DatabaseDriver;
import com.ghostchu.peerbanhelper.databasent.driver.mysql.MySQLDatabaseDriver;
import com.ghostchu.peerbanhelper.text.Lang;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.jetbrains.annotations.NotNull;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Configuration
@MapperScan("com.ghostchu.peerbanhelper.databasent.mapper.java")
public class DatabaseDriverConfig {

    public static DatabaseDriver databaseDriver;

    @Bean
    public DatabaseDriver loadDriver() throws Exception {
        log.info("Please wait, loading database driver...");
        log.info(tlUI(Lang.DBNT_LOADING_DRIVER));
        var section = Main.getMainConfig().getConfigurationSection("database");
        if (section == null) throw new IllegalStateException("Database configuration section is missing!");
        int databaseTypeId = section.getInt("type");
        var driver = switch (databaseTypeId) {
            case 1 -> new MySQLDatabaseDriver(section);
            // case 2 -> new PostgresDatabaseDriver(section);
            default -> new H2DatabaseDriver(section);
        };
        log.info(tlUI(Lang.DBNT_LOADING_DRIVER_LOADED, driver.getType().name()));
        databaseDriver = driver;
        return driver;
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(@NotNull DatabaseDriver databaseDriver) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        DbType dbType = switch (databaseDriver.getType()) {
            case H2 -> DbType.MYSQL; // H2 USE MYSQL
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
    public SqlSessionFactory sqlSessionFactory(
            MybatisPlusInterceptor mpInterceptor,
            MultiDbExplainInterceptor explainInterceptor, // 引入自定义拦截器
            DatabaseDriver driver) throws Exception {

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(driver.getDataSource());

        // 关键修复点：将两个拦截器都设置进去
        // setPlugins 接受 Interceptor[] 数组
        factoryBean.setPlugins(mpInterceptor);

        factoryBean.setTypeHandlers(new BasicInetTypeHandler(), new BasicIPAddressTypeHandler());

        String xmlPath = "classpath*:" + driver.getMapperXmlPath();
        log.info(tlUI(Lang.DBNT_LOADING_MAPPER, xmlPath));
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources(xmlPath));

        return factoryBean.getObject();
    }
}
