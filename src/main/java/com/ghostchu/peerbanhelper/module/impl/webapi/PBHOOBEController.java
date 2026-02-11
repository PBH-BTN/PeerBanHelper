package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.databasent.driver.h2.H2DatabaseDriver;
import com.ghostchu.peerbanhelper.databasent.driver.mysql.MySQLDatabaseDriver;
import com.ghostchu.peerbanhelper.databasent.driver.postgres.PostgresDatabaseDriver;
import com.ghostchu.peerbanhelper.databasent.driver.sqlite.SQLiteDatabaseDriver;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderManagerImpl;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.DatabaseNtConfigDTO;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.ReloadEntryDTO;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.DownloaderDiscovery;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.simplereloadlib.ReloadStatus;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.configuration.MemoryConfiguration;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

/*
   :(
 */
@Component
@Slf4j
public final class PBHOOBEController extends AbstractFeatureModule {
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private DownloaderManagerImpl downloaderManager;
    @Autowired
    private DownloaderDiscovery downloaderDiscovery;

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
                .post("/api/oobe/scanDownloader", this::handleOOBEScanDownloader, Role.ANYONE)
                .post("/api/oobe/testDownloader", ctx -> validateDownloader(ctx, JsonParser.parseString(ctx.body()).getAsJsonObject()), Role.ANYONE) // 指定 ANYONE，否则会被鉴权代码拉取鉴权
                .post("/api/oobe/testDatabaseConfig", this::handleDatabaseNtTest, Role.ANYONE);

    }

    private void handleOOBEScanDownloader(@NotNull Context ctx) {
        if(!ensureNotInitialized(ctx)){
            return;
        }
        var downloaders = downloaderDiscovery.scan(List.of()).join();
        ctx.json(new StdResp(true, null, downloaders));
    }

    private void handleOOBERequest(Context ctx) throws IOException {
        if(!ensureNotInitialized(ctx)){
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
        JsonObject config = draftDownloader.get("config").getAsJsonObject();
        String id = draftDownloader.get("id").getAsString();
        Downloader downloader = downloaderManager.createDownloader(id, config);
        if (downloaderManager.registerDownloader(downloader)) {
            ctx.status(HttpStatus.CREATED);
            ctx.json(new StdResp(true, tl(locale(ctx), Lang.DOWNLOADER_API_CREATED), null));
        } else {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.DOWNLOADER_API_CREATION_FAILED_ALREADY_EXISTS), null));
        }
        try {
            downloaderManager.saveDownloaders();
        } catch (IOException e) {
            log.error("Internal server error, unable to create downloader due an I/O exception", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.DOWNLOADER_API_CREATION_FAILED_IO_EXCEPTION), null));
        }
        var btn = parser.getAsJsonObject("btn");
        if (btn != null) {
            if (btn.has("enabled") && !btn.get("enabled").isJsonNull())
                conf.set("btn.enabled", btn.get("enabled").getAsBoolean());
            if (btn.has("submit") && !btn.get("submit").isJsonNull())
                conf.set("btn.submit", btn.get("submit").getAsBoolean());
            if (btn.has("app_id") && !btn.get("app_id").isJsonNull())
                conf.set("btn.app-id", btn.get("app_id").getAsString());
            if (btn.has("app_secret") && !btn.get("app_secret").isJsonNull())
                conf.set("btn.app-secret", btn.get("app_secret").getAsString());
            conf.save(Main.getMainConfigFile());
        }
        if (parser.has("database")) {
            JsonObject database = parser.get("database").getAsJsonObject();
            boolean needRestart = false;
            String type = null;
            if (database.has("type")) {
                type = database.get("type").getAsString();
                conf.set("database.type", type);
                if (!"sqlite".equals(type)) {
                    needRestart = true;
                }
            }
            if ("mysql".equals(type) || "postgresql".equals(type)) {
                if (database.has("host")) {
                    conf.set("database.host", database.get("host").getAsString());
                }
                if (database.has("port")) {
                    conf.set("database.port", database.get("port").getAsInt());
                }
                if (database.has("database")) {
                    conf.set("database.database", database.get("database").getAsString());
                }
                if (database.has("username")) {
                    conf.set("database.username", database.get("username").getAsString());
                }
                if (database.has("password")) {
                    conf.set("database.password", database.get("password").getAsString());
                }
            }
            conf.save(Main.getMainConfigFile());
            if (needRestart) {
                handleRestart(ctx);
            }
        }
        handleReloading(ctx);
    }

    private void handleReloading(Context context) {
        if(!ensureNotInitialized(context)){
            return;
        }
        Main.setupConfiguration();
        var result = Main.getReloadManager().reload();
        List<ReloadEntryDTO> entryList = new ArrayList<>();
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
            entryList.add(new ReloadEntryDTO(entryName, r.getStatus().name()));
        });


        boolean success = true;
        TranslationComponent message = new TranslationComponent(Lang.RELOAD_RESULT_SUCCESS);
        if (result.values().stream().anyMatch(r -> r.getStatus() == ReloadStatus.SCHEDULED)) {
            message = new TranslationComponent(Lang.RELOAD_RESULT_SCHEDULED);
        }
        if (result.values().stream().anyMatch(r -> r.getStatus() == ReloadStatus.REQUIRE_RESTART)) {
            message = new TranslationComponent(Lang.RELOAD_RESULT_REQUIRE_RESTART);
        }
        if (result.values().stream().anyMatch(r -> r.getStatus() == ReloadStatus.EXCEPTION)) {
            success = false;
            message = new TranslationComponent(Lang.RELOAD_RESULT_FAILED);
        }

        context.json(new StdResp(success, tl(locale(context), message), entryList));
    }

    private void handleRestart(Context context) {
        if(!ensureNotInitialized(context)){
            return;
        }
        context.json(new StdResp(false, tl(locale(context), Lang.RELOAD_RESULT_REQUIRE_RESTART), null));
        if (false) { // delay to v10.0
            // Schedule restart in a separate thread to allow the response to be sent first
            Thread restartThread = new Thread(() -> {
                try {
                    // Give some time for the response to be sent
                    Thread.sleep(1000);
                    Main.restartApplication();
                } catch (Exception e) {
                    log.error("Failed to restart application", e);
                }
            }, "RestartThread");
            restartThread.setDaemon(false);
            restartThread.start();
        }
    }

    private void handleDatabaseNtTest(@NotNull Context context) {
        if(!ensureNotInitialized(context)){
            return;
        }
        DatabaseNtConfigDTO dto = context.bodyAsClass(DatabaseNtConfigDTO.class);
        ConfigurationSection section = new MemoryConfiguration();
        section.set("type", dto.getType());
        section.set("host", dto.getHost());
        section.set("port", dto.getPort());
        section.set("username", dto.getUsername());
        section.set("password", dto.getPassword());
        section.set("database", dto.getDatabase());
        try {
            var driver = switch (dto.getType()) {
                case "h2" -> new H2DatabaseDriver(section);
                case "mysql" -> new MySQLDatabaseDriver(section);
                case "postgresql" -> new PostgresDatabaseDriver(section);
                default -> new SQLiteDatabaseDriver(section);
            };
            try (var stat = driver.getDataSource().getConnection().createStatement()) {
                boolean success = stat.execute("SELECT 1");
                context.json(new StdResp(true, null, success));
            } finally {
                driver.close();
            }
        } catch (Exception e) {
            context.status(HttpStatus.BAD_REQUEST);
            context.json(new StdResp(false, e.getClass().getName() + ": " + e.getMessage(), null));
        }
    }

    // 从 PBHDownloaderController 抄过来的，堪虑合并
    public boolean validateDownloader(Context ctx, JsonObject draftDownloader) {
        if(!ensureNotInitialized(ctx)){
            return false;
        }
        JsonObject config = draftDownloader.get("config").getAsJsonObject();
//        if (getServer().getDownloaders().stream().anyMatch(d -> d.getName().equals(name))) {
//            ctx.status(HttpStatus.CONFLICT);
//            ctx.json(Map.of("message", Lang.DOWNLOADER_API_TEST_NAME_EXISTS));
//            return;
//        }
        String id = draftDownloader.get("id").getAsString();
        Downloader downloader = downloaderManager.createDownloader(id, config);
        if (downloader == null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.DOWNLOADER_API_ADD_FAILURE), null));
            return false;
        }
        try {
            var testResult = downloader.login();
            if (testResult.success()) {
                ctx.json(new StdResp(testResult.success(), tl(locale(ctx), Lang.DOWNLOADER_API_TEST_OK), null));
            } else {
                ctx.json(new StdResp(false, tl(locale(ctx), testResult.message()), null));
            }
            downloader.close();
            return true;
        } catch (Exception e) {
            log.error("Validate downloader failed", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(new StdResp(false, e.getMessage(), null));
            return false;
        }
    }

    private boolean ensureNotInitialized(Context ctx) {
        if (webContainer.getToken() != null && !webContainer.getToken().isBlank()) {
            ctx.status(HttpStatus.FORBIDDEN);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.OOBE_DISALLOW_REINIT), null));
            return false;
        }
        return true;
    }


    @Override
    public void onDisable() {

    }
}
