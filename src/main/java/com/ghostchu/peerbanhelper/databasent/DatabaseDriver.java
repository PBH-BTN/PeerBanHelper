package com.ghostchu.peerbanhelper.databasent;

import org.apache.ibatis.type.TypeHandler;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.net.InetAddress;

public interface DatabaseDriver {
    @NotNull DatabaseType getType();

    @NotNull String getMapperPackagePath();

    @NotNull DataSource getDataSource();

    @NotNull TypeHandler<InetAddress> getInetTypeHandler();

    @NotNull TypeHandler<Object> getJsonTypeHandler();
}
