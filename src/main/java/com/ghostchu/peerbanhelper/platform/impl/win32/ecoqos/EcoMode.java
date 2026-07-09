package com.ghostchu.peerbanhelper.platform.impl.win32.ecoqos;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static com.ghostchu.peerbanhelper.platform.impl.win32.ecoqos.EcoModeConstants.*;

public class EcoMode {

    public String setEcoMode(boolean enable) {
        try (Arena arena = Arena.ofConfined()) {
            PROCESS_POWER_THROTTLING_STATE powerThrottling = new PROCESS_POWER_THROTTLING_STATE(arena);
            powerThrottling.setVersion(PROCESS_POWER_THROTTLING_STATE.PROCESS_POWER_THROTTLING_CURRENT_VERSION);

            if (enable) {
                powerThrottling.setControlMask(PROCESS_POWER_THROTTLING_STATE.PROCESS_POWER_THROTTLING_EXECUTION_SPEED);
                powerThrottling.setStateMask(PROCESS_POWER_THROTTLING_STATE.PROCESS_POWER_THROTTLING_EXECUTION_SPEED);
            } else {
                powerThrottling.setControlMask(0);
                powerThrottling.setStateMask(0);
            }

            MemorySegment currentProcess = EcoModeNative.getCurrentProcess();
            String message;

            if (!EcoModeNative.setProcessInformation(
                    currentProcess,
                    ProcessPowerThrottling,
                    powerThrottling.segment(),
                    (int) powerThrottling.byteSize())) {
                int error = EcoModeNative.getLastError();
                message = "SetProcessInformation failed with error: " + error;
                return message;
            }

            int priorityClass = enable ? IDLE_PRIORITY_CLASS : NORMAL_PRIORITY_CLASS;
            if (!EcoModeNative.setPriorityClass(currentProcess, priorityClass)) {
                int error = EcoModeNative.getLastError();
                message = "SetPriorityClass failed with error: " + error;
            } else {
                message = "SUCCESS";
            }
            return message;
        }
    }

    public int setPriority(int priority) {
        MemorySegment hProcess = EcoModeNative.getCurrentProcess();
        int dwPriorityClass;

        switch (priority) {
            case -1:
                dwPriorityClass = IDLE_PRIORITY_CLASS;
                break;
            case 0:
                dwPriorityClass = NORMAL_PRIORITY_CLASS;
                break;
            case 1:
                dwPriorityClass = HIGH_PRIORITY_CLASS;
                break;
            case 2:
                dwPriorityClass = REALTIME_PRIORITY_CLASS;
                break;
            default:
                return -1; // Invalid priority
        }

        if (EcoModeNative.setPriorityClass(hProcess, dwPriorityClass)) {
            return 0; // Success
        } else {
            return -1; // Failure
        }
    }
}