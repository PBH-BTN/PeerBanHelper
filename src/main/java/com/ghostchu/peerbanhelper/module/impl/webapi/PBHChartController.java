package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.database.dao.impl.PeerRecordDao;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.webapi.common.SlimMsg;
import com.ghostchu.peerbanhelper.pbhplus.ActivationManager;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Component
public class PBHChartController extends AbstractFeatureModule {
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private ActivationManager activationManager;
    private PeerRecordDao peerRecordDao;

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - Charts";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-charts";
    }

    @Override
    public void onEnable() {
        webContainer.javalin().get("/api/charts/geoip", this::handleGeoIP, Role.USER_READ);
    }

    private void handleGeoIP(Context ctx) {
        if (!activationManager.isActivated()) {
            ctx.status(HttpStatus.FORBIDDEN);
            ctx.json(new SlimMsg(false, tl(locale(ctx), Lang.PBHPLUS_LICENSE_FAILED), 401));
            return;
        }
    }


    @Override
    public void onDisable() {

    }


//    public record GeoIPQuery(GeoIPPie data, long count) {
//
//    }
//
//    public record GeoIPPie(
//            String country,
//            String province,
//            String city,
//            String districts,
//            String net,
//            String isp
//    ) {
//
//    }
}
