package com.ghostchu.peerbanhelper.downloader.impl.bitcomet;

public enum BCEndpoint {
    USER_LOGIN("/api/webui/login"),
    GET_DEVICE_TOKEN("/api/device_token/get"),
    GET_TASK_LIST("/api_v2/task_list/get"),
    POST_TASKS_ACTION("/api_v2/tasks/action"),
    POST_TASKS_DELETE("/api_v2/tasks/delete"),
    GET_NEW_TASK_CONFIG("/api/config/new_task/get"),
    ADD_HTTP_TASK("/api/task/http/add"),
    ADD_BT_TASK("/api/task/bt/add"),
    GET_TASK_SUMMARY("/api/task/summary/get"),
    GET_TASK_FILES("/api/task/files/get"),
    GET_TASK_TRACKERS("/api/task/trackers/get"),
    GET_TASK_PEERS("/api/task/peers/get"),
    GET_CONNECTION_CONFIG("/api/config/connection/get"),
    SET_CONNECTION_CONFIG("/api/config/connection/set"),
    GET_IP_FILTER_CONFIG("/api/config/ipfilter/get"),
    SET_IP_FILTER_CONFIG("/api/config/ipfilter/set"),
    IP_FILTER_UPLOAD("/api/config/ipfilter/upload"),
    IP_FILTER_DOWNLOAD("/api/config/ipfilter/download"),
    IP_FILTER_CLEAR("/api/config/ipfilter/clear"),
    IP_FILTER_QUERY_IMPORTING("/api/config/ipfilter/query"),
    GET_REMOTE_ACCESS_CONFIG("/api/config/remote_access/get"),
    SET_REMOTE_ACCESS_CONFIG("/api/config/remote_access/set"),
    TASK_UNBAN_PEERS("/api/task/peers/unban_peers"),
    ;
    private final String endpoint;

    BCEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getEndpoint() {
        return endpoint;
    }
}
