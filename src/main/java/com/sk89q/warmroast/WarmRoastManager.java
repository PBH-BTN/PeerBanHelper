package com.sk89q.warmroast;

import com.sun.tools.attach.*;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

@Slf4j
public class WarmRoastManager {
    private static WarmRoast roast;

    public static void start() throws IOException, AttachNotSupportedException, AgentLoadException, AgentInitializationException {
        RoastOptions opt = new RoastOptions();
        VirtualMachine vm;
        long pid = ProcessHandle.current().pid();
        System.setProperty("jdk.attach.allowAttachSelf", "true");
        vm = VirtualMachine.attach(String.valueOf(pid));
        log.debug("Attached to VM with PID: {}", pid);
        if (vm == null) {
            List<VirtualMachineDescriptor> descriptors = VirtualMachine.list();
            vm = VirtualMachine.attach(descriptors.getFirst());
        }
        roast = new WarmRoast(vm, opt.interval);
        if (opt.timeout != null && opt.timeout > 0) {
            roast.setEndTime(System.currentTimeMillis() + opt.timeout * 1000);
        }
        roast.connect();
        roast.start();
    }

    @Nullable
    public static WarmRoast getRoast() {
        return roast;
    }

    public static void stopAndReset() {
        if (roast != null) {
            roast.cancel();
            roast = null;
            log.debug("WarmRoast stopped and reset.");
        }
    }
}
