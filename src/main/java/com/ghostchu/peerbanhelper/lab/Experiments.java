package com.ghostchu.peerbanhelper.lab;

import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;

import java.util.List;

public enum Experiments {
    IPFS(new Experiment("ipfs", List.of(0), new TranslationComponent(Lang.LAB_EXPERIMENT_IPFS_TITLE), new TranslationComponent(Lang.LAB_EXPERIMENT_IPFS_DESCRIPTION))),
    DNSJAVA(new Experiment("dnsjava", List.of(0, 1, 2), new TranslationComponent(Lang.LAB_EXPERIMENT_DNSJAVA_TITLE), new TranslationComponent(Lang.LAB_EXPERIMENT_DNSJAVA_DESCRIPTION))),
    SQLITE_VACUUM(new Experiment("sqlite_vacuum", List.of(0, 1, 3, 5), new TranslationComponent(Lang.LAB_EXPERIMENT_SQLITE_VACUUM_TITLE), new TranslationComponent(Lang.LAB_EXPERIMENT_SQLITE_VACUUM_DESCRIPTION)));

    private final Experiment experiment;

    /**
     * Constructs an enum constant with the specified experiment configuration.
     *
     * @param experiment The {@link Experiment} configuration associated with this enum constant
     */
    Experiments(Experiment experiment) {
        this.experiment = experiment;
    }

    /**
     * Retrieves the {@code Experiment} associated with this enum constant.
     *
     * @return the {@link Experiment} instance representing the specific experimental configuration
     */
    public Experiment getExperiment() {
        return experiment;
    }
    }
