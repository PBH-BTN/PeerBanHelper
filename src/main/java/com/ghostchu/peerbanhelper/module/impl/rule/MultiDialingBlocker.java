package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.database.DatabaseHelper;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.IpUtil;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;

/**
 * 一组青岛ip是同时下载一个种子，但是每个都正常报告进度，估计是有比较复杂的检测对抗逻辑。
 * 针对这种情况进行多拨检测，同一网段集中下载同一个种子视为多拨，因为多拨和PCDN强相关所以可以直接封禁
 *
 multi-dialing-blocker:
     enabled: false
     # 子网掩码长度
     # IP地址前多少位相同的视为同一个子网，一般不需要修改
     subnet-mask-length: 24
     # 容许同一网段下载同一种子的IP数量，正整数
     # 防止DHCP重新分配IP、碰巧有同一小区的用户下载同一种子等导致的误判
     tolerate-num: 3
     # 缓存持续时间（秒）
     # 所有连接过的peer会记入缓存，DHCP服务会定期重新分配IP，缓存时间过长会导致误杀
     cache-lifespan: 86400
     # 是否追猎
     # 如果某IP已判定为多拨，无视缓存时间限制继续搜寻其同伙
     keep-hunting: true
     # 追猎持续时间（秒）
     # keep-hunting为true时有效，和cache-lifspan相似，对被猎杀IP的缓存持续时间
     keep-hunting-time: 2592000
 */
@Slf4j
public class MultiDialingBlocker extends AbstractFeatureModule {
//    private final DatabaseHelper db;

    private int subnetMaskLength;
    private int subnetMask;
    private int tolerateNum;
    private int cacheLifespan;
    private boolean keepHunting;
    private int keepHuntingTime;

    public MultiDialingBlocker(PeerBanHelperServer server, YamlConfiguration profile, @NotNull DatabaseHelper db) {
        super(server, profile);
//        this.db = db;
    }

    @Override
    public @NotNull String getName() {
        return "Multi Dialing Blocker";
    }

    @Override
    public @NotNull String getConfigName() {
        return "multi-dialing-blocker";
    }

    @Override
    public boolean needCheckHandshake() {
        return false;
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public void onEnable() {
        reloadConfig();
    }

    @Override
    public void onDisable() {

    }

    private void reloadConfig() {
        subnetMaskLength = getConfig().getInt("subnet-mask-length");
        subnetMask = ~0 << (32 - subnetMaskLength);
        tolerateNum = getConfig().getInt("tolerate-num");
        cacheLifespan = getConfig().getInt("cache-lifespan");
        keepHunting = getConfig().getBoolean("keep-hunting");
        keepHuntingTime = getConfig().getInt("keep-hunting-time");
    }

    @Override
    public @NotNull BanResult shouldBanPeer(
            @NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        String torrentName = torrent.getName();
        String hash = torrent.getHash();
        // 如果获取的哈希值不合法则不进行处理
        if (hash.length() < 40) {
            return new BanResult(this, PeerAction.NO_ACTION,
                    String.format(Lang.MODULE_MDB_GET_TORRENT_HASH_FAILED, torrentName));
        }

        long currentTimestamp = System.currentTimeMillis() / 1000;
        int peerIp = IpUtil.ipStr2Int(peer.getAddress().getIp());
        Map<Integer, Long> torrentPeerList = cache.computeIfAbsent(hash, h -> new ConcurrentHashMap<>());
        torrentPeerList.put(peerIp, currentTimestamp);

        int peerSubnet = peerIp & subnetMask;
        String hashSubnetStr = hash + '|' + peerSubnet;
        Set<Integer> subnetPeers = subnetCounter.computeIfAbsent(hashSubnetStr, h -> new ConcurrentSkipListSet<>());
        subnetPeers.add(peerIp);
        if (subnetPeers.size() > tolerateNum) {
            synchronized (torrentPeerList) {
                // 检查是否有过期的缓存
                Iterator<Integer> subnetPeersIterator = subnetPeers.iterator();
                while (subnetPeersIterator.hasNext()) {
                    Integer ip = subnetPeersIterator.next();
                    long timestamp = torrentPeerList.get(ip);
                    if (currentTimestamp - timestamp > cacheLifespan) {
                        torrentPeerList.remove(ip);
                        subnetPeersIterator.remove();
                    }
                }
                if (subnetPeers.size() > tolerateNum) {
                    // 落库
                    huntingList.put(hashSubnetStr, currentTimestamp);
                    // 返回当前IP即可，其他ip会在下一周期被封禁
                    return new BanResult(this, PeerAction.BAN,
                            String.format(Lang.MODULE_MDB_MULTI_DIALING_DETECTED,
                                    torrentName, IpUtil.int2IpStr(subnetMask), subnetMaskLength));
                }
            }
        }

        if (keepHunting) {
            recoverHuntingList();

            Long huntingTimestamp = huntingList.get(hashSubnetStr);
            if (huntingTimestamp != null) {
                if (currentTimestamp - huntingTimestamp < keepHuntingTime) {
                    // 落库
                    huntingList.put(hashSubnetStr, currentTimestamp);
                    return new BanResult(this, PeerAction.BAN,
                            String.format(Lang.MODULE_MDB_MULTI_DIALING_HUNTING_TRIGGERED,
                                    torrentName, IpUtil.int2IpStr(subnetMask), subnetMaskLength, peerIp));
                }
                else {
                    // 从数据库删除
                    huntingList.remove(hashSubnetStr);
                }
            }
        }

        return new BanResult(this, PeerAction.NO_ACTION,
                String.format(Lang.MODULE_MDB_MULTI_DIALING_NOT_DETECTED, torrentName));
    }

    private static boolean cacheRecovered = false;
    // 所有peer的连接记录 hash : ip : createTime
    private static final Map<String, Map<Integer, Long>> cache = new ConcurrentHashMap<>();
    // 按子网统计的连接记录 hash+subnet : ip
    private static final Map<String, Set<Integer>> subnetCounter = new ConcurrentHashMap<>();
    // 追猎名单 hash+subnet : createTime
    private static final Map<String, Long> huntingList = new ConcurrentHashMap<>();

    /**
     * 将追猎名单恢复到内存中
     * 持久化先不搞了
     */
    private void recoverHuntingList() {
        if (cacheRecovered) return;
        synchronized (MultiDialingBlocker.class) {
            if (cacheRecovered) return;

            // 根据配置删除超过限制时间的追猎记录
            // 加载追猎名单

            cacheRecovered = true;
        }
    }

    public record HuntingTarget (
            String hashSubnet,
            long createTime
    ){
    }
}


