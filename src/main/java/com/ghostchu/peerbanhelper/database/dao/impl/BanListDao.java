package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.BanListEntity;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.j256.ormlite.table.TableUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public final class BanListDao extends AbstractPBHDao<BanListEntity, String> {
    public BanListDao(@Autowired Database database) throws SQLException {
        super(database.getDataSource(), BanListEntity.class);
    }

    public Map<PeerAddress, BanMetadata> readBanList() {
        Map<PeerAddress, BanMetadata> map = new HashMap<>();
        try {
            queryForAll().forEach(e -> map.put(
                    JsonUtil.standard().fromJson(e.getAddress(), PeerAddress.class)
                    , JsonUtil.standard().fromJson(e.getMetadata(), BanMetadata.class)));
        } catch (Exception e) {
            log.error("Unable to read stored banlist, skipping...", e);
        }
        return map;
    }

    public int saveBanList(Map<PeerAddress, BanMetadata> banlist) throws SQLException {
//        TableUtils.dropTable(this, true);
//        TableUtils.createTableIfNotExists(getConnectionSource(), BanListEntity.class);
        return callBatchTasks(() -> {
            List<BanListEntity> entityList = new ArrayList<>();
            banlist.forEach((key, value) -> entityList.add(new BanListEntity(
                    JsonUtil.standard().toJson(key)
                    , JsonUtil.standard().toJson(value))));
            TableUtils.clearTable(getConnectionSource(), BanListEntity.class);
            return create(entityList);
        });
    }
}
