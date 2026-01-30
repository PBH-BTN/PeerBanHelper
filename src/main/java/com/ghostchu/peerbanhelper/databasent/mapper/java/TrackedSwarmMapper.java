package com.ghostchu.peerbanhelper.databasent.mapper.java;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.peerbanhelper.databasent.table.tmp.TrackedSwarmEntity;
import org.apache.ibatis.annotations.Select;

public interface TrackedSwarmMapper extends BaseMapper<TrackedSwarmEntity> {
    void resetTable();
    void upsert(TrackedSwarmEntity entity);
}
