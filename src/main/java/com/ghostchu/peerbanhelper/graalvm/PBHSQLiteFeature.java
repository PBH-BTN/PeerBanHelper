package com.ghostchu.peerbanhelper.graalvm;

import com.ghostchu.peerbanhelper.Main;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;
import org.sqlite.SQLiteJDBCLoader;
import org.sqlite.util.LibraryLoaderUtil;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class PBHSQLiteFeature implements Feature {

    public static String getNativeLibResourcePath(String sysArch) {
        String packagePath = SQLiteJDBCLoader.class.getPackage().getName().replace(".", "/");
        return String.format("/%s/native/%s", packagePath, sysArch);
    }

    @Override
    public void beforeAnalysis(Feature.BeforeAnalysisAccess a) {
        a.registerReachabilityHandler(
                this::nativeDbReachable, method(SQLiteJDBCLoader.class, "initialize"));
    }

    private void nativeDbReachable(Feature.DuringAnalysisAccess a) {
        handleBuildInfo();
        handleLibraryResources(getNativeLibResourcePath("Linux/aarch64"), "libsqlitejdbc.so");
        handleLibraryResources(getNativeLibResourcePath("Linux/armv7"), "libsqlitejdbc.so");
        handleLibraryResources(getNativeLibResourcePath("Linux/x86"), "libsqlitejdbc.so");
        handleLibraryResources(getNativeLibResourcePath("Linux/x86_64"), "libsqlitejdbc.so");
        handleLibraryResources(getNativeLibResourcePath("Linux-Musl/aarch64"), "libsqlitejdbc.so");
        handleLibraryResources(getNativeLibResourcePath("Linux-Musl/x86"), "libsqlitejdbc.so");
        handleLibraryResources(getNativeLibResourcePath("Linux-Musl/x86_64"), "libsqlitejdbc.so");
    }

    private void handleBuildInfo() {
        RuntimeResourceAccess.addResource(Main.class.getModule(), "/build-info.yml");
    }

    private void handleLibraryResources(String libraryPath, String libraryName) {
        // Sanity check
        if (!LibraryLoaderUtil.hasNativeLib(libraryPath, libraryName)) {
            throw new SqliteJdbcFeatureException(
                    "Unable to locate the required native resources for native-image. Please contact the maintainers of sqlite-jdbc.");
        }

        // libraryResource always has a leading '/'
        String libraryResource = libraryPath + "/" + libraryName;
        String exportLocation = System.getProperty("org.sqlite.lib.exportPath", "");
        if (exportLocation.isEmpty()) {
            // Do not export the library and include it in native-image instead
            RuntimeResourceAccess.addResource(
                    SQLiteJDBCLoader.class.getModule(), libraryResource.substring(1));
        } else {
            // export the required library to the specified path,
            // the user is responsible to make sure the created native-image can actually find it.
            Path targetPath = Paths.get(exportLocation, libraryName);
            try (InputStream in = SQLiteJDBCLoader.class.getResourceAsStream(libraryResource)) {
                Files.createDirectories(targetPath.getParent());
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new SqliteJdbcFeatureException(e);
            }
        }
    }

    private Method method(Class<?> clazz, String methodName, Class<?>... args) {
        try {
            return clazz.getDeclaredMethod(methodName, args);
        } catch (NoSuchMethodException e) {
            throw new SqliteJdbcFeatureException(e);
        }
    }


    private static class SqliteJdbcFeatureException extends RuntimeException {
        private SqliteJdbcFeatureException(Throwable cause) {
            super(cause);
        }

        private SqliteJdbcFeatureException(String message) {
            super(message);
        }
    }
}
