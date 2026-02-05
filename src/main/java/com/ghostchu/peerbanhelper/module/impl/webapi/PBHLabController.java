package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.webapi.body.EnabledConfigBody;
import com.ghostchu.peerbanhelper.module.impl.webapi.body.ExperimentPutBody;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.ExperimentRecordDTO;
import com.ghostchu.peerbanhelper.util.lab.Experiments;
import com.ghostchu.peerbanhelper.util.lab.Laboratory;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Slf4j
@Component
public final class PBHLabController extends AbstractFeatureModule {

    private final JavalinWebContainer javalinWebContainer;
    private final Laboratory laboratory;

    public PBHLabController(JavalinWebContainer javalinWebContainer, Laboratory laboratory) {
        super();
        this.javalinWebContainer = javalinWebContainer;
        this.laboratory = laboratory;
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - Laboratory";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-laboratory";
    }

    @Override
    public void onEnable() {
        javalinWebContainer.javalin()
                .post("/api/laboratory/config", this::handleConfig, Role.USER_WRITE)
                .get("/api/laboratory/config", this::handleConfigGet, Role.USER_WRITE)
                .get("/api/laboratory/experiments", this::handleExperiments, Role.USER_READ)
                .get("/api/laboratory/isExperimentActivated", this::handleExperimentActivated, Role.USER_READ)
                .put("/api/laboratory/experiment/{id}", this::handleExperimentSet, Role.USER_WRITE);
    }

    private void handleConfigGet(Context context) {
        context.json(new StdResp(true, null, new EnabledConfigBody(laboratory.isEnabled())));
    }

    private void handleConfig(Context context) {
        var configBody = context.bodyAsClass(EnabledConfigBody.class);
        laboratory.setEnabled(configBody.isEnabled());
        context.json(new StdResp(true, null, configBody.isEnabled()));
    }

    private void handleExperimentSet(@NotNull Context context) {
        var id = context.pathParam("id");
        var activated = context.bodyAsClass(ExperimentPutBody.class);
        Boolean bool = null;
        if("true".equalsIgnoreCase(activated.getStatus())){
            bool = true;
        } else if("false".equalsIgnoreCase(activated.getStatus())){
            bool = false;
        }
        laboratory.setExperimentActivated(id, bool);
        context.json(new StdResp(true, null, activated.getStatus()));
    }

    private void handleExperimentActivated(@NotNull Context context) {
        var id = context.queryParam("id");
        for (Experiments value : Experiments.values()) {
            if (value.getExperiment().id().equalsIgnoreCase(id)) {
                context.json(new StdResp(true, null, laboratory.isExperimentActivated(value.getExperiment())));
                return;
            }
        }
        context.json(new StdResp(false, "Experiment not found", null));
    }

    private void handleExperiments(@NotNull Context context) {
        List<ExperimentRecordDTO> availableExperiments = new ArrayList<>();
        for (Experiments value : Experiments.values()) {
            var record = new ExperimentRecordDTO(
                    value.getExperiment().id(),
                    laboratory.isExperimentActivated(value.getExperiment()),
                    value.getExperiment().group(),
                    tl(locale(context), value.getExperiment().title()),
                    tl(locale(context), value.getExperiment().description())
            );
            availableExperiments.add(record);
        }
        Map<String, Object> map = new HashMap<>();
        map.put("experiments", availableExperiments);
        map.put("mygroup", laboratory.getExperimentalGroup());
        map.put("labEnabled", laboratory.isEnabled());
        context.json(new StdResp(true, null, map));
    }

    @Override
    public void onDisable() {

    }

}
