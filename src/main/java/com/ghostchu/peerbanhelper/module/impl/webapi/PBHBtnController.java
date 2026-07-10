package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.ability.BtnAbility;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.ModuleStatus;
import com.ghostchu.peerbanhelper.module.ModuleStatusType;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import io.javalin.openapi.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Component
public class PBHBtnController extends AbstractFeatureModule {
    @Autowired
    private JavalinWebContainer javalinWebContainer;
    @Autowired(required = false)
    private BtnNetwork btnNetwork;

    @Override
    public @NotNull String getName() {
        return "[WebAPI] BTN Network";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-btn";
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull ModuleStatus getModuleStatus() {
        return ModuleStatus.builder()
                .type(btnNetwork != null ? ModuleStatusType.ENABLED : ModuleStatusType.DISABLED)
                .description(btnNetwork != null ? new TranslationComponent(Lang.MODULE_STATUS_DESCRIPTION_ENABLED) : new TranslationComponent(Lang.MODULE_STATUS_DESCRIPTION_DISABLED))
                .build();
    }

    @Override
    public void onEnable() {
        javalinWebContainer.routes()
                .get("/api/modules/btn", this::status, Role.USER_READ);
        Main.getEventBus().register(this);
    }

    @OpenApi(
            path = "/api/modules/btn",
            methods = HttpMethod.GET,
            summary = "获取 BTN 网络状态",
            description = "获取 BTN 网络模块的启用状态和配置信息",
            tags = {"BTN 网络"},
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "status"
    )
    private void status(Context context) {
        Map<String, Object> info = new HashMap<>();
        info.put("enabled", btnNetwork != null);
        if (btnNetwork == null) {
            info.put("configSuccess", false);
            info.put("appId", "N/A");
            info.put("appSecret", "N/A");
            info.put("abilities", Collections.emptyList());
            info.put("configUrl", tl(locale(context), Lang.BTN_SERVICES_NEED_RESTART));
            context.json(new StdResp(false, tl(locale(context), Lang.BTN_NOT_ENABLE_AND_REQUIRE_RESTART), null));
            return;
        }

        info.put("configSuccess", btnNetwork.getConfigSuccess());
        info.put("configResult", btnNetwork.getConfigResult() == null ? null : tl(locale(context), btnNetwork.getConfigResult()));
        var abilities = new ArrayList<>();
        for (Map.Entry<Class<? extends BtnAbility>, BtnAbility> entry : btnNetwork.getAbilities().entrySet()) {
            Map<String, Object> abilityStatus = new HashMap<>();
            abilityStatus.put("name", entry.getValue().getName());
            abilityStatus.put("displayName", tl(locale(context), entry.getValue().getDisplayName()));
            abilityStatus.put("description", tl(locale(context), entry.getValue().getDescription()));
            abilityStatus.put("lastSuccess", entry.getValue().lastStatus());
            abilityStatus.put("lastMessage", tl(locale(context), entry.getValue().lastMessage()));
            abilityStatus.put("lastUpdateAt", entry.getValue().lastStatusAt());
            abilities.add(abilityStatus);
        }
        info.put("abilities", abilities);
        info.put("appId", btnNetwork.getAppId());
        String appSecret;
        if (btnNetwork.getAppSecret().length() > 5) {
            appSecret = btnNetwork.getAppSecret().substring(0, 5) + "*******";
        } else {
            appSecret = "******";
        }
        info.put("appSecret", appSecret);
        info.put("configUrl", btnNetwork.getConfigUrl());
        context.json(new StdResp(true, null, info));
    }

    @Override
    public void onDisable() {
    }
}
