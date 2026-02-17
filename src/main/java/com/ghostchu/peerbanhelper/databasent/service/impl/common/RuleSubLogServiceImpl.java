package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ghostchu.peerbanhelper.databasent.mapper.java.RuleSubLogMapper;
import com.ghostchu.peerbanhelper.databasent.service.RuleSubLogService;
import com.ghostchu.peerbanhelper.databasent.table.RuleSubLogEntity;
import org.springframework.stereotype.Service;

@Service
public class RuleSubLogServiceImpl extends AbstractCommonService<RuleSubLogMapper, RuleSubLogEntity> implements RuleSubLogService {
    @Override
    public RuleSubLogEntity getLastLog(String ruleId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<RuleSubLogEntity>().eq(RuleSubLogEntity::getRuleId, ruleId).orderByDesc(RuleSubLogEntity::getId).last("LIMIT 1"));
    }

    @Override
    public IPage<RuleSubLogEntity> getLogs(Page<RuleSubLogEntity> page, String ruleId) {
        return baseMapper.selectPage(page, new LambdaQueryWrapper<RuleSubLogEntity>()
                .eq(ruleId != null, RuleSubLogEntity::getRuleId, ruleId)
                .orderByDesc(RuleSubLogEntity::getUpdateTime));
    }

    @Override
    public long countLogs(String ruleId) {
        return baseMapper.selectCount(new LambdaQueryWrapper<RuleSubLogEntity>()
                .eq(ruleId != null, RuleSubLogEntity::getRuleId, ruleId));
    }
}
