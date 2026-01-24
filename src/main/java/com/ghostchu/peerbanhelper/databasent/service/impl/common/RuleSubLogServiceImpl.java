package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.peerbanhelper.databasent.mapper.java.RuleSubLogMapper;
import com.ghostchu.peerbanhelper.databasent.service.RuleSubLogService;
import com.ghostchu.peerbanhelper.databasent.table.RuleSubLogEntity;
import org.springframework.stereotype.Service;

@Service
public class RuleSubLogServiceImpl extends ServiceImpl<RuleSubLogMapper, RuleSubLogEntity> implements RuleSubLogService {
    @Override
    public RuleSubLogEntity getLastLog(String ruleId) {
        return baseMapper.selectOne(new QueryWrapper<RuleSubLogEntity>().eq("rule_id", ruleId).orderByDesc("id").last("LIMIT 1"));
    }

    @Override
    public IPage<RuleSubLogEntity> getLogs(Page<RuleSubLogEntity> page, String ruleId) {
        return baseMapper.selectPage(page, new QueryWrapper<RuleSubLogEntity>()
                .eq(ruleId != null, "rule_id", ruleId)
                .orderByDesc("update_time"));
    }

    @Override
    public long countLogs(String ruleId) {
        return baseMapper.selectCount(new QueryWrapper<RuleSubLogEntity>()
                .eq(ruleId != null, "rule_id", ruleId));
    }
}
