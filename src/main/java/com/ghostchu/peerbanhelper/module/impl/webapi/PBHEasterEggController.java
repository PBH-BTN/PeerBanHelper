package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@IgnoreScan
public class PBHEasterEggController  extends AbstractFeatureModule {
    private final JavalinWebContainer javalinWebContainer;

    public PBHEasterEggController(JavalinWebContainer javalinWebContainer) {
        super();
        this.javalinWebContainer = javalinWebContainer;
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - Access from :9898/api/egg";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-easteregg";
    }

    @Override
    public void onEnable() {
        javalinWebContainer.javalin()
                .get("/api/egg", this::handleEgg);
    }

    private void handleEgg(Context context) {
        // get today's in week
        int day = java.time.LocalDate.now().getDayOfWeek().getValue();
        switch (day){
            case 1:
                context.redirect("https://www.bilibili.com/video/BV1S3UaYvE5o");
                break;
            case 3:
                context.redirect("https://www.bilibili.com/video/BV1QQ4y1d7Rd");
                break;
            case 4:
                context.redirect("https://www.bilibili.com/video/BV1by4y1F7nS");
                break;
            case 5:
                context.redirect("https://www.bilibili.com/video/BV1ztzBY1EP");
                break;
            case 7:
                context.redirect("https://www.bilibili.com/video/BV15Z4y1Q7mz");
                break;
            default:
                context.redirect("https://www.bilibili.com/video/BV1GJ411x7h7");
        }
    }

    @Override
    public void onDisable() {

    }
}
