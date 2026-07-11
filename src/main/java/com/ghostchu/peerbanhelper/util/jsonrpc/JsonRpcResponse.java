package com.ghostchu.peerbanhelper.util.jsonrpc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class JsonRpcResponse<T> {
    public String jsonrpc;
    public String id;
    public T result;
    public JsonRpcError error;

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class JsonRpcError {
        public int code;
        public String message;
    }
}