package com.ghostchu.peerbanhelper.configuration;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.parser.JsqlParserGlobal;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.databasent.DatabaseDriver;
import com.ghostchu.peerbanhelper.databasent.driver.common.BasicIPAddressTypeHandler;
import com.ghostchu.peerbanhelper.databasent.driver.common.BasicInetTypeHandler;
import com.ghostchu.peerbanhelper.databasent.driver.common.OffsetDateTimeTypeHandlerForwarder;
import com.ghostchu.peerbanhelper.databasent.driver.h2.H2DatabaseDriver;
import com.ghostchu.peerbanhelper.databasent.driver.mysql.MySQLDatabaseDriver;
import com.ghostchu.peerbanhelper.databasent.driver.postgres.PostgresDatabaseDriver;
import com.ghostchu.peerbanhelper.databasent.driver.sqlite.SQLiteDatabaseDriver;
import com.ghostchu.peerbanhelper.text.Lang;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.jetbrains.annotations.NotNull;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Configuration
@MapperScan("com.ghostchu.peerbanhelper.databasent.mapper.java")
public class DatabaseDriverConfig {

    public static DatabaseDriver databaseDriver;

    @PostConstruct
    public void setupJsqlParserThreadPool(){
        int parallelism = ExternalSwitch.parseInt("pbh.database.jsqlparser.thread-pool.parallelism", Math.min(4, Runtime.getRuntime().availableProcessors()));
        ExecutorService executorService = Executors.newWorkStealingPool(parallelism);
        JsqlParserGlobal.setExecutorService(executorService, Thread.ofVirtual().unstarted(() -> {
            if (!executorService.isShutdown()) {
                executorService.shutdown();
            }
        }));
    }

    @Bean
    public DatabaseDriver loadDriver() throws Exception {
        log.info(tlUI(Lang.DBNT_LOADING_DRIVER));
        var section = Main.getMainConfig().getConfigurationSection("database");
        if (section == null) throw new IllegalStateException("Database configuration section is missing!");
        String databaseTypeId = section.getString("type", "sqlite").toLowerCase();
        var driver = switch (databaseTypeId) {
            case "h2" -> new H2DatabaseDriver(section);
            case "mysql" -> new MySQLDatabaseDriver(section);
            case "postgresql" -> new PostgresDatabaseDriver(section);
            default -> new SQLiteDatabaseDriver(section);
        };
        log.info(tlUI(Lang.DBNT_LOADING_DRIVER_LOADED, driver.getType().name()));
        databaseDriver = driver;
        return driver;
    }


    @Bean
    public PlatformTransactionManager transactionManager(@NotNull DatabaseDriver driver) {
        return new DataSourceTransactionManager(driver.getReadDataSource());
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(@NotNull DatabaseDriver databaseDriver) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        DbType dbType = switch (databaseDriver.getType()) {
            case H2 -> DbType.MYSQL; // H2 USE MYSQL
            case POSTGRES -> DbType.POSTGRE_SQL;
            case MYSQL -> DbType.MYSQL;
            case SQLITE -> DbType.SQLITE;
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
            SentryMyBatisInterceptor sentryMyBatisInterceptor,
            DatabaseDriver driver) throws Exception {

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(driver.getReadDataSource());

        List<Interceptor> interceptorList = new ArrayList<>();
        interceptorList.add(mpInterceptor);
        interceptorList.add(sentryMyBatisInterceptor);
        factoryBean.setPlugins(interceptorList.toArray(new Interceptor[0]));

        factoryBean.setTypeHandlers(new BasicInetTypeHandler(), new BasicIPAddressTypeHandler(), new OffsetDateTimeTypeHandlerForwarder());

        String xmlPath = "classpath*:" + driver.getMapperXmlPath();
        log.info(tlUI(Lang.DBNT_LOADING_MAPPER, xmlPath));
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources(xmlPath));

        return factoryBean.getObject();
    }

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager platformTransactionManager){
        return new TransactionTemplate(platformTransactionManager);
    }
}
