package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.push.PushManager;
import com.ghostchu.peerbanhelper.push.PushProvider;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.jetbrains.annotations.NotNull;
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
                .post("/api/push/{name}/test", this::handlePushTest, Role.USER_WRITE);
    }

    private void handlePushTest(Context ctx) {
        String name = ctx.pathParam("name");
        PushProvider provider = pushManager.getPushProvider(name);
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

    @Override
    public void onDisable() {

    }
}
