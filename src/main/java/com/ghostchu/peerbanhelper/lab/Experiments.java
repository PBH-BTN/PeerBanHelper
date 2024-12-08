package com.ghostchu.peerbanhelper.lab;

import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;

import java.util.List;

public enum Experiments {
    IPFS(new Experiment("ipfs", List.of(0), new TranslationComponent(Lang.LAB_EXPERIMENT_IPFS_TITLE), new TranslationComponent(Lang.LAB_EXPERIMENT_IPFS_DESCRIPTION)));

    private final Experiment experiment;

    Experiments(Experiment experiment) {
        this.experiment = experiment;
    }

    public Experiment getExperiment() {
        return experiment;
    }
}
