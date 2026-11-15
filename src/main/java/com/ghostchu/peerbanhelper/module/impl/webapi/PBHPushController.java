package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.alert.AlertLevel;
import com.ghostchu.peerbanhelper.alert.NotificationManager;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.PushWrapperDTO;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.push.PushManagerImpl;
import com.ghostchu.peerbanhelper.util.push.PushProvider;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Component
@Slf4j
public final class PBHPushController extends AbstractFeatureModule {
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private PushManagerImpl pushManager;
    @Autowired
    private NotificationManager notifications;

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
        webContainer.routes()
                .get("/api/notifications/settings", ctx -> ctx.json(new StdResp(true, null, notifications.settings())), Role.USER_READ)
                .put("/api/notifications/routes/{category}", this::handleNotificationRule, Role.USER_WRITE)
                .get("/api/push", this::handlePushProviderList, Role.USER_READ)
                .put("/api/push", this::handlePushProviderPut, Role.USER_WRITE)
                .patch("/api/push/{pushName}", ctx -> handlePushProviderPatch(ctx, ctx.pathParam("pushName")), Role.USER_WRITE)
                .post("/api/push/test", this::handlePushProviderTest, Role.USER_WRITE)
                .delete("/api/push/{pushName}", ctx -> handlePushProviderDelete(ctx, ctx.pathParam("pushName")), Role.USER_WRITE);
    }


    private void handleNotificationRule(Context ctx) {
        try {
            var body = JsonParser.parseString(ctx.body()).getAsJsonObject();
            var useDefault = body.get("useDefault");
            var selected = body.get("channelIds");
            if (useDefault == null || !useDefault.isJsonPrimitive() || !useDefault.getAsJsonPrimitive().isBoolean()
                    || selected == null || !selected.isJsonArray()) {
                throw new IllegalArgumentException("Invalid notification rule");
            }
            var ids = new HashSet<String>();
            for (var element : selected.getAsJsonArray()) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("Invalid notification channel ID");
                }
                ids.add(element.getAsString());
            }
            notifications.saveRule(ctx.pathParam("category"), useDefault.getAsBoolean(), ids);
            ctx.json(new StdResp(true, null, null));
        } catch (IllegalArgumentException | IllegalStateException | com.google.gson.JsonParseException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(new StdResp(false, e.getMessage(), null));
        } catch (IOException e) {
            log.error("Unable to save notification rule", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(new StdResp(false,
                    tl(locale(ctx), Lang.PUSH_PROVIDER_API_CREATION_FAILED_IO_EXCEPTION), null));
        }
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
        saveProvider(ctx, null, pushProvider);
    }

    private void handlePushProviderPatch(Context ctx, String pushProviderName) {
        JsonObject draft = JsonParser.parseString(ctx.body()).getAsJsonObject();
        String name = draft.get("name").getAsString();
        if (name.contains(".")) {
            throw new IllegalArgumentException("Illegal character (.) in name: " + name);
        }
        PushProvider provider = pushManager.createPushProvider(name, draft.get("type").getAsString(),
                draft.getAsJsonObject("config"));
        if (provider == null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.PUSH_PROVIDER_API_UPDATE_FAILURE), null));
            return;
        }
        saveProvider(ctx, pushProviderName, provider);
    }

    private void saveProvider(Context ctx, String previousName, PushProvider provider) {
        boolean creating = previousName == null;
        try {
            if (!pushManager.updateProvider(previousName, provider)) {
                ctx.status(HttpStatus.BAD_REQUEST);
                ctx.json(new StdResp(false, tl(locale(ctx), creating
                        ? Lang.PUSH_PROVIDER_API_CREATION_FAILED_ALREADY_EXISTS
                        : Lang.PUSH_PROVIDER_API_UPDATE_FAILURE_ALREADY_EXISTS), null));
                return;
            }
            ctx.status(creating ? HttpStatus.CREATED : HttpStatus.OK);
            ctx.json(new StdResp(true, tl(locale(ctx), creating
                    ? Lang.PUSH_PROVIDER_API_CREATED : Lang.PUSH_PROVIDER_API_UPDATED), null));
        } catch (IOException e) {
            log.error("Unable to save push provider", e);
            Sentry.captureException(e);
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
            var testResult = pushProvider.push(tl(locale(ctx), Lang.PUSH_PROVIDER_TEST_TITLE), tl(locale(ctx), Lang.PUSH_PROVIDER_TEST_DESCRIPTION, name, pushProvider.getConfigType()), AlertLevel.INFO);
            if (testResult) {
                ctx.json(new StdResp(true, tl(locale(ctx), Lang.PUSH_PROVIDER_API_TEST_OK), null));
            } else {
                ctx.json(new StdResp(false, tl(locale(ctx), Lang.PUSH_PROVIDER_TEST_FAILED, name, pushProvider.getConfigType()), null));
            }
        } catch (Exception e) {
            log.error("Validate PushProvider failed", e);
            Sentry.captureException(e);
            ctx.json(new StdResp(false, e.getMessage(), null));
        }
    }

    private void handlePushProviderDelete(Context ctx, String pushProviderName) {
        try {
            if (!pushManager.deleteProvider(pushProviderName)) {
                ctx.status(HttpStatus.NOT_FOUND);
                ctx.json(new StdResp(false, tl(locale(ctx), Lang.PUSH_PROVIDER_API_REMOVE_NOT_EXISTS), null));
                return;
            }
            ctx.json(new StdResp(true, tl(locale(ctx), Lang.PUSH_PROVIDER_API_REMOVE_SAVED), null));
        } catch (IOException e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            Sentry.captureException(e);
            ctx.json(new StdResp(false, e.getClass().getName() + ": " + e.getMessage(), null));
        }
    }

    private void handlePushProviderList(@NotNull Context ctx) {
        List<PushWrapperDTO> pushProviders = pushManager.getProviderList()
                .stream().map(d -> new PushWrapperDTO(d.getName(), d.getConfigType(), d.saveJson()))
                .toList();
        ctx.json(new StdResp(true, null, pushProviders));
    }


    @Override
    public void onDisable() {

    }

}
