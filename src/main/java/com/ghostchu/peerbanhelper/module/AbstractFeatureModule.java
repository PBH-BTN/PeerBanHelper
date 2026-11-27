package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelper;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.ghostchu.peerbanhelper.util.WebUtil;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import inet.ipaddr.IPAddress;
import inet.ipaddr.ipv4.IPv4Address;
import inet.ipaddr.ipv6.IPv6Address;
import io.javalin.http.Context;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
public abstract class AbstractFeatureModule implements FeatureModule {
    @Getter
    private final ReentrantLock lock = new ReentrantLock();
    @Getter
    @Autowired
    private PeerBanHelper server;
    @Getter
    private volatile boolean register;
    @Autowired
    JavalinWebContainer javalinWebContainer;
    private final List<ScheduledFuture<?>> scheduledTasks = new ArrayList<>();
    private PluginModuleScope pluginScope;

    void bindPluginScope(PluginModuleScope pluginScope) {
        this.pluginScope = pluginScope;
    }

    @Override
    public boolean shouldModuleEnabled() {
        try {
            return !isConfigurable() || getConfig().getBoolean("enabled");
        } catch (Exception e) {
            log.warn(tlUI(Lang.CONFIGURATION_OUTDATED_MODULE_DISABLED, getName()));
            return false;
        }
    }

    @Override
    public @NotNull ModuleStatus getModuleStatus() {
        return ModuleStatus.builder()
                .type(register ? ModuleStatusType.ENABLED : ModuleStatusType.DISABLED)
                .description(register ? new TranslationComponent(Lang.MODULE_STATUS_DESCRIPTION_ENABLED) : new TranslationComponent(Lang.MODULE_STATUS_DESCRIPTION_DISABLED))
                .build();
    }

    @Override
    public ReentrantLock getThreadLock() {
        return lock;
    }

    /**
     * 如果返回 false，则不初始化任何配置文件相关对象
     *
     * @return 是否支持使用配置文件进行配置
     */
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public ConfigurationSection getConfig() {
        if (!isConfigurable()) return null;
        ConfigurationSection section = Objects.requireNonNull(Main.getProfileConfig().getConfigurationSection("module")).getConfigurationSection(getConfigName());
        if (section == null) {
            log.warn(tlUI(Lang.CONFIGURATION_OUTDATED_MODULE_DISABLED, getName()));
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.set("enabled", false);
            return configuration;
        }
        return section;
    }

    @Override
    public void disable() {
        if (register) {
            try {
                onDisable();
            } finally {
                register = false;
                cancelAllScheduledTasks();
                try {
                    Main.getEventBus().unregister(this);
                } catch (IllegalArgumentException ignored) {
                    // Some modules do not register an event listener.
                }
            }
            log.info(tlUI(Lang.MODULE_UNREGISTER, getName()));
        }
    }


    /**
     * 注册一个定时任务，在模块禁用时会自动取消
     *
     * @param task         要执行的任务
     * @param initialDelay 初始延迟
     * @param period       执行间隔
     * @param unit         时间单位
     * @return ScheduledFuture 对象
     */
    protected ScheduledFuture<?> registerScheduledTask(Runnable task, long initialDelay, long period, TimeUnit unit) {
        var scope = pluginScope;
        Runnable callback = scope == null ? task : () -> scope.invoke(task);
        ScheduledFuture<?> future = CommonUtil.getScheduler().scheduleWithFixedDelay(callback, initialDelay, period, unit);
        synchronized (scheduledTasks) {
            scheduledTasks.add(future);
        }
        return future;
    }

    /**
     * 手动取消一个已注册的定时任务
     *
     * @param future 要取消的任务
     * @return 是否成功取消
     */
    protected boolean cancelScheduledTask(ScheduledFuture<?> future) {
        if (future != null && !future.isCancelled()) {
            boolean cancelled = future.cancel(false);
            synchronized (scheduledTasks) {
                scheduledTasks.remove(future);
            }
            return cancelled;
        }
        return false;
    }

    /**
     * 取消所有已注册的定时任务
     */
    protected void cancelAllScheduledTasks() {
        synchronized (scheduledTasks) {
            for (ScheduledFuture<?> task : scheduledTasks) {
                if (task != null && !task.isCancelled()) {
                    task.cancel(false);
                }
            }
            scheduledTasks.clear();
        }
    }

    @Override
    public void enable() {
        if (register) return;
        register = true;
        try {
            onEnable();
        } catch (RuntimeException | Error failure) {
            try {
                disable();
            } catch (RuntimeException | Error cleanup) {
                if (cleanup != failure) failure.addSuppressed(cleanup);
            }
            throw failure;
        }
        log.info(tlUI(Lang.MODULE_REGISTER, getName()));
    }

    @Override
    public void saveConfig() throws IOException {
        if (!isConfigurable()) return;
        Main.getProfileConfig().set("module." + getConfigName(), getConfig());
        Main.getProfileConfig().save(new File(Main.getConfigDirectory(), "profile.yml"));
    }

    public String locale(Context ctx) {
        return javalinWebContainer.reqLocale(ctx);
    }

    public TimeZone timezone(Context ctx) {
        var tz = TimeZone.getDefault();
        if (ctx.header("X-Timezone") != null) {
            tz = TimeZone.getTimeZone(ctx.header("X-Timezone"));
        }
        return tz;
    }

    public String userIp(Context context) {
        return WebUtil.userIp(context);
    }

}
