package com.ghostchu.peerbanhelper.databasent.mapper.java;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.peerbanhelper.databasent.table.PeerConnectionMetricsTrackEntity;
import com.ghostchu.peerbanhelper.databasent.table.tmp.TrackedSwarmEntity;

public interface PeerConnectionMetricsTrackMapper extends BaseMapper<PeerConnectionMetricsTrackEntity> {
    void upsert(PeerConnectionMetricsTrackEntity entity);
}
