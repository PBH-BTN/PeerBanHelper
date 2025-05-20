package com.ghostchu.peerbanhelper.api.text;

import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface LanguageFilesManager {
    void deploy(@NotNull String locale, @NotNull YamlConfiguration newDistribution);

    void destroy(@NotNull String locale);

    @Nullable YamlConfiguration getDistribution(@NotNull String locale);

    @NotNull Map<String, YamlConfiguration> getDistributions();

    void remove(@NotNull String distributionPath);

    void remove(@NotNull String distributionPath, @NotNull String locale);

    void reset();
}
