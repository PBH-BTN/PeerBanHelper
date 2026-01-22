package com.ghostchu.peerbanhelper.configuration;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.databasent.DatabaseDriver;
import com.ghostchu.peerbanhelper.databasent.driver.h2.H2DatabaseDriver;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class DatabaseDriverConfig {
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
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor()); // 防止全表更新与删除插件
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(pagination); // 如果配置多个插件, 切记分页最后添加
        // 如果有多数据源可以不配具体类型, 否则都建议配上具体的 DbType
        return interceptor;
    }
}
