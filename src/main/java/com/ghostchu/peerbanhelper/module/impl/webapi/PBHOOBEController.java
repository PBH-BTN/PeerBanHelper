package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.webapi.common.SlimMsg;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

/*
   :(
 */
@Component
@Slf4j
public class PBHOOBEController extends AbstractFeatureModule {
    @Autowired
    private JavalinWebContainer webContainer;

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - OOBE Interface";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-oobe";
    }

    @Override
    public void onEnable() {
        webContainer.javalin()
                .post("/api/oobe/init", this::handleOOBERequest, Role.ANYONE)
                .post("/api/oobe/testDownloader", ctx -> validateDownloader(ctx, JsonParser.parseString(ctx.body()).getAsJsonObject()), Role.ANYONE); // 指定 ANYONE，否则会被鉴权代码拉取鉴权
    }

    private void handleOOBERequest(Context ctx) throws IOException {
        if (webContainer.getToken() != null && !webContainer.getToken().isBlank()) {
            ctx.status(HttpStatus.FORBIDDEN);
            ctx.json(new SlimMsg(false, tl(locale(ctx), Lang.OOBE_DISALLOW_REINIT), 403));
            return;
        }
        JsonObject parser = JsonParser.parseString(ctx.body()).getAsJsonObject();
        String token = parser.get("token").getAsString();
        JsonObject draftDownloader = parser.get("downloader").getAsJsonObject();
        if (!validateDownloader(ctx, draftDownloader)) {
            return;
        }
        YamlConfiguration conf = Main.getMainConfig();
        conf.set("server.token", token);
        conf.save(Main.getMainConfigFile());
        webContainer.setToken(token);
        String name = draftDownloader.get("name").getAsString();
        if(name.contains(".")){
            throw new IllegalArgumentException("Illegal character (.) in name: " + name);
        }
        JsonObject config = draftDownloader.get("config").getAsJsonObject();
        Downloader downloader = getServer().createDownloader(name, config);
        if (getServer().registerDownloader(downloader)) {
            ctx.status(HttpStatus.CREATED);
            ctx.json(Map.of("message", tl(locale(ctx), Lang.DOWNLOADER_API_CREATED), "code", HttpStatus.CREATED.getCode()));
        } else {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(Map.of("message", tl(locale(ctx), Lang.DOWNLOADER_API_CREATION_FAILED_ALREADY_EXISTS), "code", HttpStatus.BAD_REQUEST.getCode()));
        }
        try {
            getServer().saveDownloaders();
        } catch (IOException e) {
            log.error("Internal server error, unable to create downloader due an I/O exception", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(Map.of("message", tl(locale(ctx), Lang.DOWNLOADER_API_CREATION_FAILED_IO_EXCEPTION)));
        }
    }

    // 从 PBHDownloaderController 抄过来的，堪虑合并
    public boolean validateDownloader(Context ctx, JsonObject draftDownloader) {
        if (webContainer.getToken() != null && !webContainer.getToken().isBlank()) {
            ctx.status(HttpStatus.FORBIDDEN);
            ctx.json(new SlimMsg(false, tl(locale(ctx), Lang.OOBE_DISALLOW_REINIT), 403));
            return false;
        }
        String name = draftDownloader.get("name").getAsString();
        if(name.contains(".")){
            throw new IllegalArgumentException("Illegal character (.) in name: " + name);
        }
        JsonObject config = draftDownloader.get("config").getAsJsonObject();
//        if (getServer().getDownloaders().stream().anyMatch(d -> d.getName().equals(name))) {
//            ctx.status(HttpStatus.CONFLICT);
//            ctx.json(Map.of("message", Lang.DOWNLOADER_API_TEST_NAME_EXISTS));
//            return;
//        }
        Downloader downloader = getServer().createDownloader(name, config);
        if (downloader == null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(Map.of("message", tl(locale(ctx), Lang.DOWNLOADER_API_ADD_FAILURE)));
            return false;
        }
        try {
            var testResult = downloader.login();
            ctx.status(HttpStatus.OK);
            if (testResult.success()) {
                ctx.json(Map.of("message", tl(locale(ctx), Lang.DOWNLOADER_API_TEST_OK), "valid", testResult.success()));
            } else {
                ctx.json(Map.of("message", tl(locale(ctx), testResult.getMessage()), "valid", testResult.success()));
            }
            downloader.close();
            return true;
        } catch (Exception e) {
            log.error("Validate downloader failed", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(Map.of("message", e.getMessage(), "valid", false));
            return false;
        }
    }


    @Override
    public void onDisable() {

    }

    record ModuleRecord(
            String className,
            PBHDownloaderController.DraftDownloader draftDownloader
    ) {
    }
}
