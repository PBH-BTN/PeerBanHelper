package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.web.Role;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import inet.ipaddr.IPAddress;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 同一网段集中下载同一个种子视为多拨，因为多拨和PCDN强相关所以可以直接封禁
 */
@Slf4j
public class MultiDialingBlocker extends AbstractRuleFeatureModule {
    // 计算缓存容量
    private static final int TORRENT_PEER_MAX_NUM = 1024;
    private static final int PEER_MAX_NUM_PER_SUBNET = 16;

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
    public void onEnable() {
        reloadConfig();
        getServer().getWebContainer().javalin()
                .get("/api/modules/" + getConfigName(), this::handleConfig, Role.USER_READ)
                .get("/api/modules/" + getConfigName() + "/status", this::handleStatus, Role.USER_READ);
    }

    private void handleStatus(Context ctx) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("huntingList", huntingList.asMap());
        status.put("cache", cache.asMap());
        Map<String, Map<String, Long>> mapSubnetCounter = new LinkedHashMap<>();
        subnetCounter.asMap().forEach((k, v) -> mapSubnetCounter.put(k, v.asMap()));
        status.put("subnetCounter", mapSubnetCounter);
        ctx.status(HttpStatus.OK);
        ctx.json(status);
    }

    private void handleConfig(Context ctx) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("subnetMaskLength", subnetMaskLength);
        config.put("subnetMaskV6Length", subnetMaskV6Length);
        config.put("tolerateNum", tolerateNum);
        config.put("cacheLifespan", cacheLifespan);
        config.put("keepHunting", keepHunting);
        config.put("keepHuntingTime", keepHuntingTime);
        ctx.status(HttpStatus.OK);
        ctx.json(config);
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
    public void onDisable() {

    }

    private void reloadConfig() {
        subnetMaskLength = getConfig().getInt("subnet-mask-length");
        subnetMaskV6Length = getConfig().getInt("subnet-mask-v6-length");
        tolerateNum = getConfig().getInt("tolerate-num");
        cacheLifespan = getConfig().getInt("cache-lifespan") * 1000L;
        keepHunting = getConfig().getBoolean("keep-hunting");
        keepHuntingTime = getConfig().getInt("keep-hunting-time") * 1000L;

        cache = CacheBuilder.newBuilder().
                expireAfterWrite(cacheLifespan, TimeUnit.MILLISECONDS).
                maximumSize(TORRENT_PEER_MAX_NUM).
                softValues().
                build();
        // 内层维护子网下的peer列表，外层回收不再使用的列表
        // 外层按最后访问时间过期即可，若子网的列表还在被访问，说明还有属于该子网的peer在连接
        subnetCounter = CacheBuilder.newBuilder().
                expireAfterAccess(cacheLifespan, TimeUnit.MILLISECONDS).
                maximumSize(TORRENT_PEER_MAX_NUM).
                softValues().
                build();
        huntingList = CacheBuilder.newBuilder().
                expireAfterWrite(keepHuntingTime, TimeUnit.MILLISECONDS).
                maximumSize(TORRENT_PEER_MAX_NUM).
                softValues().
                build();
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
            Cache<String, Long> subnetPeers = subnetCounter.get(torrentSubnetStr, this::genPeerGroup);
            subnetPeers.put(peerIpStr, currentTimestamp);

            if (subnetPeers.size() > tolerateNum) {
                // 落库
                huntingList.put(torrentSubnetStr, currentTimestamp);
                // 返回当前IP即可，其他IP会在下一周期被封禁
                return new BanResult(this, PeerAction.BAN, "Multi-dialing download detected",
                        String.format(Lang.MODULE_MDB_MULTI_DIALING_DETECTED,
                                peerSubnet, peerIpStr));
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
                                            peerSubnet, peerIpStr));
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
    private static Cache<String, Cache<String, Long>> subnetCounter;
    // 追猎名单 torrentId+subnet : createTime
    private static Cache<String, Long> huntingList;

    private Cache<String, Long> genPeerGroup() {
        return CacheBuilder.newBuilder().
                expireAfterAccess(cacheLifespan, TimeUnit.MILLISECONDS).
                maximumSize(PEER_MAX_NUM_PER_SUBNET).
                softValues().
                build();
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


