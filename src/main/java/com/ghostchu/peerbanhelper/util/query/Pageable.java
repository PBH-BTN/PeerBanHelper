package com.ghostchu.peerbanhelper.util.query;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.javalin.http.Context;
import lombok.Data;

@Data
public final class Pageable {
    private long page;
    private long size;

    public Pageable(long page, long size) {
        this.page = page;
        this.size = size;
    }

    public Pageable(Context context) {
        var page = context.queryParam("page");
        var size = context.queryParam("pageSize");
        if (page == null) {
            this.page = 1;
        } else {
            this.page = Long.parseLong(page);
        }
        if (size == null) {
            this.size = 10;
        } else {
            this.size = Long.parseLong(size);
        }
    }

    public long getZeroBasedPage() {
        return this.page - 1;
    }

    public <T> Page<T> toPage(Class<T> type) {
        return Page.of(page, size);
    }

}
