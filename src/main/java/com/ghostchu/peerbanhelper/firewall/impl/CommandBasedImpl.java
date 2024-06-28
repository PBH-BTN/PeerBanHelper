package com.ghostchu.peerbanhelper.firewall.impl;

import com.ghostchu.peerbanhelper.text.Lang;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public class CommandBasedImpl {

    public int invokeCommand(String command, Map<String, String> env) throws IOException, ExecutionException, InterruptedException, TimeoutException {
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
            log.warn(Lang.COMMAND_EXECUTOR_FAILED_TIMEOUT, command);
            return -9999;
        }
        return process.exitValue();
    }
}
