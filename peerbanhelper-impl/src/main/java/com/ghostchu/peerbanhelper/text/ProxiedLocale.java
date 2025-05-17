package com.ghostchu.peerbanhelper.text;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.Locale;

@Data
public final class ProxiedLocale {
    private Locale locale;
    @Nullable
    private String origin;
    private String relative;
    private NumberFormat nf;

    public ProxiedLocale(@Nullable String origin, String relative, @NotNull NumberFormat nf, @NotNull Locale locale) {
        this.origin = origin;
        this.relative = relative;
        this.nf = nf;
        this.locale = locale;
    }

    public String getLocale() {
        return relative;
    }

    @NotNull
    public NumberFormat getNumberFormat() {
        return nf;
    }

    @NotNull
    public Locale getJavaLocale() {
        return locale;
    }
}
