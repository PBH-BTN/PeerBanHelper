package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.sk89q.warmroast.StackNode;
import com.sk89q.warmroast.WarmRoast;
import com.sk89q.warmroast.WarmRoastManager;
import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Component
@Slf4j
public class PBHPerfWarmRoastController extends AbstractFeatureModule {
    private final JavalinWebContainer javalinWebContainer;
    private WarmRoast roast;

    public PBHPerfWarmRoastController(JavalinWebContainer javalinWebContainer) {
        super();
        this.javalinWebContainer = javalinWebContainer;
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "webapi-perf-warmroast";
    }

    @Override
    public @NotNull String getConfigName() {
        return "perf-warmroast";
    }

    @Override
    public void onEnable() {
        javalinWebContainer.javalin()
                .get("/warmroast", this::handleWarmRoastRequest, Role.USER_READ)
                .get("/warmroast/launch", this::handleWarmRoastStartProfile, Role.USER_READ)
                .get("/warmroast/reset", this::handleWarmRoastResetProfile, Role.USER_READ)
                .get("/warmroast/warmroast.js", ctx -> {
                    ctx.result(WarmRoast.class.getResourceAsStream("/warmroast/warmroast.js"));
                    ctx.contentType("application/javascript");
                }, Role.ANYONE)
                .get("/warmroast/style.css", ctx -> {
                    ctx.result(WarmRoast.class.getResourceAsStream("/warmroast/style.css"));
                    ctx.contentType("text/css");
                }, Role.ANYONE);
    }

    private void handleWarmRoastResetProfile(@NotNull Context context) throws AgentLoadException, IOException, AttachNotSupportedException, AgentInitializationException {
        WarmRoastManager.stopAndReset();
        WarmRoastManager.start();
        context.result(tl(locale(context), Lang.PERF_RESTARTED_DESCRIPTION));
    }

    private void handleWarmRoastStartProfile(@NotNull Context context) throws IOException, AttachNotSupportedException, AgentLoadException, AgentInitializationException {
        WarmRoastManager.start();
        context.result(tl(locale(context), Lang.PERF_RESTARTED_DESCRIPTION));
    }

    private void handleWarmRoastRequest(@NotNull Context ctx) {
        if (WarmRoastManager.getRoast() == null) {
            ctx.status(503).result(tl(locale(ctx), Lang.PERF_NOT_RUNNING));
            return;
        }
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>WarmRoast</title>");
        html.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"style.css\">");
        html.append("</head><body>");
        html.append("<h1>WarmRoast</h1>");
        html.append("<div class=\"loading\">Downloading snapshot; please wait...</div>");
        html.append("<div class=\"stack\" style=\"display: none\">");
        Collection<StackNode> nodes = List.copyOf(WarmRoastManager.getRoast().getData().values());
        for (StackNode node : nodes) {
            html.append(node.toHtml());
        }
        if (nodes.isEmpty()) {
            html.append("<p class=\"no-results\">There are no results. ")
                    .append("(Thread filter does not match thread?)</p>");
        }
        html.append("</div>");
        html.append("<div id=\"overlay\"></div>");
        html.append("<p class=\"footer\">");
        html.append("Icons from <a href=\"http://www.fatcow.com/\">FatCow</a> &mdash; ");
        html.append("<a href=\"http://github.com/sk89q/warmroast\">github.com/sk89q/warmroast</a></p>");
        html.append("<script src=\"//ajax.googleapis.com/ajax/libs/jquery/1.10.1/jquery.min.js\"></script>");
        html.append("<script src=\"warmroast.js\"></script>");
        html.append("</body></html>");

        ctx.html(html.toString());
    }


    @Override
    public void onDisable() {

    }
}