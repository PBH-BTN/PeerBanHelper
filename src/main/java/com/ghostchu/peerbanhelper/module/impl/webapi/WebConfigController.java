package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.ReloadableContainer;
import io.javalin.http.Context;
import org.bspfsystems.yamlconfiguration.configuration.InvalidConfigurationException;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@IgnoreScan
public class WebConfigController extends AbstractFeatureModule {
    private final JavalinWebContainer javalinWebContainer;

    public WebConfigController(JavalinWebContainer javalinWebContainer) {
        super();
        this.javalinWebContainer = javalinWebContainer;
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "Web Config Editor";
    }

    @Override
    public @NotNull String getConfigName() {
        return "web-config-editor";
    }

    @Override
    public void onEnable() {
        javalinWebContainer.javalin()
                .get("/api/config", this::listConfig, Role.USER_READ)
                .get("/api/config/{configId}", this::retrieveConfigContent, Role.USER_WRITE)
                .put("/api/config/{configId}", this::editConfig, Role.USER_WRITE);
    }

    private void editConfig(Context ctx) throws IOException {
        ConfigSaveRequest configSaveRequest = ctx.bodyAsClass(ConfigSaveRequest.class);
        var test = new YamlConfiguration();
        try {
            test.loadFromString(configSaveRequest.content());
        } catch (InvalidConfigurationException e) {
            ctx.json(new StdResp(false, "Invalid configuration: " + e.getMessage(), null));
            return;
        }
        switch (ctx.pathParam("configId")) {
            case "config.yml" -> {
                Files.writeString(Main.getMainConfigFile().toPath(), configSaveRequest.content(), StandardCharsets.UTF_8);
                ctx.json(new StdResp(true, "Configuration saved", processConfigReloading()));
            }
            case "profile.yml" -> {
                Files.writeString(Main.getProfileConfigFile().toPath(), configSaveRequest.content(), StandardCharsets.UTF_8);
                ctx.json(new StdResp(true, "Configuration saved", processConfigReloading()));
            }
            default -> {
                throw new IllegalArgumentException("Invalid configId");
            }
        }
    }

    private void retrieveConfigContent(Context ctx) throws IOException {
        switch (ctx.pathParam("configId")) {
            case "config.yml" -> {
                ctx.json(new StdResp(true, null, Files.readString(Main.getMainConfigFile().toPath(), StandardCharsets.UTF_8)));
            }
            case "profile.yml" -> {
                ctx.json(new StdResp(true, null, Files.readString(Main.getProfileConfigFile().toPath(), StandardCharsets.UTF_8)));
            }
            default -> {
                throw new IllegalArgumentException("Invalid configId");
            }
        }
    }


    private void listConfig(Context ctx) {
        ctx.json(new StdResp(true, null, new String[]{"config.yml", "profile.yml"}));
    }

    @Override
    public void onDisable() {

    }

    private List<ConfigReloadEntryContainer> processConfigReloading() {
        List<ConfigReloadEntryContainer> list = new ArrayList<>();
        for (Map.Entry<ReloadableContainer, ReloadResult> entry : Main.reloadConfig().entrySet()) {
            String name = "???";
            var ref = entry.getKey().getReloadable();
            if (ref != null) {
                var obj = ref.get();
                if (obj != null) {
                    name = obj.getClass().getName();
                }
            } else {
                if (entry.getKey().getReloadableMethod() != null) {
                    name = entry.getKey().getReloadableMethod().getName();
                }
            }
            list.add(new ConfigReloadEntryContainer(
                    name,
                    entry.getValue().getStatus().name(),
                    entry.getValue().getReason(),
                    entry.getValue().getException() == null ? null : entry.getValue().getException().getMessage()
            ));
        }
        return list;
    }

    record ConfigReloadEntryContainer(
            String name,
            String result,
            String reason,
            String errorMsg
    ) {

    }

    record ConfigSaveRequest(String content) {

    }
}
