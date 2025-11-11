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
        appendOrders(def);
        if (ctx != null) {
            if (!ctx.queryParams("orderBy").isEmpty()) {
                appendOrders(ctx);
                return;
            }
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
