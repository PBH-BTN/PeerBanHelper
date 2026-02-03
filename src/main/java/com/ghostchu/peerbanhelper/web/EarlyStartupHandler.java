package com.ghostchu.peerbanhelper.web;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.MsgUtil;
import com.ghostchu.peerbanhelper.util.logger.JListAppender;
import com.ghostchu.peerbanhelper.util.logger.LogEntry;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.StringJoiner;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

public class EarlyStartupHandler implements Handler {

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        try (var res = Main.class.getResourceAsStream("/early-starting.html")) {
            if (res == null) {
                ctx.html("PeerBanHelper Starting (Fallback Page)...");
                return;
            }
            String html = new String(res.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("${EARLY_STARTING_TITLE}", tlUI(Lang.EARLY_STARTING_TITLE))
                    .replace("${EARLY_STARTING_PAGE_TITLE}", tlUI(Lang.EARLY_STARTING_PAGE_TITLE))
                    .replace("${EARLY_STARTING_PAGE_DESCRIPTION}", tlUI(Lang.EARLY_STARTING_PAGE_DESCRIPTION))
                    .replace("${EARLY_STARTING_PAGE_CONTENT}", generateLogsHTML());
            ctx.header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .header("Pragma", "no-cache")
                    .header("Expires", "0")
                    .html(html);
        }
    }

    private String generateLogsHTML(){
        StringJoiner joiner = new StringJoiner("<br/>");
        var list = new ArrayList<>(JListAppender.ringDeque.stream().limit(100).toList());
        Collections.reverse(list);
        String template = "[%s] [%s/%s]: %s";
        for (LogEntry entry : list) {
            var date = MsgUtil.getTimeFormatter().format(new Date(entry.time()));
            joiner.add( template.formatted(date, entry.thread(), entry.level(), entry.content()));
        }
        return joiner.toString();
    }
}
