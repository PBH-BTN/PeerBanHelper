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
     * @param experiment The {@link Experiment} instance representing the details of the experiment,
     *                   including its identifier, associated values, title, and description.
     */
    Experiments(Experiment experiment) {
        this.experiment = experiment;
    }

    /**
     * Retrieves the {@link Experiment} instance associated with this enum constant.
     *
     * @return the {@link Experiment} representing the details of the specific experiment
     */
    public Experiment getExperiment() {
        return experiment;
    }
    }
