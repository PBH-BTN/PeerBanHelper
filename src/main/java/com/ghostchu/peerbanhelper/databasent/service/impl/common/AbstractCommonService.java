package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.kotlin.KtQueryChainWrapper;
import com.baomidou.mybatisplus.extension.kotlin.KtQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class AbstractCommonService<M extends BaseMapper<T>, T> extends ServiceImpl<M, T> {
    private static final String LAST_LIMIT = "LIMIT 200";

    public long splitBatchDelete(@NotNull QueryWrapper<T> wrapper) {
        return splitBatchDelete0(wrapper.last(LAST_LIMIT));
    }

    public long splitBatchDelete(@NotNull QueryChainWrapper<T> wrapper) {
        return splitBatchDelete0(wrapper.last(LAST_LIMIT));
    }

    public long splitBatchDelete(@NotNull KtQueryWrapper<T> wrapper) {
        return splitBatchDelete0(wrapper.last(LAST_LIMIT));
    }

    public long splitBatchDelete(@NotNull KtQueryChainWrapper<T> wrapper) {
        return splitBatchDelete0(wrapper.last(LAST_LIMIT));
    }

    public long splitBatchDelete(@NotNull LambdaQueryWrapper<T> wrapper) {
        return splitBatchDelete0(wrapper.last(LAST_LIMIT));
    }

    public long splitBatchDelete(@NotNull LambdaQueryChainWrapper<T> wrapper) {
        return splitBatchDelete0(wrapper.last(LAST_LIMIT));
    }

    private long splitBatchDelete0(@NotNull Wrapper<T> wrapper) {
        long deleted = 0;
        int lastDeleted;
        do {
            var list = baseMapper.selectList(wrapper);
            lastDeleted = baseMapper.deleteByIds(list);
            deleted += lastDeleted;
            log.debug("[SpiltBatchDelete] Deleted {} so far.", deleted);
        } while (lastDeleted > 0);
        log.debug("[SpiltBatchDelete] Deleted {} total.", deleted);
        return deleted;
    }
}
