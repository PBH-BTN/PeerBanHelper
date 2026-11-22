//! Notification providers configured under push-notification.

use std::collections::BTreeMap;
use std::path::Path;
use std::time::Duration;

use anyhow::{Context, Result, bail};
use base64::{Engine, engine::general_purpose::STANDARD};
use lettre::address::Envelope;
use lettre::message::{Mailbox, header::ContentType};
use lettre::transport::smtp::{
    authentication::Credentials,
    client::{Tls, TlsParameters},
};
use lettre::{AsyncSmtpTransport, AsyncTransport, Message, Tokio1Executor};
use pbh_storage::AlertLevel;
use reqwest::{Client, Method, RequestBuilder, Url, header, redirect};
use serde::{Deserialize, Serialize};
use serde_json::{Map, Value, json};
use serde_yaml_ng::{Mapping, Value as Yaml};
use time::{OffsetDateTime, UtcOffset};

const ICON: &str = "https://raw.githubusercontent.com/PBH-BTN/PeerBanHelper/refs/heads/master/src/main/resources/assets/icon.png";
const DEFAULT_WEBHOOK_BODY: &str = r#"{"title":"{title}","content":"{content}","level":"{level}","date":"{date}","time":"{time}","datetime":"{datetime}","channelName":"{channelName}"}"#;

#[derive(Clone, Deserialize, Serialize)]
pub(crate) struct Provider {
    pub(crate) name: String,
    #[serde(rename = "type")]
    pub(crate) kind: String,
    config: Map<String, Value>,
}

impl Provider {
    pub(crate) fn validate(&mut self) -> Result<()> {
        if self.name.trim().is_empty() || self.name.contains('.') {
            bail!("Invalid push provider name");
        }
        self.kind = self.kind.to_ascii_lowercase();
        if !matches!(
            self.kind.as_str(),
            "pushplus"
                | "serverchan"
                | "smtp"
                | "telegram"
                | "bark"
                | "pushdeer"
                | "gotify"
                | "ntfy"
                | "webhook"
        ) {
            bail!("Unsupported push provider");
        }
        Ok(())
    }

    fn decode(name: String, mut section: Mapping) -> Result<Self> {
        let mut kind = section
            .remove("type")
            .and_then(|value| value.as_str().map(str::to_owned))
            .context("missing push provider type")?;
        kind.make_ascii_lowercase();
        for &(yaml, json) in field_aliases(&kind) {
            if let Some(value) = section.remove(yaml) {
                section.insert(json.into(), value);
            }
        }
        let config = serde_yaml_ng::from_value(Yaml::Mapping(section))?;
        let mut provider = Self { name, kind, config };
        provider.validate()?;
        Ok(provider)
    }

    pub(crate) fn yaml(&self) -> Result<Yaml> {
        let Yaml::Mapping(mut section) = serde_yaml_ng::to_value(&self.config)? else {
            bail!("Invalid push provider options");
        };
        for &(yaml, json) in field_aliases(&self.kind) {
            if let Some(value) = section.remove(json) {
                section.insert(yaml.into(), value);
            }
        }
        section.insert("type".into(), self.kind.clone().into());
        Ok(Yaml::Mapping(section))
    }

    fn text(&self, field: &str) -> Option<&str> {
        self.config
            .get(field)
            .and_then(Value::as_str)
            .filter(|value| !value.trim().is_empty())
    }

    fn field(&self, field: &str) -> &str {
        self.text(field).unwrap_or_default()
    }

    fn priority(&self, default: i64) -> i64 {
        self.config
            .get("priority")
            .and_then(Value::as_i64)
            .unwrap_or(default)
    }

    pub(crate) async fn send(
        &self,
        title: &str,
        body: &str,
        level: AlertLevel,
        proxy: &pbh_network::ProxyPolicy,
    ) -> Result<()> {
        if self.kind == "smtp" {
            return self.send_mail(title, body).await;
        }
        let http = proxy
            .apply(Client::builder())
            .timeout(Duration::from_secs(30))
            .redirect(redirect::Policy::none())
            .build()?;
        let request = self.http_request(&http, title, body, level)?;
        let response = request.send().await.map_err(|error| error.without_url())?;
        if !response.status().is_success() {
            bail!("Push provider returned HTTP {}", response.status());
        }
        if self.kind == "pushplus" {
            if response
                .content_length()
                .is_some_and(|size| size > 64 * 1024)
            {
                bail!("PushPlus response exceeds size limit");
            }
            let result: Value = response.json().await.map_err(|error| error.without_url())?;
            if result["code"].as_i64() != Some(200) {
                bail!("PushPlus rejected the message");
            }
        }
        Ok(())
    }

    fn http_request(
        &self,
        http: &Client,
        title: &str,
        body: &str,
        level: AlertLevel,
    ) -> Result<RequestBuilder> {
        let request = match self.kind.as_str() {
            "bark" => http.post(url(self.text("backend_url").unwrap_or("https://api.day.app/push"))?)
                .json(&json!({"title": title, "body": body, "device_key": self.field("device_key"), "group": self.field("message_group"), "icon": ICON})),
            "pushdeer" => http.post(url(self.text("endpoint").unwrap_or("https://api2.pushdeer.com/message/push"))?)
                .json(&json!({"pushkey": self.field("pushkey"), "text": title, "desp": body, "type": "markdown"})),
            "gotify" => {
                let form = form_urlencoded::Serializer::new(String::new()).append_pair("title", title)
                    .append_pair("message", body).append_pair("priority", &self.priority(5).to_string()).finish();
                http.post(url(self.field("endpoint"))?).header(header::CONTENT_TYPE, "application/x-www-form-urlencoded").body(form)
            }
            "ntfy" => {
                let base = self.text("server_url").unwrap_or("https://ntfy.sh").trim_end_matches('/');
                let endpoint = format!("{base}/{}", self.field("topic"));
                let mut request = http.post(url(&endpoint)?).header(header::CONTENT_TYPE, "text/plain; charset=utf-8")
                    .header("Title", format!("=?UTF-8?B?{}?=", STANDARD.encode(title)))
                    .header("Priority", self.priority(3).to_string()).header("Icon", ICON).body(body.to_owned());
                if let Some(token) = self.text("token") { request = request.bearer_auth(token); }
                if let Some(tags) = self.text("tags") { request = request.header("Tags", tags); }
                request
            }
            "pushplus" => {
                let mut payload = json!({"token": self.field("token"), "title": title, "content": body, "template": "markdown"});
                for field in ["topic", "channel"] {
                    if let Some(value) = self.text(field) { payload[field] = json!(value); }
                }
                http.post("https://www.pushplus.plus/send").json(&payload)
            }
            "serverchan" => {
                let mut endpoint = Url::parse("https://sctapi.ftqq.com/")?;
                endpoint.path_segments_mut().expect("HTTP URL has a path").pop_if_empty().push(&format!("{}.send", self.field("sendKey")));
                let mut payload = json!({"title": title, "text": title, "desp": body});
                for (key, field) in [("channel", "channel"), ("openid", "openId")] {
                    if let Some(value) = self.text(field) { payload[key] = json!(value); }
                }
                http.post(endpoint).json(&payload)
            }
            "telegram" => {
                let mut endpoint = Url::parse("https://api.telegram.org/")?;
                endpoint.path_segments_mut().expect("HTTP URL has a path").pop_if_empty()
                    .push(&format!("bot{}", self.field("token"))).push("sendPhoto");
                let markdown = format!("*{title}*\n{body}");
                http.post(endpoint).json(&json!({"chat_id": self.field("chatId"), "caption": markdown, "text": markdown, "photo": ICON, "parse_mode": "Markdown"}))
            }
            "webhook" => self.webhook(http, title, body, level)?,
            _ => bail!("Unsupported HTTP push provider"),
        };
        Ok(request)
    }

    fn webhook(
        &self,
        http: &Client,
        title: &str,
        body: &str,
        level: AlertLevel,
    ) -> Result<RequestBuilder> {
        let method = self.text("method").unwrap_or("POST").to_ascii_uppercase();
        if !matches!(method.as_str(), "GET" | "POST") {
            bail!("Unsupported webhook method");
        }
        let content_type = self
            .field("content_type")
            .trim()
            .parse::<mime::Mime>()
            .unwrap_or(mime::APPLICATION_JSON)
            .to_string();
        let now = OffsetDateTime::now_utc();
        let local = now.to_offset(UtcOffset::local_offset_at(now).unwrap_or(UtcOffset::UTC));
        let date = local.date().to_string();
        let time = format!(
            "{:02}:{:02}:{:02}",
            local.hour(),
            local.minute(),
            local.second()
        );
        let datetime = format!("{date} {time}");
        let fields = [
            ("title", title),
            ("content", body),
            ("level", level.as_str()),
            ("date", &date),
            ("time", &time),
            ("datetime", &datetime),
            ("channelName", &self.name),
        ];
        let endpoint = render(self.field("url"), &fields, encoded_url);
        let mut headers = header::HeaderMap::new();
        let mut content = None;
        if method == "POST" {
            let transform = if content_type.to_ascii_lowercase().contains("json") {
                json_fragment
            } else {
                str::to_owned
            };
            let template = self.text("body_template").unwrap_or(DEFAULT_WEBHOOK_BODY);
            content = Some(render(template, &fields, transform));
            headers.insert(header::CONTENT_TYPE, content_type.parse()?);
        }
        if let Some(custom) = self.config.get("headers").and_then(Value::as_object) {
            for (name, value) in custom {
                if let Some(value) = value.as_str().filter(|_| !name.trim().is_empty()) {
                    let value = if value.is_ascii() {
                        value.to_owned()
                    } else {
                        use percent_encoding::{AsciiSet, utf8_percent_encode};
                        const NON_ASCII: &AsciiSet = &AsciiSet::EMPTY;
                        format!("UTF-8''{}", utf8_percent_encode(value, NON_ASCII))
                    };
                    headers.insert(
                        header::HeaderName::from_bytes(name.as_bytes())?,
                        value.parse()?,
                    );
                }
            }
        }
        let request = http
            .request(Method::from_bytes(method.as_bytes())?, url(&endpoint)?)
            .headers(headers);
        Ok(match content {
            Some(content) => request.body(content),
            None => request,
        })
    }

    async fn send_mail(&self, title: &str, body: &str) -> Result<()> {
        let host = self.text("host").context("SMTP host missing")?;
        let port = self
            .config
            .get("port")
            .and_then(Value::as_u64)
            .and_then(|port| u16::try_from(port).ok())
            .unwrap_or(465);
        let tls = match self.text("encryption").unwrap_or("SSLTLS") {
            "NONE" => Tls::None,
            "STARTTLS" => Tls::Opportunistic(TlsParameters::new(host.into())?),
            "ENFORCE_STARTTLS" => Tls::Required(TlsParameters::new(host.into())?),
            "SSLTLS" => Tls::Wrapper(TlsParameters::new(host.into())?),
            _ => bail!("Invalid SMTP encryption mode"),
        };
        let mut transport = AsyncSmtpTransport::<Tokio1Executor>::builder_dangerous(host)
            .port(port)
            .tls(tls)
            .timeout(Some(Duration::from_secs(30)));
        if self.config.get("auth").and_then(Value::as_bool) == Some(true) {
            transport = transport.credentials(Credentials::new(
                self.field("username").into(),
                self.field("password").into(),
            ));
        }
        let relay = transport.build();
        let sender = Mailbox::new(
            Some(self.text("senderName").unwrap_or("PeerBanHelper").into()),
            self.field("sender").parse()?,
        );
        let recipients = self
            .config
            .get("receivers")
            .and_then(Value::as_array)
            .into_iter()
            .flatten()
            .filter_map(Value::as_str)
            .filter_map(|address| address.parse::<Mailbox>().ok())
            .collect::<Vec<_>>();
        let mut builder = Message::builder()
            .from(sender.clone())
            .subject(title)
            .header(ContentType::TEXT_HTML);
        for recipient in &recipients {
            builder = builder.to(recipient.clone());
        }
        let mut html = String::new();
        pulldown_cmark::html::push_html(&mut html, pulldown_cmark::Parser::new(body));
        let message = builder.body(html)?;
        if self
            .config
            .get("sendPartial")
            .and_then(Value::as_bool)
            .unwrap_or(true)
            && recipients.len() > 1
        {
            let content = message.formatted();
            let mut failure = None;
            for recipient in recipients {
                let envelope = Envelope::new(Some(sender.email.clone()), vec![recipient.email])?;
                if let Err(error) = relay.send_raw(&envelope, &content).await {
                    failure = Some(error);
                }
            }
            if let Some(error) = failure {
                return Err(error.into());
            }
        } else {
            relay.send(message).await?;
        }
        Ok(())
    }
}

pub(crate) fn read_providers(config_path: &Path) -> Result<BTreeMap<String, Provider>> {
    let yaml: Yaml = crate::config::read_yaml(config_path)?;
    let mut providers = BTreeMap::new();
    if let Some(section) = yaml["push-notification"].as_mapping() {
        for (name, settings) in section {
            if let (Some(name), Some(settings)) = (name.as_str(), settings.as_mapping())
                && let Ok(provider) = Provider::decode(name.to_owned(), settings.clone())
            {
                providers.insert(name.to_owned(), provider);
            }
        }
    }
    Ok(providers)
}

fn field_aliases(kind: &str) -> &'static [(&'static str, &'static str)] {
    match kind {
        "webhook" => &[
            ("content-type", "content_type"),
            ("body-template", "body_template"),
        ],
        "telegram" => &[("chatid", "chatId")],
        "serverchan" => &[("sendkey", "sendKey"), ("openid", "openId")],
        "smtp" => &[("name", "senderName"), ("receiver", "receivers")],
        _ => &[],
    }
}

fn url(endpoint: &str) -> Result<Url> {
    let url = Url::parse(endpoint).map_err(|_| anyhow::anyhow!("Invalid push endpoint URL"))?;
    if !matches!(url.scheme(), "http" | "https")
        || !url.username().is_empty()
        || url.password().is_some()
    {
        bail!("Expected an HTTP(S) push endpoint without URL credentials");
    }
    Ok(url)
}

fn render(template: &str, fields: &[(&str, &str)], transform: fn(&str) -> String) -> String {
    let mut result = template.to_owned();
    for (key, value) in fields {
        result = result.replace(&format!("{{{key}}}"), &transform(value));
    }
    result
}

fn json_fragment(value: &str) -> String {
    let escaped = serde_json::to_string(value).expect("JSON strings serialize without error");
    escaped[1..escaped.len() - 1].to_owned()
}

fn encoded_url(value: &str) -> String {
    form_urlencoded::byte_serialize(value.as_bytes())
        .collect::<String>()
        .replace('+', "%20")
}
