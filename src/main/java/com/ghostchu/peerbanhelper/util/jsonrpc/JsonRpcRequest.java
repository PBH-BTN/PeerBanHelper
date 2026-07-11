package com.ghostchu.peerbanhelper.util.jsonrpc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
// 请求结构体
public class JsonRpcRequest {
    private final String jsonrpc = "2.0";
    private String id;
    private String method;
    private List<Object> params;

    public JsonRpcRequest(String method, List<Object> params) {
        this.id = UUID.randomUUID().toString();
        this.method = method;
        this.params = params;
    }

    public JsonRpcRequest(String id, String method, List<Object> params) {
        this.id = id;
        this.method = method;
        this.params = params;
    }
}
