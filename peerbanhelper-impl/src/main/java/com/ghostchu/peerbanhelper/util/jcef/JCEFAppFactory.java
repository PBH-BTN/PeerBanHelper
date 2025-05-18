package com.ghostchu.peerbanhelper.util.jcef;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import lombok.extern.slf4j.Slf4j;
import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter;
import me.friwi.jcefmaven.impl.progress.ConsoleProgressHandler;

import java.io.File;
import java.util.List;
import java.util.StringJoiner;

@Slf4j
public final class JCEFAppFactory {
    public static CefAppBuilder createBuilder(File dataDir, String locale) {
        File baseDir = new File(dataDir, "jcef");
        if (!baseDir.exists()) baseDir.mkdirs();
        File installDir = new File(baseDir, "install");
        if (!installDir.exists()) installDir.mkdirs();
        File userData = new File(baseDir, "userdata");
        if (!userData.exists()) userData.mkdirs();
        File cacheDir = new File(userData, "cache");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        CefAppBuilder builder = new CefAppBuilder();
        builder.setInstallDir(installDir); // 安装目录
        builder.getCefSettings().locale = locale; // 浏览器默认语言，解决 WebUI 的语言问题
        builder.getCefSettings().windowless_rendering_enabled = false; // 禁用 OSR 离屏渲染，打开会导致 Swing 窗口卡住，不知道为什么
        builder.getCefSettings().persist_session_cookies = true; // 保持会话
        builder.getCefSettings().cache_path = cacheDir.getAbsolutePath(); // 缓存目录
        builder.getCefSettings().root_cache_path = userData.getAbsolutePath(); // 缓存目录
        builder.getCefSettings().user_agent = Main.getUserAgent(); // userAgent
        builder.addJcefArgs(Main.getStartupArgs());
        if (ExternalSwitch.parseBoolean("jcef.no-sandbox", false)) {
            builder.addJcefArgs("--no-sandbox");
        }
        if (ExternalSwitch.parseBoolean("jcef.ignore-ssl-cert", false)) {
            builder.addJcefArgs("--ignore-certificate-errors");
        }
        if (ExternalSwitch.parseBoolean("jcef.disable-gpu", false)) {
            builder.addJcefArgs("--disable-gpu");
        }
        if (ExternalSwitch.parseBoolean("jcef.allow-universal-access-from-files", true)) {
            builder.addJcefArgs("--allow-universal-access-from-files");
        }
        if (ExternalSwitch.parseBoolean("jcef.allow-media-stream", true)) {
            builder.addJcefArgs("--enable-media-stream");
        }
        if (ExternalSwitch.parseBoolean("jcef.disable-spell-checking", true)) {
            builder.addJcefArgs("--disable-spell-checking");
        }
        builder.setMirrors(List.of(
                "https://maven.aliyun.com/repository/central/me/friwi/jcef-natives-{platform}/{tag}/jcef-natives-{platform}-{tag}.jar",
                "https://mirrors.tencent.com/nexus/repository/maven-public/me/friwi/jcef-natives-{platform}/{tag}/jcef-natives-{platform}-{tag}.jar",
                "https://mirrors.huaweicloud.com/repository/maven/me/friwi/jcef-natives-{platform}/{tag}/jcef-natives-{platform}-{tag}.jar"
        ));
        builder.setProgressHandler(new ConsoleProgressHandler());
        //builder.getCefSettings().windowless_rendering_enabled = true; //Default - select OSR mode
        builder.setAppHandler(new MavenCefAppHandlerAdapter() {
        });
        var b = new StringJoiner(" ");
        builder.getJcefArgs().forEach(b::add);
        log.debug("JCEF App created with args: {}", b);
        return builder;
    }
}
