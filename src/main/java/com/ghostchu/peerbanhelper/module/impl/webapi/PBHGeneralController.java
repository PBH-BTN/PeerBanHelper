package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.gson.Gson;
import com.google.gson.ToNumberPolicy;
import com.google.gson.reflect.TypeToken;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Component
@IgnoreScan
public class PBHGeneralController extends AbstractFeatureModule {
    private static final Gson GSON = JsonUtil.getGson().newBuilder()
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .create();
    @Autowired
    private JavalinWebContainer webContainer;

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - General";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-general";
    }

    @Override
    public void onEnable() {
        webContainer.javalin()
                .post("/api/general/reload", this::handleReloading, Role.USER_READ)
                .get("/api/general/{configName}", this::handleConfigGet, Role.USER_READ)
                .put("/api/general/{configName}", this::handleConfigPut, Role.USER_WRITE);
    }

    private void handleReloading(Context context) {
        var result = Main.getReloadManager().reload();
        List<ReloadEntry> entryList = new ArrayList<>();
        result.forEach((container, r) -> {
            String entryName;
            if (container.getReloadable() == null) {
                entryName = container.getReloadableMethod().getDeclaringClass().getName() + "#" + container.getReloadableMethod().getName();
            } else {
                Reloadable reloadable = container.getReloadable().get();
                if (reloadable == null) {
                    entryName = "<invalid>";
                } else {
                    entryName = reloadable.getClass().getName();
                }
            }
            entryList.add(new ReloadEntry(entryName, r.getStatus().name()));
        });
        context.json(new StdResp(true, null, entryList));
    }

    private void handleConfigGet(Context context) throws FileNotFoundException {
        File configFile;
        switch (context.pathParam("configName")) {
            case "config" -> configFile = Main.getMainConfigFile();
            case "profile" -> configFile = Main.getProfileConfigFile();
            default -> {
                context.status(HttpStatus.NOT_FOUND);
                return;
            }
        }
        Map<String, Object> originalYaml = new Yaml().load(new FileReader(configFile));
        context.json(new StdResp(true, null, replaceKeys(originalYaml)));
    }

    private void handleConfigPut(Context context) throws IOException {
        File configFile;
        YamlConfiguration config;
        switch (context.pathParam("configName")) {
            case "config" -> {
                config = Main.getMainConfig();
                configFile = Main.getMainConfigFile();
            }
            case "profile" -> {
                config = Main.getProfileConfig();
                configFile = Main.getProfileConfigFile();
            }
            default -> {
                context.status(HttpStatus.NOT_FOUND);
                return;
            }
        }
        Map<String, Object> newData = GSON.fromJson(context.body(),Map.class);
        mergeYaml(config, newData);
        config.save(configFile);
        context.status(HttpStatus.CREATED);
        context.json(new StdResp(true, tl(locale(context), Lang.OPERATION_EXECUTE_SUCCESSFULLY), null));
    }

    private Map<String, Object> replaceKeys(Map<String, Object> originalMap) {
        Map<String, Object> updatedMap = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : originalMap.entrySet()) {
            String updatedKey = entry.getKey().replace("-", "_");
            // 字符串列表转为对象列表
            if (updatedKey.equals("banned_peer_id") || updatedKey.equals("banned_client_name")) {
                List<String> bannedPeerIdList = (List<String>) entry.getValue();
                List<Map<String, String>> parsedBannedPeerIdList = bannedPeerIdList.stream()
                        .map(bannedPeer -> (Map<String, String>) GSON.fromJson(bannedPeer, new TypeToken<Map<String, String>>() {}.getType()))// 将字符串转换成对象
                        .toList();
                updatedMap.put(updatedKey, parsedBannedPeerIdList); // 替换为对象列表
            }
            // 如果值是 Map，递归替换子 Map 中的键
            else if (entry.getValue() instanceof Map<?, ?>) {
                updatedMap.put(updatedKey, replaceKeys((Map<String, Object>) entry.getValue()));
            }
            // 如果值是 List，检查其中的元素是否是 Map，如果是也进行递归处理
            else if (entry.getValue() instanceof List<?>) {
                List<Object> updatedList = new ArrayList<>();
                for (Object item : (List<?>) entry.getValue()) {
                    if (item instanceof Map<?, ?>) {
                        updatedList.add(replaceKeys((Map<String, Object>) item));
                    } else {
                        updatedList.add(item); // 不是 Map 的元素直接添加
                    }
                }
                updatedMap.put(updatedKey, updatedList);
            } else {
                updatedMap.put(updatedKey, entry.getValue()); // 直接添加非 Map 和非 List 的值
            }
        }

        return updatedMap;
    }

    private ConfigurationSection mergeYaml(ConfigurationSection originalConfig, Map<String, Object> newMap) {
        for (Map.Entry<String, Object> entry : newMap.entrySet()) {
            String originalKey = entry.getKey().replace("_", "-");
            // 字符串列表转为对象列表
            if (originalKey.equals("banned-peer-id") || originalKey.equals("banned-client-name")) {
                List<Map<String, String>> bannedPeerIdList = (List<Map<String, String>>) entry.getValue();
                List<String> parsedBannedPeerIdList = bannedPeerIdList.stream()
                        .map(bannedPeer -> GSON.toJson(bannedPeer))
                        .toList();
                originalConfig.set(originalKey, parsedBannedPeerIdList);
            }
            // 如果值是 Map，递归替换子 Map 中的键
            else if (entry.getValue() instanceof Map<?, ?>) {
                originalConfig.set(originalKey, mergeYaml((ConfigurationSection) originalConfig.get(originalKey), (Map<String, Object>) entry.getValue()));
            }
            // 如果值是 List，检查其中的元素是否是 Map，如果是也进行递归处理
            else if (entry.getValue() instanceof List<?>) {
                List<Object> updatedList = new ArrayList<>();
                for (Object item : (List<?>) entry.getValue()) {
                    if (item instanceof Map<?, ?>) {
                        updatedList.add(mergeYaml((ConfigurationSection) originalConfig.get(originalKey), (Map<String, Object>) item));
                    } else {
                        updatedList.add(item); // 不是 Map 的元素直接添加
                    }
                }
                originalConfig.set(originalKey, updatedList);
            } else {
                originalConfig.set(originalKey, entry.getValue()); // 直接添加非 Map 和非 List 的值
            }
        }

        return originalConfig;
    }

    @Override
    public void onDisable() {

    }

    public record ReloadEntry(
            String reloadable,
            String reloadResult
    ) {
    }

}
