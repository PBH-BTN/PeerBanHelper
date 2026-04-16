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
import com.ghostchu.peerbanhelper.databasent.service.CommonCanDirtyService;
import com.ghostchu.peerbanhelper.databasent.service.CommonService;
import com.ghostchu.peerbanhelper.util.helpstatus.CanDirty;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class AbstractCanDirtyCommonService<M extends BaseMapper<T>, T extends CanDirty> extends AbstractCommonService<M,T> implements CommonCanDirtyService<T> {

    @Override
    public boolean saveOrUpdateIfDirty(T t) {
        if(t != null && t.isDirty()){
            boolean success = this.saveOrUpdate(t);
            if(success){
                t.setDirty(false);
            }
            return success;
        }
        return false;
    }
}
