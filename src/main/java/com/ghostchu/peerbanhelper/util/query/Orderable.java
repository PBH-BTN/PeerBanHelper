package com.ghostchu.peerbanhelper.util.query;

import com.j256.ormlite.stmt.QueryBuilder;
import io.javalin.http.Context;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class Orderable extends LinkedHashMap<String, Boolean> {
    // databaseName -> dtoName
    private final Map<String, String> dto2DatabaseNames = new HashMap<>();
    public Orderable(Map<String, Boolean> def, Context ctx) {
        // 如果用户指定了排序参数，优先使用用户排序
        // If user specified sort parameters, prioritize user sorting
        if (ctx != null && !ctx.queryParams("orderBy").isEmpty()) {
            // 先添加用户排序（作为主排序）
            // Add user sorting first (as primary sort)
            appendOrders(ctx);
            // 然后添加用户未指定的默认排序字段（作为次要排序，保证结果稳定性）
            // Then add default sort fields not specified by user (as secondary sort for stability)
            for (Map.Entry<String, Boolean> entry : def.entrySet()) {
                putIfAbsent(entry.getKey(), entry.getValue());
            }
        } else {
            // 用户未指定排序时，使用默认排序
            // Use default sorting when user did not specify any
            appendOrders(def);
        }
    }

    private Orderable appendOrders(String field, boolean asc) {
        put(field, asc);
        return this;
    }

    private Orderable appendOrders(Map<String, Boolean> fields) {
        putAll(fields);
        return this;
    }

    public Orderable addMapping(String nameForDatabase, String nameForDto){
        dto2DatabaseNames.put(nameForDto, nameForDatabase);
        return this;
    }

    public Orderable addMappings(Map<String, String> dto2Databases){
        dto2DatabaseNames.putAll(dto2Databases);
        return this;
    }

    private Orderable appendOrders(Context ctx) { // here is dtoName...
        for (String orderBy : ctx.queryParams("orderBy")) {
            String[] spilt = orderBy.split("\\|");
            if (spilt.length == 0) continue;
            String dtoName = spilt[0];
            boolean asc = spilt.length < 2 || (!spilt[1].equalsIgnoreCase("desc") && !spilt[1].equalsIgnoreCase("descend"));
            put(dtoName, asc); // so we put the databaseName in orders
        }
        return this;
    }

    public <T, ID> QueryBuilder<T, ID> apply(QueryBuilder<T, ID> queryBuilder) {
        for (Map.Entry<String, Boolean> entry : entrySet()) {
            queryBuilder.orderByRaw(dto2DatabaseNames.getOrDefault(entry.getKey(), entry.getKey()) +" "+(entry.getValue() ? "ASC" : "DESC"));
        }
        return queryBuilder;
    }
}
