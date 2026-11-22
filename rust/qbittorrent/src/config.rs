use serde::Deserialize;

/// qBittorrent settings from the downloader's section in config.yml.
#[derive(Clone, Deserialize)]
#[serde(default, rename_all = "kebab-case")]
pub struct Config {
    pub endpoint: String,
    pub username: String,
    pub password: String,
    pub api_key: String,
    pub basic_auth: BasicAuth,
    pub name: Option<String>,
    pub increment_ban: bool,
    pub use_shadow_ban: bool,
    pub verify_ssl: bool,
    pub ignore_private: bool,
    pub paused: bool,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            endpoint: String::new(),
            username: String::new(),
            password: String::new(),
            api_key: String::new(),
            basic_auth: BasicAuth::default(),
            name: None,
            increment_ban: false,
            use_shadow_ban: false,
            verify_ssl: true,
            ignore_private: false,
            paused: false,
        }
    }
}

#[derive(Clone, Default, Deserialize)]
#[serde(default)]
pub struct BasicAuth {
    pub user: String,
    pub pass: String,
}
