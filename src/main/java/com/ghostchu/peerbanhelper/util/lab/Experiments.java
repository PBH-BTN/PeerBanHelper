package com.ghostchu.peerbanhelper.util.lab;

import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;

import java.util.List;

public enum Experiments {
    //DNSJAVA(new Experiment("dnsjava", List.of(0, 1, 2, 3, 4), new TranslationComponent(Lang.LAB_EXPERIMENT_DNSJAVA_TITLE), new TranslationComponent(Lang.LAB_EXPERIMENT_DNSJAVA_DESCRIPTION))),
    SQLITE_VACUUM(new Experiment("sqlite_vacuum", List.of(0, 1, 2, 3, 4), new TranslationComponent(Lang.LAB_EXPERIMENT_SQLITE_VACUUM_TITLE), new TranslationComponent(Lang.LAB_EXPERIMENT_SQLITE_VACUUM_DESCRIPTION))),
    TRANSACTION_BATCH_BAN_HISTORY_WRITE(new Experiment("transaction_batch_ban_history_write", List.of(2, 5), new TranslationComponent(Lang.LAB_EXPERIMENT_TRANSACTION_BATCH_BAN_HISTORY_WRITE_TITLE), new TranslationComponent(Lang.LAB_EXPERIMENT_TRANSACTION_BATCH_BAN_HISTORY_WRITE_DESCRIPTION))),
    WIN32_EMPTY_WORKING_SET(new Experiment("win32_empty_working_set", List.of(1, 3), new TranslationComponent(Lang.LAB_EXPERIMENT_WIN32_EMPTY_WORKING_SET_TITLE), new TranslationComponent(Lang.LAB_EXPERIMENT_WIN32_EMPTY_WORKING_SET_DESCRIPTION))),
    ASYNC_BANLIST_APPLY(new Experiment("async_banlist_apply", List.of(0, 5), new TranslationComponent(Lang.LAB_EXPERIMENT_ASYNC_BANLIST_APPLY), new TranslationComponent(Lang.LAB_EXPERIMENT_ASYNC_BANLIST_APPLY_DESCRIPTION))),
    ASYNC_IPDB_SETUP(new Experiment("async_ipdb_setup", List.of(0, 1), new TranslationComponent(Lang.LAB_EXPERIMENT_ASYNC_IPDB_LOADING), new TranslationComponent(Lang.LAB_EXPERIMENT_ASYNC_IPDB_LOADING_DESCRIPTION)))
    ;

    private final Experiment experiment;

    Experiments(Experiment experiment) {
        this.experiment = experiment;
    }

    public Experiment getExperiment() {
        return experiment;
    }
}
