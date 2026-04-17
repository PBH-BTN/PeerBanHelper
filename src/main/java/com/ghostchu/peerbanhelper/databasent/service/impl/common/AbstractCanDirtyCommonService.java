package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.peerbanhelper.databasent.service.CommonCanDirtyService;
import com.ghostchu.peerbanhelper.util.helpstatus.CanDirty;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.BatchResult;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Slf4j
public class AbstractCanDirtyCommonService<M extends BaseMapper<T>, T extends CanDirty> extends AbstractCommonService<M,T> implements CommonCanDirtyService<T> {

    public AbstractCanDirtyCommonService(@NotNull TransactionTemplate transactionTemplate) {
        super(transactionTemplate);
    }

    @Override
    public boolean saveOrUpdateIfDirty(T t) {
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
        return transactionTemplate.execute((_)->{
            var dirtyElements = t.stream().filter(CanDirty::isDirty).toList();
            return this.baseMapper.insertOrUpdate(dirtyElements, 1000);
        });
    }
}
