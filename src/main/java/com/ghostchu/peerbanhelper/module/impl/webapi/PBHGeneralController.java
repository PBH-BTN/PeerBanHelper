package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.ghostchu.simplereloadlib.ReloadableContainer;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PBHGeneralController extends AbstractFeatureModule {
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
                .post("/api/general/reload", this::handleReloading, Role.USER_READ);
    }

    private void handleReloading(Context context) {
        var result = Main.getReloadManager().reload();
        List<ReloadEntry> entryList = new ArrayList<>();
        result.forEach((container, r)->{
           String entryName;
           if(container.getReloadable() == null){
               entryName = container.getReloadableMethod().getDeclaringClass().getName()+"#"+container.getReloadableMethod().getName();
           }else{
               Reloadable reloadable =  container.getReloadable().get();
               if(reloadable == null){
                   entryName = "<invalid>";
               }else{
                   entryName = reloadable.getClass().getName();
               }
           }
           entryList.add(new ReloadEntry(entryName, r));
        });
        context.json(entryList);
    }



    @Override
    public void onDisable() {

    }

    public record ReloadEntry(
            String reloadable,
            ReloadResult reloadResult
    ){}

}
