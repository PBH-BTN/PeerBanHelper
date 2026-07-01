package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.configuration.pf4j.PBHPlugin;
import com.ghostchu.peerbanhelper.configuration.pf4j.menu.PBHPluginMenu;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.pf4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Slf4j
@Component
public class PBHPluginController extends AbstractFeatureModule {
    @Autowired
    private PluginManager pluginManager;
    @Autowired
    private JavalinWebContainer javalinWebContainer;

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebUI - Plugin System";
    }

    @Override
    public @NotNull String getConfigName() {
        return "plugin-system";
    }

    @Override
    public void onEnable() {
        javalinWebContainer.routes()
                .get("/api/plugins", this::listPlugins, Role.USER_READ)
                .post("/api/plugins/operate", this::operatePlugins, Role.USER_WRITE);
    }

    private void operatePlugins(@NotNull Context context) {
        OperatePluginDTO dto = context.bodyAsClass(OperatePluginDTO.class);
        List<PluginOperateResultDTO> result = new ArrayList<>();
        switch (dto.getAction()) {
            case "start" -> dto.getIdentifiers().forEach(id -> {
                try {
                    var value = pluginManager.startPlugin(id);
                    result.add(new PluginOperateResultDTO(id, value, tl(locale(context), Lang.WEBAPI_PLUGIN_OPERATION_COMPLETED)));
                } catch (Exception e) {
                    result.add(new PluginOperateResultDTO(id, null, MiscUtil.throwableToString(e)));
                }
            });
            case "stop" -> dto.getIdentifiers().forEach(id -> {
                try {
                    var value = pluginManager.stopPlugin(id);
                    result.add(new PluginOperateResultDTO(id, value, tl(locale(context), Lang.WEBAPI_PLUGIN_OPERATION_COMPLETED)));
                } catch (Exception e) {
                    result.add(new PluginOperateResultDTO(id, null, MiscUtil.throwableToString(e)));
                }
            });
            case "enable" -> dto.getIdentifiers().forEach(id -> {
                try {
                    var value = pluginManager.enablePlugin(id);
                    result.add(new PluginOperateResultDTO(id, value, tl(locale(context), Lang.WEBAPI_PLUGIN_OPERATION_COMPLETED)));
                } catch (Exception e) {
                    result.add(new PluginOperateResultDTO(id, null, MiscUtil.throwableToString(e)));
                }
            });
            case "disable" -> dto.getIdentifiers().forEach(id -> {
                try {
                    var value = pluginManager.disablePlugin(id);
                    result.add(new PluginOperateResultDTO(id, value, tl(locale(context), Lang.WEBAPI_PLUGIN_OPERATION_COMPLETED)));
                } catch (Exception e) {
                    result.add(new PluginOperateResultDTO(id, null, MiscUtil.throwableToString(e)));
                }
            });
            case "load" -> dto.getIdentifiers().forEach(path -> {
                try {
                    var pathObj = Paths.get(path);
                    if (pathObj.normalize().startsWith(Main.getPluginDirectory().toPath().normalize())) {
                        result.add(new PluginOperateResultDTO(path, null, tl(locale(context), Lang.WEBAPI_PLUGIN_LOAD_FROM_UNSAFE_LOCATION)));
                        return;
                    }
                    var value = pluginManager.loadPlugin(pathObj);
                    result.add(new PluginOperateResultDTO(path, value, tl(locale(context), Lang.WEBAPI_PLUGIN_OPERATION_COMPLETED)));
                } catch (Exception e) {
                    result.add(new PluginOperateResultDTO(path, null, MiscUtil.throwableToString(e)));
                }
            });
            case "unload" -> dto.getIdentifiers().forEach(id -> {
                try {
                    var value = pluginManager.unloadPlugin(id);
                    result.add(new PluginOperateResultDTO(id, value, tl(locale(context), Lang.WEBAPI_PLUGIN_OPERATION_COMPLETED)));
                } catch (Exception e) {
                    result.add(new PluginOperateResultDTO(id, null, MiscUtil.throwableToString(e)));
                }
            });
            case "delete" -> dto.getIdentifiers().forEach(id -> {
                try {
                    var value = pluginManager.deletePlugin(id);
                    result.add(new PluginOperateResultDTO(id, value, tl(locale(context), Lang.WEBAPI_PLUGIN_OPERATION_COMPLETED)));
                } catch (Exception e) {
                    result.add(new PluginOperateResultDTO(id, null, MiscUtil.throwableToString(e)));
                }
            });
            default -> throw new IllegalArgumentException("OperatePluginDTO not supported.");
        }
        context.json(new StdResp(true, null, result));
    }

    private void listPlugins(@NotNull Context context) {
        context.json(new StdResp(true, null, pluginManager.getPlugins().stream().map(pluginWrapper -> new PluginDTO(locale(context), pluginWrapper)).toList()));
    }

    @Override
    public void onDisable() {

    }


    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class PluginOperateResultDTO {
        private String pluginId;
        private Object value;
        private String message;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class OperatePluginDTO {
        private String action;
        private List<String> identifiers;
    }

    @AllArgsConstructor
    @Data
    public static class PluginDTO {
        private String id;
        private String displayName;
        private String description;
        private String version;
        private String path;
        private PluginState state;
        private RuntimeMode runtimeMode;
        private String className;
        private String license;
        private String provider;
        private String requires;
        private List<PluginDependencyDTO> dependencies = new ArrayList<>();
        private List<PBHPluginMenuDTO> menus = new ArrayList<>();
        private String errorMessage;

        public PluginDTO(String locale, PluginWrapper plugin) {
            this.id = plugin.getPluginId();
            this.displayName = plugin.getPluginId();
            this.state = plugin.getPluginState();
            this.runtimeMode = plugin.getRuntimeMode();
            this.path = plugin.getPluginPath().toString();
            if (plugin.getFailedException() != null) {
                // 打印异常堆栈跟踪到内存文本
                this.errorMessage = MiscUtil.throwableToString(plugin.getFailedException());
            }
            PluginDescriptor descriptor = plugin.getDescriptor();
            this.version = descriptor.getVersion();
            this.description = descriptor.getPluginDescription();
            this.className = descriptor.getPluginClass();
            this.license = descriptor.getLicense();
            this.provider = descriptor.getProvider();
            this.requires = descriptor.getRequires();
            for (PluginDependency dependency : descriptor.getDependencies()) {
                this.dependencies.add(new PluginDependencyDTO(dependency));
            }
            Plugin pluginInstance = plugin.getPlugin();
            if (pluginInstance instanceof PBHPlugin pplug) {
                this.displayName = tl(locale, pplug.getPluginDisplayName());
                var desc = pplug.getPluginDescription();
                if (desc != null) {
                    this.description = tl(locale, pplug.getPluginDescription());
                }
                this.menus.addAll(pplug.getPluginMenu().stream().map(menu -> new PBHPluginMenuDTO(locale, menu)).toList());
            }
        }
    }

    @AllArgsConstructor
    @Data
    public static class PBHPluginMenuDTO {
        private String id;
        private String displayName;
        private boolean disabled;
        private String relativeEndpoint;

        public PBHPluginMenuDTO(String locale, PBHPluginMenu menu) {
            this.id = menu.getMenuId();
            this.displayName = tl(locale, menu.getDisplayName());
            this.relativeEndpoint = menu.getRelativeEndpoint();
            this.disabled = menu.isDisabled();
        }
    }


    @AllArgsConstructor
    @Data
    public static class PluginDependencyDTO {
        private String id;
        private boolean optional;
        private String versionSupport;

        public PluginDependencyDTO(PluginDependency pluginDependency) {
            this.id = pluginDependency.getPluginId();
            this.optional = pluginDependency.isOptional();
            this.versionSupport = pluginDependency.getPluginVersionSupport();
        }
    }
}
