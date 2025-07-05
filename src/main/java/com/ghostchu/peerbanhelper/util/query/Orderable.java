package com.ghostchu.peerbanhelper.util.query;

import com.j256.ormlite.stmt.QueryBuilder;
import io.javalin.http.Context;

import java.util.LinkedHashMap;
import java.util.Map;

public class Orderable extends LinkedHashMap<String, Boolean> {

    public Orderable(Map<String, Boolean> def, Context ctx) {
        if (ctx != null) {
            if (!ctx.queryParams("orderBy").isEmpty()) {
                appendOrders(ctx);
                return;
            }
        }
        appendOrders(def);
    }

    private void appendOrders(String field, boolean asc) {
        put(field, asc);
    }

    private void appendOrders(Map<String, Boolean> fields) {
        putAll(fields);
    }

    private void appendOrders(Context ctx) {
        for (String orderBy : ctx.queryParams("orderBy")) {
            String[] spilt = orderBy.split("\\|");
            if (spilt.length == 0) continue;
            String field = spilt[0];
            boolean asc = spilt.length < 2 || !spilt[1].equalsIgnoreCase("desc");
            put(field, asc);
        }
    }

    public <T, ID> QueryBuilder<T, ID> apply(QueryBuilder<T, ID> queryBuilder) {
        for (Map.Entry<String, Boolean> entry : entrySet()) {
            queryBuilder.orderBy(entry.getKey(), entry.getValue());
        }
        return queryBuilder;
    }
}
