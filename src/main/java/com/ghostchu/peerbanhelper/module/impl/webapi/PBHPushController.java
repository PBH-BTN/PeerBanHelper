package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.push.PushManager;
import com.ghostchu.peerbanhelper.push.PushProvider;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Component
@IgnoreScan
@Slf4j
public final class PBHPushController extends AbstractFeatureModule {
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
                .get("/api/push", this::handlePushProviderList, Role.USER_READ)
                .put("/api/push", this::handlePushProviderPut, Role.USER_WRITE)
                .patch("/api/push/{pushName}", ctx -> handlePushProviderPatch(ctx, ctx.pathParam("pushName")), Role.USER_WRITE)
                .post("/api/push/test", this::handlePushProviderTest, Role.USER_WRITE)
                .delete("/api/push/{pushName}", ctx -> handlePushProviderDelete(ctx, ctx.pathParam("pushName")), Role.USER_WRITE);
    }


    private void handlePushProviderPut(Context ctx) {
        JsonObject draftPushProvider = JsonParser.parseString(ctx.body()).getAsJsonObject();
        String name = draftPushProvider.get("name").getAsString();
        if (name.contains(".")) {
            throw new IllegalArgumentException("Illegal character (.) in name: " + name);
        }
        JsonObject config = draftPushProvider.get("config").getAsJsonObject();
        PushProvider pushProvider = pushManager.createPushProvider(name, draftPushProvider.get("type").getAsString(), config);
        if (pushProvider == null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.PUSH_PROVIDER_API_ADD_FAILURE), null));
            return;
        }
        if (pushManager.addPushProvider(pushProvider)) {
            ctx.status(HttpStatus.CREATED);
            ctx.json(new StdResp(true, tl(locale(ctx), Lang.PUSH_PROVIDER_API_CREATED), null));
        } else {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.PUSH_PROVIDER_API_CREATION_FAILED_ALREADY_EXISTS), null));
        }
        try {
            pushManager.savePushProviders();
        } catch (IOException e) {
            log.error("Internal server error, unable to create push provider due an I/O exception", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.PUSH_PROVIDER_API_CREATION_FAILED_IO_EXCEPTION), null));
        }
    }

    private void handlePushProviderPatch(Context ctx, String pushProviderName) {
        JsonObject draftProvider = JsonParser.parseString(ctx.body()).getAsJsonObject();
        String name = draftProvider.get("name").getAsString();
        if (name.contains(".")) {
            throw new IllegalArgumentException("Illegal character (.) in name: " + name);
        }
        JsonObject config = draftProvider.get("config").getAsJsonObject();
        PushProvider pushProvider = pushManager.createPushProvider(name, draftProvider.get("type").getAsString(), config);
        if (pushProvider == null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.PUSH_PROVIDER_API_UPDATE_FAILURE), null));
            return;
        }
        // 可能重命名了？
        List.copyOf(pushManager.getProviderList()).stream() // Copy 一下来避免并发修改错误
                .filter(d -> d.getName().equals(pushProviderName))
                .forEach(d -> pushManager.removePushProvider(d));
        if (pushManager.addPushProvider(pushProvider)) {
            ctx.json(new StdResp(true, tl(locale(ctx), Lang.PUSH_PROVIDER_API_UPDATED), null));
        } else {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.PUSH_PROVIDER_API_UPDATE_FAILURE_ALREADY_EXISTS), null));
        }
        try {
            pushManager.savePushProviders();
        } catch (IOException e) {
            log.error("Internal server error, unable to update push manager due an I/O exception", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.PUSH_PROVIDER_API_CREATION_FAILED_IO_EXCEPTION), null));
        }
    }

    private void handlePushProviderTest(Context ctx) {
        JsonObject draftPushProvider = JsonParser.parseString(ctx.body()).getAsJsonObject();
        String name = draftPushProvider.get("name").getAsString();
        if (name.contains(".")) {
            throw new IllegalArgumentException("Illegal character (.) in name: " + name);
        }
        JsonObject config = draftPushProvider.get("config").getAsJsonObject();
        PushProvider pushProvider = pushManager.createPushProvider(name, draftPushProvider.get("type").getAsString(), config);
        if (pushProvider == null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.PUSH_PROVIDER_API_ADD_FAILURE), null));
            return;
        }
        try {
            var testResult = pushProvider.push(tl(locale(ctx), Lang.PUSH_PROVIDER_TEST_TITLE), tl(locale(ctx), Lang.PUSH_PROVIDER_TEST_DESCRIPTION, name, pushProvider.getConfigType()));
            if (testResult) {
                ctx.json(new StdResp(true, tl(locale(ctx), Lang.PUSH_PROVIDER_API_TEST_OK), null));
            } else {
                ctx.json(new StdResp(false, tl(locale(ctx), Lang.PUSH_PROVIDER_TEST_FAILED, name, pushProvider.getConfigType()), null));
            }
        } catch (Exception e) {
            log.error("Validate PushProvider failed", e);
            ctx.json(new StdResp(false, e.getMessage(), null));
        }
    }

    private void handlePushProviderDelete(Context ctx, String pushProviderName) {
        Optional<PushProvider> selected = pushManager.getProviderList().stream().filter(d -> d.getName().equals(pushProviderName)).findFirst();
        if (selected.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.PUSH_PROVIDER_API_REMOVE_NOT_EXISTS), null));
            return;
        }
        PushProvider pushProvider = selected.get();
        pushManager.removePushProvider(pushProvider);
        try {
            pushManager.savePushProviders();
            ctx.json(new StdResp(true, tl(locale(ctx), Lang.PUSH_PROVIDER_API_REMOVE_SAVED), null));
        } catch (IOException e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(new StdResp(false, e.getClass().getName() + ": " + e.getMessage(), null));
        }
    }

    private void handlePushProviderList(@NotNull Context ctx) {
        List<PushWrapper> pushProviders = pushManager.getProviderList()
                .stream().map(d -> new PushWrapper(d.getName(), d.getConfigType(), d.saveJson()))
                .toList();
        ctx.json(new StdResp(true, null, pushProviders));
    }


    @Override
    public void onDisable() {

    }

    record PushWrapper(String name, String type, JsonObject config) {
    }
}
