package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.DHTRecordEntity;
import io.ipfs.multibase.binary.Base32;
import io.ipfs.multihash.Multihash;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.peergos.protocol.dht.RecordStore;
import org.peergos.protocol.ipns.IpnsRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Deque;
import java.util.Optional;

@Component
@Slf4j
public class DHTRecordDao extends AbstractPBHDao<DHTRecordEntity, String> implements RecordStore {
    private final int SIZE_OF_VAL = 10240;
    private final int SIZE_OF_PEERID = 100;

    public DHTRecordDao(@Autowired Database database) throws SQLException {
        super(database.getDataSource(), DHTRecordEntity.class);
    }

    @Override
    public void close() throws Exception {

    }

    @SneakyThrows
    public void batchSave(Deque<PersistTask> tasks) {
        callBatchTasks(() -> {
            while (!tasks.isEmpty()) {
                var task = tasks.pop();
                try {
                    if (task.delete()) {
                        remove(task.key);
                    } else {
                        put(task.key, task.value);
                    }
                } catch (Exception e) {
                    log.warn("Unable save {} to DHT records database", task);
                }
            }
            return null;
        });
    }

    private String hashToKey(Multihash hash) {
        String padded = new Base32().encodeAsString(hash.toBytes());
        int padStart = padded.indexOf("=");
        return padStart > 0 ? padded.substring(0, padStart) : padded;
    }

    @Override
    public Optional<IpnsRecord> get(Multihash peerId) {
        try {
            var entity = queryForId(hashToKey(peerId));
            if (entity == null) return Optional.empty();
            return Optional.of(
                    new IpnsRecord(
                            entity.getRaw(),
                            entity.getSequence(),
                            entity.getTtlNanos(),
                            LocalDateTime.ofEpochSecond(entity.getExpiryUTC(), 0, ZoneOffset.UTC),
                            entity.getVal().getBytes()
                    )
            );
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void put(Multihash peerId, IpnsRecord record) {
        try {
            createOrUpdate(new DHTRecordEntity(
                    hashToKey(peerId),
                    record.raw,
                    record.sequence,
                    record.ttlNanos,
                    record.expiry.toEpochSecond(ZoneOffset.UTC),
                    new String(record.value.length > SIZE_OF_VAL ?
                            Arrays.copyOfRange(record.value, 0, SIZE_OF_VAL) : record.value)
            ));
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void remove(Multihash peerId) {
        try {
            delete(queryForEq("peerId", hashToKey(peerId)));
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public record PersistTask(
            boolean delete,
            Multihash key,
            IpnsRecord value
    ) {
    }
}
