package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.ghostchu.peerbanhelper.BanList;
import com.ghostchu.peerbanhelper.databasent.mapper.java.BanListMapper;
import com.ghostchu.peerbanhelper.databasent.service.BanListService;
import com.ghostchu.peerbanhelper.databasent.table.BanListEntity;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import inet.ipaddr.IPAddress;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Slf4j
@Service
public class BanListServiceImpl extends AbstractCommonService<BanListMapper, BanListEntity> implements BanListService {
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Override
    public @NotNull Map<IPAddress, BanMetadata> readBanList() {
        Map<IPAddress, BanMetadata> map = new HashMap<>();
        try {
            baseMapper.selectList(null).forEach(e -> map.put(IPAddressUtil.getIPAddress(e.getAddress()),
                    JsonUtil.tiny().fromJson(e.getMetadata(), BanMetadata.class)));
        } catch (Exception e) { // 可能因为 BanMetadata 有变动这里的数据会反序列化失败
            log.error("Unable to read stored banlist, skipping...", e);
        }
        return map;
    }

    @Override
    public int saveBanList(@NotNull BanList banlist) {
        List<BanListEntity> entityList = new ArrayList<>();
        banlist.forEach((key, value) -> entityList.add(new BanListEntity(
                key.toNormalizedString(), JsonUtil.tiny().toJson(value))));
        Integer integer = transactionTemplate.execute(_->{
            baseMapper.delete(null);
            return baseMapper.insert(entityList).size();
        });
        if(integer == null){
            return 0;
        }
        return integer;
    }
}
