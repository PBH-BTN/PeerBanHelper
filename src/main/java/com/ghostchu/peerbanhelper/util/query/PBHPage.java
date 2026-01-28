package com.ghostchu.peerbanhelper.util.query;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 让前端开心的东西
@AllArgsConstructor
@NoArgsConstructor
@Data
public class PBHPage<T> {
    private long page;
    private long size;
    private long total;
    private List<T> results;

    public static <T> PBHPage<T> from(IPage<T> page) {
        return new PBHPage<>(
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getRecords()
        );
    }
}
