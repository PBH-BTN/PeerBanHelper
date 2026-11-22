//! Background refresh of the IPBlackRuleList module's source files.

use std::collections::{BTreeMap, HashMap};
use std::fs;
use std::io::Write;
use std::net::IpAddr;
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::Duration;

use anyhow::{Context, Result, bail};
use pbh_core::{AddressRule, PrefixMap, as_network, covering_network, parse_address};
use pbh_network::ProxyPolicy;
use reqwest::{Client, Url};
use serde::{Deserialize, Serialize};
use serde_json::json;
use sha2::{Digest, Sha256};
use tokio::sync::{Mutex, mpsc, oneshot, watch};
use tokio::task::JoinSet;

const MAX_FEED_BYTES: usize = 128 * 1024 * 1024;

use crate::background_tasks::BackgroundTasks;
use crate::storage::Database;

#[derive(Clone, Deserialize, PartialEq, Serialize)]
#[serde(default, rename_all = "kebab-case")]
pub(crate) struct SubscriptionPolicy {
    pub(crate) enabled: bool,
    pub(crate) ban_duration: serde_yaml_ng::Value,
    pub(crate) check_interval: u64,
    pub(crate) rules: BTreeMap<String, RuleSource>,
}

impl Default for SubscriptionPolicy {
    fn default() -> Self {
        Self {
            enabled: false,
            ban_duration: serde_yaml_ng::Value::Null,
            check_interval: 86_400_000,
            rules: BTreeMap::new(),
        }
    }
}

#[derive(Clone, Deserialize, Default, PartialEq, Serialize)]
#[serde(default)]
pub(crate) struct RuleSource {
    pub(crate) enabled: bool,
    pub(crate) name: Option<String>,
    pub(crate) url: String,
}

pub(crate) struct Feed {
    pub(crate) name: String,
    entries: PrefixMap<String>,
}

impl Feed {
    pub(crate) fn match_ip(&self, ip: IpAddr) -> Option<&str> {
        self.entries.find(ip).map(|(_, comment)| comment.as_str())
    }
}

#[derive(Clone)]
pub(crate) struct Subscriptions {
    runtime: Arc<Mutex<Runtime>>,
    database: Option<Arc<Mutex<Database>>>,
    data_dir: PathBuf,
    client: Client,
    background_tasks: BackgroundTasks,
}

struct Runtime {
    feeds: BTreeMap<String, ActiveFeed>,
    tasks: JoinSet<()>,
}

struct ActiveFeed {
    feed: watch::Receiver<Option<Arc<Feed>>>,
    refresh: mpsc::Sender<oneshot::Sender<Result<usize>>>,
}

struct Worker {
    id: String,
    name: String,
    url: Url,
    path: PathBuf,
    database: Option<Arc<Mutex<Database>>>,
    client: Client,
    background_tasks: BackgroundTasks,
}

impl Subscriptions {
    pub(crate) fn start(
        policy: &SubscriptionPolicy,
        data_dir: &Path,
        database: Option<Arc<Mutex<Database>>>,
        background_tasks: BackgroundTasks,
        proxy: &ProxyPolicy,
    ) -> Result<Self> {
        let client = proxy
            .apply(Client::builder())
            .timeout(Duration::from_secs(60))
            .build()?;
        let runtime = Runtime::start(
            policy,
            data_dir,
            &client,
            &database,
            &background_tasks,
            Duration::ZERO,
        )?;
        Ok(Self {
            runtime: Arc::new(Mutex::new(runtime)),
            data_dir: data_dir.to_owned(),
            database,
            client,
            background_tasks,
        })
    }

    pub(crate) async fn reload(&self, policy: &SubscriptionPolicy) -> Result<()> {
        let mut runtime = self.runtime.lock().await;
        let next = Runtime::start(
            policy,
            &self.data_dir,
            &self.client,
            &self.database,
            &self.background_tasks,
            Duration::from_secs(1),
        )?;
        runtime.tasks.shutdown().await;
        *runtime = next;
        Ok(())
    }

    pub(crate) async fn shutdown(&self) {
        self.runtime.lock().await.tasks.shutdown().await;
    }

    pub(crate) async fn snapshot(&self) -> Vec<Arc<Feed>> {
        self.runtime
            .lock()
            .await
            .feeds
            .values()
            .filter_map(|feed| feed.feed.borrow().clone())
            .collect()
    }

    pub(crate) async fn manual_refresh(&self, id: &str) -> Result<usize> {
        let task = self
            .runtime
            .lock()
            .await
            .feeds
            .get(id)
            .map(|feed| feed.refresh.clone())
            .context("subscription is missing or disabled")?;
        let (reply, receiver) = oneshot::channel();
        task.send(reply)
            .await
            .context("subscription task stopped")?;
        receiver.await.context("subscription task stopped")?
    }

    pub(crate) async fn validate_source(&self, source: &RuleSource) -> Result<()> {
        if source.enabled {
            refresh(
                self.client.clone(),
                source_url(&source.url)?,
                source.name.clone().unwrap_or_default(),
            )
            .await?;
        }
        Ok(())
    }
}

impl Runtime {
    fn start(
        policy: &SubscriptionPolicy,
        data_dir: &Path,
        client: &Client,
        database: &Option<Arc<Mutex<Database>>>,
        background_tasks: &BackgroundTasks,
        delay: Duration,
    ) -> Result<Self> {
        let interval = Duration::from_millis(policy.check_interval.max(1));
        let mut tasks = JoinSet::new();
        let mut feeds = BTreeMap::new();
        for (id, source) in &policy.rules {
            if !source.enabled {
                continue;
            }
            if !valid_id(id) {
                bail!("invalid subscription ID");
            }
            let worker = Worker {
                id: id.clone(),
                name: source.name.clone().unwrap_or_else(|| id.clone()),
                url: source_url(&source.url)?,
                path: data_dir.join("sub").join(format!("{id}.txt")),
                database: database.clone(),
                client: client.clone(),
                background_tasks: background_tasks.clone(),
            };
            let cached = fs::read(&worker.path)
                .ok()
                .filter(|bytes| bytes.len() <= MAX_FEED_BYTES)
                .and_then(|bytes| feed(&worker.name, &bytes).map(|feed| (feed, digest(&bytes))));
            let (cached, hash) = cached.map_or((None, None), |(feed, hash)| {
                (Some(Arc::new(feed)), Some(hash))
            });
            let (sender, feed) = watch::channel(cached);
            let (refresh, requests) = mpsc::channel(4);
            tasks.spawn(worker.run(sender, requests, hash, delay, interval));
            feeds.insert(id.clone(), ActiveFeed { feed, refresh });
        }
        Ok(Self { feeds, tasks })
    }
}

impl Worker {
    async fn run(
        self,
        sender: watch::Sender<Option<Arc<Feed>>>,
        mut requests: mpsc::Receiver<oneshot::Sender<Result<usize>>>,
        mut hash: Option<[u8; 32]>,
        delay: Duration,
        interval: Duration,
    ) {
        let mut next_update = tokio::time::Instant::now() + delay;
        loop {
            let reply = tokio::select! {
                result = requests.recv() => {
                    let Some(reply) = result else { return; };
                    Some(reply)
                },
                _ = tokio::time::sleep_until(next_update) => None,
            };
            let task = self
                .background_tasks
                .begin(json!({"key": "MODULE_IP_BLACK_RULE_LIST_UPDATING_RULE_TITLE"}));
            let outcome = self.update(&sender, &mut hash, reply.is_some()).await;
            if outcome.is_ok() {
                task.complete();
            } else {
                task.fail(Some(
                    json!({"key": "IP_BAN_RULE_UPDATE_FAILED", "params": [self.name]}),
                ));
            }
            if let Some(reply) = reply {
                let _ = reply.send(outcome);
            } else if let Err(error) = outcome {
                crate::journal::log_warning!("Refresh subscription {}: {error}", self.name);
            }
            next_update = tokio::time::Instant::now() + interval;
        }
    }

    async fn update(
        &self,
        sender: &watch::Sender<Option<Arc<Feed>>>,
        hash: &mut Option<[u8; 32]>,
        manual: bool,
    ) -> Result<usize> {
        let (new_feed, bytes) =
            refresh(self.client.clone(), self.url.clone(), self.name.clone()).await?;
        let new_hash = digest(&bytes);
        let count = new_feed.entries.len();
        if hash.as_ref() == Some(&new_hash) {
            return Ok(count);
        }
        if let Some(database) = &self.database {
            store_cache(self.path.clone(), bytes).await?;
            let now = (time::OffsetDateTime::now_utc().unix_timestamp_nanos() / 1_000_000) as i64;
            database
                .lock()
                .await
                .append_subscription_log(&self.id, now, i32::try_from(count)?, manual)
                .await?;
        }
        *hash = Some(new_hash);
        sender.send_replace(Some(Arc::new(new_feed)));
        Ok(count)
    }
}

fn source_url(value: &str) -> Result<Url> {
    let url = Url::parse(value).map_err(|_| anyhow::anyhow!("invalid subscription URL"))?;
    if !matches!(url.scheme(), "http" | "https")
        || !url.username().is_empty()
        || url.password().is_some()
    {
        bail!("subscription URL must use HTTP or HTTPS without URL credentials");
    }
    Ok(url)
}

pub(crate) fn valid_id(id: &str) -> bool {
    !id.is_empty()
        && id
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'-' | b'_'))
}

fn digest(bytes: &[u8]) -> [u8; 32] {
    Sha256::digest(bytes).into()
}

async fn refresh(client: Client, url: Url, name: String) -> Result<(Feed, Vec<u8>)> {
    let mut response = client
        .get(url)
        .send()
        .await
        .map_err(reqwest::Error::without_url)?
        .error_for_status()
        .map_err(reqwest::Error::without_url)?;
    let mut bytes = Vec::new();
    while let Some(chunk) = response
        .chunk()
        .await
        .map_err(reqwest::Error::without_url)?
    {
        if bytes.len().saturating_add(chunk.len()) > MAX_FEED_BYTES {
            bail!("subscription exceeds the supported size");
        }
        bytes.extend_from_slice(&chunk);
    }
    let feed = feed(&name, &bytes).context("subscription contained no usable addresses")?;
    Ok((feed, bytes))
}

async fn store_cache(path: PathBuf, bytes: Vec<u8>) -> Result<()> {
    tokio::task::spawn_blocking(move || -> Result<()> {
        let parent = path
            .parent()
            .context("subscription cache has no parent directory")?;
        fs::create_dir_all(parent)?;
        let mut temporary = tempfile::NamedTempFile::new_in(parent)?;
        temporary.write_all(&bytes)?;
        temporary.as_file().sync_all()?;
        temporary.persist(path).map_err(|error| error.error)?;
        Ok(())
    })
    .await?
}

fn feed(name: &str, bytes: &[u8]) -> Option<Feed> {
    let data = String::from_utf8_lossy(bytes);
    let mut parsed = HashMap::<ipnet::IpNet, String>::new();
    let mut preceding = Vec::new();
    for line in data.lines().map(str::trim_end) {
        if line.trim().is_empty() {
            continue;
        }
        if let Some(comment) = line.strip_prefix('#').or_else(|| line.strip_prefix("//")) {
            preceding.push(comment);
            continue;
        }
        let row = parse_line(line, &preceding);
        preceding.clear();
        if let Some((prefix, comment)) = row {
            parsed
                .entry(prefix)
                .and_modify(|old| {
                    old.push('\n');
                    old.push_str(&comment);
                })
                .or_insert(comment);
        }
    }
    if parsed.is_empty() {
        return None;
    }
    let mut entries = PrefixMap::default();
    for (prefix, comment) in parsed {
        entries.insert(prefix, comment);
    }
    Some(Feed {
        name: name.to_owned(),
        entries,
    })
}

fn parse_line(line: &str, preceding: &[&str]) -> Option<(ipnet::IpNet, String)> {
    if line.contains(',') {
        let mut fields = line.split(',');
        let AddressRule::Host(start) = parse_address(fields.next()?.trim())? else {
            return None;
        };
        let AddressRule::Host(end) = parse_address(fields.next()?.trim())? else {
            return None;
        };
        let level = fields.next()?.trim().parse::<i32>().ok()?;
        if level >= 128 {
            return None;
        }
        let comment = fields
            .next()
            .map_or_else(|| preceding.join("\n"), str::to_owned);
        return Some((covering_network(start, end)?, comment));
    }
    let separator = line.find('#').into_iter().chain(line.find("//")).min();
    let (address, comment) = match separator {
        Some(index) => {
            let width = if line.as_bytes()[index] == b'#' { 1 } else { 2 };
            (&line[..index], line[index + width..].to_owned())
        }
        None => (line, preceding.join("\n")),
    };
    Some((as_network(parse_address(address.trim())?)?, comment))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn matches_comments_and_filters_emule_levels() {
        let data = b"# first\n192.0.2.0/24\n192.0.2.0/24 // duplicate\n198.51.100.5, 198.51.100.9, 100, accepted\n203.0.113.0,203.0.113.255,200,ignored\n";
        let rules = feed("sample", data).unwrap();
        assert_eq!(
            rules.match_ip("192.0.2.5".parse().unwrap()),
            Some(" first\n duplicate")
        );
        assert_eq!(
            rules.match_ip("198.51.100.6".parse().unwrap()),
            Some(" accepted")
        );
        assert_eq!(rules.match_ip("203.0.113.5".parse().unwrap()), None);
    }
}
