//! Controls applied only to the PeerBanHelper process.

use std::io;

use windows_sys::Win32::System::Threading::{
    GetCurrentProcess, IDLE_PRIORITY_CLASS, NORMAL_PRIORITY_CLASS,
    PROCESS_POWER_THROTTLING_CURRENT_VERSION, PROCESS_POWER_THROTTLING_EXECUTION_SPEED,
    PROCESS_POWER_THROTTLING_STATE, ProcessPowerThrottling, SetPriorityClass,
    SetProcessInformation,
};

pub fn set_eco_qos(enabled: bool) -> io::Result<()> {
    let execution_speed = PROCESS_POWER_THROTTLING_EXECUTION_SPEED;
    let state = PROCESS_POWER_THROTTLING_STATE {
        Version: PROCESS_POWER_THROTTLING_CURRENT_VERSION,
        ControlMask: if enabled { execution_speed } else { 0 },
        StateMask: if enabled { execution_speed } else { 0 },
    };
    let priority = if enabled {
        IDLE_PRIORITY_CLASS
    } else {
        NORMAL_PRIORITY_CLASS
    };
    // The pseudo-handle identifies this process and does not require closing.
    // The power information is read synchronously from the initialized struct.
    unsafe {
        let current = GetCurrentProcess();
        if SetProcessInformation(
            current,
            ProcessPowerThrottling,
            (&raw const state).cast(),
            size_of::<PROCESS_POWER_THROTTLING_STATE>() as u32,
        ) == 0
        {
            return Err(io::Error::last_os_error());
        }
        if SetPriorityClass(current, priority) == 0 {
            return Err(io::Error::last_os_error());
        }
    }
    Ok(())
}
