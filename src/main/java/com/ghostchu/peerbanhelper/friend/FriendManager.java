package com.ghostchu.peerbanhelper.friend;

import com.ghostchu.peerbanhelper.database.dao.impl.FriendDao;
import com.ghostchu.peerbanhelper.decentralized.ipfs.IPFS;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
public class FriendManager implements AutoCloseable {
    @Getter
    private final Set<Friend> friends = new CopyOnWriteArraySet<>();
    private final ScheduledExecutorService reconnectExecutor = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());
    private final ScheduledExecutorService saveExecutor = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());
    private final JavalinWebContainer webContainer;
    private FriendDao friendDao;
    private IPFS ipfs;

    public FriendManager(FriendDao friendDao, IPFS ipfs, JavalinWebContainer webContainer) throws SQLException {
        this.friendDao = friendDao;
        this.ipfs = ipfs;
        this.webContainer = webContainer;
        var friendsInDb = friendDao.queryForAll();
        int ct = 0;
        for (var f : friendsInDb) {
            friends.add(new Friend(
                    ipfs,
                    f.getPeerId(),
                    f.getPubKey(),
                    f.getLastAttemptConnectTime().getTime(),
                    f.getLastCommunicationTime().getTime(),
                    f.getLastRecordedPBHVersion()
            ));
            ct++;
        }
        log.info(tlUI(Lang.FRIEND_LOADED, ct));
        reconnectExecutor.scheduleWithFixedDelay(this::reconnect, 0, 5, TimeUnit.MINUTES);
        saveExecutor.scheduleWithFixedDelay(this::flush, 0, 10, TimeUnit.MINUTES);

    }

    public void flush() {
        try {
            friendDao.saveFriendList(friends);
        } catch (Exception e) {
            log.error("Failed to save friends status to database", e);
        }
    }

    public void close() {
        flush();
    }

    private void reconnect() {
        try (var vt = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Friend friend : friends) {
                vt.submit(() -> {
                    friend.connect();
                });
            }
        }
    }
}
