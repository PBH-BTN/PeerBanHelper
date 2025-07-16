package com.ghostchu.peerbanhelper.util.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.function.Function;

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

    public static <OLD, NEW> Page<NEW> map(Page<OLD> page, Function<OLD, NEW> mapper){
        List<NEW> newResults = page.getResults().stream()
                .map(mapper)
                .toList();
        var newPage = new Page<NEW>();
        newPage.setSize(page.getSize());
        newPage.setPage(page.getPage());
        newPage.setTotal(page.getTotal());
        newPage.setResults(newResults);
        return newPage;
    }
}
