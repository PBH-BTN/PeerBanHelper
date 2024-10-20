package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.FriendEntity;
import com.ghostchu.peerbanhelper.friend.Friend;
import com.j256.ormlite.table.TableUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Set;

@Component
@Slf4j
public class FriendDao extends AbstractPBHDao<FriendEntity, String> {
    public FriendDao(@Autowired Database database) throws SQLException {
        super(database.getDataSource(), FriendEntity.class);
    }

    public void saveFriendList(Set<Friend> friends) throws SQLException {
        callBatchTasks(() -> {
            var entities = friends.stream().map(f -> new FriendEntity(
                    f.getPeerId(),
                    f.getPubKey(),
                    new Timestamp(f.getLastAttemptConnectTime()),
                    new Timestamp(f.getLastCommunicationTime()),
                    f.getLastRecordedPBHVersion(),
                    f.getLastRecordedConnectionStatus()
            )).toList();
            TableUtils.clearTable(getConnectionSource(), FriendEntity.class);
            create(entities);
            return null;
        });
    }
}
