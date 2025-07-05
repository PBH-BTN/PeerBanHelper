package com.ghostchu.peerbanhelper.util.query;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.j256.ormlite.stmt.QueryBuilder;
import io.javalin.http.Context;

import java.util.LinkedHashMap;
import java.util.Map;

public class Orderable extends LinkedHashMap<String, Boolean> {
    // databaseName -> dtoName
    private final BiMap<String, String> dualDirectionMapping = HashBiMap.create();
    public Orderable(Map<String, Boolean> def, Context ctx) {
        if (ctx != null) {
            if (!ctx.queryParams("orderBy").isEmpty()) {
                appendOrders(ctx);
                return;
            }
        }
        appendOrders(def);
    }

    private Orderable appendOrders(String field, boolean asc) {
        put(field, asc);
        return this;
    }

    private Orderable appendOrders(Map<String, Boolean> fields) {
        putAll(fields);
        return this;
    }

    private Orderable addMapping(String nameForDatabase, String nameForDto){
        dualDirectionMapping.put(nameForDatabase, nameForDto);
        return this;
    }

    private Orderable addMappings(Map<String, String> database2Dto){
        dualDirectionMapping.putAll(database2Dto);
        return this;
    }

    private Orderable appendOrders(Context ctx) { // here is dtoName...
        for (String orderBy : ctx.queryParams("orderBy")) {
            String[] spilt = orderBy.split("\\|");
            if (spilt.length == 0) continue;
            String dtoName = spilt[0];
            boolean asc = spilt.length < 2 || !spilt[1].equalsIgnoreCase("desc");
            put(dualDirectionMapping.inverse().getOrDefault(dtoName, dtoName), asc); // so we put the databaseName in orders
        }
        return this;
    }

    public <T, ID> QueryBuilder<T, ID> apply(QueryBuilder<T, ID> queryBuilder) {
        for (Map.Entry<String, Boolean> entry : entrySet()) {
            queryBuilder.orderBy(entry.getKey(), entry.getValue());
        }
        return queryBuilder;
    }
}
