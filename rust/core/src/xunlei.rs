//! The Xunlei preset in the Java AntiVampire module.

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum XunleiBan {
    OtherVersion,
    Version0019Seeding,
}

pub fn xunlei_ban(
    peer_id: Option<&str>,
    client_name: Option<&str>,
    seeding: bool,
) -> Option<XunleiBan> {
    let peer_id = peer_id.map(str::to_lowercase).unwrap_or_default();
    let client_name = client_name.map(str::to_lowercase).unwrap_or_default();
    let xunlei_client = client_name.starts_with("xunlei");
    if !peer_id.starts_with("-xl") && !xunlei_client {
        return None;
    }

    let version_0019 = peer_id.starts_with("-xl0019")
        || (xunlei_client && (client_name.contains("0019") || client_name.contains("0.0.1.9")));
    if version_0019 {
        seeding.then_some(XunleiBan::Version0019Seeding)
    } else {
        Some(XunleiBan::OtherVersion)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn recognizes_peer_id_and_name_and_allows_0019_when_downloading() {
        assert_eq!(xunlei_ban(Some("-XL0019-"), None, false), None);
        assert_eq!(
            xunlei_ban(None, Some("Xunlei 0.0.1.9"), true),
            Some(XunleiBan::Version0019Seeding)
        );
        assert_eq!(
            xunlei_ban(Some("-XL1000-"), Some("other0019"), false),
            Some(XunleiBan::OtherVersion)
        );
        assert_eq!(xunlei_ban(Some("-OTHER-"), Some("Not Xunlei"), true), None);
    }
}
