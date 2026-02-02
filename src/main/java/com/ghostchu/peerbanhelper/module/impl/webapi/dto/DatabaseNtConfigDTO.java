package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class DatabaseNtConfigDTO {
    private String type;
    private String host;
    private Integer port;
    private String database;
    private String username;
    private String password;
}
