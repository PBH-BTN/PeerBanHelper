package com.ghostchu.peerbanhelper;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class MainJumpLoader {
    static void main(String[] args) {
        // Do something before real Main class
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            setupCharsets();
            System.setProperty("sun.net.useExclusiveBind", "false");
        }
        Main.main(args);
    }

    private static void setupCharsets() {
        try {
            invokeCommand("cmd.exe /c chcp 65001", Collections.emptyMap());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int invokeCommand(String command, Map<String, String> env) throws IOException, ExecutionException, InterruptedException, TimeoutException {
        StringTokenizer st = new StringTokenizer(command);
        String[] cmdarray = new String[st.countTokens()];
        for (int i = 0; st.hasMoreTokens(); i++) {
            cmdarray[i] = st.nextToken();
        }
        ProcessBuilder builder = new ProcessBuilder(cmdarray)
                .inheritIO();
        Map<String, String> liveEnv = builder.environment();
        liveEnv.putAll(env);
        Process p = builder.start();
        Process process = p.onExit().get(10, TimeUnit.SECONDS);
        if (process.isAlive()) {
            process.destroy();
            return -9999;
        }
        return process.exitValue();
    }

}
