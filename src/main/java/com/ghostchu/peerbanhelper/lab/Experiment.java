package com.ghostchu.peerbanhelper.lab;

import com.ghostchu.peerbanhelper.text.TranslationComponent;

import java.util.List;

public record Experiment(String id, List<Integer> group, TranslationComponent title, TranslationComponent description) {
}
