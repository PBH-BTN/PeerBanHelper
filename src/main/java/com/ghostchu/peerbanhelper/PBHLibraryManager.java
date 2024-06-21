//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.ghostchu.peerbanhelper;

import com.alessiodp.libby.LibraryManager;
import com.alessiodp.libby.classloader.ClassLoaderHelper;
import com.alessiodp.libby.classloader.SystemClassLoaderHelper;
import com.alessiodp.libby.classloader.URLClassLoaderHelper;
import com.alessiodp.libby.logging.adapters.LogAdapter;
import org.jetbrains.annotations.NotNull;

import java.net.URLClassLoader;
import java.nio.file.Path;

public class PBHLibraryManager extends LibraryManager {
    private final @NotNull ClassLoaderHelper classLoaderHelper;

    public PBHLibraryManager(@NotNull LogAdapter logAdapter, @NotNull Path dataDirectory) {
        this(logAdapter, dataDirectory, "lib");
    }

    public PBHLibraryManager(@NotNull LogAdapter logAdapter, @NotNull Path dataDirectory, @NotNull String directoryName) {
        super(logAdapter, dataDirectory, directoryName);
        ClassLoader classLoader = this.getClass().getClassLoader();
        if (classLoader instanceof URLClassLoader) {
            this.classLoaderHelper = new URLClassLoaderHelper((URLClassLoader) classLoader, this);
        } else {
            if (classLoader != ClassLoader.getSystemClassLoader()) {
                throw new RuntimeException("Unsupported class loader: " + classLoader.getClass().getName());
            }

            this.classLoaderHelper = new SystemClassLoaderHelper(classLoader, this);
        }

    }

    protected void addToClasspath(@NotNull Path file) {
        this.classLoaderHelper.addToClasspath(file);
    }
}
