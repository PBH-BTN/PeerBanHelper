package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import inet.ipaddr.IPAddress;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 同一网段集中下载同一个种子视为多拨，因为多拨和PCDN强相关所以可以直接封禁
 */
@Slf4j
@Component
@IgnoreScan
public final class MultiDialingBlocker extends AbstractRuleFeatureModule implements Reloadable {
    // 计算缓存容量
    private static final int TORRENT_PEER_MAX_NUM = 1024;
    private static final int PEER_MAX_NUM_PER_SUBNET = 16;

    private int subnetMaskLength;
    private int subnetMaskV6Length;
    private long cacheLifespan;
    private boolean keepHunting;
    private long keepHuntingTime;
    @Autowired
    private JavalinWebContainer webContainer;
    private long banDuration;
    private int tolerateNumV4;
    private int tolerateNumV6;

    @Override
    public void onEnable() {
        reloadConfig();
        webContainer.javalin()
                .get("/api/modules/" + getConfigName(), this::handleConfig, Role.USER_READ)
                .get("/api/modules/" + getConfigName() + "/status", this::handleStatus, Role.USER_READ);
        Main.getReloadManager().register(this);
    }

    @Override
    public void onDisable() {
        Main.getReloadManager().unregister(this);
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        reloadConfig();
        return Reloadable.super.reloadModule();
    }

    private void handleStatus(Context ctx) {
        Map<String, Object> status = new HashMap<>();
        status.put("huntingList", huntingList.asMap());
        status.put("cache", cache.asMap());
        Map<String, Map<String, Long>> mapSubnetCounter = new HashMap<>();
        subnetCounter.asMap().forEach((k, v) -> mapSubnetCounter.put(k, v.asMap()));
        status.put("subnetCounter", mapSubnetCounter);
        ctx.json(new StdResp(true, null, status));
    }

    private void handleConfig(Context ctx) {
        Map<String, Object> config = new HashMap<>();
        config.put("subnetMaskLength", subnetMaskLength);
        config.put("subnetMaskV6Length", subnetMaskV6Length);
        config.put("tolerateNumV4", tolerateNumV4);
        config.put("tolerateNumV5", tolerateNumV6);
        config.put("cacheLifespan", cacheLifespan);
        config.put("keepHunting", keepHunting);
        config.put("keepHuntingTime", keepHuntingTime);
        ctx.json(new StdResp(true, null, config));
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
    public boolean isConfigurable() {
        return true;
    }

    private void reloadConfig() {
        this.banDuration = getConfig().getLong("ban-duration", 0);
        subnetMaskLength = getConfig().getInt("subnet-mask-length");
        subnetMaskV6Length = getConfig().getInt("subnet-mask-v6-length");
        tolerateNumV4 = getConfig().getInt("tolerate-num-ipv4");
        tolerateNumV6 = getConfig().getInt("tolerate-num-ipv6");
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
        getCache().invalidateAll();
    }

    @Override
    public @NotNull CheckResult shouldBanPeer(
            @NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader, @NotNull ExecutorService ruleExecuteExecutor) {
        if (isHandShaking(peer)) {
            return handshaking();
        }
        String torrentName = torrent.getName();
        String torrentId = torrent.getId();
        IPAddress peerAddress = peer.getPeerAddress().getAddress();
        String peerIpStr = peerAddress.toString();
        IPAddress peerSubnet = peerAddress.isIPv4() ? IPAddressUtil.toPrefixBlock(peerAddress, subnetMaskLength) : IPAddressUtil.toPrefixBlock(peerAddress, subnetMaskV6Length);
        try {
            long currentTimestamp = System.currentTimeMillis();

            String torrentIpStr = torrentId + '@' + peerIpStr;
            cache.put(torrentIpStr, currentTimestamp);

            String torrentSubnetStr = torrentId + '@' + peerSubnet;
            Cache<String, Long> subnetPeers = subnetCounter.get(torrentSubnetStr, this::genPeerGroup);
            subnetPeers.put(peerIpStr, currentTimestamp);
            int tolerateNum = Integer.MAX_VALUE;
            if (peerSubnet.isIPv4()) {
                tolerateNum = tolerateNumV4;
            }
            if (peerSubnet.isIPv6()) {
                tolerateNum = tolerateNumV6;
            }
            if (subnetPeers.size() > tolerateNum) {
                // 落库
                huntingList.put(torrentSubnetStr, currentTimestamp);
                // 返回当前IP即可，其他IP会在下一周期被封禁
                return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.MDB_MULTI_DIALING_DETECTED),
                        new TranslationComponent(Lang.MODULE_MDB_MULTI_DIALING_DETECTED, peerSubnet.toString(), peerIpStr));
            }

            if (keepHunting) {
                recoverHuntingList();
                try {
                    long huntingTimestamp = huntingList.get(torrentSubnetStr, () -> 0L);
                    if (huntingTimestamp > 0) {
                        if (currentTimestamp - huntingTimestamp < keepHuntingTime) {
                            // 落库
                            huntingList.put(torrentSubnetStr, currentTimestamp);
                            return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.MDB_MULTI_HUNTING),
                                    new TranslationComponent(Lang.MODULE_MDB_MULTI_DIALING_HUNTING_TRIGGERED, peerSubnet.toString(), peerIpStr));
                        } else {
                            huntingList.invalidate(torrentSubnetStr);
                        }
                    }
                } catch (ExecutionException ignored) {
                }
            }
        } catch (Exception e) {
            log.error("shouldBanPeer exception", e);
        }

        return pass();
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

    public record HuntingTarget(
            String hashSubnet,
            long createTime
    ) {
    }
}


