package com.ghostchu.peerbanhelper.lab;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@AllArgsConstructor
@Data
public class Experiment {

    private final String id;
    private final List<Integer> group;
    private TranslationComponent title;
    private TranslationComponent description;
}
