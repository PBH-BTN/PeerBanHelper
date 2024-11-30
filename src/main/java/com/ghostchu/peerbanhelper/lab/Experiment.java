package com.ghostchu.peerbanhelper.lab;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class Experiment {

    private final String id;
    private final int group;
    private TranslationComponent title;
    private TranslationComponent description;
}
