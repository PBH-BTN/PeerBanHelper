package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.ghostchu.peerbanhelper.databasent.mapper.java.RuleSubInfoMapper;
import com.ghostchu.peerbanhelper.databasent.service.RuleSubInfoService;
import com.ghostchu.peerbanhelper.databasent.table.RuleSubInfoEntity;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RuleSubInfoServiceImpl extends AbstractCommonService<RuleSubInfoMapper, RuleSubInfoEntity> implements RuleSubInfoService {

    public RuleSubInfoServiceImpl(@NotNull TransactionTemplate transactionTemplate) {
        super(transactionTemplate);
    }
}
