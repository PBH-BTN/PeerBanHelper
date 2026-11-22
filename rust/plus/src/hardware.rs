use uuid::Uuid;

/// Hardware UUID used to select an existing per-machine Java local public key.
pub fn hardware_uuid() -> Option<String> {
    let raw = platform_uuid()?;
    validate(&raw)
}

fn validate(raw: &str) -> Option<String> {
    let value = raw.trim();
    let uuid = Uuid::parse_str(value).ok()?;
    (!uuid.is_nil() && uuid != Uuid::max()).then(|| value.to_owned())
}

#[cfg(windows)]
fn platform_uuid() -> Option<String> {
    use windows_sys::Win32::System::SystemInformation::GetSystemFirmwareTable;
    const RSMB: u32 = u32::from_be_bytes(*b"RSMB");
    // SAFETY: A null pointer and size zero ask Windows for the required size.
    let byte_count = unsafe { GetSystemFirmwareTable(RSMB, 0, std::ptr::null_mut(), 0) };
    if !(8..=1024 * 1024).contains(&byte_count) {
        return None;
    }
    let mut bytes = vec![0; byte_count as usize];
    // SAFETY: The vector is writable for byte_count bytes during the call.
    let found = unsafe { GetSystemFirmwareTable(RSMB, 0, bytes.as_mut_ptr(), byte_count) };
    if found != byte_count {
        return None;
    }
    parse_windows_smbios(&bytes).map(|uuid| uuid.to_string().to_ascii_uppercase())
}

#[cfg(any(windows, test))]
fn parse_windows_smbios(bytes: &[u8]) -> Option<Uuid> {
    // RawSMBIOSData starts with version bytes and a little-endian table length.
    let header = bytes.get(..8)?;
    let length = u32::from_le_bytes(header[4..8].try_into().ok()?) as usize;
    let mut remaining = bytes.get(8..)?.get(..length)?;
    while let [kind, size, ..] = remaining {
        let size = *size as usize;
        if size < 4 || *kind == 127 {
            return None;
        }
        let formatted = remaining.get(..size)?;
        if *kind == 1 {
            let mut uuid: [u8; 16] = formatted.get(8..24)?.try_into().ok()?;
            if (header[1], header[2]) >= (2, 6) {
                // Newer SMBIOS stores the first three UUID fields little-endian.
                uuid[..4].reverse();
                uuid[4..6].reverse();
                uuid[6..8].reverse();
            }
            return Some(Uuid::from_bytes(uuid));
        }
        // Skip the NUL-terminated string set and its extra NUL terminator.
        let after = remaining.get(size..)?;
        let terminator = after.windows(2).position(|bytes| bytes == [0, 0])?;
        remaining = after.get(terminator + 2..)?;
    }
    None
}

#[cfg(target_os = "linux")]
fn platform_uuid() -> Option<String> {
    std::fs::read_to_string("/sys/class/dmi/id/product_uuid")
        .ok()
        .and_then(|value| validate(&value))
        .or_else(|| command_uuid("/usr/sbin/dmidecode", &["-s", "system-uuid"]))
}

#[cfg(target_os = "freebsd")]
fn platform_uuid() -> Option<String> {
    command_uuid("/bin/kenv", &["-q", "smbios.system.uuid"])
        .or_else(|| command_uuid("/sbin/sysctl", &["-n", "kern.hostuuid"]))
}

#[cfg(any(target_os = "linux", target_os = "freebsd"))]
fn command_uuid(path: &str, args: &[&str]) -> Option<String> {
    let output = std::process::Command::new(path).args(args).output().ok()?;
    if !output.status.success() {
        return None;
    }
    validate(std::str::from_utf8(&output.stdout).ok()?)
}

#[cfg(target_os = "macos")]
fn platform_uuid() -> Option<String> {
    let output = std::process::Command::new("/usr/sbin/ioreg")
        .args(["-rd1", "-c", "IOPlatformExpertDevice"])
        .output()
        .ok()?;
    if !output.status.success() {
        return None;
    }
    std::str::from_utf8(&output.stdout)
        .ok()?
        .lines()
        .find_map(|line| {
            let (_, suffix) = line.split_once("\"IOPlatformUUID\" = ")?;
            validate(suffix.trim().trim_matches('"'))
        })
}

#[cfg(not(any(
    windows,
    target_os = "linux",
    target_os = "macos",
    target_os = "freebsd"
)))]
fn platform_uuid() -> Option<String> {
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_little_endian_smbios_type_one_after_a_string_set() {
        let mut table = vec![0, 4, 0, 0, b'X', 0, 0];
        table.extend([1, 24, 1, 0, 0, 0, 0, 0]);
        table.extend([
            0x78, 0x56, 0x34, 0x12, 0xcd, 0xab, 0x00, 0xef, 1, 2, 3, 4, 5, 6, 7, 8,
        ]);
        table.extend([0, 0]);
        let mut raw = vec![0, 3, 0, 0];
        raw.extend((table.len() as u32).to_le_bytes());
        raw.extend(table);
        assert_eq!(
            parse_windows_smbios(&raw).unwrap().to_string(),
            "12345678-abcd-ef00-0102-030405060708"
        );
        assert!(parse_windows_smbios(&raw[..raw.len() - 2]).is_none());
        assert!(validate("unknown").is_none());
        assert!(validate("00000000-0000-0000-0000-000000000000").is_none());
        assert!(validate("ffffffff-ffff-ffff-ffff-ffffffffffff").is_none());
    }
}
