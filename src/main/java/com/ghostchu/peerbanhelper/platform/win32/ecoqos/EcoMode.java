package com.ghostchu.peerbanhelper.platform.win32.ecoqos;

import com.sun.jna.platform.win32.WinNT;

import static com.ghostchu.peerbanhelper.platform.win32.ecoqos.EcoModeConstants.*;

public class EcoMode {

    public String setEcoMode(boolean enable) {
        PROCESS_POWER_THROTTLING_STATE powerThrottling = new PROCESS_POWER_THROTTLING_STATE();
        powerThrottling.Version = PROCESS_POWER_THROTTLING_STATE.PROCESS_POWER_THROTTLING_CURRENT_VERSION;

        if (enable) {
            powerThrottling.ControlMask = PROCESS_POWER_THROTTLING_STATE.PROCESS_POWER_THROTTLING_EXECUTION_SPEED;
            powerThrottling.StateMask = PROCESS_POWER_THROTTLING_STATE.PROCESS_POWER_THROTTLING_EXECUTION_SPEED;
        } else {
            powerThrottling.ControlMask = 0;
            powerThrottling.StateMask = 0;
        }

        WinNT.HANDLE currentProcess = EcoModeNative.INSTANCE.GetCurrentProcess();
        String message;

        if (!EcoModeNative.INSTANCE.SetProcessInformation(
                currentProcess,
                ProcessPowerThrottling,
                powerThrottling,
                powerThrottling.size())) {
            int error = EcoModeNative.INSTANCE.GetLastError();
            message = "SetProcessInformation failed with error: " + error;
            return message;
        }

        int priorityClass = enable ? IDLE_PRIORITY_CLASS : NORMAL_PRIORITY_CLASS;
        if (!EcoModeNative.INSTANCE.SetPriorityClass(currentProcess, priorityClass)) {
            int error = EcoModeNative.INSTANCE.GetLastError();
            message = "SetPriorityClass failed with error: " + error;
        } else {
            message = "SUCCESS";
        }
        return message;
    }

    public int setPriority(int priority) {
        WinNT.HANDLE hProcess = EcoModeNative.INSTANCE.GetCurrentProcess();
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

        if (EcoModeNative.INSTANCE.SetPriorityClass(hProcess, dwPriorityClass)) {
            return 0; // Success
        } else {
            return -1; // Failure
        }
    }
}