package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.configuration.pf4j.PBHSpringPluginManager;
import com.ghostchu.peerbanhelper.configuration.pf4j.PluginDisplay;
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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Slf4j
@Component
public class PBHPluginController extends AbstractFeatureModule {
    @Autowired
    private PBHSpringPluginManager pluginManager;
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
        synchronized (pluginManager) {
            for (var id : dto.getIdentifiers()) {
                try {
                    Object value = switch (dto.getAction()) {
                        case "start" -> pluginManager.startPlugin(id);
                        case "stop" -> pluginManager.stopPlugin(id);
                        case "enable" -> pluginManager.enablePlugin(id);
                        case "disable" -> pluginManager.disablePlugin(id);
                        case "load" -> pluginManager.loadPlugin(installedPath(id));
                        case "unload" -> pluginManager.unloadPlugin(id);
                        case "delete" -> pluginManager.deletePlugin(id);
                        default -> throw new IllegalArgumentException("Unknown plugin operation: " + dto.getAction());
                    };
                    var wrapper = pluginManager.getPlugin(id);
                    var failure = wrapper == null ? null : wrapper.getFailedException();
                    result.add(new PluginOperateResultDTO(id, value, failure == null
                            ? tl(locale(context), Lang.WEBAPI_PLUGIN_OPERATION_COMPLETED)
                            : MiscUtil.throwableToString(failure)));
                } catch (Exception e) {
                    result.add(new PluginOperateResultDTO(id, null, MiscUtil.throwableToString(e)));
                }
            }
        }
        context.json(new StdResp(true, null, result));
    }

    private Path installedPath(String name) throws IOException {
        var root = Main.getPluginDirectory().toPath().toRealPath();
        var path = root.resolve(name).toRealPath();
        if (!root.equals(path.getParent())) {
            throw new IllegalArgumentException("Expected an installed plugin in " + root);
        }
        return path;
    }

    private void listPlugins(@NotNull Context context) {
        synchronized (pluginManager) {
            context.json(new StdResp(true, null, pluginManager.getPlugins().stream()
                    .map(plugin -> new PluginDTO(locale(context), plugin, pluginManager.getDisplay(plugin.getPluginId()))).toList()));
        }
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

        public PluginDTO(String locale, PluginWrapper plugin, PluginDisplay display) {
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
            if (display != null) {
                if (display.name() != null) this.displayName = tl(locale, display.name());
                if (display.description() != null) this.description = tl(locale, display.description());
                this.menus = display.menus().stream().map(menu -> new PBHPluginMenuDTO(locale, menu)).toList();
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

        public PBHPluginMenuDTO(String locale, PluginDisplay.Menu menu) {
            this.id = menu.id();
            this.displayName = tl(locale, menu.name());
            this.relativeEndpoint = menu.endpoint();
            this.disabled = menu.disabled();
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
