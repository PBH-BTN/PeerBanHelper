use axum::http::{HeaderMap, header};

pub(crate) struct Messages {
    pub(crate) auth_invalid: &'static str,
    pub(crate) auth_ok: &'static str,
    pub(crate) auth_limited: &'static str,
    pub(crate) auth_required: &'static str,
    pub(crate) need_init: &'static str,
    pub(crate) route_unavailable: &'static str,
    pub(crate) already_initialized: &'static str,
    pub(crate) downloader_created: &'static str,
    pub(crate) downloader_valid: &'static str,
}

// HTTP message keys in src/main/resources/lang/{en_us,zh_cn,zh_tw}/messages.yml.
const EN: Messages = Messages {
    auth_invalid: "Login failed, invalid token",
    auth_ok: "Login successful",
    auth_limited: "Too many login errors, this IP address has been temporarily banned",
    auth_required: "Operation failed, you are not logged in",
    need_init: "PeerBanHelper need redirect OOBE to initialize.",
    route_unavailable: "Given route not available or not exists, Is that feature module enabled? Route: {} {}",
    already_initialized: "This PeerBanHelper instance already initialized，OOBE interface no-longer available",
    downloader_created: "Downloader created successfully",
    downloader_valid: "Validation successful, configuration is valid",
};

const ZH_CN: Messages = Messages {
    auth_invalid: "登录失败，Token 无效",
    auth_ok: "登录成功",
    auth_limited: "登录错误次数过多，此 IP 地址已被暂时封禁",
    auth_required: "操作失败，您还未登录",
    need_init: "PeerBanHelper 需要 OOBE 向导初始化",
    route_unavailable: "指定的路由不可用或不存在，对应的功能模块是否开启？路由：{} {}",
    already_initialized: "此 PeerBanHelper 已被初始化，OOBE 接口不再可用。",
    downloader_created: "下载器创建成功",
    downloader_valid: "验证成功，配置有效",
};

const ZH_TW: Messages = Messages {
    auth_invalid: "登入失敗，Token 無效",
    auth_ok: "登入成功",
    auth_limited: "登入錯誤次數過多，此 IP 位址已被暫時封禁",
    auth_required: "操作失敗，您還未登入",
    need_init: "PeerBanHelper 需要 OOBE 嚮導初始化",
    route_unavailable: "指定的路由不可用或不存在，對應的功能模組是否開啟？路由：{} {}",
    already_initialized: "此 PeerBanHelper 已被初始化，OOBE 介面不再可用。",
    downloader_created: "下載器建立成功",
    downloader_valid: "驗證成功，配置有效",
};

pub(crate) fn preferred(headers: &HeaderMap) -> &'static Messages {
    let languages = headers
        .get(header::ACCEPT_LANGUAGE)
        .and_then(|value| value.to_str().ok());
    let mut selected = &EN;
    let mut max_quality = 0.0;
    for language in languages.unwrap_or_default().split(',') {
        let (locale, quality) = language.split_once(';').unwrap_or((language, "q=1"));
        let messages = match locale
            .trim()
            .to_ascii_lowercase()
            .replace('_', "-")
            .as_str()
        {
            "en-us" => &EN,
            "zh-cn" => &ZH_CN,
            "zh-tw" => &ZH_TW,
            _ => continue,
        };
        let quality = quality
            .trim()
            .strip_prefix("q=")
            .and_then(|value| value.parse::<f32>().ok())
            .unwrap_or(1.0);
        if quality > max_quality {
            selected = messages;
            max_quality = quality;
        }
    }
    selected
}
