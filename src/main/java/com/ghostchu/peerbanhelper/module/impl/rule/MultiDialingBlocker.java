package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import inet.ipaddr.IPAddress;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 同一网段集中下载同一个种子视为多拨，因为多拨和PCDN强相关所以可以直接封禁
 */
@Slf4j
public class MultiDialingBlocker extends AbstractRuleFeatureModule {

    private int subnetMaskLength;
    private int subnetMaskV6Length;
    private int tolerateNum;
    private long cacheLifespan;
    private boolean keepHunting;
    private long keepHuntingTime;

    public MultiDialingBlocker(PeerBanHelperServer server, YamlConfiguration profile) {
        super(server, profile);
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
    public boolean isCheckCacheable() {
        return false;
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
        subnetMaskV6Length = getConfig().getInt("subnet-mask-v6-length");
        tolerateNum = getConfig().getInt("tolerate-num");
        cacheLifespan = getConfig().getInt("cache-lifespan") * 1000L;
        keepHunting = getConfig().getBoolean("keep-hunting");
        keepHuntingTime = getConfig().getInt("keep-hunting-time") * 1000L;

        cache = CacheBuilder.newBuilder().expireAfterWrite(cacheLifespan, TimeUnit.MILLISECONDS).build();
        // 内层需要自己维护，外层交给Cache，回收不再使用的PeerGroup
        subnetCounter = CacheBuilder.newBuilder().expireAfterAccess(cacheLifespan, TimeUnit.MILLISECONDS).build();
        huntingList = CacheBuilder.newBuilder().expireAfterWrite(keepHuntingTime, TimeUnit.MILLISECONDS).build();
    }

    @Override
    public @NotNull BanResult shouldBanPeer(
            @NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        String torrentName = torrent.getName();
        String torrentId = torrent.getId();
        IPAddress peerAddress = peer.getAddress().getAddress();
        String peerIpStr = peerAddress.toString();
        IPAddress peerSubnet = peerAddress.isIPv4() ?
                peerAddress.toPrefixBlock(subnetMaskLength) : peerAddress.toPrefixBlock(subnetMaskV6Length);

        try {
            long currentTimestamp = System.currentTimeMillis();

            String torrentIpStr = torrentId + '@' + peerIpStr;
            cache.put(torrentIpStr, currentTimestamp);

            String torrentSubnetStr = torrentId + '@' + peerSubnet;
            PeerGroup subnetPeers = subnetCounter.get(torrentSubnetStr, () -> new PeerGroup(cacheLifespan));
            subnetPeers.put(peerIpStr, currentTimestamp);

            if (subnetPeers.size() > tolerateNum) {
                // 落库
                huntingList.put(torrentSubnetStr, currentTimestamp);
                // 返回当前IP即可，其他IP会在下一周期被封禁
                return new BanResult(this, PeerAction.BAN, "Multi-dialing download detected",
                        String.format(Lang.MODULE_MDB_MULTI_DIALING_DETECTED,
                                torrentName, peerSubnet, peerIpStr));
            }

            if (keepHunting) {
                recoverHuntingList();
                try {
                    long huntingTimestamp = huntingList.get(torrentSubnetStr, () -> 0L);
                    if (huntingTimestamp > 0) {
                        if (currentTimestamp - huntingTimestamp < keepHuntingTime) {
                            // 落库
                            huntingList.put(torrentSubnetStr, currentTimestamp);
                            return new BanResult(this, PeerAction.BAN, "Multi-dialing hunting",
                                    String.format(Lang.MODULE_MDB_MULTI_DIALING_HUNTING_TRIGGERED,
                                            torrentName, peerSubnet, peerIpStr));
                        }
                        else {
                            huntingList.invalidate(torrentSubnetStr);
                        }
                    }
                } catch (ExecutionException ignored) {}
            }
        }
        catch (Exception e) {
            log.error("shouldBanPeer exception", e);
        }

        return new BanResult(this, PeerAction.NO_ACTION, "N/A",
                String.format(Lang.MODULE_MDB_MULTI_DIALING_NOT_DETECTED, torrentName));
    }

    // 是否已从数据库恢复追猎名单，持久化用的，目前没用
    private static volatile boolean cacheRecovered = false;
    // 所有peer的连接记录 torrentId+ip : createTime
    private static Cache<String, Long> cache;
    // 按子网统计的连接记录 torrentId+subnet : peerGroup
    // 需要统计同一子网下的peer数量，Cache不支持size()，所以需要自己维护
    private static Cache<String, PeerGroup> subnetCounter;
    // 追猎名单 torrentId+subnet : createTime
    private static Cache<String, Long> huntingList;

    /**
     * 维护一组带寿命的peer，提供其准确数量
     */
    private static class PeerGroup extends ConcurrentHashMap<String, Long> {
        // 5分钟清理一次，降低消耗
        private static final long cleanCycle = 300000L;

        public volatile long cleanTime = 0L;
        private final long peerLifespan;

        public PeerGroup(long peerLifespan) {
            this.peerLifespan = peerLifespan;
        }

        @Override
        public int size() {
            clean();
            return super.size();
        }

        private void clean() {
            long currentTime = System.currentTimeMillis();
            if (currentTime > cleanTime) {
                synchronized (this) {
                    if (currentTime > cleanTime) {
                        long passLine = currentTime - peerLifespan;
                        entrySet().removeIf(x -> x.getValue() < passLine);
                        cleanTime = currentTime + cleanCycle;
                    }
                }
            }
        }
    }

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


