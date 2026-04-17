package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.peerbanhelper.databasent.service.CommonCanDirtyService;
import com.ghostchu.peerbanhelper.util.helpstatus.CanDirty;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.BatchResult;

import java.util.List;

@Slf4j
public class AbstractCanDirtyCommonService<M extends BaseMapper<T>, T extends CanDirty> extends AbstractCommonService<M,T> implements CommonCanDirtyService<T> {

    @Override
    public boolean saveOrUpdateIfDirtyWithIdRefill(T t) {
        if(t != null && t.isDirty()){
            boolean success = this.baseMapper.insertOrUpdate(t);
            if(success){
                t.setDirty(false);
            }
            return success;
        }
        return false;
    }

    @Override
    public List<BatchResult> saveOrUpdateIfDirty(List<T> t) {
        var dirtyElements = t.stream().filter(CanDirty::isDirty).toList();
        return this.baseMapper.insertOrUpdate(dirtyElements, 1000);
    }
}
