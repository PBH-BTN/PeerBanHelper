#include <jni.h>
#include <windows.h>
#include <processthreadsapi.h>
#include <string>

extern "C"
{
    JNIEXPORT jstring JNICALL Java_com_ghostchu_lib_jni_EcoMode_setEcoMode(JNIEnv *env, jobject obj, jobject enable)
    {
        PROCESS_POWER_THROTTLING_STATE PowerThrottling;
        RtlZeroMemory(&PowerThrottling, sizeof(PowerThrottling));
        PowerThrottling.Version = PROCESS_POWER_THROTTLING_CURRENT_VERSION;

        //
        // EcoQoS
        // Turn EXECUTION_SPEED throttling on.
        // ControlMask selects the mechanism and StateMask declares which mechanism should be on or off.
        //

        PowerThrottling.ControlMask = enable ? PROCESS_POWER_THROTTLING_EXECUTION_SPEED : NULL;
        PowerThrottling.StateMask = enable ? PROCESS_POWER_THROTTLING_EXECUTION_SPEED : NULL;

        std::string message;

        if (!SetProcessInformation(GetCurrentProcess(),
                                   ProcessPowerThrottling,
                                   &PowerThrottling,
                                   sizeof(PowerThrottling)))
        {
            DWORD error = GetLastError();
            message = "SetProcessInformation failed with error: " + std::to_string(error);
            return env->NewStringUTF(message.c_str());
        }

        if (!SetPriorityClass(GetCurrentProcess(), enable ? IDLE_PRIORITY_CLASS : NORMAL_PRIORITY_CLASS))
        {
            DWORD error = GetLastError();
            message = "SetPriorityClass failed with error: " + std::to_string(error);
        }
        else
        {
            message = "SUCCESS";
        }
        // 将 C++ 字符串转换为 Java 字符串并返回
        return env->NewStringUTF(message.c_str());
    }

    JNIEXPORT jint JNICALL Java_com_ghostchu_lib_jni_ProcessPriority_setPriority(JNIEnv *env, jclass cls, jint priority)
    {
        HANDLE hProcess = GetCurrentProcess();
        DWORD dwPriorityClass;

        switch (priority)
        {
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

        if (SetPriorityClass(hProcess, dwPriorityClass))
        {
            return 0; // Success
        }
        else
        {
            return -1; // Failure
        }
    }
}
