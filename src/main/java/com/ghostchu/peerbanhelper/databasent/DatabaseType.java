package com.ghostchu.peerbanhelper.databasent;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor

public enum DatabaseType {
    H2("mysql", "mysql", "mysql"),
    POSTGRES("postgres", "postgres", "postgres"),
    MYSQL("mysql", "mysql", "mysql");

    private final String mapperType;
    private final String migrationType;
    private final String repeatType;

}
