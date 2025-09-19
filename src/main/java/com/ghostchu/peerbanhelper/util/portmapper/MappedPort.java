package com.ghostchu.peerbanhelper.util.portmapper;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MappedPort {
    private Protocol protocol;
    private int externalPort;
}
