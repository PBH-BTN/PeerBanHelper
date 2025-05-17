package com.ghostchu.peerbanhelper.util.paging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public final class Page<T> {
    private long page;
    private long size;
    private long total;
    private List<T> results;

    public Page(Pageable pageable, long total, List<T> results) {
        this.page = pageable.getPage();
        this.size = pageable.getSize();
        this.total = total;
        this.results = results;
    }
}
