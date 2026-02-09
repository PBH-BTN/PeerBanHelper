package com.ghostchu.peerbanhelper.databasent.routing;

/**
 * 数据源类型枚举，用于区分读写数据源
 */
public enum DataSourceType {
    /**
     * 读数据源 - 用于查询操作
     * SQLite: 多连接池，FULLMUTEX 模式
     */
    READ,
    
    /**
     * 写数据源 - 用于写入操作
     * SQLite: 单连接池，NOMUTEX 模式
     */
    WRITE
}
