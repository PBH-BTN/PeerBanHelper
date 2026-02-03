package com.ghostchu.peerbanhelper.web;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.MsgUtil;
import com.ghostchu.peerbanhelper.util.logger.JListAppender;
import com.ghostchu.peerbanhelper.util.logger.LogEntry;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;
import org.slf4j.event.Level;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

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

    private String generateLogsHTML() {
        var logs = new ArrayList<>(JListAppender.ringDeque.stream().limit(100).toList());
        Collections.reverse(logs);

        StringBuilder html = new StringBuilder("<div class='terminal'>");
        for (LogEntry entry : logs) {
            String timestamp = MsgUtil.getTimeFormatter().format(new Date(entry.time()));
            String line = "[%s] [%s/%s]: %s".formatted(timestamp, entry.thread(), entry.level(), entry.content());
            String cssClass = "log-line " + getLogClass(entry.level());
            html.append("<div class='").append(cssClass).append("'>")
                .append(escapeHtml(line))
                .append("</div>");
        }
        html.append("</div>");
        return html.toString();
    }

    private String getLogClass(Level level) {
        return switch (level) {
            case ERROR -> "log-error";
            case WARN -> "log-warn";
            case DEBUG -> "log-debug";
            default -> "";
        };
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}
