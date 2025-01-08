package com.ghostchu.peerbanhelper.util.paging;

import io.javalin.http.Context;
import lombok.Data;

@Data
public final class Pageable {
    private long page;
    private long size;

    /**
     * Constructs a new Pageable instance with specified page number and page size.
     *
     * @param page The page number to be used for pagination (must be a positive long value)
     * @param size The number of items per page (must be a positive long value)
     */
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

}
