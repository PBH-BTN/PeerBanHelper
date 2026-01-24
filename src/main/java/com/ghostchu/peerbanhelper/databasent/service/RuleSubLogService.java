package com.ghostchu.peerbanhelper.databasent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.peerbanhelper.databasent.table.RuleSubLogEntity;

public interface RuleSubLogService extends IService<RuleSubLogEntity> {
    RuleSubLogEntity getLastLog(String ruleId);

    IPage<RuleSubLogEntity> getLogs(Page<RuleSubLogEntity> page, String ruleId);

    long countLogs(String ruleId);
}
