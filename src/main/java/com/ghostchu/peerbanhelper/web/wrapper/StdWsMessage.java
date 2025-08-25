package com.ghostchu.peerbanhelper.web.wrapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StdWsMessage {
    private boolean success;
    private StdWsAction action;
    private Object data;
}
