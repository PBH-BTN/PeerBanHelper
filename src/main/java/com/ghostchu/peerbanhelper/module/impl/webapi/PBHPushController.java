package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.push.PushManager;
import com.ghostchu.peerbanhelper.push.PushProvider;
import com.ghostchu.peerbanhelper.push.impl.PushPlusPushProvider;
import com.ghostchu.peerbanhelper.push.impl.ServerChanPushProvider;
import com.ghostchu.peerbanhelper.push.impl.SmtpPushProvider;
import com.ghostchu.peerbanhelper.push.impl.TelegramPushProvider;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Component
@IgnoreScan
public class PBHPushController extends AbstractFeatureModule {
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private PushManager pushManager;

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - Push";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-push";
    }

    @Override
    public void onEnable() {
        webContainer.javalin()
                .get("/api/push/list", this::handlePushList, Role.USER_READ)
                .post("/api/push/{name}/test", this::handlePushTest, Role.USER_WRITE);
    }

    private void handlePushList(@NotNull Context context) {
        for (PushProvider pushProvider : pushManager.getProviderList()) {

        }
    }

    private void handlePushTest(Context ctx) {
        String name = ctx.pathParam("name");
        PushProvider provider = getPushProvider(name);
        if (provider == null) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        String providerType = provider.getClass().getSimpleName();
        try {
            if (provider.push(tl(locale(ctx), Lang.PUSH_PROVIDER_TEST_TITLE), tl(locale(ctx), Lang.PUSH_PROVIDER_TEST_DESCRIPTION, name, providerType))) {
                ctx.json(new StdResp(true, tl(locale(ctx), Lang.PUSH_PROVIDER_TEST_SUCCESS, name, providerType), null));
            } else {
                ctx.json(new StdResp(false, tl(locale(ctx), Lang.PUSH_PROVIDER_TEST_FAILED, name, providerType), null));
            }
        } catch (Exception e) {
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.PUSH_PROVIDER_TEST_FAILED, name, providerType, e), null));
        }
    }

    @Nullable
    private PushProvider getPushProvider(String name) {
        if (name == null) {
            return null;
        }
        var config = Main.getMainConfig().getConfigurationSection("push-notification");
        if (config == null) {
            return null;
        }
        var section = config.getConfigurationSection(name);
        if (section == null) {
            return null;
        }
        String type = section.getString("type");
        if (type == null) {
            return null;
        }
        switch (type) {
            case "smtp" -> {
                return SmtpPushProvider.loadFromYaml(section);
            }
            case "pushplus" -> {
                return PushPlusPushProvider.loadFromYaml(section);
            }
            case "serverchan" -> {
                return ServerChanPushProvider.loadFromYaml(section);
            }
            case "telegram" -> {
                return TelegramPushProvider.loadFromYaml(section);
            }
        }
        return null;
    }

    @Override
    public void onDisable() {

    }
}
