//! Track the lifetime of background work owned by the scan and web runtimes.

use std::collections::VecDeque;
use std::convert::Infallible;
use std::sync::{Arc, Mutex, Weak};
use std::time::{Duration, Instant};

use axum::Router;
use axum::extract::State;
use axum::http::HeaderMap;
use axum::response::IntoResponse;
use axum::response::sse::{Event, KeepAlive, Sse};
use axum::routing::get;
use futures_util::stream;
use serde::Serialize;
use serde_json::Value;
use tokio::sync::broadcast;
use uuid::Uuid;

use crate::ban_api::BanLanguage;

const UPDATE_BUFFER: usize = 256;
const SUCCESS_RETENTION: Duration = Duration::from_secs(60);
const OTHER_RETENTION: Duration = Duration::from_secs(30 * 60);

#[derive(Clone)]
pub(crate) struct BackgroundTasks {
    shared: Arc<Shared>,
    fallback_language: BanLanguage,
}

struct Shared {
    state: Mutex<Registry>,
    updates: broadcast::Sender<Update>,
}

#[derive(Default)]
struct Registry {
    tasks: Vec<Tracked>,
    closed: bool,
}

struct Tracked {
    task: Snapshot,
    finished: Option<Instant>,
}

#[derive(Clone)]
enum Update {
    Task(Snapshot),
    Shutdown,
}

#[derive(Clone)]
struct Snapshot {
    id: Uuid,
    title: Value,
    status_text: Option<Value>,
    status: Status,
    bar_type: BarType,
    current: i64,
    max: i64,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
enum Status {
    Queued,
    Running,
    Completed,
    Failed,
    Cancelled,
}

impl Status {
    fn snapshot_rank(self) -> u8 {
        match self {
            Self::Running => 0,
            Self::Completed | Self::Failed => 1,
            Self::Queued | Self::Cancelled => 2,
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub(crate) enum BarType {
    Determinate,
    Indeterminate,
}

/// Owned by the worker that performs the operation. Dropping an unfinished
/// handle publishes cancellation, including on async task abort.
pub(crate) struct Task {
    shared: Arc<Shared>,
    id: Option<Uuid>,
}

impl Default for BackgroundTasks {
    fn default() -> Self {
        Self::new("en_us")
    }
}

impl BackgroundTasks {
    pub(crate) fn new(locale: &str) -> Self {
        let (updates, _) = broadcast::channel(UPDATE_BUFFER);
        Self {
            shared: Arc::new(Shared {
                state: Mutex::new(Registry::default()),
                updates,
            }),
            fallback_language: BanLanguage::from_locale(locale),
        }
    }

    /// Starts tracking work in the caller's existing scheduler. Titles and
    /// status text use the same translation component JSON as ban messages.
    pub(crate) fn begin(&self, title: Value) -> Task {
        let mut state = self
            .shared
            .state
            .lock()
            .expect("background task registry lock poisoned");
        state.prune(Instant::now());
        let id = if state.closed {
            None
        } else {
            let id = Uuid::new_v4();
            let mut task = Snapshot {
                id,
                title,
                status_text: None,
                status: Status::Queued,
                bar_type: BarType::Indeterminate,
                current: 0,
                max: 0,
            };
            self.shared.send(&task);
            task.status = Status::Running;
            self.shared.send(&task);
            state.tasks.push(Tracked {
                task,
                finished: None,
            });
            Some(id)
        };
        Task {
            shared: self.shared.clone(),
            id,
        }
    }

    /// Cancel outstanding work in the view and let SSE listeners exit.
    pub(crate) fn shutdown(&self) {
        let mut state = self
            .shared
            .state
            .lock()
            .expect("background task registry lock poisoned");
        if state.closed {
            return;
        }
        state.closed = true;
        let now = Instant::now();
        for task in state
            .tasks
            .iter_mut()
            .filter(|task| task.finished.is_none())
        {
            task.finish(Status::Cancelled, now);
            self.shared.send(&task.task);
        }
        let _ = self.shared.updates.send(Update::Shutdown);
    }
}

impl Task {
    pub(crate) fn progress(
        &self,
        bar_type: BarType,
        current: i64,
        max: i64,
        status_text: Option<Value>,
    ) {
        self.mutate(|tracked| {
            tracked.task.bar_type = bar_type;
            tracked.task.current = current;
            tracked.task.max = max;
            tracked.task.status_text = status_text;
        });
    }

    pub(crate) fn complete(mut self) {
        self.finish(Status::Completed, None);
    }

    pub(crate) fn fail(mut self, status_text: Option<Value>) {
        self.finish(Status::Failed, status_text);
    }

    fn finish(&mut self, status: Status, status_text: Option<Value>) {
        self.mutate(|tracked| {
            if status_text.is_some() {
                tracked.task.status_text = status_text;
            }
            tracked.finish(status, Instant::now());
        });
        self.id = None;
    }

    fn mutate(&self, change: impl FnOnce(&mut Tracked)) {
        let Some(id) = self.id else { return };
        let mut state = self
            .shared
            .state
            .lock()
            .expect("background task registry lock poisoned");
        if state.closed {
            return;
        }
        state.prune(Instant::now());
        let Some(tracked) = state
            .tasks
            .iter_mut()
            .find(|tracked| tracked.task.id == id && tracked.finished.is_none())
        else {
            return;
        };
        change(tracked);
        self.shared.send(&tracked.task);
    }
}

impl Drop for Task {
    fn drop(&mut self) {
        self.finish(Status::Cancelled, None);
    }
}

impl Registry {
    fn prune(&mut self, now: Instant) {
        self.tasks.retain(|tracked| {
            tracked.finished.is_none_or(|finished| {
                let retention = if tracked.task.status == Status::Completed {
                    SUCCESS_RETENTION
                } else {
                    OTHER_RETENTION
                };
                now.saturating_duration_since(finished) <= retention
            })
        });
    }

    fn snapshot(&self) -> VecDeque<Snapshot> {
        let mut tasks = self
            .tasks
            .iter()
            .rev()
            .map(|tracked| &tracked.task)
            .collect::<Vec<_>>();
        tasks.sort_by_key(|task| task.status.snapshot_rank());
        tasks.into_iter().cloned().collect()
    }
}

impl Tracked {
    fn finish(&mut self, status: Status, now: Instant) {
        self.finished = Some(now);
        self.task.status = status;
    }
}

impl Shared {
    fn send(&self, task: &Snapshot) {
        let _ = self.updates.send(Update::Task(task.clone()));
    }

    fn subscribe(shared: &Arc<Self>) -> StreamState {
        let mut state = shared
            .state
            .lock()
            .expect("background task registry lock poisoned");
        state.prune(Instant::now());
        // Producers publish while holding this lock. Taking the subscription
        // and snapshot together prevents missed or duplicate startup updates.
        StreamState {
            shared: Arc::downgrade(shared),
            initial: state.snapshot(),
            updates: (!state.closed).then(|| shared.updates.subscribe()),
        }
    }
}

struct StreamState {
    shared: Weak<Shared>,
    initial: VecDeque<Snapshot>,
    updates: Option<broadcast::Receiver<Update>>,
}

impl StreamState {
    async fn next(&mut self) -> Option<Snapshot> {
        loop {
            if let Some(task) = self.initial.pop_front() {
                return Some(task);
            }
            match self.updates.as_mut()?.recv().await {
                Ok(Update::Task(task)) => return Some(task),
                Ok(Update::Shutdown) | Err(broadcast::error::RecvError::Closed) => return None,
                Err(broadcast::error::RecvError::Lagged(_)) => {
                    *self = Shared::subscribe(&self.shared.upgrade()?);
                }
            }
        }
    }
}

pub(crate) fn router(tasks: BackgroundTasks) -> Router {
    Router::new()
        .route("/api/tasks/live", get(live))
        .with_state(tasks)
}

async fn live(State(tasks): State<BackgroundTasks>, headers: HeaderMap) -> impl IntoResponse {
    let language = BanLanguage::from_headers_or(&headers, tasks.fallback_language);
    let events = stream::unfold(
        Shared::subscribe(&tasks.shared),
        move |mut state| async move {
            let task = state.next().await?;
            let event = Event::default()
                .json_data(task.view(language))
                .expect("background task view is serializable");
            Some((Ok::<_, Infallible>(event), state))
        },
    );
    Sse::new(events).keep_alive(KeepAlive::new().interval(Duration::from_secs(15)))
}

#[derive(Serialize)]
struct TaskEvent {
    #[serde(rename = "type")]
    kind: &'static str,
    task: TaskView,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct TaskView {
    id: String,
    title: String,
    status_text: Option<String>,
    status: Status,
    bar_type: BarType,
    progress: f64,
    current: i64,
    max: i64,
}

impl Snapshot {
    fn view(&self, language: BanLanguage) -> TaskEvent {
        let progress = if self.max == 0 {
            0.0
        } else {
            self.current as f64 / self.max as f64
        };
        TaskEvent {
            kind: "UPDATED",
            task: TaskView {
                id: self.id.to_string(),
                title: language.render(&self.title),
                status_text: self.status_text.as_ref().map(|text| language.render(text)),
                status: self.status,
                bar_type: self.bar_type,
                progress,
                current: self.current,
                max: self.max,
            },
        }
    }
}

#[cfg(test)]
mod tests {
    use pbh_web::{BuildVersion, Manifest, WebConfig};
    use serde_json::json;

    use super::*;

    struct Reader {
        response: reqwest::Response,
        bytes: Vec<u8>,
    }

    impl Reader {
        async fn next(&mut self) -> Option<Value> {
            tokio::time::timeout(Duration::from_secs(3), async {
                loop {
                    if let Some(end) = self.bytes.windows(2).position(|part| part == b"\n\n") {
                        let frame = self.bytes.drain(..end + 2).collect::<Vec<_>>();
                        if let Some(data) = frame.strip_prefix(b"data: ") {
                            return Some(serde_json::from_slice(data).unwrap());
                        }
                    } else {
                        match self.response.chunk().await.unwrap() {
                            Some(chunk) => self.bytes.extend_from_slice(&chunk),
                            None => return None,
                        }
                    }
                }
            })
            .await
            .expect("SSE did not produce a response in time")
        }
    }

    #[tokio::test]
    async fn authenticated_sse_localizes_initial_and_live_progress_and_closes_on_shutdown() {
        let tasks = BackgroundTasks::new("zh_tw");
        let active = tasks.begin(json!({"key":"IPDB_DOWNLOAD_MMDB","params":["GeoIP-City"]}));
        let completed = tasks.begin(json!({"key":"BTN_ABILITY_ALLOW_LIST_SYNC_SERVER"}));
        completed.complete();
        let config = WebConfig {
            token: "task-fixture".into(),
            manifest: Manifest {
                version: BuildVersion {
                    version: "fixture".into(),
                    os: String::new(),
                    branch: String::new(),
                    commit: String::new(),
                    abbrev: String::new(),
                },
                modules: Vec::new(),
                installation_id: "fixture".into(),
                analytics: false,
            },
            static_dir: None,
            allow_cors: false,
        };
        let app = pbh_web::router_with_protected_routes(config, router(tasks.clone()));
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let url = format!("http://{}/api/tasks/live", listener.local_addr().unwrap());
        let server = tokio::spawn(async move {
            pbh_web::serve(listener, app, std::future::pending())
                .await
                .unwrap()
        });
        let client = reqwest::Client::builder().no_proxy().build().unwrap();
        assert_eq!(client.get(&url).send().await.unwrap().status(), 403);

        let mut fallback = Reader {
            response: client
                .get(format!("{url}?token=task-fixture"))
                .header("accept-language", "unsupported")
                .send()
                .await
                .unwrap(),
            bytes: Vec::new(),
        };
        assert_eq!(fallback.response.status(), 200);
        assert_eq!(
            fallback.response.headers()["content-type"],
            "text/event-stream"
        );
        let first = fallback.next().await.unwrap();
        let id = first["task"]["id"].as_str().unwrap();
        assert!(Uuid::parse_str(id).is_ok());
        assert_eq!(
            first,
            json!({"type":"UPDATED","task":{
                "id":id,"title":"[GeoIPDB] 下載資料庫: GeoIP-City","statusText":null,
                "status":"RUNNING","barType":"INDETERMINATE","progress":0.0,"current":0,"max":0
            }})
        );
        let second = fallback.next().await.unwrap();
        assert_eq!(second["task"]["status"], "COMPLETED");

        let mut en = Reader {
            response: client
                .get(&url)
                .bearer_auth("task-fixture")
                .header("accept-language", "zh-CN;q=0.5,en-US;q=0.9")
                .send()
                .await
                .unwrap(),
            bytes: Vec::new(),
        };
        assert_eq!(
            en.next().await.unwrap()["task"]["title"],
            "[GeoIPDB] Download database: GeoIP-City"
        );
        assert_eq!(en.next().await.unwrap()["task"]["status"], "COMPLETED");
        active.progress(
            BarType::Determinate,
            9,
            12,
            Some(json!({"key":"BTN_ABILITY_ALLOW_LIST_SYNC_SERVER"})),
        );
        let update = fallback.next().await.unwrap();
        assert_eq!(update["task"]["progress"], 0.75);
        assert_eq!(update["task"]["statusText"], "[BTN 網路] 同步 IP 允許列表");
        assert_eq!(
            en.next().await.unwrap()["task"]["statusText"],
            "[BTN Network] Sync IP allowlist"
        );
        drop(en);
        active.fail(Some(
            json!({"key":"IPDB_DOWNLOAD_MMDB_FAILED_DESCRIPTION","params":["fixture"]}),
        ));
        let failed = fallback.next().await.unwrap();
        assert_eq!(failed["task"]["status"], "FAILED");
        assert_eq!(
            failed["task"]["statusText"],
            "從遠端伺服器下載失敗: fixture"
        );
        let dropped = tasks.begin(json!("cancelled worker"));
        assert_eq!(fallback.next().await.unwrap()["task"]["status"], "QUEUED");
        assert_eq!(fallback.next().await.unwrap()["task"]["status"], "RUNNING");
        drop(dropped);
        assert_eq!(
            fallback.next().await.unwrap()["task"]["status"],
            "CANCELLED"
        );
        let in_progress = tasks.begin(json!("worker at shutdown"));
        assert_eq!(fallback.next().await.unwrap()["task"]["status"], "QUEUED");
        assert_eq!(fallback.next().await.unwrap()["task"]["status"], "RUNNING");
        tasks.shutdown();
        assert_eq!(
            fallback.next().await.unwrap()["task"]["status"],
            "CANCELLED"
        );
        assert!(fallback.next().await.is_none());
        drop(in_progress);
        server.abort();
    }

    #[tokio::test]
    async fn slow_receiver_resynchronizes_to_current_task_snapshot() {
        let tasks = BackgroundTasks::default();
        let mut receiver = Shared::subscribe(&tasks.shared);
        let task = tasks.begin(json!("fixture"));
        for index in 1..=300 {
            task.progress(BarType::Determinate, index, 300, None);
        }
        let latest = receiver.next().await.unwrap();
        assert_eq!(latest.current, 300);
        assert_eq!(latest.status, Status::Running);
        assert!(matches!(
            receiver.updates.as_mut().unwrap().try_recv(),
            Err(broadcast::error::TryRecvError::Empty)
        ));
        assert_eq!(tasks.shared.updates.receiver_count(), 1);
        drop(receiver);
        assert_eq!(tasks.shared.updates.receiver_count(), 0);
    }

    #[test]
    fn finished_tasks_expire_at_their_java_retention_windows() {
        let tasks = BackgroundTasks::default();
        tasks.begin(json!("completed")).complete();
        tasks.begin(json!("failed")).fail(None);
        let _active = tasks.begin(json!("active"));
        let mut state = tasks.shared.state.lock().unwrap();
        let now = Instant::now();
        state.prune(now + Duration::from_secs(61));
        let snapshot = state.snapshot();
        assert_eq!(
            snapshot.iter().map(|task| task.status).collect::<Vec<_>>(),
            [Status::Running, Status::Failed]
        );
        state.prune(now + Duration::from_secs(30 * 60 + 1));
        assert_eq!(state.snapshot().len(), 1);
    }
}
